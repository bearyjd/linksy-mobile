//! Accepted media sessions, by id, for Android's media code to use straight
//! through JNI (`app.linksy.media.NativeSession`), not through Dart:
//!
//! - `screen`, `camera`: the phone writes H.264; nothing comes back.
//! - `control`: the phone writes H.264 and reads framed input messages.
//! - `display`: the phone reads H.264 and writes framed input messages.
//! - `input`: the phone (touchpad, keyboard) writes framed input messages.
//! - `screen` / `control` the phone asked for: it reads the computer's H.264
//!   and, for `control`, writes framed input messages.
//!
//! Each session gets a task that owns its streams: it writes what the app
//! queues and hands what arrives to the app, until either side ends.

use std::collections::HashMap;
use std::sync::{Arc, Mutex, OnceLock};

use linksy_core::{Node, Session, SessionKind};
use tokio::sync::{Notify, mpsc};

use crate::api::node::rt;

struct Handle {
    /// The device on the other side, and the session's id, for messages
    /// about it (the camera's state).
    peer: linksy_core::Fingerprint,
    /// Taken to end the session: a reader blocked in `read` may still hold
    /// the handle, so ending can't wait for it to be dropped.
    out: Mutex<Option<mpsc::Sender<Vec<u8>>>>,
    /// What the other side sent, for kinds where the phone reads.
    incoming: Mutex<Option<mpsc::Receiver<Vec<u8>>>>,
    /// Wakes the session's task if it's waiting on a reader that stopped.
    closed: Arc<Notify>,
}

fn sessions() -> &'static Mutex<HashMap<String, Arc<Handle>>> {
    static MAP: OnceLock<Mutex<HashMap<String, Arc<Handle>>>> = OnceLock::new();
    MAP.get_or_init(Default::default)
}

/// Keeps every session the user accepted, until it ends.
pub(crate) fn collect(node: &Node) {
    let Some(mut accepted) = node.incoming_sessions() else { return };
    rt().spawn(async move {
        while let Some(session) = accepted.recv().await {
            // Only a computer typing on this phone comes in as `input`; core
            // refuses it unless the keyboard is on.
            if session.kind == SessionKind::Input {
                if let Some(typing) = session.keyboard_input() {
                    run_typing(typing);
                }
            } else {
                run(session);
            }
        }
    });
}

pub(crate) fn run(mut session: Session) {
    let id = session.id.to_string();
    // The phone reads video when it watches (a second display, or a computer's
    // screen it asked for), and input when it's controlled. An `input`
    // session only writes.
    let watching = !session.sharing && session.kind != SessionKind::Input;
    // A computer may send `camera_control` on a camera session (docs/android-app.md).
    let reads = watching || (session.sharing && matches!(session.kind, SessionKind::Control | SessionKind::Camera));
    // Video in ends the session when the sharer stops.
    let ends_with_input = watching;
    let (out, mut queued) = mpsc::channel::<Vec<u8>>(32);
    let (arrived, incoming) = mpsc::channel::<Vec<u8>>(256);
    let closed = Arc::new(Notify::new());
    sessions().lock().unwrap().insert(
        id.clone(),
        Arc::new(Handle {
            peer: session.peer.id,
            out: Mutex::new(Some(out)),
            incoming: Mutex::new(reads.then_some(incoming)),
            closed: closed.clone(),
        }),
    );

    rt().spawn(async move {
        let mut buf = vec![0u8; 64 * 1024];
        let mut reading = true;
        let Session { send, recv, .. } = &mut session;
        loop {
            tokio::select! {
                next = queued.recv() => match next {
                    Some(bytes) => {
                        if send.write_all(&bytes).await.is_err() {
                            break;
                        }
                    }
                    // The app ended it.
                    None => {
                        let _ = send.finish();
                        break;
                    }
                },
                read = recv.read(&mut buf), if reading => match read {
                    Ok(Some(n)) => {
                        if reads {
                            tokio::select! {
                                sent = arrived.send(buf[..n].to_vec()) => if sent.is_err() { break },
                                // Ended while the app had stopped reading.
                                _ = closed.notified() => break,
                            }
                        }
                    }
                    // A viewer that never sends may finish its half early:
                    // only a display, where this is the video, ends here.
                    Ok(None) if ends_with_input => break,
                    Ok(None) => reading = false,
                    Err(_) => break,
                },
            }
        }
        sessions().lock().unwrap().remove(&id);
        // Dropping the session reports `SessionEnded`.
        drop(session);
    });
}

/// Registers a session the phone only reads (typing): no way to write to it.
/// The returned sender feeds [`read`]; the notify is woken by [`end`].
fn add_incoming(id: &str, peer: linksy_core::Fingerprint) -> (mpsc::Sender<Vec<u8>>, Arc<Notify>) {
    let (arrived, incoming) = mpsc::channel::<Vec<u8>>(256);
    let closed = Arc::new(Notify::new());
    sessions().lock().unwrap().insert(
        id.to_string(),
        Arc::new(Handle {
            peer,
            out: Mutex::new(None),
            incoming: Mutex::new(Some(incoming)),
            closed: closed.clone(),
        }),
    );
    (arrived, closed)
}

/// An accepted `input` session on this phone: a computer typing (§4.26). Held
/// until the typing ends, the user stops it or the keyboard is turned off.
fn run_typing(typing: linksy_core::keyboard::KeyboardInput) {
    let id = typing.id.to_string();
    let (tx, closed) = add_incoming(&id, typing.peer.id);
    rt().spawn(pump_typing(typing, tx, closed));
}

/// Moves what the source types into the session's queue until it ends. The
/// entry stays until the app has read what is still queued ([`read`] forgets
/// it then); only [`end`] discards queued input. Dropping the source ends the
/// session; core then reports `SessionEnded`.
async fn pump_typing(
    mut source: impl crate::keyboard::Source + Send + 'static,
    tx: mpsc::Sender<Vec<u8>>,
    closed: Arc<Notify>,
) {
    crate::keyboard::forward(&mut source, tx, closed).await;
    drop(source);
}

fn handle(id: &str) -> Option<Arc<Handle>> {
    sessions().lock().unwrap().get(id).cloned()
}

/// Queues bytes for the other side. False once the session is gone: the
/// app's encoder or input should stop. Blocks while the queue is full, which
/// paces the encoder to the network.
pub(crate) fn write(id: &str, bytes: Vec<u8>) -> bool {
    let Some(out) = handle(id).and_then(|h| h.out.lock().unwrap().clone()) else { return false };
    out.blocking_send(bytes).is_ok()
}

/// [`write`] for async callers (Dart): waits for room instead of blocking.
pub(crate) async fn write_async(id: &str, bytes: Vec<u8>) -> bool {
    let Some(out) = handle(id).and_then(|h| h.out.lock().unwrap().clone()) else { return false };
    out.send(bytes).await.is_ok()
}

/// The webcam's capabilities and controls (JSON), to the computer using it
/// (`camera_state`, docs/protocol.md §4.8), sent on the core's runtime.
pub(crate) fn camera_state(id: &str, json: &str) {
    let (Some(h), Ok(session), Some(node)) = (handle(id), linksy_core::Uuid::parse_str(id), crate::api::node::running()) else { return };
    let Ok(state) = serde_json::from_str::<serde_json::Value>(json) else { return };
    let peer = h.peer;
    rt().spawn(async move {
        if let Err(e) = node.send_camera_state(&peer, session, state).await {
            tracing::debug!(error = %e, "camera state didn't go out");
        }
    });
}

/// The next bytes from the other side, blocking; None once it's over.
pub(crate) fn read(id: &str) -> Option<Vec<u8>> {
    let h = handle(id)?;
    let next = {
        let mut slot = h.incoming.lock().unwrap();
        slot.as_mut()?.blocking_recv()
    };
    if next.is_none() {
        // Over and drained: forget it, unless the id already belongs to a newer session.
        let mut map = sessions().lock().unwrap();
        if map.get(id).is_some_and(|now| Arc::ptr_eq(now, &h)) {
            map.remove(id);
        }
    }
    next
}

/// What arrives on a session, for a reader in Dart (a terminal's output):
/// the receiver itself, taken once.
pub(crate) fn take_incoming(id: &str) -> Option<mpsc::Receiver<Vec<u8>>> {
    handle(id)?.incoming.lock().unwrap().take()
}

/// Ends this side; the core then reports `SessionEnded`. Closing the queue
/// ends the session's task, which also wakes a reader waiting in `read`.
pub(crate) fn end(id: &str) {
    if let Some(h) = sessions().lock().unwrap().remove(id) {
        h.out.lock().unwrap().take();
        h.closed.notify_one();
    }
}

#[cfg(target_os = "android")]
mod jni_api {
    use jni::JNIEnv;
    use jni::objects::{JByteArray, JClass, JString};
    use jni::sys::{JNI_FALSE, JNI_TRUE, jboolean, jbyteArray};

    fn id(env: &mut JNIEnv, id: &JString) -> Option<String> {
        env.get_string(id).ok().map(Into::into)
    }

    #[unsafe(no_mangle)]
    pub extern "system" fn Java_app_linksy_media_NativeSession_write(
        mut env: JNIEnv,
        _this: JClass,
        session: JString,
        data: JByteArray,
    ) -> jboolean {
        let Some(session) = id(&mut env, &session) else { return JNI_FALSE };
        let Ok(bytes) = env.convert_byte_array(&data) else { return JNI_FALSE };
        if super::write(&session, bytes) { JNI_TRUE } else { JNI_FALSE }
    }

    #[unsafe(no_mangle)]
    pub extern "system" fn Java_app_linksy_media_NativeSession_read(
        mut env: JNIEnv,
        _this: JClass,
        session: JString,
    ) -> jbyteArray {
        let Some(session) = id(&mut env, &session) else { return std::ptr::null_mut() };
        match super::read(&session) {
            Some(bytes) => env.byte_array_from_slice(&bytes).map(|a| a.into_raw()).unwrap_or(std::ptr::null_mut()),
            None => std::ptr::null_mut(),
        }
    }

    #[unsafe(no_mangle)]
    pub extern "system" fn Java_app_linksy_media_NativeSession_cameraState(
        mut env: JNIEnv,
        _this: JClass,
        session: JString,
        json: JString,
    ) {
        let (Some(session), Some(json)) = (id(&mut env, &session), id(&mut env, &json)) else { return };
        super::camera_state(&session, &json);
    }

    #[unsafe(no_mangle)]
    pub extern "system" fn Java_app_linksy_media_NativeSession_finish(
        mut env: JNIEnv,
        _this: JClass,
        session: JString,
    ) {
        if let Some(session) = id(&mut env, &session) {
            super::end(&session);
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn peer() -> linksy_core::Fingerprint {
        linksy_core::Fingerprint::of(b"a computer")
    }

    #[test]
    fn a_typing_session_is_read_in_order_and_cannot_be_written_to() {
        let (tx, _closed) = add_incoming("typing-1", peer());
        tx.blocking_send(b"Tone".to_vec()).unwrap();
        tx.blocking_send(b"Ttwo".to_vec()).unwrap();
        assert_eq!(read("typing-1").as_deref(), Some(&b"Tone"[..]));
        assert_eq!(read("typing-1").as_deref(), Some(&b"Ttwo"[..]));
        assert!(!write("typing-1", vec![1]));
        end("typing-1");
    }

    #[test]
    fn ending_wakes_the_session_and_forgets_it() {
        let (tx, closed) = add_incoming("typing-2", peer());
        end("typing-2");
        // The permit is kept for the task that waits on it later.
        let rt = tokio::runtime::Builder::new_current_thread().enable_time().build().unwrap();
        rt.block_on(async {
            tokio::time::timeout(std::time::Duration::from_secs(2), closed.notified()).await.expect("woken by end");
        });
        assert_eq!(read("typing-2"), None);
        drop(tx);
    }

    #[test]
    fn the_reader_sees_the_end_when_the_source_is_dropped() {
        let (tx, _closed) = add_incoming("typing-3", peer());
        drop(tx);
        assert_eq!(read("typing-3"), None);
        end("typing-3");
    }

    #[test]
    fn a_close_after_a_burst_still_delivers_every_queued_event_then_ends() {
        let (tx, _closed) = add_incoming("typing-4", peer());
        for i in 0..200u32 {
            tx.blocking_send(i.to_be_bytes().to_vec()).unwrap();
        }
        // The stream hit EOF: the source is gone, the entry must stay for the reader.
        drop(tx);
        assert!(handle("typing-4").is_some());
        for i in 0..200u32 {
            assert_eq!(read("typing-4"), Some(i.to_be_bytes().to_vec()));
        }
        assert_eq!(read("typing-4"), None);
        // Drained: now it is forgotten.
        assert!(handle("typing-4").is_none());
    }

    #[test]
    fn ending_by_the_user_discards_what_was_queued() {
        let (tx, _closed) = add_incoming("typing-5", peer());
        tx.blocking_send(b"Tx".to_vec()).unwrap();
        end("typing-5");
        assert_eq!(read("typing-5"), None);
        drop(tx);
    }

    /// Yields `items`, then ends.
    struct Script(std::collections::VecDeque<linksy_core::keyboard::KeyInput>);

    impl crate::keyboard::Source for Script {
        fn next(&mut self) -> impl std::future::Future<Output = Option<linksy_core::keyboard::KeyInput>> + Send {
            std::future::ready(self.0.pop_front())
        }
    }

    #[test]
    fn the_typing_task_leaves_the_entry_for_the_reader_after_the_source_ends() {
        use linksy_core::keyboard::KeyInput;
        let (tx, closed) = add_incoming("typing-6", peer());
        let items = (0..3).map(|i| KeyInput::Text(format!("t{i}"))).collect();
        let rt = tokio::runtime::Builder::new_current_thread().enable_time().build().unwrap();
        // The same task `run_typing` spawns, run to the source's EOF.
        rt.block_on(pump_typing(Script(items), tx, closed));
        assert!(handle("typing-6").is_some(), "the task must not forget the entry at EOF");
        for i in 0..3 {
            assert_eq!(read("typing-6"), Some(format!("Tt{i}").into_bytes()));
        }
        assert_eq!(read("typing-6"), None);
        assert!(handle("typing-6").is_none());
    }
}

//! Typing sessions: a computer typing on this phone (docs/protocol.md §4.26).
//! Core hands over [`KeyInput`]s; they are turned into small byte messages
//! and read by the Linksy Keyboard (Kotlin) through `NativeSession.read`,
//! never through Dart. What is typed is never logged here.

use std::future::Future;
use std::sync::Arc;

use linksy_core::keyboard::KeyInput;
use tokio::sync::{Notify, mpsc};

/// `T` + the text (UTF-8), or `K` + 1 (pressed) / 0 + the keysym (big-endian
/// u32). Kotlin's `TypingEvent.decode` reads the same.
pub(crate) fn encode(_input: &KeyInput) -> Vec<u8> {
    todo!()
}

/// Passes what `next` yields on to `tx`, encoded, until the source ends, the
/// reader is gone or `closed` is notified. Never logs what was typed.
pub(crate) async fn forward<F, Fut>(_next: F, _tx: mpsc::Sender<Vec<u8>>, _closed: Arc<Notify>)
where
    F: FnMut() -> Fut,
    Fut: Future<Output = Option<KeyInput>>,
{
    todo!()
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::VecDeque;

    fn scripted(items: Vec<KeyInput>) -> impl FnMut() -> std::future::Ready<Option<KeyInput>> {
        let mut items: VecDeque<KeyInput> = items.into();
        move || std::future::ready(items.pop_front())
    }

    #[test]
    fn encodes_text_as_t_and_utf8() {
        assert_eq!(encode(&KeyInput::Text("hé".into())), [b'T', b'h', 0xC3, 0xA9]);
    }

    #[test]
    fn encodes_keys_as_k_pressed_and_big_endian_keysym() {
        assert_eq!(encode(&KeyInput::Key { keysym: 0xFF0D, pressed: true }), [b'K', 1, 0, 0, 0xFF, 0x0D]);
        assert_eq!(encode(&KeyInput::Key { keysym: 0x0100_20AC, pressed: false }), [b'K', 0, 1, 0, 0x20, 0xAC]);
    }

    #[tokio::test]
    async fn forwards_in_order_then_closes_when_the_source_ends() {
        let (tx, mut rx) = mpsc::channel(8);
        let input = vec![
            KeyInput::Text("a".into()),
            KeyInput::Key { keysym: 0xFF08, pressed: true },
            KeyInput::Key { keysym: 0xFF08, pressed: false },
        ];
        forward(scripted(input.clone()), tx, Arc::new(Notify::new())).await;
        let mut got = Vec::new();
        while let Some(bytes) = rx.recv().await {
            got.push(bytes);
        }
        assert_eq!(got, input.iter().map(encode).collect::<Vec<_>>());
    }

    #[tokio::test]
    async fn stops_at_once_when_closed_even_if_the_source_has_more() {
        let (tx, mut rx) = mpsc::channel(8);
        let closed = Arc::new(Notify::new());
        // `notify_one` keeps its permit until a waiter takes it.
        closed.notify_one();
        let endless = || std::future::ready(Some(KeyInput::Text("x".into())));
        tokio::time::timeout(std::time::Duration::from_secs(2), forward(endless, tx, closed))
            .await
            .expect("forward must return when closed");
        assert!(rx.recv().await.is_none() || rx.try_recv().is_err());
    }

    #[tokio::test]
    async fn stops_when_the_reader_is_gone() {
        let (tx, rx) = mpsc::channel(1);
        drop(rx);
        let endless = || std::future::ready(Some(KeyInput::Text("x".into())));
        tokio::time::timeout(std::time::Duration::from_secs(2), forward(endless, tx, Arc::new(Notify::new())))
            .await
            .expect("forward must return when nobody reads");
    }

    #[test]
    fn a_key_input_debug_never_shows_the_text() {
        let shown = format!("{:?}", KeyInput::Text("hunter2".into()));
        assert!(!shown.contains("hunter2"));
    }
}

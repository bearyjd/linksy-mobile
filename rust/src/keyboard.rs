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
pub(crate) fn encode(input: &KeyInput) -> Vec<u8> {
    match input {
        KeyInput::Text(text) => [&b"T"[..], text.as_bytes()].concat(),
        KeyInput::Key { keysym, pressed } => {
            let mut bytes = vec![b'K', u8::from(*pressed)];
            bytes.extend_from_slice(&keysym.to_be_bytes());
            bytes
        }
    }
}

/// Where typed input comes from: core's `KeyboardInput`, or a script in tests.
pub(crate) trait Source {
    fn next(&mut self) -> impl Future<Output = Option<KeyInput>> + Send;
}

impl Source for linksy_core::keyboard::KeyboardInput {
    fn next(&mut self) -> impl Future<Output = Option<KeyInput>> + Send {
        linksy_core::keyboard::KeyboardInput::next(self)
    }
}

/// Passes what `source` yields on to `tx`, encoded, until the source ends, the
/// reader is gone or `closed` is notified. Never logs what was typed.
pub(crate) async fn forward(
    source: &mut impl Source,
    tx: mpsc::Sender<Vec<u8>>,
    closed: Arc<Notify>,
) {
    loop {
        let input = tokio::select! {
            biased;
            _ = closed.notified() => break,
            input = source.next() => match input {
                Some(input) => input,
                None => break,
            },
        };
        tokio::select! {
            biased;
            _ = closed.notified() => break,
            sent = tx.send(encode(&input)) => if sent.is_err() { break },
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::VecDeque;

    /// Yields `items` in order, then ends; `endless` never ends.
    struct Script {
        items: VecDeque<KeyInput>,
        endless: bool,
    }

    impl Source for Script {
        fn next(&mut self) -> impl Future<Output = Option<KeyInput>> + Send {
            let next = if self.endless {
                Some(KeyInput::Text("x".into()))
            } else {
                self.items.pop_front()
            };
            std::future::ready(next)
        }
    }

    fn scripted(items: Vec<KeyInput>) -> Script {
        Script {
            items: items.into(),
            endless: false,
        }
    }

    fn endless() -> Script {
        Script {
            items: VecDeque::new(),
            endless: true,
        }
    }

    #[test]
    fn encodes_text_as_t_and_utf8() {
        assert_eq!(
            encode(&KeyInput::Text("hé".into())),
            [b'T', b'h', 0xC3, 0xA9]
        );
    }

    #[test]
    fn encodes_keys_as_k_pressed_and_big_endian_keysym() {
        assert_eq!(
            encode(&KeyInput::Key {
                keysym: 0xFF0D,
                pressed: true
            }),
            [b'K', 1, 0, 0, 0xFF, 0x0D]
        );
        assert_eq!(
            encode(&KeyInput::Key {
                keysym: 0x0100_20AC,
                pressed: false
            }),
            [b'K', 0, 1, 0, 0x20, 0xAC]
        );
    }

    #[tokio::test]
    async fn forwards_in_order_then_closes_when_the_source_ends() {
        let (tx, mut rx) = mpsc::channel(8);
        let input = vec![
            KeyInput::Text("a".into()),
            KeyInput::Key {
                keysym: 0xFF08,
                pressed: true,
            },
            KeyInput::Key {
                keysym: 0xFF08,
                pressed: false,
            },
        ];
        forward(&mut scripted(input.clone()), tx, Arc::new(Notify::new())).await;
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
        tokio::time::timeout(
            std::time::Duration::from_secs(2),
            forward(&mut endless(), tx, closed),
        )
        .await
        .expect("forward must return when closed");
        assert!(rx.recv().await.is_none() || rx.try_recv().is_err());
    }

    #[tokio::test]
    async fn stops_when_the_reader_is_gone() {
        let (tx, rx) = mpsc::channel(1);
        drop(rx);
        tokio::time::timeout(
            std::time::Duration::from_secs(2),
            forward(&mut endless(), tx, Arc::new(Notify::new())),
        )
        .await
        .expect("forward must return when nobody reads");
    }

    #[test]
    fn a_key_input_debug_never_shows_the_text() {
        let shown = format!("{:?}", KeyInput::Text("hunter2".into()));
        assert!(!shown.contains("hunter2"));
    }
}

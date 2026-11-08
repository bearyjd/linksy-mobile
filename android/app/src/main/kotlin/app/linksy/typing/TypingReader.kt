package app.linksy.typing

/// The loop that reads a typing session on its own thread, with everything it
/// touches passed in so it can be tested. Each event is stamped with the field
/// generation read BEFORE it waits for room: if the user moves to another field
/// while it waits, the event is still for the old one and is dropped there
/// ([TypingGate.canType]), never typed into the new one. [finish] runs on every
/// way out, including a throw.
class TypingReader(
    private val canRead: () -> Boolean,
    private val read: () -> ByteArray?,
    private val fieldGeneration: () -> Int,
    private val acquire: (wanted: () -> Boolean) -> Boolean,
    private val post: (TypingEvent, Int) -> Unit,
    private val finish: () -> Unit,
    private val pause: (Long) -> Unit,
) {
    fun run() {
        try {
            var gotSome = false
            var tries = 0
            while (canRead()) {
                val bytes = read()
                if (bytes == null) {
                    if (!gotSome && tries++ < REGISTER_TRIES) {
                        pause(REGISTER_WAIT_MS)
                        continue
                    }
                    break
                }
                gotSome = true
                val event = TypingEvent.decode(bytes) ?: continue
                val field = fieldGeneration()
                if (!acquire(canRead)) break
                post(event, field)
            }
        } finally {
            finish()
        }
    }

    companion object {
        /// How long a session may take to show up on the Rust side.
        const val REGISTER_TRIES = 20
        const val REGISTER_WAIT_MS = 100L
    }
}

package app.linksy.typing

/// What a computer typed on this phone, as the Rust side hands it over
/// (rust/src/keyboard.rs): `T` + UTF-8 text, or `K` + pressed (1/0) + the
/// X11 keysym as a big-endian u32.
sealed interface TypingEvent {
    data class Text(val text: String) : TypingEvent {
        /// Never the text: it can be a password.
        override fun toString() = "Text(len=${text.length})"
    }

    data class Key(val keysym: Long, val pressed: Boolean) : TypingEvent

    companion object {
        fun decode(bytes: ByteArray): TypingEvent? = when {
            bytes.size > 1 && bytes[0] == 'T'.code.toByte() -> Text(String(bytes, 1, bytes.size - 1, Charsets.UTF_8))
            bytes.size == 6 && bytes[0] == 'K'.code.toByte() && (bytes[1] == 0.toByte() || bytes[1] == 1.toByte()) -> {
                var keysym = 0L
                for (i in 2..5) keysym = (keysym shl 8) or (bytes[i].toLong() and 0xFF)
                Key(keysym, bytes[1] == 1.toByte())
            }
            else -> null
        }
    }
}

package app.linksy.control

/// One input message from a computer (`pointer`, `button`, `scroll`, `key`,
/// `text`), checked and ready to hand to the companion.
sealed interface ControlMsg {
    data class Pointer(val x: Double, val y: Double) : ControlMsg
    data class Button(val button: String, val pressed: Boolean) : ControlMsg
    data class Scroll(val dx: Double, val dy: Double, val discrete: Boolean) : ControlMsg
    data class Key(val keysym: Long, val pressed: Boolean) : ControlMsg
    data class Text(val text: String) : ControlMsg {
        /// Never the text: it can be a password.
        override fun toString() = "Text(len=${text.length})"
    }

    /// A press that has a matching release which must never be lost.
    val releases: Boolean
        get() = false

    /// The JSON the companion reads.
    fun toJson(): String = TODO("stub")

    companion object {
        const val MAX_TEXT = 4096

        /// Null for a type this phone doesn't act on, and for anything malformed.
        fun parse(json: String): ControlMsg? = TODO("stub")
    }
}

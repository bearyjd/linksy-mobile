package app.linksy.typing

/// Where to go when the last typing session ends.
enum class Leave { Nothing, Previous, Last, Hint }

/// Linksy Keyboard has no keys, so it must not stay the keyboard in use once
/// nobody types through it.
object SwitchBack {
    fun decide(sessionsRemain: Boolean, ours: Boolean, otherEnabled: Boolean, sdk: Int): Leave = when {
        sessionsRemain || !ours -> Leave.Nothing
        !otherEnabled -> Leave.Hint
        sdk >= 28 -> Leave.Previous
        else -> Leave.Last
    }
}

package app.linksy.typing

/// The time each key was pressed, so its release and its repeats carry the
/// key's own `downTime`, not one shared by every key.
class PressTimes(private val now: () -> Long) {
    private val pressed = HashMap<Int, Long>()

    fun downTime(code: Int, down: Boolean): Long =
        if (down) now().also { pressed[code] = it } else pressed.remove(code) ?: now()
}

package app.linksy.typing

import android.view.KeyEvent

/// X11 keysyms (what the computer sends) to Android key codes, only for the
/// keys that cannot act outside the focused field: letters, digits, space,
/// Backspace, Delete, Home, End and Shift. Everything else is refused: Ctrl,
/// Alt, Super, Meta, power, media and launcher keys, Caps/Num Lock, Print and
/// Menu would act outside the field or reach the clipboard; Tab, the arrows
/// (at the field's edge they move focus), Page Up/Down, Escape (Back) and
/// F-keys would act on other views; Enter is never a key (see [Typist]).
object KeysymMap {
    fun keyCode(keysym: Long): Int? = when (keysym) {
        in 0x61L..0x7AL -> KeyEvent.KEYCODE_A + (keysym - 0x61L).toInt()
        in 0x41L..0x5AL -> KeyEvent.KEYCODE_A + (keysym - 0x41L).toInt()
        in 0x30L..0x39L -> KeyEvent.KEYCODE_0 + (keysym - 0x30L).toInt()
        0x20L -> KeyEvent.KEYCODE_SPACE
        0xFF08L -> KeyEvent.KEYCODE_DEL
        0xFFFFL -> KeyEvent.KEYCODE_FORWARD_DEL
        0xFF50L -> KeyEvent.KEYCODE_MOVE_HOME
        0xFF57L -> KeyEvent.KEYCODE_MOVE_END
        0xFFE1L -> KeyEvent.KEYCODE_SHIFT_LEFT
        0xFFE2L -> KeyEvent.KEYCODE_SHIFT_RIGHT
        else -> null
    }

    /// Ctrl, Alt, Meta and Super. Never forwarded: with a letter they are
    /// shortcuts (Ctrl+A/C/X/V reach the phone's clipboard), so a letter pressed
    /// while one is held is dropped instead.
    fun isChordModifier(keysym: Long): Boolean = when (keysym) {
        0xFFE3L, 0xFFE4L, 0xFFE9L, 0xFFEAL, 0xFFE7L, 0xFFE8L, 0xFFEBL, 0xFFECL -> true
        else -> false
    }

    /// Keys that type a character by themselves.
    fun isPrintable(code: Int): Boolean =
        code in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z || code in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 ||
            code == KeyEvent.KEYCODE_SPACE

    /// The meta state a held modifier key adds; 0 for any other key.
    fun meta(code: Int): Int = when (code) {
        KeyEvent.KEYCODE_SHIFT_LEFT -> KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        KeyEvent.KEYCODE_SHIFT_RIGHT -> KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_RIGHT_ON
        else -> 0
    }
}

package app.linksy.typing

import android.view.KeyEvent

/// X11 keysyms (what the computer sends) to Android key codes, only for the
/// keys a text field needs. Anything else (Ctrl, Alt, Super, Meta, power, media
/// and launcher keys, Caps/Num Lock, Print, Menu) is refused: it would act
/// outside the focused field or reach the clipboard.
object KeysymMap {
    fun keyCode(keysym: Long): Int? = when (keysym) {
        in 0x61L..0x7AL -> KeyEvent.KEYCODE_A + (keysym - 0x61L).toInt()
        in 0x41L..0x5AL -> KeyEvent.KEYCODE_A + (keysym - 0x41L).toInt()
        in 0x30L..0x39L -> KeyEvent.KEYCODE_0 + (keysym - 0x30L).toInt()
        0x20L -> KeyEvent.KEYCODE_SPACE
        0xFF08L -> KeyEvent.KEYCODE_DEL
        0xFFFFL -> KeyEvent.KEYCODE_FORWARD_DEL
        0xFF0DL, 0xFF8DL -> KeyEvent.KEYCODE_ENTER
        0xFF09L -> KeyEvent.KEYCODE_TAB
        0xFF1BL -> KeyEvent.KEYCODE_ESCAPE
        0xFF50L -> KeyEvent.KEYCODE_MOVE_HOME
        0xFF51L -> KeyEvent.KEYCODE_DPAD_LEFT
        0xFF52L -> KeyEvent.KEYCODE_DPAD_UP
        0xFF53L -> KeyEvent.KEYCODE_DPAD_RIGHT
        0xFF54L -> KeyEvent.KEYCODE_DPAD_DOWN
        0xFF55L -> KeyEvent.KEYCODE_PAGE_UP
        0xFF56L -> KeyEvent.KEYCODE_PAGE_DOWN
        0xFF57L -> KeyEvent.KEYCODE_MOVE_END
        in 0xFFBEL..0xFFC9L -> KeyEvent.KEYCODE_F1 + (keysym - 0xFFBEL).toInt()
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

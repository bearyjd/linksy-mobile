package app.linksy.typing

import android.view.KeyEvent

/// X11 keysyms (what the computer sends) to Android key codes, only for the
/// keys a text field needs. Anything else (Super, Meta, power, media and
/// launcher keys, Caps/Num Lock, Print, Menu) is refused: it would act
/// outside the focused field.
object KeysymMap {
    fun keyCode(keysym: Long): Int? = when (keysym) {
        in 0x61L..0x7AL -> KeyEvent.KEYCODE_A + (keysym - 0x61L).toInt()
        in 0x41L..0x5AL -> KeyEvent.KEYCODE_A + (keysym - 0x41L).toInt()
        in 0x30L..0x39L -> KeyEvent.KEYCODE_0 + (keysym - 0x30L).toInt()
        0x20L -> KeyEvent.KEYCODE_SPACE
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
        0xFFE3L -> KeyEvent.KEYCODE_CTRL_LEFT
        0xFFE4L -> KeyEvent.KEYCODE_CTRL_RIGHT
        0xFFE9L -> KeyEvent.KEYCODE_ALT_LEFT
        0xFFEAL -> KeyEvent.KEYCODE_ALT_RIGHT
        else -> null
    }

    /// The meta state a held modifier key adds; 0 for any other key.
    fun meta(code: Int): Int = when (code) {
        KeyEvent.KEYCODE_SHIFT_LEFT -> KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        KeyEvent.KEYCODE_SHIFT_RIGHT -> KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_RIGHT_ON
        KeyEvent.KEYCODE_CTRL_LEFT -> KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        KeyEvent.KEYCODE_CTRL_RIGHT -> KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_RIGHT_ON
        KeyEvent.KEYCODE_ALT_LEFT -> KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        KeyEvent.KEYCODE_ALT_RIGHT -> KeyEvent.META_ALT_ON or KeyEvent.META_ALT_RIGHT_ON
        else -> 0
    }
}

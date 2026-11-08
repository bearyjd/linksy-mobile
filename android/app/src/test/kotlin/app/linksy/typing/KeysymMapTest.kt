package app.linksy.typing

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeysymMapTest {
    @Test fun mapsTheKeysAFieldNeeds() {
        assertEquals(KeyEvent.KEYCODE_ENTER, KeysymMap.keyCode(0xFF0D))
        assertEquals(KeyEvent.KEYCODE_ENTER, KeysymMap.keyCode(0xFF8D))
        assertEquals(KeyEvent.KEYCODE_TAB, KeysymMap.keyCode(0xFF09))
        assertEquals(KeyEvent.KEYCODE_ESCAPE, KeysymMap.keyCode(0xFF1B))
        assertEquals(KeyEvent.KEYCODE_DPAD_LEFT, KeysymMap.keyCode(0xFF51))
        assertEquals(KeyEvent.KEYCODE_DPAD_UP, KeysymMap.keyCode(0xFF52))
        assertEquals(KeyEvent.KEYCODE_DPAD_RIGHT, KeysymMap.keyCode(0xFF53))
        assertEquals(KeyEvent.KEYCODE_DPAD_DOWN, KeysymMap.keyCode(0xFF54))
        assertEquals(KeyEvent.KEYCODE_MOVE_HOME, KeysymMap.keyCode(0xFF50))
        assertEquals(KeyEvent.KEYCODE_MOVE_END, KeysymMap.keyCode(0xFF57))
        assertEquals(KeyEvent.KEYCODE_PAGE_UP, KeysymMap.keyCode(0xFF55))
        assertEquals(KeyEvent.KEYCODE_PAGE_DOWN, KeysymMap.keyCode(0xFF56))
        assertEquals(KeyEvent.KEYCODE_F1, KeysymMap.keyCode(0xFFBE))
        assertEquals(KeyEvent.KEYCODE_F12, KeysymMap.keyCode(0xFFC9))
    }

    @Test fun lettersAndDigitsServeShortcuts() {
        assertEquals(KeyEvent.KEYCODE_A, KeysymMap.keyCode('a'.code.toLong()))
        assertEquals(KeyEvent.KEYCODE_A, KeysymMap.keyCode('A'.code.toLong()))
        assertEquals(KeyEvent.KEYCODE_Z, KeysymMap.keyCode('z'.code.toLong()))
        assertEquals(KeyEvent.KEYCODE_0, KeysymMap.keyCode('0'.code.toLong()))
        assertEquals(KeyEvent.KEYCODE_9, KeysymMap.keyCode('9'.code.toLong()))
        assertEquals(KeyEvent.KEYCODE_SPACE, KeysymMap.keyCode(0x20))
    }

    @Test fun onlyShiftIsAKeyWithAMeta() {
        assertEquals(KeyEvent.KEYCODE_SHIFT_LEFT, KeysymMap.keyCode(0xFFE1))
        assertEquals(KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON, KeysymMap.meta(KeyEvent.KEYCODE_SHIFT_LEFT))
        assertEquals(KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_RIGHT_ON, KeysymMap.meta(KeyEvent.KEYCODE_SHIFT_RIGHT))
        assertEquals(0, KeysymMap.meta(KeyEvent.KEYCODE_A))
        // Ctrl and Alt are never forwarded: with a letter they would be clipboard shortcuts.
        for (keysym in longArrayOf(0xFFE3, 0xFFE4, 0xFFE9, 0xFFEA)) assertNull(KeysymMap.keyCode(keysym))
        assertEquals(0, KeysymMap.meta(KeyEvent.KEYCODE_CTRL_LEFT))
    }

    @Test fun chordModifiersAreCtrlAltMetaAndSuper() {
        for (keysym in longArrayOf(0xFFE3, 0xFFE4, 0xFFE9, 0xFFEA, 0xFFE7, 0xFFE8, 0xFFEB, 0xFFEC)) {
            assertTrue("0x" + keysym.toString(16), KeysymMap.isChordModifier(keysym))
        }
        for (keysym in longArrayOf(0xFFE1, 0xFFE2, 'a'.code.toLong(), 0xFF0D, 0)) {
            assertFalse("0x" + keysym.toString(16), KeysymMap.isChordModifier(keysym))
        }
    }

    @Test fun deletionIsRealDeleteKeys() {
        assertEquals(KeyEvent.KEYCODE_DEL, KeysymMap.keyCode(0xFF08))
        assertEquals(KeyEvent.KEYCODE_FORWARD_DEL, KeysymMap.keyCode(0xFFFF))
    }

    @Test fun printableKeysAreLettersDigitsAndSpace() {
        for (code in listOf(KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_SPACE)) {
            assertTrue(code.toString(), KeysymMap.isPrintable(code))
        }
        for (code in listOf(KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_F1, KeyEvent.KEYCODE_DEL)) {
            assertFalse(code.toString(), KeysymMap.isPrintable(code))
        }
    }

    @Test fun refusesKeysThatActOutsideTheField() {
        // Super, Meta, Caps/Num Lock, Print, Menu.
        for (keysym in longArrayOf(0xFFEB, 0xFFEC, 0xFFE7, 0xFFE8, 0xFFE5, 0xFF7F, 0xFF61, 0xFF67)) {
            assertNull("0x" + keysym.toString(16), KeysymMap.keyCode(keysym))
        }
        // XF86 power, sleep, audio, launchers, calculator.
        for (keysym in longArrayOf(0x1008FF2A, 0x1008FF2F, 0x1008FF14, 0x1008FF13, 0x1008FF1D, 0x1008FF18)) {
            assertNull("0x" + keysym.toString(16), KeysymMap.keyCode(keysym))
        }
        assertNull(KeysymMap.keyCode(0))
        assertNull(KeysymMap.keyCode(0xFFFFFF))
    }
}

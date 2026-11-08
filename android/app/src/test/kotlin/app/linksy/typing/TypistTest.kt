package app.linksy.typing

import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Records what the Typist asks of the field; [accepts] is what the field answers. */
private class FakeEditor(
    override var multiLine: Boolean = false,
    override var enterAction: Int? = null,
    var accepts: Boolean = true,
) : Editor {
    val calls = mutableListOf<String>()
    override fun finishComposingText() { calls += "finish" }
    override fun commitText(text: String): Boolean { calls += "commit:$text"; return accepts }
    override fun performEditorAction(action: Int): Boolean { calls += "action:$action"; return accepts }
    override fun key(code: Int, down: Boolean, meta: Int): Boolean {
        calls += "key:$code:${if (down) "down" else "up"}:$meta"
        return accepts
    }
}

class TypistTest {
    private fun key(keysym: Long, pressed: Boolean) = TypingEvent.Key(keysym, pressed)

    @Test fun textEndsAnyCompositionThenIsCommitted() {
        val editor = FakeEditor()
        Typist(editor).apply(TypingEvent.Text("héllo"))
        assertEquals(listOf("finish", "commit:héllo"), editor.calls)
    }

    @Test fun aPastedNewlineIsASpaceInASingleLineFieldAndNeverPressesTheAction() {
        val editor = FakeEditor(multiLine = false, enterAction = EditorInfo.IME_ACTION_SEND)
        Typist(editor).apply(TypingEvent.Text("a\nb\n"))
        assertEquals(listOf("finish", "commit:a b "), editor.calls)
    }

    @Test fun everyKindOfLineBreakIsASpaceInASingleLineField() {
        val editor = FakeEditor(multiLine = false)
        Typist(editor).apply(TypingEvent.Text("a\r\nb\rc\nd"))
        assertEquals(listOf("finish", "commit:a b c d"), editor.calls)
    }

    @Test fun aPastedNewlineStaysInAMultiLineField() {
        val editor = FakeEditor(multiLine = true)
        Typist(editor).apply(TypingEvent.Text("a\nb"))
        assertEquals(listOf("finish", "commit:a\nb"), editor.calls)
    }

    @Test fun enterRunsTheFieldsActionInASingleLineField() {
        val editor = FakeEditor(multiLine = false, enterAction = EditorInfo.IME_ACTION_SEARCH)
        val typist = Typist(editor)
        typist.apply(key(0xFF0D, true))
        typist.apply(key(0xFF0D, false))
        assertEquals(listOf("finish", "action:${EditorInfo.IME_ACTION_SEARCH}"), editor.calls)
    }

    @Test fun enterIsANewlineInAMultiLineFieldEvenWithAnAction() {
        val editor = FakeEditor(multiLine = true, enterAction = EditorInfo.IME_ACTION_SEND)
        val typist = Typist(editor)
        assertTrue(typist.apply(key(0xFF0D, true)))
        typist.apply(key(0xFF0D, false))
        assertEquals(listOf("finish", "commit:\n"), editor.calls)
    }

    @Test fun enterWithNoActionInASingleLineFieldDoesNothingAndIsRefused() {
        // Never a key event: it could press whatever button has focus.
        for (keysym in longArrayOf(0xFF0D, 0xFF8D)) {
            val editor = FakeEditor(multiLine = false, enterAction = null)
            assertFalse(Typist(editor).apply(key(keysym, true)))
            assertEquals(listOf("finish"), editor.calls)
        }
    }

    @Test fun enterIsNeverSentAsAKeyEvent() {
        for (multi in listOf(true, false)) for (action in listOf(null, EditorInfo.IME_ACTION_SEND)) {
            val editor = FakeEditor(multiLine = multi, enterAction = action)
            val typist = Typist(editor)
            typist.apply(key(0xFF0D, true))
            typist.apply(key(0xFF0D, false))
            assertTrue(editor.calls.toString(), editor.calls.none { it.startsWith("key:") })
        }
    }

    @Test fun focusMovingKeysAreDroppedAsDone() {
        val editor = FakeEditor()
        val typist = Typist(editor)
        for (keysym in longArrayOf(0xFF09, 0xFF1B, 0xFF51, 0xFF52, 0xFF53, 0xFF54, 0xFF55, 0xFF56, 0xFFBE)) {
            assertTrue(typist.apply(key(keysym, true)))
            assertTrue(typist.apply(key(keysym, false)))
        }
        assertTrue(editor.calls.isEmpty())
    }

    @Test fun deletionAndEnterAreDroppedWhileAChordModifierIsHeld() {
        // Ctrl+Backspace / Ctrl+Enter / Ctrl+Delete are word-delete and send shortcuts.
        val editor = FakeEditor(multiLine = true, enterAction = EditorInfo.IME_ACTION_SEND)
        val typist = Typist(editor)
        typist.apply(key(0xFFE3, true))
        for (keysym in longArrayOf(0xFF08, 0xFFFF, 0xFF0D, 0xFF8D)) assertTrue(typist.apply(key(keysym, true)))
        assertTrue(editor.calls.isEmpty())
        typist.apply(key(0xFFE3, false))
        typist.apply(key(0xFF08, true))
        assertEquals(KeyEvent.KEYCODE_DEL, editor.calls[1].split(":")[1].toInt())
    }

    @Test fun eachSessionKeepsItsOwnHeldKeysAndReleasesOnlyThose() {
        val editor = FakeEditor()
        val typist = Typist(editor)
        typist.apply(key(0xFFE1, true), "a") // Shift, session a
        typist.apply(key(0xFF50, true), "b") // Home, session b
        typist.apply(key(0xFFE3, true), "a") // Ctrl held by a only
        editor.calls.clear()
        typist.releaseAll("a")
        assertEquals(listOf("key:${KeyEvent.KEYCODE_SHIFT_LEFT}:up:0"), editor.calls)
        // b still holds Home and a's Ctrl does not block b's letters; a's Shift is not b's meta.
        editor.calls.clear()
        typist.apply(key('x'.code.toLong(), true), "b")
        assertEquals(listOf("key:${KeyEvent.KEYCODE_X}:down:0"), editor.calls)
        editor.calls.clear()
        typist.releaseAll("b")
        assertEquals(setOf("key:${KeyEvent.KEYCODE_MOVE_HOME}:up:0", "key:${KeyEvent.KEYCODE_X}:up:0"), editor.calls.toSet())
    }

    @Test fun backspaceAndDeleteAreRealKeysTheFieldInterprets() {
        val back = FakeEditor()
        Typist(back).apply(key(0xFF08, true))
        assertEquals(
            listOf("finish", "key:${KeyEvent.KEYCODE_DEL}:down:0", "key:${KeyEvent.KEYCODE_DEL}:up:0"),
            back.calls,
        )
        val forward = FakeEditor()
        Typist(forward).apply(key(0xFFFF, true))
        assertEquals(
            listOf("finish", "key:${KeyEvent.KEYCODE_FORWARD_DEL}:down:0", "key:${KeyEvent.KEYCODE_FORWARD_DEL}:up:0"),
            forward.calls,
        )
    }

    @Test fun deletionNeverCarriesAModifier() {
        // Shift+Delete is Cut in a text view.
        val editor = FakeEditor()
        val typist = Typist(editor)
        typist.apply(key(0xFFE1, true))
        editor.calls.clear()
        typist.apply(key(0xFFFF, true))
        assertEquals(
            listOf("finish", "key:${KeyEvent.KEYCODE_FORWARD_DEL}:down:0", "key:${KeyEvent.KEYCODE_FORWARD_DEL}:up:0"),
            editor.calls,
        )
    }

    @Test fun releasingBackspaceOrDeleteDoesNothing() {
        val editor = FakeEditor()
        val typist = Typist(editor)
        typist.apply(key(0xFF08, false))
        typist.apply(key(0xFFFF, false))
        assertTrue(editor.calls.isEmpty())
    }

    @Test fun shiftIsCarriedByTheKeysPressedWhileItIsDown() {
        val editor = FakeEditor()
        val typist = Typist(editor)
        typist.apply(key(0xFFE1, true))
        typist.apply(key('a'.code.toLong(), true))
        typist.apply(key('a'.code.toLong(), false))
        typist.apply(key(0xFFE1, false))
        val shift = KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        assertEquals(
            listOf(
                "key:${KeyEvent.KEYCODE_SHIFT_LEFT}:down:$shift",
                "key:${KeyEvent.KEYCODE_A}:down:$shift",
                "key:${KeyEvent.KEYCODE_A}:up:$shift",
                "key:${KeyEvent.KEYCODE_SHIFT_LEFT}:up:0",
            ),
            editor.calls,
        )
    }

    @Test fun aLetterWithCtrlAltMetaOrSuperIsDroppedAndNothingIsForwarded() {
        // Ctrl+A/C/X/V would select, copy, cut and paste the phone's clipboard.
        for (modifier in longArrayOf(0xFFE3, 0xFFE4, 0xFFE9, 0xFFEA, 0xFFE7, 0xFFE8, 0xFFEB, 0xFFEC)) {
            val editor = FakeEditor()
            val typist = Typist(editor)
            assertTrue(typist.apply(key(modifier, true)))
            for (letter in "acxv0 ") {
                assertTrue(typist.apply(key(letter.code.toLong(), true)))
                assertTrue(typist.apply(key(letter.code.toLong(), false)))
            }
            assertTrue(typist.apply(key(modifier, false)))
            assertTrue("0x" + modifier.toString(16) + " " + editor.calls, editor.calls.isEmpty())
        }
    }

    @Test fun keysThatAreNotPrintableStillWorkWithCtrlHeldWithoutItsMeta() {
        val editor = FakeEditor()
        val typist = Typist(editor)
        typist.apply(key(0xFFE3, true))
        typist.apply(key(0xFF50, true))
        typist.apply(key(0xFF50, false))
        assertEquals(
            listOf("key:${KeyEvent.KEYCODE_MOVE_HOME}:down:0", "key:${KeyEvent.KEYCODE_MOVE_HOME}:up:0"),
            editor.calls,
        )
    }

    @Test fun lettersWorkAgainOnceTheChordModifierIsReleased() {
        val editor = FakeEditor()
        val typist = Typist(editor)
        typist.apply(key(0xFFE3, true))
        typist.apply(key(0xFFE3, false))
        typist.apply(key('a'.code.toLong(), true))
        assertEquals(listOf("key:${KeyEvent.KEYCODE_A}:down:0"), editor.calls)
    }

    @Test fun releaseAllForgetsAHeldCtrl() {
        val editor = FakeEditor()
        val typist = Typist(editor)
        typist.apply(key(0xFFE3, true))
        typist.releaseAll()
        typist.apply(key('a'.code.toLong(), true))
        assertEquals(listOf("key:${KeyEvent.KEYCODE_A}:down:0"), editor.calls)
    }

    @Test fun textIsStillTypedWhileCtrlIsHeld() {
        val editor = FakeEditor()
        val typist = Typist(editor)
        typist.apply(key(0xFFE3, true))
        typist.apply(TypingEvent.Text("v"))
        assertEquals(listOf("finish", "commit:v"), editor.calls)
    }

    @Test fun saysWhetherTheFieldTookIt() {
        val refusing = FakeEditor(accepts = false)
        val typist = Typist(refusing)
        assertFalse(typist.apply(TypingEvent.Text("a")))
        assertFalse(typist.apply(key('a'.code.toLong(), true)))
        assertFalse(typist.apply(key(0xFF08, true)))
        assertFalse(typist.apply(key(0xFF0D, true)))
        val enterAction = FakeEditor(enterAction = EditorInfo.IME_ACTION_SEND, accepts = false)
        assertFalse(Typist(enterAction).apply(key(0xFF0D, true)))
        val taking = FakeEditor()
        assertTrue(Typist(taking).apply(TypingEvent.Text("a")))
        assertTrue(Typist(taking).apply(key(0xFF08, true)))
    }

    @Test fun refusedKeysDoNothing() {
        val editor = FakeEditor()
        val typist = Typist(editor)
        typist.apply(key(0xFFEB, true)) // Super
        typist.apply(key(0x1008FF2A, true)) // power
        typist.apply(key(0xFFEB, false))
        assertTrue(editor.calls.isEmpty())
    }

    @Test fun releaseAllLetsGoOfEveryKeyStillDownAndForgetsTheModifiers() {
        val editor = FakeEditor()
        val typist = Typist(editor)
        typist.apply(key(0xFFE1, true)) // Shift
        typist.apply(key(0xFF50, true)) // Home
        editor.calls.clear()
        typist.releaseAll()
        assertEquals(
            setOf("key:${KeyEvent.KEYCODE_SHIFT_LEFT}:up:0", "key:${KeyEvent.KEYCODE_MOVE_HOME}:up:0"),
            editor.calls.toSet(),
        )
        assertEquals(2, editor.calls.size)
        // Nothing more the second time, and a fresh key carries no old modifier.
        editor.calls.clear()
        typist.releaseAll()
        typist.apply(key(0xFF50, true))
        assertEquals(listOf("key:${KeyEvent.KEYCODE_MOVE_HOME}:down:0"), editor.calls)
    }

    @Test fun aReleaseForAKeyNeverPressedIsIgnored() {
        val editor = FakeEditor()
        Typist(editor).apply(key(0xFF50, false))
        assertTrue(editor.calls.isEmpty())
    }
}

package app.linksy.typing

import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Records what the Typist asks of the field. */
private class FakeEditor(
    override var multiLine: Boolean = false,
    override var hasSelection: Boolean = false,
    override var enterAction: Int? = null,
) : Editor {
    val calls = mutableListOf<String>()
    override fun finishComposingText() { calls += "finish" }
    override fun commitText(text: String) { calls += "commit:$text" }
    override fun deleteBackward() { calls += "back" }
    override fun deleteForward() { calls += "forward" }
    override fun performEditorAction(action: Int) { calls += "action:$action" }
    override fun key(code: Int, down: Boolean, meta: Int) { calls += "key:$code:${if (down) "down" else "up"}:$meta" }
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

    @Test fun enterIsAKeyWhereTheFieldHasNoAction() {
        val editor = FakeEditor(multiLine = true, enterAction = EditorInfo.IME_ACTION_SEND)
        Typist(editor).apply(key(0xFF0D, true))
        assertEquals(
            listOf("finish", "key:${KeyEvent.KEYCODE_ENTER}:down:0", "key:${KeyEvent.KEYCODE_ENTER}:up:0"),
            editor.calls,
        )
        val plain = FakeEditor(multiLine = false, enterAction = null)
        Typist(plain).apply(key(0xFF8D, true))
        assertEquals(KeyEvent.KEYCODE_ENTER, plain.calls[1].split(":")[1].toInt())
    }

    @Test fun backspaceDeletesTheSelectionElseOneCharacterBack() {
        val none = FakeEditor(hasSelection = false)
        Typist(none).apply(key(0xFF08, true))
        assertEquals(listOf("finish", "back"), none.calls)

        val selected = FakeEditor(hasSelection = true)
        Typist(selected).apply(key(0xFF08, true))
        assertEquals(listOf("finish", "commit:"), selected.calls)
    }

    @Test fun deleteGoesForwardOrDeletesTheSelection() {
        val none = FakeEditor()
        Typist(none).apply(key(0xFFFF, true))
        assertEquals(listOf("finish", "forward"), none.calls)
        val selected = FakeEditor(hasSelection = true)
        Typist(selected).apply(key(0xFFFF, true))
        assertEquals(listOf("finish", "commit:"), selected.calls)
    }

    @Test fun releasingBackspaceOrDeleteDoesNothing() {
        val editor = FakeEditor()
        val typist = Typist(editor)
        typist.apply(key(0xFF08, false))
        typist.apply(key(0xFFFF, false))
        assertTrue(editor.calls.isEmpty())
    }

    @Test fun modifiersAreCarriedByTheKeysPressedWhileTheyAreDown() {
        val editor = FakeEditor()
        val typist = Typist(editor)
        typist.apply(key(0xFFE3, true))
        typist.apply(key('a'.code.toLong(), true))
        typist.apply(key('a'.code.toLong(), false))
        typist.apply(key(0xFFE3, false))
        val ctrl = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        assertEquals(
            listOf(
                "key:${KeyEvent.KEYCODE_CTRL_LEFT}:down:$ctrl",
                "key:${KeyEvent.KEYCODE_A}:down:$ctrl",
                "key:${KeyEvent.KEYCODE_A}:up:$ctrl",
                "key:${KeyEvent.KEYCODE_CTRL_LEFT}:up:0",
            ),
            editor.calls,
        )
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
        typist.apply(key(0xFF51, true)) // Left
        editor.calls.clear()
        typist.releaseAll()
        assertEquals(
            setOf("key:${KeyEvent.KEYCODE_SHIFT_LEFT}:up:0", "key:${KeyEvent.KEYCODE_DPAD_LEFT}:up:0"),
            editor.calls.toSet(),
        )
        assertEquals(2, editor.calls.size)
        // Nothing more the second time, and a fresh key carries no old modifier.
        editor.calls.clear()
        typist.releaseAll()
        typist.apply(key(0xFF51, true))
        assertEquals(listOf("key:${KeyEvent.KEYCODE_DPAD_LEFT}:down:0"), editor.calls)
    }

    @Test fun aReleaseForAKeyNeverPressedIsIgnored() {
        val editor = FakeEditor()
        Typist(editor).apply(key(0xFF51, false))
        assertTrue(editor.calls.isEmpty())
    }
}

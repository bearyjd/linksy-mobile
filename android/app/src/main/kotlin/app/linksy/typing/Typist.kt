package app.linksy.typing

import android.view.KeyEvent

/// Applies what a computer typed to a field. Keeps the keys it holds down, so
/// [releaseAll] can let go of them if the typing stops with one still down
/// (core stops sending once the keyboard is dropped).
///
/// Ctrl, Alt, Meta and Super are never forwarded, and a letter, digit or space
/// pressed while one is held is dropped: Android's text views turn those into
/// select-all, copy, cut and paste, which would reach the phone's clipboard.
/// The computer's own paste arrives as text.
class Typist(private val editor: Editor) {
    private val held = LinkedHashSet<Int>()
    private val chord = HashSet<Long>()

    /// False if the field refused what was sent. A key that is dropped on
    /// purpose counts as done.
    fun apply(event: TypingEvent): Boolean = when (event) {
        is TypingEvent.Text -> text(event.text)
        is TypingEvent.Key -> key(event.keysym, event.pressed)
    }

    /// A key up for every key still down, and no modifier left.
    fun releaseAll() {
        chord.clear()
        for (code in held.toList()) {
            held.remove(code)
            editor.key(code, false, meta())
        }
    }

    private fun text(text: String): Boolean {
        if (text.isEmpty()) return true
        editor.finishComposingText()
        // A pasted line break must not press Send in a chat field, once per line.
        return editor.commitText(if (editor.multiLine) text else text.replace(LINE_BREAK, " "))
    }

    private fun key(keysym: Long, pressed: Boolean): Boolean {
        if (KeysymMap.isChordModifier(keysym)) {
            if (pressed) chord.add(keysym) else chord.remove(keysym)
            return true
        }
        val code = KeysymMap.keyCode(keysym) ?: return true
        return when (keysym) {
            BACKSPACE, DELETE -> if (pressed) erase(code) else true
            ENTER, KP_ENTER -> if (pressed) enter() else true
            else -> if (pressed) press(code) else release(code)
        }
    }

    private fun press(code: Int): Boolean {
        if (chord.isNotEmpty() && KeysymMap.isPrintable(code)) return true
        held.add(code)
        return editor.key(code, true, meta())
    }

    private fun release(code: Int): Boolean {
        if (!held.remove(code)) return true
        return editor.key(code, false, meta())
    }

    /// A real Delete key for the field to interpret: it knows whether there is
    /// a selection, which Android only tells the keyboard after the fact. Never
    /// with a modifier (Shift+Delete cuts).
    private fun erase(code: Int): Boolean {
        editor.finishComposingText()
        val down = editor.key(code, true, 0)
        return editor.key(code, false, 0) && down
    }

    private fun enter(): Boolean {
        editor.finishComposingText()
        val action = editor.enterAction
        if (!editor.multiLine && action != null) return editor.performEditorAction(action)
        val down = editor.key(KeyEvent.KEYCODE_ENTER, true, meta())
        return editor.key(KeyEvent.KEYCODE_ENTER, false, meta()) && down
    }

    /// The modifiers held now.
    private fun meta(): Int = held.fold(0) { acc, code -> acc or KeysymMap.meta(code) }

    private companion object {
        const val BACKSPACE = 0xFF08L
        const val DELETE = 0xFFFFL
        const val ENTER = 0xFF0DL
        const val KP_ENTER = 0xFF8DL
        val LINE_BREAK = Regex("\r\n|\r|\n")
    }
}

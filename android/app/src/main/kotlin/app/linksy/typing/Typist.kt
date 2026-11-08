package app.linksy.typing

/// Applies what a computer typed to a field. Keeps the keys each session holds
/// down, so [releaseAll] can let go of them if the typing stops with one still
/// down (core stops sending once the keyboard is dropped), without touching
/// another session's.
///
/// Enter is never a key event (it would press whatever button has focus): in a
/// multi-line field it is a newline, in a field with an action (Send, Search...)
/// it runs that action, and otherwise it is refused. Keys that move focus
/// (Tab, arrows) are not mapped at all; see [KeysymMap].
///
/// Ctrl, Alt, Meta and Super are never forwarded, and a letter, digit or space
/// pressed while one is held is dropped: Android's text views turn those into
/// select-all, copy, cut and paste, which would reach the phone's clipboard.
/// The computer's own paste arrives as text.
class Typist(private val editor: Editor) {
    private class Keys {
        val held = LinkedHashSet<Int>()
        val chord = HashSet<Long>()

        /// The modifiers held now.
        fun meta(): Int = held.fold(0) { acc, code -> acc or KeysymMap.meta(code) }
    }

    private val sessions = HashMap<String, Keys>()

    /// False if the field refused what was sent. A key that is dropped on
    /// purpose counts as done.
    fun apply(event: TypingEvent, session: String = ""): Boolean = when (event) {
        is TypingEvent.Text -> text(event.text)
        is TypingEvent.Key -> key(sessions.getOrPut(session) { Keys() }, event.keysym, event.pressed)
    }

    /// A key up for every key [session] still holds (every session's, with
    /// none given), and no modifier left.
    fun releaseAll(session: String? = null) {
        val gone = if (session == null) sessions.keys.toList() else listOf(session)
        for (name in gone) {
            val keys = sessions.remove(name) ?: continue
            for (code in keys.held.toList()) {
                keys.held.remove(code)
                editor.key(code, false, keys.meta())
            }
        }
    }

    private fun text(text: String): Boolean {
        if (text.isEmpty()) return true
        editor.finishComposingText()
        // A pasted line break must not press Send in a chat field, once per line.
        return editor.commitText(if (editor.multiLine) text else text.replace(LINE_BREAK, " "))
    }

    private fun key(keys: Keys, keysym: Long, pressed: Boolean): Boolean {
        if (KeysymMap.isChordModifier(keysym)) {
            if (pressed) keys.chord.add(keysym) else keys.chord.remove(keysym)
            return true
        }
        // Ctrl+Backspace/Delete/Enter delete a word or send: dropped like Ctrl+letter.
        if (keys.chord.isNotEmpty() && keysym in CHORD_DROPPED) return true
        return when (keysym) {
            BACKSPACE, DELETE -> if (pressed) erase(KeysymMap.keyCode(keysym) ?: return true) else true
            ENTER, KP_ENTER -> if (pressed) enter() else true
            else -> {
                val code = KeysymMap.keyCode(keysym) ?: return true
                if (pressed) press(keys, code) else release(keys, code)
            }
        }
    }

    private fun press(keys: Keys, code: Int): Boolean {
        if (keys.chord.isNotEmpty() && KeysymMap.isPrintable(code)) return true
        keys.held.add(code)
        return editor.key(code, true, keys.meta())
    }

    private fun release(keys: Keys, code: Int): Boolean {
        if (!keys.held.remove(code)) return true
        return editor.key(code, false, keys.meta())
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
        if (editor.multiLine) return editor.commitText("\n")
        val action = editor.enterAction ?: return false
        return editor.performEditorAction(action)
    }

    private companion object {
        const val BACKSPACE = 0xFF08L
        const val DELETE = 0xFFFFL
        const val ENTER = 0xFF0DL
        const val KP_ENTER = 0xFF8DL
        val CHORD_DROPPED = setOf(BACKSPACE, DELETE, ENTER, KP_ENTER)
        val LINE_BREAK = Regex("\r\n|\r|\n")
    }
}

package app.linksy.typing

/// Applies what a computer typed to a field. Keeps the keys it holds down, so
/// [releaseAll] can let go of them if the typing stops with one still down
/// (core stops sending once the keyboard is dropped).
class Typist(private val editor: Editor) {
    private val held = LinkedHashSet<Int>()

    fun apply(event: TypingEvent) {
        when (event) {
            is TypingEvent.Text -> text(event.text)
            is TypingEvent.Key -> key(event.keysym, event.pressed)
        }
    }

    /// A key up for every key still down, and no modifier left.
    fun releaseAll() {
        for (code in held.toList()) {
            held.remove(code)
            editor.key(code, false, meta())
        }
    }

    private fun text(text: String) {
        if (text.isEmpty()) return
        editor.finishComposingText()
        // A pasted line break must not press Send in a chat field, once per line.
        editor.commitText(if (editor.multiLine) text else text.replace('\n', ' '))
    }

    private fun key(keysym: Long, pressed: Boolean) {
        when (keysym) {
            BACKSPACE, DELETE -> if (pressed) erase(keysym == BACKSPACE)
            ENTER, KP_ENTER -> if (pressed) enter()
            else -> {
                val code = KeysymMap.keyCode(keysym) ?: return
                if (pressed) {
                    held.add(code)
                    editor.key(code, true, meta())
                } else if (held.remove(code)) {
                    // Without its own modifier, if it is one.
                    editor.key(code, false, if (KeysymMap.meta(code) != 0) meta() else metaWith(code))
                }
            }
        }
    }

    private fun erase(backward: Boolean) {
        editor.finishComposingText()
        when {
            editor.hasSelection -> editor.commitText("")
            backward -> editor.deleteBackward()
            else -> editor.deleteForward()
        }
    }

    private fun enter() {
        editor.finishComposingText()
        val action = editor.enterAction
        if (!editor.multiLine && action != null) {
            editor.performEditorAction(action)
        } else {
            editor.key(android.view.KeyEvent.KEYCODE_ENTER, true, meta())
            editor.key(android.view.KeyEvent.KEYCODE_ENTER, false, meta())
        }
    }

    /// The modifiers held now.
    private fun meta(): Int = held.fold(0) { acc, code -> acc or KeysymMap.meta(code) }

    /// The modifiers held now, as when [released] was still down.
    private fun metaWith(released: Int): Int = meta() or KeysymMap.meta(released)

    private companion object {
        const val BACKSPACE = 0xFF08L
        const val DELETE = 0xFFFFL
        const val ENTER = 0xFF0DL
        const val KP_ENTER = 0xFF8DL
    }
}

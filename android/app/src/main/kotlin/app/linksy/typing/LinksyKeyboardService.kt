package app.linksy.typing

import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import app.linksy.Lang
import app.linksy.R

/// The Linksy Keyboard: no keys of its own. A computer types on this phone
/// through it (docs/protocol.md §4.26), into whichever field is open, while
/// the user has it selected and a typing session runs. It never reads what
/// is in the field or what the user types on the phone, and it is never the
/// default: Android has the user pick it, and the user picks another to go back.
class LinksyKeyboardService : InputMethodService() {
    private var typist: Typist? = null
    private var info: EditorInfo? = null
    private var selStart = -1
    private var selEnd = -1
    private var status: TextView? = null

    override fun onCreate() {
        super.onCreate()
        Typing.service = this
    }

    override fun onDestroy() {
        releaseAll()
        Typing.gate.keyboardLive(false)
        if (Typing.service === this) Typing.service = null
        super.onDestroy()
    }

    override fun onEvaluateFullscreenMode() = false

    override fun onCreateInputView(): View {
        val pad = (12 * resources.displayMetrics.density).toInt()
        val statusView = TextView(this).apply { textSize = 16f }
        status = statusView
        fun button(label: Int, onClick: () -> Unit) = Button(this).apply {
            text = Lang.str(this@LinksyKeyboardService, label)
            setOnClickListener { onClick() }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(button(R.string.typing_stop) { Typing.stopAll() })
            addView(button(R.string.keyboard_switch) { switchKeyboard() })
        }
        refresh()
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(statusView)
            addView(row)
        }
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        info = attribute
        selStart = attribute?.initialSelStart ?: -1
        selEnd = attribute?.initialSelEnd ?: -1
        typist = Typist(ConnectionEditor())
        Typing.gate.keyboardLive(currentInputConnection != null)
    }

    override fun onFinishInput() {
        releaseAll()
        typist = null
        info = null
        Typing.gate.keyboardLive(false)
        super.onFinishInput()
    }

    // Only where the cursor and selection are, never the text.
    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        selStart = newSelStart
        selEnd = newSelEnd
    }

    fun type(event: TypingEvent) {
        typist?.apply(event)
    }

    fun releaseAll() {
        typist?.releaseAll()
    }

    /// What the view says: who types, or that nothing does now.
    fun refresh() {
        val who = Typing.who()
        status?.text = if (who == null) Lang.str(this, R.string.keyboard_idle) else Lang.str(this, R.string.keyboard_typing, who)
    }

    /// Back to the keyboard the user had, or Android's picker.
    private fun switchKeyboard() {
        if (Build.VERSION.SDK_INT >= 28 && switchToPreviousInputMethod()) return
        getSystemService(InputMethodManager::class.java).showInputMethodPicker()
    }

    /// The open field, through the connection Android gave this keyboard.
    private inner class ConnectionEditor : Editor {
        private val connection: InputConnection? get() = currentInputConnection
        private val downTime = SystemClock.uptimeMillis()

        override val multiLine get() = EnterAction.isMultiLine(info?.inputType ?: 0)
        override val hasSelection get() = selStart >= 0 && selEnd >= 0 && selStart != selEnd
        override val enterAction get() = EnterAction.of(info?.imeOptions ?: 0)

        override fun finishComposingText() {
            connection?.finishComposingText()
        }

        override fun commitText(text: String) {
            connection?.commitText(text, 1)
        }

        override fun deleteBackward() {
            connection?.deleteSurroundingTextInCodePoints(1, 0)
        }

        override fun deleteForward() {
            connection?.deleteSurroundingTextInCodePoints(0, 1)
        }

        override fun performEditorAction(action: Int) {
            connection?.performEditorAction(action)
        }

        override fun key(code: Int, down: Boolean, meta: Int) {
            val action = if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
            connection?.sendKeyEvent(KeyEvent(downTime, SystemClock.uptimeMillis(), action, code, 0, meta))
        }
    }
}

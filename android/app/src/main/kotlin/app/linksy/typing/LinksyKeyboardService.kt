package app.linksy.typing

import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.SystemClock
import android.view.KeyCharacterMap
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
/// through it (docs/protocol.md §4.26), into whichever text field is open,
/// while the user has it selected and a typing session runs. It never reads
/// what is in the field or what the user types on the phone. It is never the
/// default: Android has the user pick it, and it switches back to the keyboard
/// before as soon as the last session ends (or, if there is none to go back to,
/// says so).
class LinksyKeyboardService : InputMethodService() {
    private var typist: Typist? = null
    private var info: EditorInfo? = null
    private var status: TextView? = null
    private val fieldRestart = FieldRestart()
    private val pressTimes = PressTimes { SystemClock.uptimeMillis() }

    override fun onCreate() {
        super.onCreate()
        Typing.service = this
    }

    override fun onDestroy() {
        releaseAll()
        Typing.gate.setField(FieldState.None)
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
        val state = if (attribute == null) FieldState.None else FieldPolicy.of(attribute.inputType, currentInputConnection != null)
        val key = attribute?.let { FieldKey(it.fieldId, it.packageName, it.inputType, it.imeOptions) }
        info = attribute
        // The same field started again (a chat app after Send): what is in
        // flight and the keys held stay, nothing is replaced.
        if (key != null && fieldRestart.keeps(key, state, restarting)) return
        if (key == null) fieldRestart.reset()
        // A new field skips onFinishInput on a restart: let go of what the old
        // Typist holds before it is replaced.
        releaseAll()
        typist = Typist(ConnectionEditor())
        Typing.field(state)
    }

    // Shown with nobody typing (picked by hand, or the lock screen's bouncer):
    // it has no keys, so leave at once.
    override fun onStartInputView(editorInfo: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)
        Typing.leaveIfIdle(this)
    }

    override fun onFinishInput() {
        releaseAll()
        fieldRestart.reset()
        typist = null
        info = null
        Typing.field(FieldState.None)
        super.onFinishInput()
    }

    /// True if the field took it (or it was dropped on purpose).
    fun type(session: String, event: TypingEvent): Boolean = typist?.apply(event, session) ?: false

    /// Lets go of the keys [session] holds, or every session's.
    fun releaseAll(session: String? = null) {
        typist?.releaseAll(session)
    }

    /// What the view says: who types, or that nothing does now.
    fun refresh() {
        status?.text = Typing.statusText(this)
    }

    /// Back to the keyboard the user had. False if Android would not.
    fun leave(sdk: Int): Boolean {
        if (sdk >= 28) return switchToPreviousInputMethod()
        val token = window?.window?.attributes?.token ?: return false
        @Suppress("DEPRECATION")
        return getSystemService(InputMethodManager::class.java).switchToLastInputMethod(token)
    }

    /// Back to the keyboard the user had, or Android's picker.
    private fun switchKeyboard() {
        if (leave(Build.VERSION.SDK_INT)) return
        getSystemService(InputMethodManager::class.java).showInputMethodPicker()
    }

    /// The open field, through the connection Android gave this keyboard.
    private inner class ConnectionEditor : Editor {
        private val connection: InputConnection? get() = currentInputConnection

        override val multiLine get() = EnterAction.isMultiLine(info?.inputType ?: 0)
        override val enterAction get() = EnterAction.of(info?.imeOptions ?: 0)

        override fun finishComposingText() {
            connection?.finishComposingText()
        }

        override fun commitText(text: String) = connection?.commitText(text, 1) ?: false

        override fun performEditorAction(action: Int) = connection?.performEditorAction(action) ?: false

        override fun key(code: Int, down: Boolean, meta: Int): Boolean {
            val action = if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
            val event = KeyEvent(
                pressTimes.downTime(code, down), SystemClock.uptimeMillis(), action, code, 0, meta,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE,
            )
            return connection?.sendKeyEvent(event) ?: false
        }
    }
}

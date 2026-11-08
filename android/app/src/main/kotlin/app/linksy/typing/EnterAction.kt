package app.linksy.typing

import android.text.InputType
import android.view.inputmethod.EditorInfo

/// How a field wants Enter, from its `EditorInfo`.
object EnterAction {
    /// The field's own action (Send, Search, Go...), or null when Enter is
    /// just a key for it.
    fun of(imeOptions: Int): Int? {
        if (imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return null
        val action = imeOptions and EditorInfo.IME_MASK_ACTION
        return if (action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED) null else action
    }

    fun isMultiLine(inputType: Int): Boolean =
        inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT &&
            inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0
}

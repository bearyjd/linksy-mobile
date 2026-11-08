package app.linksy.typing

/// The focused field, as the Typist sees it. The keyboard service backs it
/// with the `InputConnection`; tests with a fake. It never reads what is in
/// the field: only whether text is selected, from the offsets Android gives
/// the keyboard.
interface Editor {
    val multiLine: Boolean
    val hasSelection: Boolean

    /// The field's own Enter action (Send, Search...), or null for a plain Enter.
    val enterAction: Int?

    fun finishComposingText()
    fun commitText(text: String)

    /// One character (code point) before / after the cursor.
    fun deleteBackward()
    fun deleteForward()
    fun performEditorAction(action: Int)
    fun key(code: Int, down: Boolean, meta: Int)
}

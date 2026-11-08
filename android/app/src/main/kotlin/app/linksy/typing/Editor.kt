package app.linksy.typing

/// The focused field, as the Typist sees it. The keyboard service backs it
/// with the `InputConnection`; tests with a fake. It never reads what is in
/// the field. Each call says whether the field took it.
interface Editor {
    val multiLine: Boolean

    /// The field's own Enter action (Send, Search...), or null for a plain Enter.
    val enterAction: Int?

    fun finishComposingText()
    fun commitText(text: String): Boolean
    fun performEditorAction(action: Int): Boolean
    fun key(code: Int, down: Boolean, meta: Int): Boolean
}

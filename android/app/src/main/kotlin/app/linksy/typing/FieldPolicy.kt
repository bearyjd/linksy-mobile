package app.linksy.typing

import android.text.InputType

/// What the open field means for typing from a computer.
enum class FieldState {
    /// No field is open.
    None,

    /// A text-class field: typing goes in.
    Typable,

    /// A password field: typing is paused, whatever the computer sends.
    Password,

    /// A field with no input connection, or `TYPE_NULL` (raw key editors such
    /// as some games and terminals): nothing can be typed into it.
    NoInput,

    /// The field refused what was sent (an `InputConnection` returned false).
    Refused,
}

object FieldPolicy {
    /// [inputType] is the field's `EditorInfo.inputType`. The variation is read
    /// within its own class: the number password variation and the text URI
    /// variation share a value.
    fun of(inputType: Int, hasConnection: Boolean): FieldState = when {
        !hasConnection || inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_NULL -> FieldState.NoInput
        isPassword(inputType) -> FieldState.Password
        else -> FieldState.Typable
    }

    fun isPassword(inputType: Int): Boolean {
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return when (inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_TEXT ->
                variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }
}

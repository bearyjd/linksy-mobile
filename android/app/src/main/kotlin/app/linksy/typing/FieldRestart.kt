package app.linksy.typing

import android.text.InputType

/// What identifies the open field. The input type is read by class and
/// variation only: text flags (auto-correct, caps) may change on a restart.
data class FieldKey(val fieldId: Int, val packageName: String?, val inputType: Int, val imeOptions: Int)

/// Tells a restart of the same field (chat apps restart input after Send or
/// an autocomplete) from a new one. Only a new field, or a policy that
/// changed, starts a new field generation and so drops what is in flight.
class FieldRestart {
    private var key: FieldKey? = null
    private var state: FieldState? = null

    /// True if [key] with [state] is the field already open and this is only
    /// a restart of it. Remembers it either way.
    fun keeps(key: FieldKey, state: FieldState, restarting: Boolean): Boolean {
        val identity = key.copy(inputType = key.inputType and (InputType.TYPE_MASK_CLASS or InputType.TYPE_MASK_VARIATION))
        val same = restarting && identity == this.key && state == this.state
        this.key = identity
        this.state = state
        return same
    }

    /// The input finished: whatever opens next is a new field.
    fun reset() {
        key = null
        state = null
    }
}

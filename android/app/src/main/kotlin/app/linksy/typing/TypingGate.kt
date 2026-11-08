package app.linksy.typing

/// Whether typing from a computer may act now: a typing session is running
/// and the Linksy Keyboard has a field that takes it. A session's events are
/// valid only for the generation it began with, so Stop, screen-off, the kill
/// switch or a switch to another keyboard, which end it here at once, also
/// discard whatever was already queued for it. Also notices the user
/// switching to another keyboard after using this one.
class TypingGate {
    private val sessions = LinkedHashMap<String, Int>()
    private var generation = 0
    private var field = FieldState.None
    private var fieldGeneration = 0
    private var wasOurs = false

    /// The generation to hand the session's events with.
    @Synchronized fun begin(session: String, keyboardIsCurrent: Boolean = false): Int {
        wasOurs = keyboardIsCurrent || (sessions.isNotEmpty() && wasOurs)
        generation += 1
        sessions[session] = generation
        return generation
    }

    /// Ends [session] whatever its generation (the user's Stop).
    @Synchronized fun end(session: String) {
        sessions.remove(session)
    }

    /// Ends [session] only if it is still the one that began as [generation].
    /// False if a newer session has taken the id: a late end from the old
    /// reader must leave it alone.
    @Synchronized fun end(session: String, generation: Int): Boolean {
        if (replaced(session, generation)) return false
        sessions.remove(session)
        return true
    }

    /// A newer session now has [session]'s id.
    @Synchronized fun replaced(session: String, generation: Int): Boolean =
        sessions[session]?.let { it != generation } ?: false

    /// Whether the reader of [session] should still read.
    @Synchronized fun canRead(session: String, generation: Int): Boolean = sessions[session] == generation

    @Synchronized fun sessions(): Set<String> = sessions.keys.toSet()

    /// Every call is a new field (or none): events posted for the old one are
    /// discarded, never typed into this one.
    @Synchronized fun setField(state: FieldState) {
        field = state
        fieldGeneration += 1
    }

    /// The field generation to post an event with.
    @Synchronized fun fieldGeneration(): Int = fieldGeneration

    @Synchronized fun field(): FieldState = field

    @Synchronized fun canType(session: String, generation: Int, forField: Int = fieldGeneration): Boolean =
        sessions[session] == generation && forField == fieldGeneration && (field == FieldState.Typable || field == FieldState.Refused)

    /// What the field answered for an event. True if that changes what is shown.
    @Synchronized fun delivered(ok: Boolean): Boolean = when {
        !ok && field == FieldState.Typable -> { field = FieldState.Refused; true }
        ok && field == FieldState.Refused -> { field = FieldState.Typable; true }
        else -> false
    }

    /// The user's current keyboard changed. True if it was ours and is not any
    /// more while a session runs: the sessions should end.
    @Synchronized fun defaultKeyboardChanged(ours: Boolean): Boolean {
        val switchedAway = sessions.isNotEmpty() && wasOurs && !ours
        wasOurs = ours
        return switchedAway
    }

    companion object {
        /// Settings' `default_input_method` is `package/service`.
        fun isOurs(setting: String?, packageName: String): Boolean = setting?.substringBefore('/') == packageName
    }
}

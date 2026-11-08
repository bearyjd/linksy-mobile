package app.linksy.typing

/// Whether typing from a computer may act now: a typing session is running
/// and the Linksy Keyboard is alive with a field to type into. Also notices
/// the user switching to another keyboard after using this one.
class TypingGate {
    private val sessions = LinkedHashSet<String>()
    private var live = false
    private var wasOurs = false

    @Synchronized fun begin(session: String, keyboardIsCurrent: Boolean = false) {
        wasOurs = keyboardIsCurrent || (sessions.isNotEmpty() && wasOurs)
        sessions.add(session)
    }

    @Synchronized fun end(session: String) {
        sessions.remove(session)
    }

    @Synchronized fun sessions(): Set<String> = sessions.toSet()

    /// The keyboard service has a field to type into (or has not any more).
    @Synchronized fun keyboardLive(live: Boolean) {
        this.live = live
    }

    @Synchronized fun canType(): Boolean = sessions.isNotEmpty() && live

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

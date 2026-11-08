package app.linksy.control

/// Reads a control session to its end and hands on what the computer sent.
/// Reading to the end is the point: the Rust side stops sending video once
/// nobody drains the input, so a broken frame, a bad message or a companion
/// that is off must never stop the reading. Never logs what was read.
class ControlReader(
    private val read: () -> ByteArray?,
    private val pause: (Long) -> Unit,
    private val enabled: Boolean,
    private val onMsg: (ControlMsg) -> Unit,
    private val onBroken: () -> Unit,
    private val log: (String) -> Unit,
    private val parse: (String) -> ControlMsg? = ControlMsg::parse,
    maxFrame: Int = FrameDecoder.MAX_FRAME,
) {
    private val frames = FrameDecoder(maxFrame)
    private var brokenSeen = false
    private var poisonSeen = false

    fun run() {
        var gotSome = false
        var tries = 0
        while (true) {
            val bytes = read()
            if (bytes == null) {
                // The Rust side registers the session a moment after the app hears of it.
                if (!gotSome && tries++ < REGISTER_TRIES) {
                    pause(REGISTER_WAIT_MS)
                    continue
                }
                return
            }
            gotSome = true
            for (json in frames.feed(bytes)) if (enabled) handle(json)
            if (frames.broken && enabled && !brokenSeen) {
                brokenSeen = true
                log("Control: stopped, a frame was too large")
                guarded { onBroken() }
            }
        }
    }

    private fun handle(json: String) = guarded { parse(json)?.let(onMsg) }

    /// Nothing one frame does may end the loop (a StackOverflowError included).
    private fun guarded(step: () -> Unit) {
        try {
            step()
        } catch (t: Throwable) {
            if (!poisonSeen) {
                poisonSeen = true
                log("Control: a frame could not be handled and was skipped (${t.javaClass.simpleName})")
            }
        }
    }

    private companion object {
        const val REGISTER_TRIES = 20
        const val REGISTER_WAIT_MS = 100L
    }
}

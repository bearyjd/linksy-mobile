package app.linksy.control

/// What the client needs from Android's bound companion service.
interface BridgeLink {
    fun bind()
    fun unbind()
    fun begin()
    fun end()
    fun inject(json: String)
}

enum class BridgeStatus { Stopped, Connecting, Active, Failed }

enum class FailReason { Setup, NotPaired, AccessibilityOff, Unreachable, Protocol }

interface BridgeListener {
    fun onStatus(status: BridgeStatus, reason: FailReason?)
}

/// Talks to the companion for one control session. Not thread-safe: every call
/// comes from one thread.
class BridgeClient(
    private val link: BridgeLink,
    private val clock: () -> Long,
    private val schedule: (delayMs: Long) -> Unit,
    private val listener: BridgeListener,
) {
    var status = BridgeStatus.Stopped
        private set

    fun start(): Unit = TODO("stub")
    fun stop(): Unit = TODO("stub")
    fun onBound(): Unit = TODO("stub")
    fun onBindFailed(): Unit = TODO("stub")
    fun onLost(): Unit = TODO("stub")
    fun onReply(code: Int): Unit = TODO("stub")
    fun send(msg: ControlMsg): Unit = TODO("stub")
    fun tick(): Unit = TODO("stub")

    companion object {
        const val QUEUE_MAX = 256
        const val RATE_PER_SEC = 1200
        const val BURST = 300
        const val IDLE_RENEW_MS = 45_000L
        const val MAX_RENEW_MS = 25 * 60_000L
    }
}

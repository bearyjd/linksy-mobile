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

/// Talks to the companion for one control session: binds, BEGINs, forwards
/// messages (bounded queue, consecutive pointer moves merged, a rate under the
/// companion's budget) and ENDs. The companion tells nobody when it ends a
/// session (60 s idle, 30 min, revoke), so a new BEGIN is sent before either
/// limit can pass. Not thread-safe: every call comes from one thread, and
/// [schedule] must call [tick] after the delay it is given. Never logs what is
/// forwarded.
class BridgeClient(
    private val link: BridgeLink,
    private val clock: () -> Long,
    private val schedule: (delayMs: Long) -> Unit,
    private val listener: BridgeListener,
) {
    private enum class State { Stopped, Binding, Beginning, Active, Waiting, Failed }

    private var state = State.Stopped
    private var failure: FailReason? = null
    private var attempts = 0
    private var wakeAt = 0L
    private var pumpAt = 0L
    private var renewing = false
    private var sessionStart = 0L
    private var lastSend = 0L
    private var tokens = BURST.toDouble()
    private var tokensAt = clock()
    private val queue = ArrayDeque<ControlMsg>()
    private val heldButtons = LinkedHashSet<String>()
    private val heldKeys = LinkedHashSet<Long>()
    private var published: Pair<BridgeStatus, FailReason?>? = null

    val status: BridgeStatus
        get() = when (state) {
            State.Stopped -> BridgeStatus.Stopped
            State.Failed -> BridgeStatus.Failed
            State.Active -> BridgeStatus.Active
            State.Beginning -> if (renewing) BridgeStatus.Active else BridgeStatus.Connecting
            State.Binding, State.Waiting -> BridgeStatus.Connecting
        }

    fun start() {
        if (state != State.Stopped) return
        attempts = 0
        bind()
    }

    fun stop() {
        if (state == State.Stopped) return
        if (state == State.Active) {
            for (b in heldButtons) link.inject(ControlMsg.Button(b, false).toJson())
            for (k in heldKeys) link.inject(ControlMsg.Key(k, false).toJson())
            link.end()
        }
        link.unbind()
        reset()
        state = State.Stopped
        publish()
    }

    fun onBound() {
        if (state != State.Binding) return
        begin(renew = false)
    }

    fun onBindFailed() {
        if (state == State.Binding) retry()
    }

    /// The service went away, or its process died.
    fun onLost() {
        if (state == State.Binding || state == State.Beginning || state == State.Active) retry()
    }

    fun onReply(code: Int) {
        if (state != State.Beginning) return
        when (code) {
            0 -> activate()
            1, 2, 3 -> fail(FailReason.Setup)
            4 -> fail(FailReason.NotPaired)
            5 -> fail(FailReason.AccessibilityOff)
            6 -> retry()
            else -> fail(FailReason.Protocol)
        }
    }

    fun send(msg: ControlMsg) {
        when (state) {
            State.Stopped, State.Failed, State.Waiting -> return
            State.Binding, State.Beginning -> enqueue(msg)
            State.Active -> {
                if (renewalDue()) begin(renew = true)
                enqueue(msg)
                if (state == State.Active) pump()
            }
        }
    }

    fun tick() {
        val now = clock()
        if ((state == State.Binding || state == State.Beginning || state == State.Waiting) && now >= wakeAt) {
            if (state == State.Waiting) bind() else retry()
        }
        if (state == State.Active) pump()
    }

    private fun bind() {
        state = State.Binding
        arm(BIND_TIMEOUT_MS)
        link.bind()
        publish()
    }

    private fun begin(renew: Boolean) {
        if (renew) link.end()
        heldButtons.clear()
        heldKeys.clear()
        renewing = renew
        state = State.Beginning
        arm(REPLY_TIMEOUT_MS)
        link.begin()
        publish()
    }

    private fun activate() {
        state = State.Active
        attempts = 0
        renewing = false
        sessionStart = clock()
        lastSend = sessionStart
        publish()
        pump()
    }

    private fun retry() {
        link.unbind()
        reset()
        attempts += 1
        if (attempts > MAX_RETRIES) {
            fail(FailReason.Unreachable)
            return
        }
        state = State.Waiting
        arm(minOf(BACKOFF_BASE_MS shl (attempts - 1), BACKOFF_MAX_MS))
        publish()
    }

    private fun fail(reason: FailReason) {
        link.unbind()
        reset()
        state = State.Failed
        failure = reason
        publish()
    }

    /// What was waiting or held belongs to a session that is gone.
    private fun reset() {
        queue.clear()
        heldButtons.clear()
        heldKeys.clear()
        renewing = false
        failure = null
    }

    private fun arm(ms: Long) {
        wakeAt = clock() + ms
        schedule(ms)
    }

    private fun renewalDue(): Boolean {
        val now = clock()
        return now - lastSend >= IDLE_RENEW_MS || now - sessionStart >= MAX_RENEW_MS
    }

    private fun publish() {
        val now = status to failure
        if (now == published) return
        published = now
        listener.onStatus(now.first, now.second)
    }

    private fun enqueue(msg: ControlMsg) {
        if (msg is ControlMsg.Pointer && queue.lastOrNull() is ControlMsg.Pointer) {
            queue.removeLast()
            queue.addLast(msg)
            return
        }
        if (queue.size >= QUEUE_MAX) {
            val move = queue.indexOfFirst { it is ControlMsg.Pointer }
            if (move >= 0) {
                queue.removeAt(move)
            } else if (msg.releases) {
                val other = queue.indexOfFirst { !it.releases }
                if (other < 0) return
                queue.removeAt(other)
            } else {
                return
            }
        }
        queue.addLast(msg)
    }

    private fun pump() {
        val now = clock()
        tokens = minOf(BURST.toDouble(), tokens + (now - tokensAt) * RATE_PER_SEC / 1000.0)
        tokensAt = now
        while (queue.isNotEmpty() && tokens >= 1.0) {
            transmit(queue.removeFirst())
            tokens -= 1.0
        }
        if (queue.isNotEmpty() && pumpAt <= now) {
            val ms = Math.ceil((1.0 - tokens) * 1000.0 / RATE_PER_SEC).toLong().coerceAtLeast(1L)
            pumpAt = now + ms
            schedule(ms)
        }
    }

    private fun transmit(msg: ControlMsg) {
        when (msg) {
            is ControlMsg.Button -> if (msg.pressed) heldButtons += msg.button else heldButtons -= msg.button
            is ControlMsg.Key -> if (msg.pressed) heldKeys += msg.keysym else heldKeys -= msg.keysym
            else -> Unit
        }
        lastSend = clock()
        link.inject(msg.toJson())
    }

    companion object {
        const val QUEUE_MAX = 256

        /// Under the companion's own budget (1500/s sustained, burst 3000).
        const val RATE_PER_SEC = 1200
        const val BURST = 300

        /// The companion ends a session after 60 s without input and after 30 min.
        const val IDLE_RENEW_MS = 45_000L
        const val MAX_RENEW_MS = 25 * 60_000L

        const val BIND_TIMEOUT_MS = 5_000L
        const val REPLY_TIMEOUT_MS = 3_000L
        const val BACKOFF_BASE_MS = 1_000L
        const val BACKOFF_MAX_MS = 30_000L
        const val MAX_RETRIES = 6
    }
}

package app.linksy.control

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.os.SystemClock
import app.linksy.Logs
import app.linksy.media.NativeSession
import java.util.concurrent.atomic.AtomicBoolean

/// A control session's path to the companion: reads the computer's input
/// frames from the session (rust/src/sessions.rs, framed JSON), checks them
/// and hands them to [BridgeClient], which talks to the companion's bound
/// service. The session is always read to its end, whether the companion
/// works or not, because the Rust side stops sending video when nobody reads
/// input. Never logs what is forwarded.
class ControlBridge(
    private val context: Context,
    private val session: String,
    private val enabled: Boolean,
    private val onStatus: (BridgeStatus) -> Unit,
) {
    private val thread = HandlerThread("linksy-control").apply { start() }
    private val handler = Handler(thread.looper)
    private var service: Messenger? = null
    private var connection: ServiceConnection? = null
    private var generation = 0
    private val stopped = AtomicBoolean(false)

    private val reply = Messenger(Handler(thread.looper) { msg ->
        if (msg.what == REPLY_BEGIN) client.onReply(msg.arg1)
        true
    })

    /// One per bind: what an older connection says after a rebind is ignored.
    private fun connectionFor(gen: Int) = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            handler.post { if (gen == generation) { service = Messenger(binder); client.onBound() } }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            handler.post { if (gen == generation) { service = null; client.onLost() } }
        }

        override fun onBindingDied(name: ComponentName) {
            handler.post { if (gen == generation) { service = null; client.onLost() } }
        }

        override fun onNullBinding(name: ComponentName) {
            handler.post { if (gen == generation) client.onBindFailed() }
        }
    }

    private val link = object : BridgeLink {
        override fun bind() {
            val gen = ++generation
            // Checked before every bind: a companion that was replaced since
            // it was last trusted must not get the session.
            if (!ControlCompanion.trusted(context)) {
                handler.post { if (gen == generation) client.onUntrusted() }
                return
            }
            val intent = Intent(ControlCompanion.BIND_ACTION).setComponent(ControlCompanion.service())
            val conn = connectionFor(gen)
            val ok = try {
                context.bindService(intent, conn, Context.BIND_AUTO_CREATE)
            } catch (_: SecurityException) {
                false
            }
            if (ok) {
                connection = conn
            } else {
                runCatching { context.unbindService(conn) }
                handler.post { if (gen == generation) client.onBindFailed() }
            }
        }

        override fun unbind() {
            generation++
            service = null
            connection?.let { runCatching { context.unbindService(it) } }
            connection = null
        }

        override fun begin() = post(Message.obtain(null, MSG_BEGIN).also { it.replyTo = reply })
        override fun end() = post(Message.obtain(null, MSG_END))
        override fun inject(json: String) =
            post(Message.obtain(null, MSG_INJECT).also { it.data = Bundle().apply { putString(KEY_JSON, json) } })

        private fun post(msg: Message) {
            try {
                service?.send(msg)
            } catch (_: RemoteException) {
                handler.post { client.onLost() }
            }
        }
    }

    private val client: BridgeClient = BridgeClient(
        link,
        SystemClock::elapsedRealtime,
        { ms -> handler.postDelayed({ client.tick() }, ms) },
        object : BridgeListener {
            override fun onStatus(status: BridgeStatus, reason: FailReason?) {
                Logs.add("Control: $status${reason?.let { " ($it)" } ?: ""}")
                // Not approved there (any more), or refused for good: don't
                // offer control again until it is paired again.
                if (reason == FailReason.NotPaired || reason == FailReason.Setup) ControlCompanion.setPaired(context, false)
                onStatus(status)
            }
        },
    )

    fun start() {
        if (enabled) handler.post { client.start() }
        Thread({ read() }, "linksy-control-read").start()
    }

    /// Releases what is held, ENDs and unbinds, in that order. Harmless twice.
    fun stop() {
        if (!stopped.compareAndSet(false, true)) return
        handler.post {
            client.stop()
            thread.quitSafely()
        }
    }

    private fun read() {
        ControlReader(
            read = { NativeSession.read(session) },
            pause = { Thread.sleep(it) },
            enabled = enabled,
            onMsg = { msg -> handler.post { client.send(msg) } },
            onBroken = { handler.post { client.stop() } },
            log = { Logs.add(it) },
        ).run()
        stop()
    }

    private companion object {
        const val MSG_BEGIN = 1
        const val MSG_END = 2
        const val MSG_INJECT = 3
        const val REPLY_BEGIN = 100
        const val KEY_JSON = "json"
    }
}

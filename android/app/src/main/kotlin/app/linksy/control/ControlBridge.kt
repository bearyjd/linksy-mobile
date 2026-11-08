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
    private val onActive: (Boolean) -> Unit,
) {
    private val thread = HandlerThread("linksy-control").apply { start() }
    private val handler = Handler(thread.looper)
    private var service: Messenger? = null
    private var bound = false

    private val reply = Messenger(Handler(thread.looper) { msg ->
        if (msg.what == REPLY_BEGIN) client.onReply(msg.arg1)
        true
    })

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            handler.post { service = Messenger(binder); client.onBound() }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            handler.post { service = null; client.onLost() }
        }

        override fun onBindingDied(name: ComponentName) {
            handler.post { service = null; client.onLost() }
        }

        override fun onNullBinding(name: ComponentName) {
            handler.post { client.onBindFailed() }
        }
    }

    private val link = object : BridgeLink {
        override fun bind() {
            val intent = Intent(ControlCompanion.BIND_ACTION).setComponent(ControlCompanion.service())
            bound = try {
                context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            } catch (_: SecurityException) {
                false
            }
            if (!bound) handler.post { client.onBindFailed() }
        }

        override fun unbind() {
            service = null
            runCatching { context.unbindService(connection) }
            bound = false
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
                // Not approved there (any more): don't offer control again.
                if (reason == FailReason.NotPaired) ControlCompanion.setPaired(context, false)
                onActive(status == BridgeStatus.Active)
            }
        },
    )

    fun start() {
        if (enabled) handler.post { client.start() }
        Thread({ read() }, "linksy-control-read").start()
    }

    /// Releases what is held, ENDs and unbinds, in that order.
    fun stop() {
        handler.post {
            client.stop()
            thread.quitSafely()
        }
    }

    private fun read() {
        val frames = FrameDecoder()
        var gotSome = false
        var tries = 0
        while (true) {
            val bytes = NativeSession.read(session)
            if (bytes == null) {
                // The Rust side registers the session a moment after the app hears of it.
                if (!gotSome && tries++ < REGISTER_TRIES) {
                    Thread.sleep(REGISTER_WAIT_MS)
                    continue
                }
                break
            }
            gotSome = true
            for (json in frames.feed(bytes)) {
                ControlMsg.parse(json)?.let { msg -> if (enabled) handler.post { client.send(msg) } }
            }
            if (frames.broken && enabled) {
                Logs.add("Control: stopped, a frame was too large")
                handler.post { client.stop() }
            }
        }
        stop()
    }

    private companion object {
        const val MSG_BEGIN = 1
        const val MSG_END = 2
        const val MSG_INJECT = 3
        const val REPLY_BEGIN = 100
        const val KEY_JSON = "json"
        const val REGISTER_TRIES = 20
        const val REGISTER_WAIT_MS = 100L
    }
}

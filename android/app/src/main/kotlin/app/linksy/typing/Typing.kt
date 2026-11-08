package app.linksy.typing

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import app.linksy.Lang
import app.linksy.Logs
import app.linksy.MainActivity
import app.linksy.R
import app.linksy.media.NativeSession

/// A computer typing on this phone (docs/protocol.md §4.26): reads what the
/// Rust side hands over (rust/src/keyboard.rs) and gives it to the Linksy
/// Keyboard, but only while a session runs and that keyboard is the one in
/// use. Ends the session when the screen turns off or the user switches to
/// another keyboard. What is typed is never logged, only the session's start
/// and end.
object Typing {
    val gate = TypingGate()

    /// The running keyboard service, if Android has started it.
    @Volatile var service: LinksyKeyboardService? = null

    /// The keyboard stayed because there is no other keyboard to go back to.
    @Volatile private var hint = false

    private val main = Handler(Looper.getMainLooper())
    private val peers = LinkedHashMap<String, String>()
    private var watching: Context? = null

    private const val CHANNEL = "typing"
    private const val ACTION_STOP = "app.linksy.STOP_TYPING"
    private const val EXTRA_SESSION = "session"
    private const val NOTIFICATION = 40_000
    private const val HINT_NOTIFICATION = 39_999

    /// Events handed to the main thread and not yet applied, per session.
    private const val IN_FLIGHT = 32

    /// How long a session may take to show up on the Rust side.
    private const val REGISTER_TRIES = 20
    private const val REGISTER_WAIT_MS = 100L

    /// Who is typing now, for the keyboard's own view.
    fun who(): String? = synchronized(peers) { peers.values.lastOrNull() }

    /// Starts reading [session]. The Rust side registers it a moment after the
    /// app hears of it, so a first read that finds nothing is tried again.
    fun begin(context: Context, session: String, peer: String) {
        val app = context.applicationContext
        val generation = gate.begin(session, keyboardIsCurrent(app))
        hint = false
        main.post {
            if (session !in gate.sessions()) return@post
            synchronized(peers) { peers[session] = peer }
            watch(app)
            notice(app, session, peer)
            app.getSystemService(NotificationManager::class.java).cancel(HINT_NOTIFICATION)
            Logs.add("Typing: $peer can type on this phone")
            service?.refresh()
        }
        Thread({ read(app, session, generation) }, "linksy-typing").start()
    }

    /// One reader per session. At most [IN_FLIGHT] events wait for the main
    /// thread, which is never blocked: only this thread waits for room.
    private fun read(app: Context, session: String, generation: Int) {
        val room = Backpressure(IN_FLIGHT)
        var gotSome = false
        var tries = 0
        while (true) {
            val bytes = NativeSession.read(session)
            if (bytes == null) {
                if (!gotSome && tries++ < REGISTER_TRIES) {
                    Thread.sleep(REGISTER_WAIT_MS)
                    continue
                }
                // Never registered: end it, rather than leave it open.
                if (!gotSome) NativeSession.finish(session)
                break
            }
            gotSome = true
            val event = TypingEvent.decode(bytes) ?: continue
            if (!room.acquire { session in gate.sessions() }) break
            main.post {
                try {
                    if (gate.canType(session, generation)) deliver(event)
                } finally {
                    room.release()
                }
            }
        }
        main.post { ended(app, session) }
    }

    private fun deliver(event: TypingEvent) {
        val took = service?.type(event) ?: false
        if (gate.delivered(took)) changed()
    }

    /// The user's Stop, the notification's, or the keyboard's own. Ends it
    /// here at once, so nothing already queued is typed, then tells core.
    fun stop(session: String) {
        gate.end(session)
        NativeSession.finish(session)
        if (gate.sessions().isEmpty()) service?.releaseAll()
    }

    fun stopAll() {
        gate.sessions().forEach(::stop)
    }

    /// The field Linksy Keyboard types into changed.
    fun field(state: FieldState) {
        gate.setField(state)
        changed()
    }

    /// The keyboard's view and the notifications say what is happening.
    private fun changed() {
        service?.refresh()
        val app = watching ?: return
        val shown = synchronized(peers) { peers.toMap() }
        shown.forEach { (session, peer) -> if (session in gate.sessions()) notice(app, session, peer) }
    }

    private fun ended(app: Context, session: String) {
        gate.end(session)
        synchronized(peers) { peers.remove(session) }
        app.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION + session.hashCode())
        Logs.add("Typing: session ended")
        if (gate.sessions().isEmpty()) {
            service?.let {
                it.releaseAll()
                leaveIfIdle(it)
            }
            unwatch(app)
        }
    }

    /// With no session running, Linksy Keyboard (which has no keys) must not
    /// stay the keyboard in use: back to the one before; or, with none to go
    /// back to, or if Android refuses, say so.
    fun leaveIfIdle(keyboard: LinksyKeyboardService) {
        val app = keyboard.applicationContext
        val other = app.getSystemService(InputMethodManager::class.java).enabledInputMethodList
            .any { it.packageName != app.packageName }
        val sdk = Build.VERSION.SDK_INT
        val leave = SwitchBack.decide(gate.sessions().isNotEmpty(), keyboardIsCurrent(app), other, sdk)
        hint = leave == Leave.Hint || (leave != Leave.Nothing && !keyboard.leave(sdk))
        if (hint) hintNotice(app)
        keyboard.refresh()
    }

    /// What the keyboard's view and the notification say now.
    fun statusText(context: Context): String {
        val strings = when {
            gate.sessions().isEmpty() -> if (hint) R.string.keyboard_hint else R.string.keyboard_idle
            gate.field() == FieldState.Password -> R.string.keyboard_paused_password
            gate.field() == FieldState.NoInput || gate.field() == FieldState.Refused -> R.string.keyboard_unsupported
            else -> return Lang.str(context, R.string.keyboard_typing, who() ?: "")
        }
        return Lang.str(context, strings)
    }

    // The screen turning off, or another keyboard coming in, ends the typing.
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = stopAll()
    }

    private val keyboardChanged = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) {
            val app = watching ?: return
            if (gate.defaultKeyboardChanged(keyboardIsCurrent(app))) stopAll()
        }
    }

    private fun watch(app: Context) {
        if (watching != null) return
        watching = app
        app.registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
        app.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.DEFAULT_INPUT_METHOD), false, keyboardChanged,
        )
    }

    private fun unwatch(app: Context) {
        if (watching == null) return
        watching = null
        runCatching { app.unregisterReceiver(screenOff) }
        app.contentResolver.unregisterContentObserver(keyboardChanged)
    }

    /// Whether Linksy Keyboard is turned on in Android's settings, is the one
    /// in use, and whether a computer is typing now.
    fun state(context: Context): Map<String, Boolean> {
        val imm = context.getSystemService(InputMethodManager::class.java)
        val enabled = imm.enabledInputMethodList.any {
            it.packageName == context.packageName && it.serviceName == LinksyKeyboardService::class.java.name
        }
        return mapOf("enabled" to enabled, "current" to keyboardIsCurrent(context), "typing" to gate.sessions().isNotEmpty())
    }

    fun keyboardIsCurrent(context: Context): Boolean = TypingGate.isOurs(
        Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD),
        context.packageName,
    )

    private fun builder(app: Context): Notification.Builder {
        val manager = app.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, Lang.str(app, R.string.channel_typing), NotificationManager.IMPORTANCE_LOW),
            )
        }
        val open = PendingIntent.getActivity(app, 1, Intent(app, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        @Suppress("DEPRECATION")
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(app, CHANNEL) else Notification.Builder(app)
        return builder.setSmallIcon(R.drawable.ic_stat_linksy).setContentTitle("Linksy").setContentIntent(open)
    }

    /// While it goes on: a notification that says so (or that it is paused, or
    /// cannot type into this field), with Stop.
    private fun notice(app: Context, session: String, peer: String) {
        val stop = PendingIntent.getBroadcast(
            app, session.hashCode(),
            Intent(app, Stop::class.java).setAction(ACTION_STOP).putExtra(EXTRA_SESSION, session),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = when (gate.field()) {
            FieldState.Password, FieldState.NoInput, FieldState.Refused -> statusText(app)
            else -> Lang.str(app, R.string.typing_notice, peer)
        }
        val note = builder(app)
            .setContentText(text)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, Lang.str(app, R.string.typing_stop), stop).build())
            .build()
        app.getSystemService(NotificationManager::class.java).notify(NOTIFICATION + session.hashCode(), note)
    }

    /// Linksy Keyboard is still the keyboard, and there is no other to go back to.
    private fun hintNotice(app: Context) {
        val note = builder(app).setContentText(Lang.str(app, R.string.keyboard_hint)).setAutoCancel(true).build()
        app.getSystemService(NotificationManager::class.java).notify(HINT_NOTIFICATION, note)
    }

    /// The Stop button on the notification.
    class Stop : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            intent.getStringExtra(EXTRA_SESSION)?.let(::stop)
        }
    }
}

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

    private val main = Handler(Looper.getMainLooper())
    private val peers = LinkedHashMap<String, String>()
    private var watching: Context? = null

    private const val CHANNEL = "typing"
    private const val ACTION_STOP = "app.linksy.STOP_TYPING"
    private const val EXTRA_SESSION = "session"
    private const val NOTIFICATION = 40_000

    /// Who is typing now, for the keyboard's own view.
    fun who(): String? = synchronized(peers) { peers.values.lastOrNull() }

    /// Starts reading [session]. The Rust side registers it a moment after the
    /// app hears of it, so a first read that finds nothing is tried again.
    fun begin(context: Context, session: String, peer: String) {
        val app = context.applicationContext
        main.post {
            gate.begin(session, keyboardIsCurrent(app))
            synchronized(peers) { peers[session] = peer }
            watch(app)
            notice(app, session, peer)
            Logs.add("Typing: $peer can type on this phone")
            service?.refresh()
        }
        Thread({ read(app, session) }, "linksy-typing").start()
    }

    private fun read(app: Context, session: String) {
        var gotSome = false
        var tries = 0
        while (true) {
            val bytes = NativeSession.read(session)
            if (bytes == null) {
                if (!gotSome && tries++ < 20) {
                    Thread.sleep(100)
                    continue
                }
                break
            }
            gotSome = true
            val event = TypingEvent.decode(bytes) ?: continue
            main.post { if (gate.canType()) service?.type(event) }
        }
        main.post { ended(app, session) }
    }

    /// The user's Stop, the notification's, or the keyboard's own.
    fun stop(session: String) {
        NativeSession.finish(session)
    }

    fun stopAll() {
        gate.sessions().forEach(::stop)
    }

    private fun ended(app: Context, session: String) {
        gate.end(session)
        synchronized(peers) { peers.remove(session) }
        app.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION + session.hashCode())
        Logs.add("Typing: session ended")
        service?.let {
            it.releaseAll()
            it.refresh()
        }
        if (gate.sessions().isEmpty()) unwatch(app)
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

    /// While it goes on: a notification that says so, with Stop.
    private fun notice(app: Context, session: String, peer: String) {
        val manager = app.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, Lang.str(app, R.string.channel_typing), NotificationManager.IMPORTANCE_LOW),
            )
        }
        val stop = PendingIntent.getBroadcast(
            app, session.hashCode(),
            Intent(app, Stop::class.java).setAction(ACTION_STOP).putExtra(EXTRA_SESSION, session),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val open = PendingIntent.getActivity(
            app, session.hashCode() + 1, Intent(app, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        @Suppress("DEPRECATION")
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(app, CHANNEL) else Notification.Builder(app)
        val note = builder
            .setSmallIcon(R.drawable.ic_stat_linksy)
            .setContentTitle("Linksy")
            .setContentText(Lang.str(app, R.string.typing_notice, peer))
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, Lang.str(app, R.string.typing_stop), stop).build())
            .build()
        manager.notify(NOTIFICATION + session.hashCode(), note)
    }

    /// The Stop button on the notification.
    class Stop : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            intent.getStringExtra(EXTRA_SESSION)?.let(::stop)
        }
    }
}

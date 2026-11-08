package app.linksy.control

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings

/// The companion app (app.linksy.mg.control, github.com/bearyjd/linksy-control)
/// as this phone sees it. Reading its state needs only a `<queries>` entry,
/// no permission; nothing here changes anything on the phone.
object ControlCompanion {
    private const val PAIRED = "companion.paired"
    const val PAIR_ACTION = "app.linksy.mg.control.PAIR"
    const val BIND_ACTION = "app.linksy.mg.control.BIND"

    private fun prefs(context: Context) = context.getSharedPreferences("linksy", Context.MODE_PRIVATE)

    fun installed(context: Context): Boolean = try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(CompanionPolicy.PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    fun accessibilityOn(context: Context): Boolean = CompanionPolicy.accessibilityOn(
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
    )

    /// Approved once in the companion's own screen; the companion is asked
    /// again at every BEGIN, so a revoke shows up there (reply 4).
    fun paired(context: Context): Boolean = prefs(context).getBoolean(PAIRED, false)

    fun setPaired(context: Context, paired: Boolean) {
        prefs(context).edit().putBoolean(PAIRED, paired).apply()
    }

    /// The user's switch, kept by the app's settings (`control`).
    fun userOn(context: Context): Boolean = prefs(context).getString("app.control", null) == "on"

    fun status(context: Context): CompanionStatus =
        CompanionPolicy.status(installed(context), accessibilityOn(context), paired(context))

    /// Whether a control session may use the companion now.
    fun mayControl(context: Context): Boolean = CompanionPolicy.advertises(status(context), userOn(context))

    fun state(context: Context): Map<String, Boolean> = mapOf(
        "installed" to installed(context),
        "accessibility" to accessibilityOn(context),
        "paired" to paired(context),
    )

    fun service() = ComponentName(CompanionPolicy.PACKAGE, "${CompanionPolicy.PACKAGE}.ControlBridgeService")

    fun pairIntent(): Intent = Intent(PAIR_ACTION)
        .setComponent(ComponentName(CompanionPolicy.PACKAGE, "${CompanionPolicy.PACKAGE}.PairActivity"))

    fun accessibilitySettings(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    /// The companion's App info, where "Allow restricted settings" is.
    fun appInfo(): Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${CompanionPolicy.PACKAGE}"))
}

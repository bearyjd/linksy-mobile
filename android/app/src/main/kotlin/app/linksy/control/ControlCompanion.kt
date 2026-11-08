package app.linksy.control

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import app.linksy.BuildConfig
import app.linksy.media.ShareService
import java.security.MessageDigest

/// The companion app (app.linksy.mg.control, github.com/bearyjd/linksy-control)
/// as this phone sees it. Reading its state needs only a `<queries>` entry,
/// no permission; nothing here changes anything on the phone.
object ControlCompanion {
    private const val PAIRED = "companion.paired"
    const val PAIR_ACTION = "app.linksy.mg.control.PAIR"
    const val BIND_ACTION = "app.linksy.mg.control.BIND"

    private fun prefs(context: Context) = context.getSharedPreferences("linksy", Context.MODE_PRIVATE)

    /// This build knows whose companion to trust (a pin was given at build
    /// time) and this Android can tell who signed it.
    fun configured(): Boolean =
        CompanionTrust.configured(BuildConfig.COMPANION_CERT_SHA256) && CompanionTrust.supported(Build.VERSION.SDK_INT)

    /// SHA-256 of each certificate the installed companion is signed with now
    /// (not the history of keys it was once signed with); empty if it is absent.
    private fun signers(context: Context): List<String> {
        if (Build.VERSION.SDK_INT < CompanionTrust.MIN_SDK) return emptyList()
        return try {
            val info = context.packageManager.getPackageInfo(CompanionPolicy.PACKAGE, PackageManager.GET_SIGNING_CERTIFICATES)
            val digest = MessageDigest.getInstance("SHA-256")
            info.signingInfo?.apkContentsSigners.orEmpty().map { sig ->
                digest.digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
            }
        } catch (_: PackageManager.NameNotFoundException) {
            emptyList()
        }
    }

    /// The companion is there and signed with the pinned certificate. Checked
    /// every time it matters (pairing, binding), never remembered.
    fun trusted(context: Context): Boolean =
        configured() && CompanionTrust.trusted(BuildConfig.COMPANION_CERT_SHA256, signers(context))

    /// Installed and trusted: anything else is treated as not installed.
    fun installed(context: Context): Boolean = trusted(context)

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
        "configured" to configured(),
        "installed" to installed(context),
        "bridged" to ShareService.controlActive,
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

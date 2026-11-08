package app.linksy.control

/// How far the user got with the companion (app.linksy.mg.control).
enum class CompanionStatus { NotInstalled, NeedsAccessibility, NeedsPairing, Ready }

object CompanionPolicy {
    const val PACKAGE = "app.linksy.mg.control"
    const val SERVICE = "app.linksy.mg.control.ControlAccessibilityService"

    fun status(installed: Boolean, accessibilityOn: Boolean, paired: Boolean): CompanionStatus = TODO("stub")

    /// Whether this phone says `control` to computers.
    fun advertises(status: CompanionStatus, userOn: Boolean): Boolean = TODO("stub")

    /// Whether [setting] (Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, a
    /// colon-separated list of `package/class`) has the companion's service on.
    fun accessibilityOn(setting: String?): Boolean = TODO("stub")
}

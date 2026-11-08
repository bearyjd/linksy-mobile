package app.linksy.control

/// Whether the app installed as app.linksy.mg.control is the companion: its
/// signing certificate must be the one pinned at build time
/// (`-PcompanionCertSha256=<64 hex>`, BuildConfig.COMPANION_CERT_SHA256). No
/// pin, or a malformed one, trusts nothing: control stays off until a build
/// says whose companion it works with.
object CompanionTrust {
    private val SHA256 = Regex("[0-9a-f]{64}")

    /// First Android that reports the current signer apart from its history
    /// (`SigningInfo`); below it control is not offered.
    const val MIN_SDK = 28

    /// 64 lowercase hex digits, from any case, with colons or spaces between
    /// bytes allowed; null for anything else.
    fun normalise(hex: String): String? =
        hex.filterNot { it == ':' || it.isWhitespace() }.lowercase().takeIf { SHA256.matches(it) }

    fun configured(pin: String): Boolean = normalise(pin) != null

    /// [signers] are the SHA-256 digests of the certificates the installed
    /// app is signed with now (not its history). Exactly one must be there
    /// and it must be the pin.
    fun trusted(pin: String, signers: List<String>): Boolean {
        val wanted = normalise(pin) ?: return false
        return signers.size == 1 && normalise(signers[0]) == wanted
    }

    fun supported(sdk: Int): Boolean = sdk >= MIN_SDK
}

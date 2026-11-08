package app.linksy.control

object CompanionTrust {
    fun normalise(hex: String): String? = null

    fun configured(pin: String): Boolean = false

    fun trusted(pin: String, signers: List<String>): Boolean = false

    fun supported(sdk: Int): Boolean = false
}

package app.linksy.control

object JsonShape {
    const val MAX_DEPTH = 8

    fun withinDepth(json: String, max: Int = MAX_DEPTH): Boolean = true
}

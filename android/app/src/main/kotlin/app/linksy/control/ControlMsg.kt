package app.linksy.control

import org.json.JSONException
import org.json.JSONObject

/// One input message from a computer (`pointer`, `button`, `scroll`, `key`,
/// `text`), checked and ready to hand to the companion.
sealed interface ControlMsg {
    data class Pointer(val x: Double, val y: Double) : ControlMsg
    data class Button(val button: String, val pressed: Boolean) : ControlMsg
    data class Scroll(val dx: Double, val dy: Double, val discrete: Boolean) : ControlMsg
    data class Key(val keysym: Long, val pressed: Boolean) : ControlMsg
    data class Text(val text: String) : ControlMsg {
        /// Never the text: it can be a password.
        override fun toString() = "Text(len=${text.length})"
    }

    /// A press that has a matching release which must never be lost.
    val releases: Boolean
        get() = (this is Button && !pressed) || (this is Key && !pressed)

    /// The JSON the companion reads.
    fun toJson(): String {
        val o = JSONObject()
        when (this) {
            is Pointer -> o.put("type", "pointer").put("x", x).put("y", y)
            is Button -> o.put("type", "button").put("button", button).put("pressed", pressed)
            is Scroll -> o.put("type", "scroll").put("dx", dx).put("dy", dy).put("discrete", discrete)
            is Key -> o.put("type", "key").put("keysym", keysym).put("pressed", pressed)
            is Text -> o.put("type", "text").put("text", text)
        }
        return o.toString()
    }

    companion object {
        const val MAX_TEXT = 4096

        /// Null for a type this phone doesn't act on, and for anything malformed.
        fun parse(json: String): ControlMsg? {
            val o = try {
                JSONObject(json)
            } catch (_: JSONException) {
                return null
            }
            return when (o.optString("type")) {
                "pointer" -> {
                    val x = number(o, "x") ?: return null
                    val y = number(o, "y") ?: return null
                    Pointer(x.coerceIn(0.0, 1.0), y.coerceIn(0.0, 1.0))
                }
                "button" -> {
                    val name = o.opt("button") as? String ?: return null
                    val pressed = o.opt("pressed") as? Boolean ?: return null
                    if (name !in BUTTONS) null else Button(name, pressed)
                }
                "scroll" -> {
                    val dx = number(o, "dx") ?: return null
                    val dy = number(o, "dy") ?: return null
                    Scroll(dx, dy, o.opt("discrete") as? Boolean ?: false)
                }
                "key" -> {
                    val sym = number(o, "keysym") ?: return null
                    val pressed = o.opt("pressed") as? Boolean ?: return null
                    if (sym < 0.0 || sym > MAX_KEYSYM || sym != Math.floor(sym)) null else Key(sym.toLong(), pressed)
                }
                "text" -> {
                    val text = o.opt("text") as? String ?: return null
                    if (text.length > MAX_TEXT) null else Text(text)
                }
                else -> null
            }
        }

        private val BUTTONS = setOf("left", "middle", "right", "back", "forward")
        private const val MAX_KEYSYM = 4294967295.0

        private fun number(o: JSONObject, name: String): Double? {
            val v = (o.opt(name) as? Number)?.toDouble() ?: return null
            return if (v.isFinite()) v else null
        }
    }
}

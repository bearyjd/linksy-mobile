package app.linksy.control

/// A cheap look at a frame before org.json sees it. org.json parses nested
/// arrays and objects recursively, and a 64 KiB frame can hold tens of
/// thousands of `[`: enough to overflow the reader's stack. Every message this
/// phone acts on is flat, so deeper nesting than [MAX_DEPTH] is refused.
/// Iterative, and it keeps to JSON's strings and escapes so brackets typed
/// into a `text` message are not counted.
object JsonShape {
    const val MAX_DEPTH = 8

    fun withinDepth(json: String, max: Int = MAX_DEPTH): Boolean {
        var depth = 0
        var inString = false
        var escaped = false
        for (c in json) {
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '[', '{' -> if (++depth > max) return false
                ']', '}' -> if (--depth < 0) return false
            }
        }
        return true
    }
}

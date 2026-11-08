package app.linksy.control

/// Cuts the bytes of a control session into messages: a big-endian u32 length,
/// then that many bytes of JSON (docs/protocol.md §4.8). Chunks from the
/// session arrive split or merged anywhere.
class FrameDecoder(private val maxFrame: Int = MAX_FRAME) {
    /// A length over the limit was seen: nothing after it can be trusted, so
    /// nothing more is returned.
    var broken = false
        private set

    private var pending = ByteArray(0)

    fun feed(chunk: ByteArray): List<String> {
        if (broken) return emptyList()
        pending += chunk
        val frames = ArrayList<String>()
        var pos = 0
        while (pending.size - pos >= 4) {
            val length = ((pending[pos].toInt() and 0xFF) shl 24) or
                ((pending[pos + 1].toInt() and 0xFF) shl 16) or
                ((pending[pos + 2].toInt() and 0xFF) shl 8) or
                (pending[pos + 3].toInt() and 0xFF)
            if (length < 0 || length > maxFrame) {
                broken = true
                pending = ByteArray(0)
                return frames
            }
            if (pending.size - pos - 4 < length) break
            if (length > 0) frames += String(pending, pos + 4, length, Charsets.UTF_8)
            pos += 4 + length
        }
        pending = pending.copyOfRange(pos, pending.size)
        return frames
    }

    companion object {
        const val MAX_FRAME = 64 * 1024
    }
}

package app.linksy.control

/// Cuts the bytes of a control session into messages: a big-endian u32 length,
/// then that many bytes of JSON (docs/protocol.md §4.8). Chunks from the
/// session arrive split or merged anywhere.
class FrameDecoder(private val maxFrame: Int = MAX_FRAME) {
    /// A length over the limit was seen: nothing after it can be trusted, so
    /// nothing more is returned.
    var broken = false
        private set

    fun feed(chunk: ByteArray): List<String> = TODO("stub")

    companion object {
        const val MAX_FRAME = 64 * 1024
    }
}

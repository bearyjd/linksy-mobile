package app.linksy.control

class ControlReader(
    private val read: () -> ByteArray?,
    private val pause: (Long) -> Unit,
    private val enabled: Boolean,
    private val onMsg: (ControlMsg) -> Unit,
    private val onBroken: () -> Unit,
    private val log: (String) -> Unit,
    private val parse: (String) -> ControlMsg? = ControlMsg::parse,
    private val maxFrame: Int = FrameDecoder.MAX_FRAME,
) {
    fun run() {}
}

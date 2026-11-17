package app.linksy.control

/// Who may answer a BEGIN: only the companion's own process. A Messenger
/// can be handed to any app, so the sender's UID is checked on every reply.
object ReplyGate {
    fun accepts(sendingUid: Int, companionUid: Int?): Boolean =
        companionUid != null && companionUid >= 0 && sendingUid == companionUid
}

package app.linksy.control

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyGateTest {
    @Test fun acceptsOnlyTheCompanionsUid() {
        assertTrue(ReplyGate.accepts(sendingUid = 10123, companionUid = 10123))
        assertFalse(ReplyGate.accepts(sendingUid = 10124, companionUid = 10123))
    }

    @Test fun refusesEverythingWhenTheCompanionIsUnknown() {
        assertFalse(ReplyGate.accepts(sendingUid = 10123, companionUid = null))
        assertFalse(ReplyGate.accepts(sendingUid = -1, companionUid = null))
    }

    @Test fun refusesAnInvalidUid() {
        assertFalse(ReplyGate.accepts(sendingUid = -1, companionUid = -1))
    }
}

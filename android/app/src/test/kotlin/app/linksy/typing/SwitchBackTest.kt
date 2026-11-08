package app.linksy.typing

import org.junit.Assert.assertEquals
import org.junit.Test

class SwitchBackTest {
    private fun decide(remain: Boolean = false, ours: Boolean = true, other: Boolean = true, sdk: Int = 34) =
        SwitchBack.decide(sessionsRemain = remain, ours = ours, otherEnabled = other, sdk = sdk)

    @Test fun leavesForThePreviousKeyboardFromApi28() {
        assertEquals(Leave.Previous, decide(sdk = 28))
        assertEquals(Leave.Previous, decide(sdk = 36))
    }

    @Test fun leavesForTheLastKeyboardBefore() {
        assertEquals(Leave.Last, decide(sdk = 27))
        assertEquals(Leave.Last, decide(sdk = 24))
    }

    @Test fun staysWhileAComputerIsStillTyping() {
        assertEquals(Leave.Nothing, decide(remain = true))
    }

    @Test fun staysAwayFromAKeyboardThatIsNotOurs() {
        assertEquals(Leave.Nothing, decide(ours = false))
    }

    @Test fun hintsWhenThereIsNothingToSwitchTo() {
        assertEquals(Leave.Hint, decide(other = false))
        assertEquals(Leave.Hint, decide(other = false, sdk = 24))
    }
}

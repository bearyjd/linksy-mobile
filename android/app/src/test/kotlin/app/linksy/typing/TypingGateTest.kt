package app.linksy.typing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TypingGateTest {
    @Test fun typesOnlyWithASessionAndALiveKeyboard() {
        val gate = TypingGate()
        assertFalse(gate.canType())
        gate.begin("s1")
        assertFalse(gate.canType())
        gate.keyboardLive(true)
        assertTrue(gate.canType())
        gate.keyboardLive(false)
        assertFalse(gate.canType())
    }

    @Test fun stopsTypingWhenTheLastSessionEnds() {
        val gate = TypingGate()
        gate.keyboardLive(true)
        gate.begin("a")
        gate.begin("b")
        gate.end("a")
        assertTrue(gate.canType())
        gate.end("b")
        assertFalse(gate.canType())
        assertTrue(gate.sessions().isEmpty())
    }

    @Test fun switchingAwayAfterBeingTheKeyboardEndsTheSessions() {
        val gate = TypingGate()
        gate.begin("s", keyboardIsCurrent = true)
        assertTrue(gate.defaultKeyboardChanged(ours = false))
        assertEquals(setOf("s"), gate.sessions())
    }

    @Test fun notYetSwitchedToItIsNotSwitchedAwayFromIt() {
        val gate = TypingGate()
        gate.begin("s", keyboardIsCurrent = false)
        assertFalse(gate.defaultKeyboardChanged(ours = false))
        assertFalse(gate.defaultKeyboardChanged(ours = true))
        assertTrue(gate.defaultKeyboardChanged(ours = false))
    }

    @Test fun nothingToEndWithoutASession() {
        val gate = TypingGate()
        assertFalse(gate.defaultKeyboardChanged(ours = true))
        assertFalse(gate.defaultKeyboardChanged(ours = false))
    }

    @Test fun recognisesItsOwnKeyboardInTheSetting() {
        assertTrue(TypingGate.isOurs("app.linksy.mg/app.linksy.typing.LinksyKeyboardService", "app.linksy.mg"))
        assertFalse(TypingGate.isOurs("com.google.android.inputmethod.latin/.LatinIME", "app.linksy.mg"))
        assertFalse(TypingGate.isOurs(null, "app.linksy.mg"))
        assertFalse(TypingGate.isOurs("app.linksy.mgx/Other", "app.linksy.mg"))
    }
}

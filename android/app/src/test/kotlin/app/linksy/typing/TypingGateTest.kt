package app.linksy.typing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TypingGateTest {
    @Test fun typesOnlyWithASessionAndATypableField() {
        val gate = TypingGate()
        assertFalse(gate.canType("s1", 1))
        val g = gate.begin("s1")
        assertFalse(gate.canType("s1", g))
        gate.setField(FieldState.Typable)
        assertTrue(gate.canType("s1", g))
        for (paused in listOf(FieldState.None, FieldState.NoInput, FieldState.Password)) {
            gate.setField(paused)
            assertFalse(paused.name, gate.canType("s1", g))
        }
    }

    @Test fun endingASessionStopsItsQueuedEventsAtOnce() {
        val gate = TypingGate()
        gate.setField(FieldState.Typable)
        val a = gate.begin("a")
        val b = gate.begin("b")
        gate.end("a")
        assertFalse(gate.canType("a", a))
        assertTrue(gate.canType("b", b))
        gate.end("b")
        assertFalse(gate.canType("b", b))
        assertTrue(gate.sessions().isEmpty())
    }

    @Test fun aSessionIdUsedAgainDoesNotRevivePostedEvents() {
        val gate = TypingGate()
        gate.setField(FieldState.Typable)
        val first = gate.begin("s")
        gate.end("s")
        val second = gate.begin("s")
        assertFalse(gate.canType("s", first))
        assertTrue(gate.canType("s", second))
    }

    @Test fun aRefusedDeliveryIsShownAndRecovers() {
        val gate = TypingGate()
        val g = gate.begin("s")
        gate.setField(FieldState.Typable)
        assertFalse(gate.delivered(true))
        assertTrue(gate.delivered(false))
        assertEquals(FieldState.Refused, gate.field())
        // Still tried: the next one may work.
        assertTrue(gate.canType("s", g))
        assertFalse(gate.delivered(false))
        assertTrue(gate.delivered(true))
        assertEquals(FieldState.Typable, gate.field())
    }

    @Test fun aPausedFieldIsNotMarkedRefused() {
        val gate = TypingGate()
        gate.begin("s")
        gate.setField(FieldState.Password)
        assertFalse(gate.delivered(false))
        assertEquals(FieldState.Password, gate.field())
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

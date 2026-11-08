package app.linksy.typing

import org.junit.Assert.assertEquals
import org.junit.Test

class PressTimesTest {
    @Test fun aReleaseCarriesThePressTimeOfItsOwnKey() {
        var now = 100L
        val times = PressTimes { now }
        assertEquals(100L, times.downTime(29, down = true))
        now = 150L
        assertEquals(150L, times.downTime(30, down = true))
        now = 200L
        assertEquals(100L, times.downTime(29, down = false))
        assertEquals(150L, times.downTime(30, down = false))
    }

    @Test fun aNewPressStartsAgain() {
        var now = 100L
        val times = PressTimes { now }
        times.downTime(29, down = true)
        times.downTime(29, down = false)
        now = 300L
        assertEquals(300L, times.downTime(29, down = true))
    }

    @Test fun aReleaseWithoutAPressIsNow() {
        val times = PressTimes { 500L }
        assertEquals(500L, times.downTime(29, down = false))
    }
}

package app.linksy.typing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackpressureTest {
    @Test fun letsInOnlyTheLimit() {
        val limit = Backpressure(2)
        assertTrue(limit.acquire { true })
        assertTrue(limit.acquire { true })
        assertEquals(0, limit.available())
    }

    @Test fun givesUpWhenFullAndNoLongerWanted() {
        val limit = Backpressure(1)
        assertTrue(limit.acquire { true })
        assertFalse(limit.acquire { false })
        assertEquals(0, limit.available())
    }

    @Test fun aReleaseMakesRoom() {
        val limit = Backpressure(1)
        assertTrue(limit.acquire { true })
        limit.release()
        assertEquals(1, limit.available())
        assertTrue(limit.acquire { true })
    }

    @Test(timeout = 2000) fun waitsForARelease() {
        val limit = Backpressure(1)
        assertTrue(limit.acquire { true })
        Thread { Thread.sleep(80); limit.release() }.start()
        assertTrue(limit.acquire { true })
    }
}

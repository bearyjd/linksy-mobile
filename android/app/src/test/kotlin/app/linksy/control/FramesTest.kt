package app.linksy.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FramesTest {
    private fun frame(json: String): ByteArray {
        val body = json.toByteArray(Charsets.UTF_8)
        return byteArrayOf(
            (body.size ushr 24).toByte(), (body.size ushr 16).toByte(),
            (body.size ushr 8).toByte(), body.size.toByte(),
        ) + body
    }

    @Test fun oneFrameInOneChunk() {
        assertEquals(listOf("{\"a\":1}"), FrameDecoder().feed(frame("{\"a\":1}")))
    }

    @Test fun mergedFramesComeOutInOrder() {
        val both = frame("{\"a\":1}") + frame("{\"b\":2}")
        assertEquals(listOf("{\"a\":1}", "{\"b\":2}"), FrameDecoder().feed(both))
    }

    @Test fun aFrameSplitAnywhereIsPutBackTogether() {
        val bytes = frame("{\"text\":\"héllo\"}")
        for (cut in 1 until bytes.size) {
            val d = FrameDecoder()
            val got = d.feed(bytes.copyOfRange(0, cut)) + d.feed(bytes.copyOfRange(cut, bytes.size))
            assertEquals("cut at $cut", listOf("{\"text\":\"héllo\"}"), got)
        }
    }

    @Test fun oneByteAtATime() {
        val d = FrameDecoder()
        val got = frame("{\"a\":1}").flatMap { d.feed(byteArrayOf(it)) }
        assertEquals(listOf("{\"a\":1}"), got)
    }

    @Test fun anEmptyFrameIsSkipped() {
        val d = FrameDecoder()
        assertEquals(listOf("{}"), d.feed(frame("") + frame("{}")))
        assertFalse(d.broken)
    }

    @Test fun aFrameAtTheLimitIsAccepted() {
        val d = FrameDecoder(maxFrame = 8)
        assertEquals(listOf("12345678"), d.feed(frame("12345678")))
        assertFalse(d.broken)
    }

    @Test fun aFrameOverTheLimitBreaksTheStream() {
        val d = FrameDecoder(maxFrame = 8)
        val got = d.feed(frame("{}") + frame("123456789") + frame("{}"))
        assertEquals(listOf("{}"), got)
        assertTrue(d.broken)
        assertEquals(emptyList<String>(), d.feed(frame("{}")))
    }

    @Test fun aHugeLengthIsRefusedWithoutWaitingForTheBytes() {
        val d = FrameDecoder()
        assertEquals(emptyList<String>(), d.feed(byteArrayOf(0x7f, 0, 0, 0, 1)))
        assertTrue(d.broken)
    }

    @Test fun aNegativeLengthIsRefused() {
        val d = FrameDecoder()
        d.feed(byteArrayOf(0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte()))
        assertTrue(d.broken)
    }
}

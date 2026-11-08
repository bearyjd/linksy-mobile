package app.linksy.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun frame(json: String): ByteArray {
    val b = json.toByteArray()
    return byteArrayOf((b.size ushr 24).toByte(), (b.size ushr 16).toByte(), (b.size ushr 8).toByte(), b.size.toByte()) + b
}

private val OVERSIZE = byteArrayOf(0x7F, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())

private class Harness(
    private val chunks: List<ByteArray?>,
    enabled: Boolean = true,
    parse: (String) -> ControlMsg? = ControlMsg::parse,
) {
    var reads = 0
    val pauses = mutableListOf<Long>()
    val msgs = mutableListOf<ControlMsg>()
    var broken = 0
    val logs = mutableListOf<String>()
    private val reader = ControlReader(
        read = { chunks.getOrNull(reads++) },
        pause = { pauses += it },
        enabled = enabled,
        onMsg = { msgs += it },
        onBroken = { broken += 1 },
        log = { logs += it },
        parse = parse,
    )

    fun run() = reader.run()
}

private fun scroll(n: Int) = """{"type":"scroll","dx":0,"dy":$n,"discrete":true}"""

class ControlReaderTest {
    @Test fun validFramesAreHandedOnInOrder() {
        val h = Harness(listOf(frame(scroll(1)), frame(scroll(2)), null))
        h.run()
        assertEquals(listOf(ControlMsg.Scroll(0.0, 1.0, true), ControlMsg.Scroll(0.0, 2.0, true)), h.msgs)
    }

    @Test fun aBrokenFrameStopsInputOnceButTheSessionIsStillReadToItsEnd() {
        val h = Harness(listOf(frame(scroll(1)), OVERSIZE, frame(scroll(2)), frame(scroll(3)), null))
        h.run()
        assertEquals("drained to the end", 5, h.reads)
        assertEquals(listOf(ControlMsg.Scroll(0.0, 1.0, true)), h.msgs)
        assertEquals(1, h.broken)
        assertEquals("logged once", 1, h.logs.size)
    }

    @Test fun aPoisonFrameIsSkippedAndLaterFramesStillGo() {
        val h = Harness(
            listOf(frame(scroll(1)), frame(scroll(66)), frame(scroll(67)), frame(scroll(2)), null),
            parse = { json -> if ("\"dy\":6" in json) throw StackOverflowError() else ControlMsg.parse(json) },
        )
        h.run()
        assertEquals(listOf(ControlMsg.Scroll(0.0, 1.0, true), ControlMsg.Scroll(0.0, 2.0, true)), h.msgs)
        assertEquals(5, h.reads)
        assertEquals("logged once for two poison frames", 1, h.logs.size)
    }

    @Test fun aHandlerThatThrowsDoesNotEndTheLoop() {
        var first = true
        val seen = mutableListOf<ControlMsg>()
        val chunks = ArrayDeque(listOf<ByteArray?>(frame(scroll(1)), frame(scroll(2)), null))
        ControlReader(
            read = { chunks.removeFirstOrNull() },
            pause = {}, enabled = true,
            onMsg = { if (first) { first = false; throw IllegalStateException("boom") } else seen += it },
            onBroken = {}, log = {},
        ).run()
        assertEquals(listOf<ControlMsg>(ControlMsg.Scroll(0.0, 2.0, true)), seen)
        assertTrue(chunks.isEmpty())
    }

    @Test fun whenOffNothingIsForwardedButEverythingIsDrained() {
        val h = Harness(listOf(frame(scroll(1)), OVERSIZE, frame(scroll(2)), null), enabled = false)
        h.run()
        assertTrue(h.msgs.isEmpty())
        assertEquals(0, h.broken)
        assertEquals(4, h.reads)
    }

    @Test fun framesSplitAcrossChunksAreJoined() {
        val bytes = frame(scroll(1))
        val h = Harness(listOf(bytes.copyOfRange(0, 3), bytes.copyOfRange(3, 9), bytes.copyOfRange(9, bytes.size), null))
        h.run()
        assertEquals(1, h.msgs.size)
    }

    @Test fun theSessionMayRegisterAfterTheFirstReads() {
        val h = Harness(listOf(null, null, frame(scroll(1)), null))
        h.run()
        assertEquals(listOf(100L, 100L), h.pauses)
        assertEquals(1, h.msgs.size)
    }

    @Test fun aSessionThatNeverRegistersIsGivenUpOn() {
        val h = Harness(emptyList())
        h.run()
        assertEquals(20, h.pauses.size)
        assertTrue(h.msgs.isEmpty())
    }
}

package app.linksy.typing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TypingReaderTest {
    private fun text(s: String) = "T$s".toByteArray()

    /** A reader over [chunks] (null = nothing there); records what it did. */
    private class Rig(
        chunks: List<ByteArray?>,
        val canRead: () -> Boolean = { true },
        val acquire: (() -> Boolean) -> Boolean = { true },
        val fieldGeneration: () -> Int = { 0 },
    ) {
        private val queue = ArrayDeque(chunks)
        val posted = mutableListOf<Pair<TypingEvent, Int>>()
        val log = mutableListOf<String>()
        var reads = 0

        fun reader() = TypingReader(
            canRead = canRead,
            read = { reads++; queue.removeFirstOrNull() },
            fieldGeneration = fieldGeneration,
            acquire = acquire,
            post = { event, field -> posted += event to field },
            finish = { log += "finish" },
            pause = { log += "pause" },
        )
    }

    @Test fun postsEveryEventUntilTheSourceEndsThenFinishes() {
        val rig = Rig(listOf(text("a"), text("b"), null, null))
        // null after data: ended at once (no retry).
        rig.reader().run()
        assertEquals(listOf<TypingEvent>(TypingEvent.Text("a"), TypingEvent.Text("b")), rig.posted.map { it.first })
        assertEquals(listOf("finish"), rig.log)
    }

    @Test fun stopsReadingWhenTheGateIsClosed() {
        var open = true
        val rig = Rig(listOf(text("a"), text("b"), text("c")), canRead = { open })
        val reader = TypingReader(
            canRead = rig.canRead,
            read = { rig.reads++; if (rig.reads == 2) open = false; text("x") },
            fieldGeneration = { 0 },
            acquire = { true },
            post = { e, f -> rig.posted += e to f },
            finish = { rig.log += "finish" },
            pause = {},
        )
        reader.run()
        assertEquals(2, rig.reads)
        assertEquals(listOf("finish"), rig.log)
    }

    @Test fun neverReadsWithAClosedGateButStillFinishes() {
        val rig = Rig(listOf(text("a")), canRead = { false })
        rig.reader().run()
        assertEquals(0, rig.reads)
        assertEquals(listOf("finish"), rig.log)
    }

    @Test fun finishIsCalledOnceEvenWhenReadingThrows() {
        val log = mutableListOf<String>()
        val reader = TypingReader(
            canRead = { true }, read = { throw IllegalStateException("boom") },
            fieldGeneration = { 0 }, acquire = { true }, post = { _, _ -> }, finish = { log += "finish" }, pause = {},
        )
        try { reader.run(); fail("should rethrow") } catch (_: IllegalStateException) {}
        assertEquals(listOf("finish"), log)
    }

    @Test fun anEventWaitingForRoomIsNotPostedWhenTheWaitIsGivenUp() {
        val rig = Rig(listOf(text("a"), text("b")), acquire = { false })
        rig.reader().run()
        assertTrue(rig.posted.isEmpty())
        assertEquals(listOf("finish"), rig.log)
    }

    @Test fun anEventIsStampedWithTheFieldItArrivedForNotOneOpenedWhileWaiting() {
        val gate = TypingGate()
        gate.begin("s")
        gate.setField(FieldState.Typable)
        val gen = gate.begin("s")
        val typed = mutableListOf<Boolean>()
        val rig = Rig(
            listOf(text("for field A"), null),
            // The user moves to field B while the reader waits for room.
            acquire = { gate.setField(FieldState.Typable); true },
            fieldGeneration = gate::fieldGeneration,
        )
        val reader = TypingReader(
            canRead = rig.canRead, read = { rig.reads++; if (rig.reads == 1) text("for field A") else null },
            fieldGeneration = rig.fieldGeneration, acquire = rig.acquire,
            post = { _, field -> typed += gate.canType("s", gen, field) },
            finish = {}, pause = {},
        )
        reader.run()
        assertEquals(listOf(false), typed)
    }

    @Test fun anEventWithNoFieldChangeIsStillTyped() {
        val gate = TypingGate()
        val gen = gate.begin("s")
        gate.setField(FieldState.Typable)
        val typed = mutableListOf<Boolean>()
        var n = 0
        TypingReader(
            canRead = { true }, read = { if (n++ == 0) text("a") else null },
            fieldGeneration = gate::fieldGeneration, acquire = { true },
            post = { _, field -> typed += gate.canType("s", gen, field) }, finish = {}, pause = {},
        ).run()
        assertEquals(listOf(true), typed)
    }

    @Test fun aSessionNotYetRegisteredIsRetriedThenGivenUp() {
        val rig = Rig(emptyList())
        rig.reader().run()
        assertEquals(21, rig.reads)
        assertEquals(20, rig.log.count { it == "pause" })
        assertEquals("finish", rig.log.last())
    }

    @Test fun aLateStartIsPickedUp() {
        val rig = Rig(listOf(null, null, text("a"), null))
        rig.reader().run()
        assertEquals(1, rig.posted.size)
    }

    @Test fun garbageIsSkipped() {
        val rig = Rig(listOf(byteArrayOf(1, 2, 3), text("ok"), null))
        rig.reader().run()
        assertEquals(listOf<TypingEvent>(TypingEvent.Text("ok")), rig.posted.map { it.first })
        assertFalse(rig.posted.isEmpty())
    }
}

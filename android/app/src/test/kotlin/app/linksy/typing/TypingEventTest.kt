package app.linksy.typing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TypingEventTest {
    @Test fun decodesText() {
        val event = TypingEvent.decode(byteArrayOf('T'.code.toByte(), 'h'.code.toByte(), 0xC3.toByte(), 0xA9.toByte()))
        assertEquals(TypingEvent.Text("hé"), event)
    }

    @Test fun anInvalidByteBecomesTheReplacementCharacterAndNeverThrows() {
        val event = TypingEvent.decode(byteArrayOf('T'.code.toByte(), 'a'.code.toByte(), 0xFF.toByte(), 'b'.code.toByte()))
        assertEquals(TypingEvent.Text("a\uFFFDb"), event)
        // A cut multi-byte sequence too.
        assertEquals(TypingEvent.Text("\uFFFD"), TypingEvent.decode(byteArrayOf('T'.code.toByte(), 0xC3.toByte())))
    }

    @Test fun decodesAKeyPressAndRelease() {
        val press = byteArrayOf('K'.code.toByte(), 1, 0, 0, 0xFF.toByte(), 0x0D)
        val release = byteArrayOf('K'.code.toByte(), 0, 0, 0, 0xFF.toByte(), 0x0D)
        assertEquals(TypingEvent.Key(0xFF0DL, true), TypingEvent.decode(press))
        assertEquals(TypingEvent.Key(0xFF0DL, false), TypingEvent.decode(release))
    }

    @Test fun aKeysymAboveTheSignBitStaysPositive() {
        val bytes = byteArrayOf('K'.code.toByte(), 1, 0x10, 0x08, 0xFF.toByte(), 0x2A)
        assertEquals(TypingEvent.Key(0x1008FF2AL, true), TypingEvent.decode(bytes))
    }

    @Test fun refusesWhatItDoesNotUnderstand() {
        assertNull(TypingEvent.decode(byteArrayOf()))
        assertNull(TypingEvent.decode(byteArrayOf('X'.code.toByte(), 1)))
        assertNull(TypingEvent.decode(byteArrayOf('K'.code.toByte(), 1, 0, 0)))
        assertNull(TypingEvent.decode(byteArrayOf('K'.code.toByte(), 2, 0, 0, 0, 1)))
        // An empty text is nothing to type.
        assertNull(TypingEvent.decode(byteArrayOf('T'.code.toByte())))
    }

    @Test fun neverShowsWhatWasTyped() {
        val shown = TypingEvent.Text("hunter2").toString()
        assertFalse(shown.contains("hunter2"))
        assertTrue(shown.contains("7"))
    }
}

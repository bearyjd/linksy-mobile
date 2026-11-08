package app.linksy.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionTrustTest {
    private val hex = "ab12".repeat(16)

    @Test fun normaliseLowercasesAndDropsColonsAndSpaces() {
        assertEquals(hex, CompanionTrust.normalise(hex.uppercase()))
        val colons = hex.chunked(2).joinToString(":").uppercase()
        assertEquals(hex, CompanionTrust.normalise(colons))
        assertEquals(hex, CompanionTrust.normalise("  $hex\n"))
    }

    @Test fun normaliseRefusesAnythingButSixtyFourHexDigits() {
        assertNull(CompanionTrust.normalise(""))
        assertNull(CompanionTrust.normalise(hex.dropLast(1)))
        assertNull(CompanionTrust.normalise(hex + "0"))
        assertNull(CompanionTrust.normalise("g".repeat(64)))
        assertNull(CompanionTrust.normalise("\"; evil(); \""))
    }

    @Test fun anEmptyOrMalformedPinIsNotConfigured() {
        assertFalse(CompanionTrust.configured(""))
        assertFalse(CompanionTrust.configured("abc"))
        assertTrue(CompanionTrust.configured(hex))
        assertTrue(CompanionTrust.configured(hex.uppercase()))
    }

    @Test fun anEmptyPinTrustsNothing() {
        assertFalse(CompanionTrust.trusted("", listOf(hex)))
        assertFalse(CompanionTrust.trusted("", listOf("")))
        assertFalse(CompanionTrust.trusted("", emptyList()))
        assertFalse(CompanionTrust.trusted("zz", listOf("zz")))
    }

    @Test fun theSignerMustEqualThePin() {
        assertTrue(CompanionTrust.trusted(hex, listOf(hex)))
        assertTrue(CompanionTrust.trusted(hex, listOf(hex.uppercase())))
        assertTrue(CompanionTrust.trusted(hex.uppercase(), listOf(hex.chunked(2).joinToString(":"))))
        assertFalse(CompanionTrust.trusted(hex, listOf("cd34".repeat(16))))
    }

    @Test fun noSignerOrAMalformedOneIsNotTrusted() {
        assertFalse(CompanionTrust.trusted(hex, emptyList()))
        assertFalse(CompanionTrust.trusted(hex, listOf("")))
        assertFalse(CompanionTrust.trusted(hex, listOf(hex.dropLast(2))))
    }

    @Test fun moreThanOneCurrentSignerIsNotTrusted() {
        assertFalse(CompanionTrust.trusted(hex, listOf(hex, "cd34".repeat(16))))
        assertFalse(CompanionTrust.trusted(hex, listOf(hex, hex)))
    }

    @Test fun controlNeedsAndroid9() {
        assertFalse(CompanionTrust.supported(24))
        assertFalse(CompanionTrust.supported(27))
        assertTrue(CompanionTrust.supported(28))
        assertTrue(CompanionTrust.supported(36))
    }
}

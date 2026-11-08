package app.linksy.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionPolicyTest {
    private val full = "app.linksy.mg.control/app.linksy.mg.control.ControlAccessibilityService"

    @Test fun theStepsGoInOrder() {
        assertEquals(CompanionStatus.NotInstalled, CompanionPolicy.status(false, false, false))
        assertEquals(CompanionStatus.NeedsAccessibility, CompanionPolicy.status(true, false, false))
        assertEquals(CompanionStatus.NeedsPairing, CompanionPolicy.status(true, true, false))
        assertEquals(CompanionStatus.Ready, CompanionPolicy.status(true, true, true))
    }

    @Test fun notInstalledWinsOverEverythingElse() {
        assertEquals(CompanionStatus.NotInstalled, CompanionPolicy.status(false, true, true))
        assertEquals(CompanionStatus.NotInstalled, CompanionPolicy.status(false, false, true))
    }

    @Test fun pairingWithoutAccessibilityStillAsksForAccessibilityFirst() {
        assertEquals(CompanionStatus.NeedsAccessibility, CompanionPolicy.status(true, false, true))
    }

    @Test fun controlIsOfferedOnlyWhenReadyAndTheUserTurnedItOn() {
        for (s in CompanionStatus.values()) {
            assertEquals("$s on", s == CompanionStatus.Ready, CompanionPolicy.advertises(s, true))
            assertFalse("$s off", CompanionPolicy.advertises(s, false))
        }
    }

    @Test fun accessibilityOffWhenNothingIsEnabled() {
        assertFalse(CompanionPolicy.accessibilityOn(null))
        assertFalse(CompanionPolicy.accessibilityOn(""))
    }

    @Test fun accessibilityOnForTheFullAndTheShortComponentName() {
        assertTrue(CompanionPolicy.accessibilityOn(full))
        assertTrue(CompanionPolicy.accessibilityOn("app.linksy.mg.control/.ControlAccessibilityService"))
    }

    @Test fun accessibilityFoundAmongOthers() {
        assertTrue(CompanionPolicy.accessibilityOn("com.a/.A:$full:com.b/com.b.B"))
    }

    @Test fun anotherPackageOrServiceDoesNotCount() {
        assertFalse(CompanionPolicy.accessibilityOn("com.evil/app.linksy.mg.control.ControlAccessibilityService"))
        assertFalse(CompanionPolicy.accessibilityOn("app.linksy.mg.control/.OtherService"))
        assertFalse(CompanionPolicy.accessibilityOn("app.linksy.mg.control.evil/.ControlAccessibilityService"))
        assertFalse(CompanionPolicy.accessibilityOn("com.a/.A:com.b/.B"))
    }

    @Test fun junkEntriesAreIgnored() {
        assertFalse(CompanionPolicy.accessibilityOn("::garbage:/:app.linksy.mg.control"))
        assertTrue(CompanionPolicy.accessibilityOn(":garbage::$full:"))
    }
}

package app.linksy.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeLink : BridgeLink {
    val calls = mutableListOf<String>()
    val sent = mutableListOf<String>()
    override fun bind() { calls += "bind" }
    override fun unbind() { calls += "unbind" }
    override fun begin() { calls += "begin" }
    override fun end() { calls += "end" }
    override fun inject(json: String) { sent += json }
    fun count(call: String) = calls.count { it == call }
}

private class Events : BridgeListener {
    val seen = mutableListOf<Pair<BridgeStatus, FailReason?>>()
    override fun onStatus(status: BridgeStatus, reason: FailReason?) { seen += status to reason }
}

private class Rig {
    var now = 1_000_000L
    val link = FakeLink()
    val events = Events()
    val delays = mutableListOf<Long>()
    val client = BridgeClient(link, { now }, { delays += it }, events)

    fun active() {
        client.start()
        client.onBound()
        client.onReply(0)
    }

    /// One failed attempt: the companion is busy.
    fun busy() {
        client.onBound()
        client.onReply(6)
    }

    fun after(ms: Long) {
        now += ms
        client.tick()
    }

    val status get() = client.status
    val last get() = events.seen.last()
}

class BridgeClientTest {
    private fun move(x: Double) = ControlMsg.Pointer(x, 0.5)
    private fun scroll() = ControlMsg.Scroll(0.0, 1.0, true)

    @Test fun bindsThenBeginsThenIsActive() {
        val r = Rig()
        r.client.start()
        assertEquals(listOf("bind"), r.link.calls)
        assertEquals(BridgeStatus.Connecting, r.status)
        r.client.onBound()
        assertEquals(listOf("bind", "begin"), r.link.calls)
        r.client.onReply(0)
        assertEquals(BridgeStatus.Active, r.status)
        assertEquals(BridgeStatus.Active to null, r.last)
    }

    @Test fun whatArrivesBeforeTheReplyWaitsAndThenGoesInOrder() {
        val r = Rig()
        r.client.start()
        r.client.send(ControlMsg.Button("left", true))
        r.client.send(ControlMsg.Button("left", false))
        assertTrue(r.link.sent.isEmpty())
        r.client.onBound()
        r.client.onReply(0)
        assertEquals(
            listOf(ControlMsg.Button("left", true).toJson(), ControlMsg.Button("left", false).toJson()),
            r.link.sent,
        )
    }

    @Test fun sendWhenActiveGoesStraightOn() {
        val r = Rig()
        r.active()
        r.client.send(scroll())
        assertEquals(listOf(scroll().toJson()), r.link.sent)
    }

    // Reply codes.

    @Test fun repliesThatCannotBeFixedHereFailForGood() {
        val cases = mapOf(
            1 to FailReason.Setup, 2 to FailReason.Setup, 3 to FailReason.Setup,
            4 to FailReason.NotPaired, 5 to FailReason.AccessibilityOff, 99 to FailReason.Protocol,
            -1 to FailReason.Protocol,
        )
        for ((code, reason) in cases) {
            val r = Rig()
            r.client.start()
            r.client.onBound()
            r.client.onReply(code)
            assertEquals("code $code", BridgeStatus.Failed, r.status)
            assertEquals("code $code", BridgeStatus.Failed to reason, r.last)
            assertEquals("code $code", 1, r.link.count("unbind"))
            r.after(120_000)
            assertEquals("code $code: never binds again", 1, r.link.count("bind"))
        }
    }

    @Test fun nothingIsSentAfterAFailure() {
        val r = Rig()
        r.client.start()
        r.client.onBound()
        r.client.onReply(4)
        r.client.send(scroll())
        assertTrue(r.link.sent.isEmpty())
    }

    @Test fun busyTriesAgainAfterABackoffThatGrowsAndIsCapped() {
        val r = Rig()
        r.client.start()
        val expected = listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L)
        for (delay in expected) {
            r.busy()
            assertEquals(BridgeStatus.Connecting, r.status)
            assertEquals(delay, r.delays.last())
            val binds = r.link.count("bind")
            r.after(delay - 1)
            assertEquals("not before the delay", binds, r.link.count("bind"))
            r.after(1)
            assertEquals(binds + 1, r.link.count("bind"))
        }
        r.busy()
        assertEquals(BridgeStatus.Failed to FailReason.Unreachable, r.last)
    }

    @Test fun busyReleasesTheBindingWhileWaiting() {
        val r = Rig()
        r.client.start()
        r.busy()
        assertEquals("unbind", r.link.calls.last())
    }

    @Test fun aSuccessStartsTheBackoffOver() {
        val r = Rig()
        r.client.start()
        r.busy()
        r.after(1_000)
        r.client.onBound()
        r.client.onReply(0)
        assertEquals(BridgeStatus.Active, r.status)
        r.client.onLost()
        assertEquals(1_000L, r.delays.last())
    }

    @Test fun aTickBeforeTheTimeDoesNothing() {
        val r = Rig()
        r.client.start()
        r.busy()
        val binds = r.link.count("bind")
        r.after(10)
        assertEquals(binds, r.link.count("bind"))
    }

    @Test fun aServiceThatCannotBeBoundIsRetriedThenGivenUp() {
        val r = Rig()
        r.client.start()
        repeat(6) {
            r.client.onBindFailed()
            assertEquals(BridgeStatus.Connecting, r.status)
            r.after(30_000)
        }
        r.client.onBindFailed()
        assertEquals(BridgeStatus.Failed to FailReason.Unreachable, r.last)
    }

    @Test fun noAnswerToBindOrBeginCountsAsAFailedTry() {
        val r = Rig()
        r.client.start()
        r.after(5_000)
        assertEquals(BridgeStatus.Connecting, r.status)
        assertEquals(1_000L, r.delays.last())
        r.after(1_000)
        r.client.onBound()
        r.after(3_000)
        assertEquals(2_000L, r.delays.last())
        assertEquals(BridgeStatus.Connecting, r.status)
    }

    @Test fun losingTheServiceWhileActiveBindsAgainAndBeginsAgain() {
        val r = Rig()
        r.active()
        r.client.onLost()
        assertEquals(BridgeStatus.Connecting, r.status)
        r.after(1_000)
        assertEquals(2, r.link.count("bind"))
        r.client.onBound()
        assertEquals(2, r.link.count("begin"))
        r.client.onReply(0)
        assertEquals(BridgeStatus.Active, r.status)
    }

    @Test fun whatWasAskedBeforeALossIsNotReplayedLater() {
        val r = Rig()
        r.active()
        r.client.onLost()
        r.client.send(ControlMsg.Button("left", true))
        r.after(1_000)
        r.client.onBound()
        r.client.onReply(0)
        assertTrue(r.link.sent.isEmpty())
    }

    @Test fun aStaleReplyOrLossAfterStopIsIgnored() {
        val r = Rig()
        r.active()
        r.client.stop()
        r.client.onReply(0)
        r.client.onLost()
        r.client.onBound()
        assertEquals(BridgeStatus.Stopped, r.status)
        assertEquals(1, r.link.count("bind"))
    }

    // Queue and coalescing.

    @Test fun consecutivePointerMovesWhileWaitingBecomeTheLastOne() {
        val r = Rig()
        r.client.start()
        r.client.send(move(0.1))
        r.client.send(move(0.2))
        r.client.send(move(0.3))
        r.client.onBound()
        r.client.onReply(0)
        assertEquals(listOf(move(0.3).toJson()), r.link.sent)
    }

    @Test fun movesAroundAClickAreNotMergedAcrossIt() {
        val r = Rig()
        r.client.start()
        r.client.send(move(0.1))
        r.client.send(ControlMsg.Button("left", true))
        r.client.send(move(0.2))
        r.client.onBound()
        r.client.onReply(0)
        assertEquals(
            listOf(move(0.1), ControlMsg.Button("left", true), move(0.2)).map { it.toJson() },
            r.link.sent,
        )
    }

    @Test fun theQueueIsBoundedAndReleasesAreNeverDropped() {
        val r = Rig()
        r.client.start()
        repeat(BridgeClient.QUEUE_MAX + 100) { r.client.send(scroll()) }
        r.client.send(ControlMsg.Button("left", false))
        r.client.send(ControlMsg.Key(65, false))
        r.client.onBound()
        r.client.onReply(0)
        // Everything that fits goes at once; the rest follows as the budget refills.
        r.after(1_000)
        assertTrue(r.link.sent.size <= BridgeClient.QUEUE_MAX)
        assertTrue(r.link.sent.contains(ControlMsg.Button("left", false).toJson()))
        assertTrue(r.link.sent.contains(ControlMsg.Key(65, false).toJson()))
    }

    @Test fun whenFullPointerMovesAreDroppedBeforeAnythingElse() {
        val r = Rig()
        r.client.start()
        r.client.send(move(0.1))
        repeat(BridgeClient.QUEUE_MAX) { r.client.send(scroll()) }
        r.client.onBound()
        r.client.onReply(0)
        r.after(1_000)
        assertFalse(r.link.sent.contains(move(0.1).toJson()))
        assertEquals(BridgeClient.QUEUE_MAX, r.link.sent.count { it == scroll().toJson() })
    }

    // Rate.

    @Test fun nothingGoesFasterThanTheBudget() {
        val r = Rig()
        r.active()
        repeat(2_000) { r.client.send(scroll()) }
        assertEquals(BridgeClient.BURST, r.link.sent.size)
        assertTrue("a wake-up is asked for", r.delays.isNotEmpty())
        r.after(100)
        assertEquals(BridgeClient.BURST + BridgeClient.RATE_PER_SEC / 10, r.link.sent.size)
    }

    @Test fun overSecondsOfFloodingTheRateStaysUnderTheCompanionsLimit() {
        val r = Rig()
        r.active()
        repeat(3_000) {
            repeat(10) { r.client.send(scroll()) }
            r.after(1)
        }
        // 3 s offered at 10 000/s; the companion allows 1500/s sustained, 3000 burst.
        assertTrue("sent ${r.link.sent.size}", r.link.sent.size <= BridgeClient.BURST + BridgeClient.RATE_PER_SEC * 3)
        assertTrue("and it keeps flowing", r.link.sent.size >= BridgeClient.RATE_PER_SEC * 3 - 10)
    }

    // Renewal: the companion tells nobody when it ends a session.

    @Test fun afterALongPauseItBeginsAgainBeforeSending() {
        val r = Rig()
        r.active()
        r.client.send(scroll())
        r.after(BridgeClient.IDLE_RENEW_MS)
        r.client.send(ControlMsg.Button("left", true))
        assertEquals(listOf("bind", "begin", "end", "begin"), r.link.calls)
        assertEquals("not sent into a session that may be gone", 1, r.link.sent.size)
        r.client.onReply(0)
        assertEquals(ControlMsg.Button("left", true).toJson(), r.link.sent.last())
    }

    @Test fun renewingDoesNotFlickerTheStatus() {
        val r = Rig()
        r.active()
        val before = r.events.seen.size
        r.after(BridgeClient.IDLE_RENEW_MS)
        r.client.send(scroll())
        assertEquals(BridgeStatus.Active, r.status)
        r.client.onReply(0)
        assertEquals(before, r.events.seen.size)
    }

    @Test fun steadyInputStillBeginsAgainBeforeTheMaximumLength() {
        val r = Rig()
        r.active()
        var begins = 1
        var t = 0L
        while (t < BridgeClient.MAX_RENEW_MS + 60_000) {
            r.after(10_000)
            t += 10_000
            r.client.send(scroll())
            if (r.link.count("begin") > begins) {
                begins = r.link.count("begin")
                r.client.onReply(0)
            }
        }
        assertTrue(begins >= 2)
        assertTrue("renewed no later than the limit", BridgeClient.MAX_RENEW_MS < 30 * 60_000L)
    }

    @Test fun aRenewalThatIsRefusedFails() {
        val r = Rig()
        r.active()
        r.after(BridgeClient.IDLE_RENEW_MS)
        r.client.send(scroll())
        r.client.onReply(4)
        assertEquals(BridgeStatus.Failed to FailReason.NotPaired, r.last)
    }

    // Stop.

    @Test fun stopReleasesWhatIsHeldThenEndsAndUnbinds() {
        val r = Rig()
        r.active()
        r.client.send(ControlMsg.Button("left", true))
        r.client.send(ControlMsg.Key(65, true))
        r.client.send(ControlMsg.Button("right", true))
        r.client.send(ControlMsg.Button("right", false))
        r.client.stop()
        assertEquals(
            listOf(
                ControlMsg.Button("left", true), ControlMsg.Key(65, true), ControlMsg.Button("right", true),
                ControlMsg.Button("right", false), ControlMsg.Button("left", false), ControlMsg.Key(65, false),
            ).map { it.toJson() },
            r.link.sent,
        )
        assertEquals(listOf("bind", "begin", "end", "unbind"), r.link.calls)
        assertEquals(BridgeStatus.Stopped, r.status)
        assertEquals(BridgeStatus.Stopped to null, r.last)
    }

    @Test fun stopWithNothingHeldJustEnds() {
        val r = Rig()
        r.active()
        r.client.stop()
        assertTrue(r.link.sent.isEmpty())
        assertEquals(listOf("bind", "begin", "end", "unbind"), r.link.calls)
    }

    @Test fun stopBeforeTheReplyUnbindsWithoutAnEndToAnUnknownSession() {
        val r = Rig()
        r.client.start()
        r.client.onBound()
        r.client.stop()
        assertEquals("unbind", r.link.calls.last())
        assertEquals(BridgeStatus.Stopped, r.status)
    }

    @Test fun stopTwiceOrWithoutStartIsHarmless() {
        val r = Rig()
        r.client.stop()
        r.active()
        r.client.stop()
        r.client.stop()
        assertEquals(1, r.link.count("end"))
        assertEquals(1, r.link.count("unbind"))
    }

    @Test fun sendAfterStopIsDropped() {
        val r = Rig()
        r.active()
        r.client.stop()
        r.client.send(scroll())
        assertTrue(r.link.sent.isEmpty())
    }

    // Review round 1.

    @Test fun anUntrustedCompanionFailsForGoodWithoutBeginning() {
        val r = Rig()
        r.client.start()
        r.client.onUntrusted()
        assertEquals(BridgeStatus.Failed to FailReason.Setup, r.last)
        assertEquals(listOf("bind", "unbind"), r.link.calls)
        r.client.onBound()
        assertEquals(1, r.link.count("bind"))
        assertEquals(0, r.link.count("begin"))
    }

    @Test fun simultaneouslyHeldKeysAreCappedAndTheSessionEndsCleanly() {
        val r = Rig()
        r.active()
        for (k in 1L..BridgeClient.MAX_HELD_KEYS.toLong()) r.client.send(ControlMsg.Key(k, true))
        assertEquals(BridgeStatus.Active, r.status)
        assertEquals(0, r.link.count("end"))
        r.client.send(ControlMsg.Key(1000, true))
        assertEquals(BridgeStatus.Failed to FailReason.TooManyKeys, r.last)
        assertEquals(1, r.link.count("end"))
        assertEquals("unbind", r.link.calls.last())
        assertFalse(r.link.sent.contains(ControlMsg.Key(1000, true).toJson()))
        for (k in 1L..BridgeClient.MAX_HELD_KEYS.toLong()) assertTrue(r.link.sent.contains(ControlMsg.Key(k, false).toJson()))
        assertEquals("a bounded release burst", 2 * BridgeClient.MAX_HELD_KEYS, r.link.sent.size)
        r.client.send(ControlMsg.Key(2000, true))
        assertEquals(2 * BridgeClient.MAX_HELD_KEYS, r.link.sent.size)
    }

    @Test fun theSameKeyPressedAgainIsNotAnotherHeldKey() {
        val r = Rig()
        r.active()
        repeat(500) { r.client.send(ControlMsg.Key(65, true)) }
        assertEquals(BridgeStatus.Active, r.status)
    }

    @Test fun releasingKeysFreesRoomUnderTheCap() {
        val r = Rig()
        r.active()
        for (k in 1L..BridgeClient.MAX_HELD_KEYS.toLong()) r.client.send(ControlMsg.Key(k, true))
        r.client.send(ControlMsg.Key(1, false))
        r.client.send(ControlMsg.Key(999, true))
        assertEquals(BridgeStatus.Active, r.status)
    }

    @Test fun aKeyFloodAtTheWireRateStillEndsThereAndLogsOnce() {
        val r = Rig()
        r.active()
        for (k in 1L..2000L) {
            r.now += 1
            r.client.send(ControlMsg.Key(k, true))
        }
        assertEquals(1, r.events.seen.count { it.first == BridgeStatus.Failed })
        assertTrue(r.link.sent.size <= 2 * BridgeClient.MAX_HELD_KEYS)
    }

    @Test fun aReleaseIsNotDroppedWhenTheQueueIsFullOfReleases() {
        val r = Rig()
        r.client.start()
        for (k in 1L..BridgeClient.QUEUE_MAX.toLong()) r.client.send(ControlMsg.Key(k, false))
        r.client.send(ControlMsg.Button("left", false))
        r.client.onBound()
        r.client.onReply(0)
        r.after(1_000)
        assertTrue(r.link.sent.contains(ControlMsg.Button("left", false).toJson()))
    }

    @Test fun whenEvictingReleasesThoseOfHeldKeysAreKept() {
        val r = Rig()
        r.active()
        r.client.send(ControlMsg.Key(7, true))
        // Use up the burst so the queue builds.
        repeat(BridgeClient.BURST - 1) { r.client.send(scroll()) }
        r.client.send(ControlMsg.Key(7, false))
        for (k in 100L until 100L + BridgeClient.QUEUE_MAX - 1) r.client.send(ControlMsg.Key(k, false))
        r.client.send(ControlMsg.Button("left", false))
        r.after(2_000)
        assertTrue(r.link.sent.contains(ControlMsg.Key(7, false).toJson()))
        assertTrue(r.link.sent.contains(ControlMsg.Button("left", false).toJson()))
    }

    @Test fun steadyInputFirstBeginsAgainNoLaterThanTheMaximum() {
        val r = Rig()
        r.active()
        val start = r.now
        var firstRenewal = -1L
        while (r.now - start < BridgeClient.MAX_RENEW_MS + 60_000 && firstRenewal < 0) {
            r.after(10_000)
            r.client.send(scroll())
            if (r.link.count("begin") > 1) firstRenewal = r.now - start
        }
        assertTrue("renewed", firstRenewal > 0)
        assertTrue("at $firstRenewal", firstRenewal <= BridgeClient.MAX_RENEW_MS)
    }
}

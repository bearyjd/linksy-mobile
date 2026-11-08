package app.linksy.typing

import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/// Caps the events handed to the main thread but not yet applied, so a
/// computer sending faster than the phone types cannot pile them up. Only the
/// reader thread waits here; the main thread never does.
class Backpressure(limit: Int) {
    private val room = Semaphore(limit)

    /// Waits for room, as long as [wanted] says the event is still wanted;
    /// false if it is not any more (the session ended).
    fun acquire(wanted: () -> Boolean): Boolean {
        while (wanted()) {
            if (room.tryAcquire(POLL_MS, TimeUnit.MILLISECONDS)) return true
        }
        return false
    }

    /// An event was applied or dropped.
    fun release() = room.release()

    fun available(): Int = room.availablePermits()

    private companion object {
        const val POLL_MS = 50L
    }
}

package app.brokoli5191.zeitig.data.net

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * The single, process-wide gate in front of the Wiener Linien API. Fair use demands at least
 * [minInterval] between two requests; callers queue up instead of being rejected.
 *
 * Inject [timeSource] (and run under a test dispatcher) to test it with virtual time.
 */
public class RateLimiter(
    public val minInterval: Duration = 15.seconds,
    private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
) {
    private val mutex = Mutex()
    private var lastStart: ComparableTimeMark? = null

    /** Time a caller would have to wait right now; zero when a request may start immediately. */
    public fun currentWait(): Duration {
        val last = lastStart ?: return Duration.ZERO
        val elapsed = last.elapsedNow()
        return if (elapsed >= minInterval) Duration.ZERO else minInterval - elapsed
    }

    /** Suspends until a request may start, records the start, then runs [block] outside the lock. */
    public suspend fun <T> withPermit(block: suspend () -> T): T {
        mutex.withLock {
            val wait = currentWait()
            if (wait > Duration.ZERO) delay(wait)
            lastStart = timeSource.markNow()
        }
        return block()
    }
}

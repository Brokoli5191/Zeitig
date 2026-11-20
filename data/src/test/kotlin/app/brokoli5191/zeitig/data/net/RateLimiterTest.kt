package app.brokoli5191.zeitig.data.net

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class RateLimiterTest {
    @Test
    fun firstRequestStartsImmediately() =
        runTest {
            val limiter = RateLimiter(15.seconds, testTimeSource)
            assertEquals(Duration.ZERO, limiter.currentWait())
            val startedAt = limiter.withPermit { currentTime }
            assertEquals(0L, startedAt)
        }

    @Test
    fun secondRequestWaitsForTheInterval() =
        runTest {
            val limiter = RateLimiter(15.seconds, testTimeSource)
            limiter.withPermit { }
            assertEquals(15.seconds, limiter.currentWait())
            val startedAt = limiter.withPermit { currentTime }
            assertEquals(15_000L, startedAt)
        }

    @Test
    fun concurrentCallersAreSpacedFifteenSecondsApart() =
        runTest {
            val limiter = RateLimiter(15.seconds, testTimeSource)
            val starts = (1..4).map { async { limiter.withPermit { currentTime } } }.awaitAll()
            assertEquals(listOf(0L, 15_000L, 30_000L, 45_000L), starts.sorted())
        }

    @Test
    fun noWaitWhenTheIntervalHasPassed() =
        runTest {
            val limiter = RateLimiter(15.seconds, testTimeSource)
            limiter.withPermit { }
            kotlinx.coroutines.delay(20_000)
            assertEquals(Duration.ZERO, limiter.currentWait())
            assertEquals(20_000L, limiter.withPermit { currentTime })
        }

    @Test
    fun blockRunsOutsideTheLock() =
        runTest {
            val limiter = RateLimiter(15.seconds, testTimeSource)
            // A slow first request must not delay the second one beyond the interval.
            val first = async { limiter.withPermit { kotlinx.coroutines.delay(60_000) } }
            val second = async { limiter.withPermit { currentTime } }
            assertEquals(15_000L, second.await())
            first.await()
        }
}

package app.brokoli5191.zeitig.core.walk

import app.brokoli5191.zeitig.core.model.GeoPoint
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import kotlin.math.roundToLong
import kotlin.test.assertEquals
import kotlin.test.assertIs

class WalkTimeTest {
    private fun s(seconds: Long): Duration = Duration.ofSeconds(seconds)

    private val learner = WalkTimeLearner()
    private val initial = WalkEstimate(s(300), WalkTimeSource.ESTIMATED, 0)

    @Test
    fun `first estimate is haversine times detour divided by speed`() {
        val home = GeoPoint(48.20849, 16.37208)
        val stop = GeoPoint(48.20066, 16.36968)
        val meters = home.distanceTo(stop)
        val expected = (meters * 1.35 / 1.3).roundToLong()
        assertEquals(s(expected), WalkTimeEstimator.estimate(home, stop))
        val brisk = WalkTimeEstimator.estimate(home, stop, WalkSettings(speedMetersPerSecond = 1.6, detourFactor = 1.2))
        assertEquals(s((meters * 1.2 / 1.6).roundToLong()), brisk)
        assertEquals(Duration.ZERO, WalkTimeEstimator.estimate(home, home))
    }

    @Test
    fun `settings reject implausible constants`() {
        assertThrows<IllegalArgumentException> { WalkSettings(speedMetersPerSecond = 0.1) }
        assertThrows<IllegalArgumentException> { WalkSettings(detourFactor = 0.9) }
        assertEquals(1.3, WalkSettings().speedMetersPerSecond)
        assertEquals(1.35, WalkSettings().detourFactor)
    }

    @Test
    fun `without trips the initial estimate stays`() {
        assertEquals(initial, learner.estimate(initial, emptyList()))
    }

    @Test
    fun `exponentially weighted mean with alpha 0_3`() {
        // 300 → 0.3·400 + 0.7·300 = 330 → 0.3·360 + 0.7·330 = 339
        val estimate = learner.estimate(initial, listOf(s(400), s(360)))
        assertEquals(s(339), estimate.duration)
        assertEquals(WalkTimeSource.LEARNED, estimate.source)
        assertEquals(2, estimate.samples)
    }

    @Test
    fun `outliers above twice the median are rejected once there is history`() {
        val history = listOf(s(300), s(320), s(310))
        val verdict = assertIs<TripVerdict.Outlier>(learner.judge(s(700), history))
        assertEquals(s(310), verdict.median)
        assertEquals(2.0, verdict.factor)
        assertEquals(TripVerdict.Accepted, learner.judge(s(620), history), "exactly 2x is still fine")
        // With fewer than three trips nothing counts as an outlier – the first guess may be far off.
        assertEquals(TripVerdict.Accepted, learner.judge(s(900), listOf(s(300), s(310))))
    }

    @Test
    fun `implausibly short trips are rejected`() {
        assertEquals(TripVerdict.Implausible, learner.judge(s(5), emptyList()))
    }

    @Test
    fun `history keeps the last fifty accepted trips`() {
        var history = emptyList<Duration>()
        repeat(55) { i ->
            val (verdict, next) = learner.record(s(300L + i), history)
            assertEquals(TripVerdict.Accepted, verdict)
            history = next
        }
        assertEquals(50, history.size)
        assertEquals(s(305), history.first())
        val (verdict, unchanged) = learner.record(s(5000), history)
        assertIs<TripVerdict.Outlier>(verdict)
        assertEquals(history, unchanged)
        assertEquals(50, learner.estimate(initial, history).samples)
    }

    @Test
    fun `median of odd and even counts`() {
        assertEquals(s(2), WalkTimeLearner.median(listOf(s(3), s(1), s(2))))
        assertEquals(s(25), WalkTimeLearner.median(listOf(s(10), s(40), s(20), s(30))))
        assertThrows<IllegalArgumentException> { WalkTimeLearner.median(emptyList()) }
    }

    @Test
    fun `learner validates its parameters`() {
        assertThrows<IllegalArgumentException> { WalkTimeLearner(alpha = 0.0) }
        assertThrows<IllegalArgumentException> { WalkTimeLearner(outlierFactor = 1.0) }
        assertThrows<IllegalArgumentException> { WalkTimeLearner(maxSamples = 0) }
    }
}

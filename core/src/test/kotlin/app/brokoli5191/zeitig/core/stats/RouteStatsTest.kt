package app.brokoli5191.zeitig.core.stats

import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RouteStatsTest {
    private val zone = ZoneId.of("Europe/Vienna")
    private val monday = Instant.parse("2026-11-23T06:40:00Z")

    private fun trip(
        day: Long,
        walkSeconds: Long,
        startDeltaSeconds: Long? = 0,
        accepted: Boolean = true,
    ): TripRecord {
        val planned = monday.plus(Duration.ofDays(day))
        return TripRecord(
            startedAt = planned.plusSeconds(startDeltaSeconds ?: 0),
            walkDuration = Duration.ofSeconds(walkSeconds),
            accepted = accepted,
            plannedLeaveAt = if (startDeltaSeconds == null) null else planned,
        )
    }

    @Test
    fun `empty history`() {
        val stats = RouteStatistics.compute(emptyList(), zone)
        assertEquals(0, stats.trips)
        assertNull(stats.medianWalk)
        assertNull(stats.onTimeRate)
        assertNull(stats.averageDelta)
        assertEquals(Trend.UNKNOWN, stats.walkTrend)
    }

    @Test
    fun `punctuality, median and weekdays`() {
        val trips =
            listOf(
                trip(0, 300, startDeltaSeconds = -30),
                trip(1, 320, startDeltaSeconds = 30),
                trip(2, 310, startDeltaSeconds = 180),
                trip(3, 900, startDeltaSeconds = null, accepted = false),
            )
        val stats = RouteStatistics.compute(trips, zone)
        assertEquals(4, stats.trips)
        assertEquals(3, stats.acceptedTrips)
        assertEquals(Duration.ofSeconds(310), stats.medianWalk)
        assertEquals(2.0 / 3.0, stats.onTimeRate)
        assertEquals(Duration.ofSeconds(60), stats.averageDelta)
        assertEquals(
            mapOf(DayOfWeek.MONDAY to 1, DayOfWeek.TUESDAY to 1, DayOfWeek.WEDNESDAY to 1, DayOfWeek.THURSDAY to 1),
            stats.tripsByWeekday,
        )
    }

    @Test
    fun `walking trend compares the last five with the five before`() {
        fun trendOf(vararg seconds: Long) = RouteStatistics.compute(seconds.mapIndexed { i, s -> trip(i.toLong(), s) }, zone).walkTrend
        assertEquals(Trend.FASTER, trendOf(400, 400, 400, 400, 400, 300, 300, 300, 300, 300))
        assertEquals(Trend.SLOWER, trendOf(300, 300, 300, 300, 300, 400, 400, 400, 400, 400))
        assertEquals(Trend.STEADY, trendOf(300, 300, 300, 300, 300, 305, 305, 305, 305, 305))
        assertEquals(Trend.UNKNOWN, trendOf(300, 300, 300))
    }
}

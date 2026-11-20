package app.brokoli5191.zeitig.core.stats

import app.brokoli5191.zeitig.core.walk.WalkTimeLearner
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** A finished trip measurement. [plannedLeaveAt] is what Zeitig recommended, if anything. */
public data class TripRecord(
    val startedAt: Instant,
    val walkDuration: Duration,
    val accepted: Boolean,
    val plannedLeaveAt: Instant? = null,
)

public enum class Trend { FASTER, STEADY, SLOWER, UNKNOWN }

public data class RouteStats(
    val trips: Int,
    val acceptedTrips: Int,
    val medianWalk: Duration?,
    /** Share of trips started no later than the recommended moment (+ tolerance). */
    val onTimeRate: Double?,
    /** Average start relative to the recommendation; negative = early. */
    val averageDelta: Duration?,
    val walkTrend: Trend,
    val tripsByWeekday: Map<DayOfWeek, Int>,
)

public object RouteStatistics {
    public fun compute(
        trips: List<TripRecord>,
        zone: ZoneId,
        tolerance: Duration = Duration.ofSeconds(60),
    ): RouteStats {
        val accepted = trips.filter { it.accepted }.sortedBy { it.startedAt }
        val withPlan = trips.filter { it.plannedLeaveAt != null }
        val deltas = withPlan.map { Duration.between(it.plannedLeaveAt, it.startedAt) }
        return RouteStats(
            trips = trips.size,
            acceptedTrips = accepted.size,
            medianWalk = accepted.takeIf { it.isNotEmpty() }?.let { list -> WalkTimeLearner.median(list.map { it.walkDuration }) },
            onTimeRate = deltas.takeIf { it.isNotEmpty() }?.let { list -> list.count { it <= tolerance }.toDouble() / list.size },
            averageDelta = deltas.takeIf { it.isNotEmpty() }?.let { list -> Duration.ofMillis(list.sumOf { it.toMillis() } / list.size) },
            walkTrend = trend(accepted.map { it.walkDuration }),
            tripsByWeekday = trips.groupingBy { it.startedAt.atZone(zone).dayOfWeek }.eachCount(),
        )
    }

    /** Compares the median of the last five accepted walks with the five before (±5 % is steady). */
    private fun trend(walks: List<Duration>): Trend {
        if (walks.size < 2 * WINDOW) return Trend.UNKNOWN
        val recent = WalkTimeLearner.median(walks.takeLast(WINDOW)).toMillis().toDouble()
        val before = WalkTimeLearner.median(walks.dropLast(WINDOW).takeLast(WINDOW)).toMillis().toDouble()
        val ratio = recent / before
        return when {
            ratio < 1 - STEADY_BAND -> Trend.FASTER
            ratio > 1 + STEADY_BAND -> Trend.SLOWER
            else -> Trend.STEADY
        }
    }

    private const val WINDOW = 5
    private const val STEADY_BAND = 0.05
}

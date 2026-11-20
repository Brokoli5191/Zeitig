package app.brokoli5191.zeitig.core.walk

import app.brokoli5191.zeitig.core.model.GeoPoint
import java.time.Duration
import kotlin.math.roundToLong

/** Constants of the first estimate; both are user-adjustable in the settings. */
public data class WalkSettings(
    val speedMetersPerSecond: Double = DEFAULT_SPEED,
    val detourFactor: Double = DEFAULT_DETOUR,
) {
    init {
        require(speedMetersPerSecond in 0.3..3.0) { "implausible walking speed $speedMetersPerSecond m/s" }
        require(detourFactor in 1.0..3.0) { "implausible detour factor $detourFactor" }
    }

    public companion object {
        public const val DEFAULT_SPEED: Double = 1.3
        public const val DEFAULT_DETOUR: Double = 1.35
    }
}

public enum class WalkTimeSource {
    /** Haversine × detour ÷ speed – no measured trips yet. */
    ESTIMATED,

    /** The walk time the route planner suggested. */
    PLANNED,

    /** Typed in by the user. */
    MANUAL,

    /** Learned from the user's own trips. */
    LEARNED,
}

public data class WalkEstimate(
    val duration: Duration,
    val source: WalkTimeSource,
    val samples: Int,
)

public object WalkTimeEstimator {
    /** First guess without history: great-circle distance × detour factor ÷ walking speed. */
    public fun estimate(
        from: GeoPoint,
        to: GeoPoint,
        settings: WalkSettings = WalkSettings(),
    ): Duration {
        val meters = from.distanceTo(to) * settings.detourFactor
        return Duration.ofSeconds((meters / settings.speedMetersPerSecond).roundToLong())
    }
}

public sealed interface TripVerdict {
    public data object Accepted : TripVerdict

    /** Longer than [factor] × the median of the accepted trips – a stop at the bakery, not a walk. */
    public data class Outlier(
        val median: Duration,
        val factor: Double,
    ) : TripVerdict

    /** Too short to be a real walk (e.g. "I'm there" tapped right away). */
    public data object Implausible : TripVerdict
}

/**
 * Learns the walking time per route: exponentially weighted mean (α = 0.3) over the accepted trips,
 * outliers above 2 × median rejected once there are enough samples, at most 50 trips kept.
 */
public class WalkTimeLearner(
    public val alpha: Double = 0.3,
    public val outlierFactor: Double = 2.0,
    public val maxSamples: Int = 50,
    public val minSamplesForOutlierCheck: Int = 3,
    public val minPlausible: Duration = Duration.ofSeconds(20),
) {
    init {
        require(alpha > 0.0 && alpha <= 1.0) { "alpha must be in (0, 1]" }
        require(outlierFactor > 1.0) { "outlier factor must exceed 1" }
        require(maxSamples > 0) { "maxSamples must be positive" }
    }

    /** Whether a measured trip may enter the history of [accepted] (chronological) trips. */
    public fun judge(
        measured: Duration,
        accepted: List<Duration>,
    ): TripVerdict {
        if (measured < minPlausible) return TripVerdict.Implausible
        val window = accepted.takeLast(maxSamples)
        if (window.size < minSamplesForOutlierCheck) return TripVerdict.Accepted
        val median = median(window)
        val limit = Duration.ofMillis((median.toMillis() * outlierFactor).roundToLong())
        return if (measured > limit) TripVerdict.Outlier(median, outlierFactor) else TripVerdict.Accepted
    }

    /** Current estimate: [initial] refined by the accepted trips, oldest first. */
    public fun estimate(
        initial: WalkEstimate,
        accepted: List<Duration>,
    ): WalkEstimate {
        val window = accepted.takeLast(maxSamples)
        if (window.isEmpty()) return initial
        var millis = initial.duration.toMillis().toDouble()
        for (sample in window) millis = alpha * sample.toMillis() + (1 - alpha) * millis
        return WalkEstimate(Duration.ofMillis(millis.roundToLong()), WalkTimeSource.LEARNED, window.size)
    }

    /** History after adding a trip: rejected trips are not kept, the oldest falls out beyond 50. */
    public fun record(
        measured: Duration,
        accepted: List<Duration>,
    ): Pair<TripVerdict, List<Duration>> {
        val verdict = judge(measured, accepted)
        val history = if (verdict == TripVerdict.Accepted) (accepted + measured).takeLast(maxSamples) else accepted.takeLast(maxSamples)
        return verdict to history
    }

    public companion object {
        public fun median(values: List<Duration>): Duration {
            require(values.isNotEmpty()) { "median of nothing" }
            val sorted = values.map { it.toMillis() }.sorted()
            val mid = sorted.size / 2
            val millis = if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
            return Duration.ofMillis(millis)
        }
    }
}

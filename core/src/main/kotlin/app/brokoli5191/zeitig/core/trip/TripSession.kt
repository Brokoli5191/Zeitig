package app.brokoli5191.zeitig.core.trip

import app.brokoli5191.zeitig.core.model.GeoPoint
import java.time.Duration
import java.time.Instant

/**
 * One walk from the door to the stop, measured from "I'm leaving" until the user is within
 * [arrivalRadiusMeters] of the stop or taps "I'm there". Positions are only compared, never kept.
 */
public class TripSession(
    public val routeId: Long,
    public val startedAt: Instant,
    public val target: GeoPoint?,
    public val arrivalRadiusMeters: Double = DEFAULT_RADIUS_M,
    public val timeout: Duration = DEFAULT_TIMEOUT,
) {
    public sealed interface State {
        public data class Walking(
            val elapsed: Duration,
            val distanceMeters: Double?,
        ) : State

        public data class Arrived(
            val duration: Duration,
            val manual: Boolean,
        ) : State

        public data object TimedOut : State
    }

    private var finished: State? = null
    private var lastDistance: Double? = null

    public val state: State? get() = finished

    /** Feeds a position fix; returns the new state. */
    public fun onLocation(
        position: GeoPoint,
        accuracyMeters: Float?,
        at: Instant,
    ): State {
        finished?.let { return it }
        if (isExpired(at)) return finish(State.TimedOut)
        val target = target ?: return State.Walking(elapsed(at), null)
        val distance = position.distanceTo(target)
        lastDistance = distance
        // A fix whose uncertainty is larger than the radius proves nothing.
        val precise = accuracyMeters == null || accuracyMeters <= arrivalRadiusMeters * 1.5f
        return if (distance <= arrivalRadiusMeters && precise) {
            finish(State.Arrived(elapsed(at), manual = false))
        } else {
            State.Walking(elapsed(at), distance)
        }
    }

    public fun onManualArrival(at: Instant): State =
        finished ?: if (isExpired(at)) finish(State.TimedOut) else finish(State.Arrived(elapsed(at), manual = true))

    public fun tick(at: Instant): State =
        finished ?: if (isExpired(at)) finish(State.TimedOut) else State.Walking(elapsed(at), lastDistance)

    public fun isExpired(at: Instant): Boolean = Duration.between(startedAt, at) > timeout

    private fun elapsed(at: Instant): Duration = Duration.between(startedAt, at).coerceAtLeast(Duration.ZERO)

    private fun finish(state: State): State {
        finished = state
        return state
    }

    public companion object {
        public const val DEFAULT_RADIUS_M: Double = 40.0
        public val DEFAULT_TIMEOUT: Duration = Duration.ofMinutes(30)
    }
}

package app.brokoli5191.zeitig.core.routing

import app.brokoli5191.zeitig.core.journey.Journey
import app.brokoli5191.zeitig.core.model.GeoPoint
import app.brokoli5191.zeitig.core.provider.FailureReason
import java.time.Duration
import java.time.Instant

/**
 * Online journey planning (A → B with transfers) and address search. Transitous is the v1
 * implementation; the interface keeps other planners possible (see the roadmap).
 */
public interface JourneyPlanner {
    public val id: String

    public suspend fun plan(request: PlanRequest): PlanResult

    public suspend fun geocode(
        query: String,
        near: GeoPoint? = null,
        limit: Int = 10,
    ): GeocodeResult
}

public data class PlanRequest(
    val from: GeoPoint,
    val to: GeoPoint,
    /** Departure time, or arrival time when [arriveBy]. */
    val time: Instant,
    val arriveBy: Boolean = false,
    /** Step-free transfers and access (wheelchair profile). */
    val accessible: Boolean = false,
    val language: String = "de",
    val itineraries: Int = 5,
) {
    init {
        require(itineraries in 1..MAX_ITINERARIES) { "itineraries must be within 1..$MAX_ITINERARIES" }
    }

    public companion object {
        public const val MAX_ITINERARIES: Int = 10
    }
}

/** One planned option with its concrete times. */
public data class Itinerary(
    val journey: Journey,
    val departure: Instant,
    val arrival: Instant,
    /** At least one leg carries real-time information from the planner. */
    val realtime: Boolean,
) {
    val duration: Duration get() = Duration.between(departure, arrival)
    val transfers: Int get() = journey.transfers
}

public sealed interface PlanResult {
    public data class Success(
        val itineraries: List<Itinerary>,
    ) : PlanResult

    public data class Failure(
        val reason: FailureReason,
        val message: String? = null,
    ) : PlanResult
}

public enum class PlaceKind { ADDRESS, PLACE, STOP }

public data class GeocodeMatch(
    val name: String,
    val location: GeoPoint,
    val kind: PlaceKind,
    /** District or municipality for disambiguation, e.g. "Mariahilf". */
    val area: String? = null,
    val postalCode: String? = null,
)

public sealed interface GeocodeResult {
    public data class Success(
        val matches: List<GeocodeMatch>,
    ) : GeocodeResult

    public data class Failure(
        val reason: FailureReason,
        val message: String? = null,
    ) : GeocodeResult
}

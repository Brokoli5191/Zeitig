package app.brokoli5191.zeitig.core.provider

import app.brokoli5191.zeitig.core.model.Diva
import app.brokoli5191.zeitig.core.model.StopBoard
import app.brokoli5191.zeitig.core.model.StopId

/**
 * Source of real-time departures. Wiener Linien is the only implementation in v1; other operators
 * (ÖBB via HAFAS, other cities) would plug in here – see the roadmap in the README.
 */
public interface DepartureProvider {
    /** Stable identifier, e.g. `"wienerlinien"`. */
    public val id: String

    /** Departures (and, if asked, disruptions) for the given stop points. */
    public suspend fun departures(request: DepartureRequest): ProviderResult
}

public data class DepartureRequest(
    val stopIds: Set<StopId> = emptySet(),
    val stations: Set<Diva> = emptySet(),
    val includeDisruptions: Boolean = true,
) {
    init {
        require(stopIds.isNotEmpty() || stations.isNotEmpty()) { "A request needs at least one stop or station" }
    }
}

public sealed interface ProviderResult {
    public data class Success(
        val board: StopBoard,
    ) : ProviderResult

    public data class Failure(
        val reason: FailureReason,
        val message: String? = null,
    ) : ProviderResult
}

public enum class FailureReason {
    /** No connectivity, DNS failure, timeout. */
    NETWORK,

    /** The operator answered "query limit reached" (OGD code 316) or HTTP 429. */
    RATE_LIMITED,

    /** HTTP 5xx or OGD codes 311/322 (database unavailable / no data). */
    SERVER,

    /** The stop does not exist (OGD code 312). */
    UNKNOWN_STOP,

    /** The request itself was rejected as invalid or incomplete (OGD codes 320/321, HTTP 4xx). */
    INVALID_REQUEST,

    /** The response could not be understood. */
    PARSE,
}

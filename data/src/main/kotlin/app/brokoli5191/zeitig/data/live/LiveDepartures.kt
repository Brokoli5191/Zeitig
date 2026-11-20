package app.brokoli5191.zeitig.data.live

import app.brokoli5191.zeitig.core.model.Departure
import app.brokoli5191.zeitig.core.model.Disruption
import app.brokoli5191.zeitig.core.model.StopId
import app.brokoli5191.zeitig.core.provider.DepartureProvider
import app.brokoli5191.zeitig.core.provider.DepartureRequest
import app.brokoli5191.zeitig.core.provider.FailureReason
import app.brokoli5191.zeitig.core.provider.ProviderResult
import app.brokoli5191.zeitig.data.ogd.WienerLinienApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.time.toKotlinDuration

/** What is known about a set of stops right now. */
public data class LiveSnapshot(
    val departures: List<Departure>,
    val disruptions: List<Disruption>,
    /** Oldest fetch time among the requested stops; null if nothing was ever fetched. */
    val fetchedAt: Instant?,
    /** Set when the latest attempt failed (older data may still be present). */
    val failure: FailureReason?,
)

/**
 * The app-wide cache in front of the provider. Screens, the notification service, widget and tile
 * all read through it: stops refreshed less than [maxAge] ago are served from memory, everything
 * else is fetched in one batched request – so several consumers never multiply the request rate.
 */
public class LiveDepartures(
    private val provider: DepartureProvider,
    private val api: WienerLinienApi?,
    private val clock: Clock,
    private val maxAge: Duration = Duration.ofSeconds(25),
    private val disruptionMaxAge: Duration = Duration.ofMinutes(5),
) {
    private class Entry(
        val departures: List<Departure>,
        val disruptions: List<Disruption>,
        val fetchedAt: Instant,
    )

    private val mutex = Mutex()
    private val byStop = HashMap<StopId, Entry>()
    private var lineDisruptions: Pair<Set<String>, Pair<List<Disruption>, Instant>>? = null
    private var lastFailure: FailureReason? = null

    public suspend fun snapshot(
        stops: Set<StopId>,
        lines: Set<String> = emptySet(),
        forceRefresh: Boolean = false,
    ): LiveSnapshot =
        mutex.withLock {
            val now = clock.instant()
            val stale = stops.filter { stop -> forceRefresh || byStop[stop]?.let { Duration.between(it.fetchedAt, now) >= maxAge } ?: true }
            if (stale.isNotEmpty()) fetch(stale.toSet(), now)
            if (lines.isNotEmpty() && api != null) refreshLineDisruptions(lines, now, forceRefresh)
            merged(stops, lines)
        }

    /** Emits a snapshot every [interval] while collected; stop collecting to stop the network. */
    public fun poll(
        stops: Set<StopId>,
        lines: Set<String> = emptySet(),
        interval: Duration = Duration.ofSeconds(30),
    ): Flow<LiveSnapshot> =
        flow {
            while (true) {
                emit(snapshot(stops, lines))
                delay(interval.toKotlinDuration())
            }
        }

    private suspend fun fetch(
        stops: Set<StopId>,
        now: Instant,
    ) {
        when (val result = provider.departures(DepartureRequest(stopIds = stops, includeDisruptions = true))) {
            is ProviderResult.Success -> {
                lastFailure = null
                val board = result.board
                for (stop in stops) {
                    byStop[stop] =
                        Entry(
                            departures = board.departures.filter { it.stopId == stop },
                            disruptions = board.disruptions.filter { stop in it.relatedStops },
                            fetchedAt = now,
                        )
                }
                // Line-wide disruptions without stop references belong to every requested stop.
                val general = board.disruptions.filter { it.relatedStops.isEmpty() }
                if (general.isNotEmpty()) {
                    for (stop in stops) {
                        byStop[stop]?.let {
                            byStop[stop] =
                                Entry(it.departures, (it.disruptions + general).distinctBy { d -> d.id }, it.fetchedAt)
                        }
                    }
                }
            }

            is ProviderResult.Failure -> {
                lastFailure = result.reason
            }
        }
    }

    private suspend fun refreshLineDisruptions(
        lines: Set<String>,
        now: Instant,
        force: Boolean,
    ) {
        val cached = lineDisruptions
        if (!force && cached != null && cached.first.containsAll(lines) &&
            Duration.between(cached.second.second, now) < disruptionMaxAge
        ) {
            return
        }
        when (val result = api!!.trafficInfoList(lines = lines)) {
            is ProviderResult.Success -> lineDisruptions = lines to (result.board.disruptions to now)
            is ProviderResult.Failure -> lastFailure = result.reason
        }
    }

    private fun merged(
        stops: Set<StopId>,
        lines: Set<String>,
    ): LiveSnapshot {
        val entries = stops.mapNotNull { byStop[it] }
        val lineInfos = lineDisruptions?.takeIf { lines.isNotEmpty() }?.second?.first.orEmpty()
        return LiveSnapshot(
            departures = entries.flatMap { it.departures }.sortedBy { it.time },
            disruptions = (entries.flatMap { it.disruptions } + lineInfos).distinctBy { it.id },
            fetchedAt =
                if (entries.size == stops.size &&
                    entries.isNotEmpty()
                ) {
                    entries.minOf { it.fetchedAt }
                } else {
                    entries.minOfOrNull { it.fetchedAt }
                },
            failure = lastFailure,
        )
    }
}

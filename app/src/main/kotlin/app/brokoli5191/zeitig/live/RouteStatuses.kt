package app.brokoli5191.zeitig.live

import app.brokoli5191.zeitig.AppContainer
import app.brokoli5191.zeitig.core.model.Departure
import app.brokoli5191.zeitig.core.model.Direction
import app.brokoli5191.zeitig.core.model.StopId
import app.brokoli5191.zeitig.core.provider.FailureReason
import app.brokoli5191.zeitig.core.route.Route
import app.brokoli5191.zeitig.core.route.RouteStatus
import app.brokoli5191.zeitig.core.routing.PlanRequest
import app.brokoli5191.zeitig.core.routing.PlanResult
import app.brokoli5191.zeitig.data.live.LiveSnapshot
import app.brokoli5191.zeitig.data.settings.AppSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import kotlin.time.toKotlinDuration

/** One route with what the app currently knows about it. */
data class LiveRoute(
    val route: Route,
    val status: RouteStatus?,
    val failure: FailureReason?,
)

/**
 * Turns routes into live statuses: polls the shared [app.brokoli5191.zeitig.data.live.LiveDepartures]
 * cache for every WL boarding stop (one batched request per cycle), asks the journey planner for a
 * timetable when the first leg is not a Wiener Linien line, and re-evaluates the engine every few
 * seconds so the countdown, "leave now" and re-planning stay current between polls.
 */
class RouteStatuses(
    private val container: AppContainer,
) {
    private val scheduleCache = HashMap<Long, Pair<Instant, List<Departure>>>()
    private val scheduleMutex = Mutex()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(
        routes: Flow<List<Route>>,
        pollInterval: Duration = Duration.ofSeconds(30),
        evaluateEvery: Duration = Duration.ofSeconds(10),
    ): Flow<List<LiveRoute>> {
        val live: Flow<Pair<List<Route>, LiveSnapshot>> =
            routes.flatMapLatest { list ->
                val stops = list.mapNotNull { it.journey.firstTransit?.boarding?.stopId }.toSet()
                val lines = list.flatMap { it.journey.lines }.toSet()
                if (stops.isEmpty()) {
                    flowOf(list to LiveSnapshot(emptyList(), emptyList(), null, null))
                } else {
                    container.liveDepartures.poll(stops, lines, pollInterval).map { list to it }
                }
            }
        val ticker =
            flow {
                while (true) {
                    emit(Unit)
                    delay(evaluateEvery.toKotlinDuration())
                }
            }
        return combine(live, container.settings.settings, ticker) { (list, snapshot), settings, _ ->
            val now = container.clock.instant()
            list.map { route -> evaluate(route, snapshot, settings, now) }
        }
    }

    private suspend fun evaluate(
        route: Route,
        snapshot: LiveSnapshot,
        settings: AppSettings,
        now: Instant,
    ): LiveRoute {
        val first = route.journey.firstTransit
        val walks = container.routes.acceptedWalks(route.id)
        if (first == null) return LiveRoute(route, null, null)
        val departures =
            if (first.boarding != null) {
                snapshot.departures
            } else {
                timetableDepartures(route, settings, now)
            }
        val hasData = first.boarding == null || snapshot.fetchedAt != null
        val status =
            if (!hasData) {
                null
            } else {
                container.statusCalculator.status(
                    route,
                    departures,
                    snapshot.disruptions,
                    walks,
                    settings.walkSettings,
                    now,
                    snapshot.fetchedAt,
                )
            }
        return LiveRoute(route, status, snapshot.failure)
    }

    /**
     * For first legs without WL real-time (ÖBB, Badner Bahn): departures from today's timetable via
     * the planner, cached for ten minutes. Only with the user's routing opt-in.
     */
    private suspend fun timetableDepartures(
        route: Route,
        settings: AppSettings,
        now: Instant,
    ): List<Departure> {
        if (!settings.routingEnabled) return emptyList()
        val from = route.origin.location ?: return emptyList()
        val to = route.destination.location ?: return emptyList()
        val line = route.journey.firstTransit?.line ?: return emptyList()
        return scheduleMutex.withLock {
            scheduleCache[route.id]?.takeIf { Duration.between(it.first, now) < Duration.ofMinutes(10) }?.let { return@withLock it.second }
            val result = container.planner.plan(PlanRequest(from, to, now, itineraries = 5, accessible = route.accessible))
            val departures =
                (result as? PlanResult.Success)
                    ?.itineraries
                    .orEmpty()
                    .mapNotNull { itinerary ->
                        val leg = itinerary.journey.firstTransit?.takeIf { it.line == line } ?: return@mapNotNull null
                        val walkBefore = itinerary.journey.plannedAccessWalk
                        Departure(StopId(0), leg.line, leg.headsign, Direction.OUTBOUND, leg.type, itinerary.departure + walkBefore, null)
                    }
            scheduleCache[route.id] = now to departures
            departures
        }
    }
}

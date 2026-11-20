package app.brokoli5191.zeitig.data.repo

import app.brokoli5191.zeitig.core.route.Route
import app.brokoli5191.zeitig.core.stats.TripRecord
import app.brokoli5191.zeitig.core.walk.TripVerdict
import app.brokoli5191.zeitig.core.walk.WalkTimeLearner
import app.brokoli5191.zeitig.data.db.RouteDao
import app.brokoli5191.zeitig.data.db.RouteMapping
import app.brokoli5191.zeitig.data.db.TripDao
import app.brokoli5191.zeitig.data.db.TripEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Duration
import java.time.Instant

public enum class ArrivalSource { GEOFENCE, MANUAL }

/** Routes and their trip history. Trips store durations only – never a location track. */
public class RouteRepository(
    private val routes: RouteDao,
    private val trips: TripDao,
    private val clock: Clock,
    private val learner: WalkTimeLearner = WalkTimeLearner(),
) {
    public fun observeRoutes(): Flow<List<Route>> = routes.observeAll().map { list -> list.map(RouteMapping::toDomain) }

    public fun observeRoute(id: Long): Flow<Route?> = routes.observe(id).map { it?.let(RouteMapping::toDomain) }

    public suspend fun routes(): List<Route> = routes.all().map(RouteMapping::toDomain)

    public suspend fun route(id: Long): Route? = routes.get(id)?.let(RouteMapping::toDomain)

    /** Inserts when [Route.id] is 0, updates otherwise; returns the id. */
    public suspend fun save(route: Route): Long {
        val existing = if (route.id != 0L) routes.get(route.id) else null
        return if (existing == null) {
            routes.insert(RouteMapping.toEntity(route.copy(id = 0), routes.nextSortOrder(), clock.millis()))
        } else {
            routes.update(RouteMapping.toEntity(route, existing.sortOrder, existing.createdAt))
            route.id
        }
    }

    public suspend fun delete(id: Long): Unit = routes.delete(id)

    public suspend fun reorder(ids: List<Long>): Unit = routes.reorder(ids)

    public fun observeTrips(routeId: Long): Flow<List<TripRecord>> = trips.observe(routeId).map { list -> list.map(::record) }

    public suspend fun acceptedWalks(routeId: Long): List<Duration> =
        trips.all(routeId).filter { it.accepted }.map { Duration.ofSeconds(it.walkSeconds.toLong()) }

    /** Judges and stores a measured trip; returns how it was judged. */
    public suspend fun recordTrip(
        routeId: Long,
        startedAt: Instant,
        walk: Duration,
        plannedLeaveAt: Instant?,
        arrival: ArrivalSource,
    ): TripVerdict {
        val verdict = learner.judge(walk, acceptedWalks(routeId))
        trips.insertAndPrune(
            TripEntity(
                routeId = routeId,
                startedAt = startedAt.toEpochMilli(),
                walkSeconds = walk.seconds.toInt(),
                accepted = verdict == TripVerdict.Accepted,
                plannedLeaveAt = plannedLeaveAt?.toEpochMilli(),
                arrival = arrival.name.lowercase(),
            ),
            keep = learner.maxSamples,
        )
        return verdict
    }

    /** "Reset walking speed": forget everything learned for the route. */
    public suspend fun resetLearning(routeId: Long): Unit = trips.deleteForRoute(routeId)

    private fun record(entity: TripEntity) =
        TripRecord(
            startedAt = Instant.ofEpochMilli(entity.startedAt),
            walkDuration = Duration.ofSeconds(entity.walkSeconds.toLong()),
            accepted = entity.accepted,
            plannedLeaveAt = entity.plannedLeaveAt?.let(Instant::ofEpochMilli),
        )
}

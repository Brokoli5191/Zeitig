package app.brokoli5191.zeitig.core.route

import app.brokoli5191.zeitig.core.engine.LeaveInput
import app.brokoli5191.zeitig.core.engine.LeavePlan
import app.brokoli5191.zeitig.core.engine.LeaveTimeEngine
import app.brokoli5191.zeitig.core.journey.Leg
import app.brokoli5191.zeitig.core.model.Departure
import app.brokoli5191.zeitig.core.model.Disruption
import app.brokoli5191.zeitig.core.schedule.Occurrence
import app.brokoli5191.zeitig.core.schedule.ScheduleCalendar
import app.brokoli5191.zeitig.core.walk.WalkEstimate
import app.brokoli5191.zeitig.core.walk.WalkSettings
import app.brokoli5191.zeitig.core.walk.WalkTimeEstimator
import app.brokoli5191.zeitig.core.walk.WalkTimeLearner
import app.brokoli5191.zeitig.core.walk.WalkTimeSource
import java.time.Duration
import java.time.Instant

/** Everything the UI, notifications, widget and tile show for one route at one moment. */
public data class RouteStatus(
    val route: Route,
    val plan: LeavePlan,
    val walk: WalkEstimate,
    /** The schedule occurrence running now or coming next, if the route has a schedule. */
    val occurrence: Occurrence?,
    val disruptions: List<Disruption>,
    /** False when the first leg has no Wiener Linien real-time and runs on the timetable. */
    val hasRealtimeSource: Boolean,
    val dataAge: Duration?,
    val evaluatedAt: Instant,
) {
    /** Inside the scheduled window right now (monitoring and reminders are due). */
    val isActiveNow: Boolean get() = occurrence != null && evaluatedAt in occurrence
}

/** Combines a route with live data into a [RouteStatus]; pure, so it runs anywhere. */
public class RouteStatusCalculator(
    private val engine: LeaveTimeEngine = LeaveTimeEngine(),
    private val learner: WalkTimeLearner = WalkTimeLearner(),
    private val calendar: ScheduleCalendar = ScheduleCalendar(),
) {
    /**
     * The walking time used for [route]: typed in > learned from [acceptedTrips] (seeded with the
     * planner's or the geometric estimate) > planner > geometric > [fallback].
     */
    public fun walkEstimate(
        route: Route,
        acceptedTrips: List<Duration>,
        settings: WalkSettings,
        fallback: Duration = DEFAULT_WALK,
    ): WalkEstimate {
        route.manualWalk?.let { return WalkEstimate(it, WalkTimeSource.MANUAL, 0) }
        val firstTransit = route.journey.firstTransit
        val planned =
            route.journey.plannedAccessWalk.takeIf {
                firstTransit != null && route.journey.legs.first() is Leg.Walk &&
                    it > Duration.ZERO
            }
        val origin = route.origin.location
        val stop = firstTransit?.from?.location
        val seed =
            when {
                planned != null -> {
                    WalkEstimate(planned, WalkTimeSource.PLANNED, 0)
                }

                origin != null && stop != null -> {
                    WalkEstimate(
                        WalkTimeEstimator.estimate(origin, stop, settings),
                        WalkTimeSource.ESTIMATED,
                        0,
                    )
                }

                else -> {
                    WalkEstimate(fallback, WalkTimeSource.ESTIMATED, 0)
                }
            }
        return learner.estimate(seed, acceptedTrips)
    }

    public fun status(
        route: Route,
        departures: List<Departure>,
        disruptions: List<Disruption>,
        acceptedTrips: List<Duration>,
        settings: WalkSettings,
        now: Instant,
        dataFetchedAt: Instant?,
    ): RouteStatus {
        val walk = walkEstimate(route, acceptedTrips, settings)
        val boarding = route.journey.firstTransit?.boarding
        val relevantDepartures = if (boarding != null) departures.filter { it.matches(boarding) } else departures
        val relevantDisruptions = route.journey.relevantDisruptions(disruptions)
        val occurrence = route.schedule?.let { calendar.next(it, now, route.journey.plannedTotal) }
        val activeArriveBy = occurrence?.arriveBy?.takeIf { now in occurrence }
        val plan =
            engine.plan(
                LeaveInput(
                    now = now,
                    departures = relevantDepartures,
                    walkTime = walk.duration,
                    walkSource = walk.source,
                    buffer = route.buffer,
                    onward = route.journey.plannedOnward.takeIf { route.journey.firstTransit != null && it > Duration.ZERO },
                    arriveBy = activeArriveBy,
                    disruptions = relevantDisruptions,
                ),
            )
        return RouteStatus(
            route = route,
            plan = plan,
            walk = walk,
            occurrence = occurrence,
            disruptions = relevantDisruptions,
            hasRealtimeSource = boarding != null,
            dataAge = dataFetchedAt?.let { Duration.between(it, now).coerceAtLeast(Duration.ZERO) },
            evaluatedAt = now,
        )
    }

    public companion object {
        public val DEFAULT_WALK: Duration = Duration.ofMinutes(5)
    }
}

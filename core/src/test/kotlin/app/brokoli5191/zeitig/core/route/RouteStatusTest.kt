package app.brokoli5191.zeitig.core.route

import app.brokoli5191.zeitig.core.engine.LeavePlan
import app.brokoli5191.zeitig.core.journey.Journey
import app.brokoli5191.zeitig.core.journey.Leg
import app.brokoli5191.zeitig.core.journey.LegPoint
import app.brokoli5191.zeitig.core.journey.Place
import app.brokoli5191.zeitig.core.model.Departure
import app.brokoli5191.zeitig.core.model.Direction
import app.brokoli5191.zeitig.core.model.Disruption
import app.brokoli5191.zeitig.core.model.DisruptionKind
import app.brokoli5191.zeitig.core.model.GeoPoint
import app.brokoli5191.zeitig.core.model.LineTarget
import app.brokoli5191.zeitig.core.model.StopId
import app.brokoli5191.zeitig.core.model.TransportType
import app.brokoli5191.zeitig.core.schedule.Schedule
import app.brokoli5191.zeitig.core.schedule.ScheduleMode
import app.brokoli5191.zeitig.core.walk.WalkSettings
import app.brokoli5191.zeitig.core.walk.WalkTimeSource
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RouteStatusTest {
    private val calculator = RouteStatusCalculator()
    private val settings = WalkSettings()
    private val now = OffsetDateTime.parse("2026-11-23T07:20:00+01:00").toInstant()
    private val target = LineTarget(StopId(4116), "U1", Direction.INBOUND)
    private val home = Place("Home", GeoPoint(48.2101, 16.3735))
    private val office = Place("Office", GeoPoint(48.1859, 16.3773))

    private fun min(m: Long) = Duration.ofMinutes(m)

    private val plannedJourney =
        Journey(
            listOf(
                Leg.Walk(LegPoint("Home", home.location), LegPoint("Stephansplatz", GeoPoint(48.2085, 16.3721), StopId(4116)), min(4)),
                Leg.Transit(
                    LegPoint("Stephansplatz", GeoPoint(48.2085, 16.3721), StopId(4116)),
                    LegPoint("Hauptbahnhof", GeoPoint(48.1871, 16.3733)),
                    min(5),
                    "U1",
                    TransportType.METRO,
                    "Oberlaa",
                    target,
                ),
                Leg.Walk(LegPoint("Hauptbahnhof", null), LegPoint("Office", office.location), min(6)),
            ),
        )

    private fun route(
        journey: Journey = plannedJourney,
        schedule: Schedule? = null,
        manualWalk: Duration? = null,
        origin: Place = home,
    ) = Route(1, "Arbeit", origin, office, journey, schedule, manualWalk = manualWalk)

    private fun departure(
        inMinutes: Long,
        line: String = "U1",
        stop: Int = 4116,
        direction: Direction = Direction.INBOUND,
    ) = Departure(StopId(stop), line, "Oberlaa", direction, TransportType.METRO, now.plus(min(inMinutes)), now.plus(min(inMinutes)))

    @Test
    fun `walk time prefers manual, then learned, then planner, then geometry`() {
        assertEquals(WalkTimeSource.MANUAL, calculator.walkEstimate(route(manualWalk = min(7)), listOf(min(3)), settings).source)
        assertEquals(WalkTimeSource.LEARNED, calculator.walkEstimate(route(), listOf(min(3)), settings).source)
        val planned = calculator.walkEstimate(route(), emptyList(), settings)
        assertEquals(WalkTimeSource.PLANNED, planned.source)
        assertEquals(min(4), planned.duration)

        val simple =
            Journey.simple(
                home,
                LegPoint("Stephansplatz", GeoPoint(48.2085, 16.3721), StopId(4116)),
                "U1",
                TransportType.METRO,
                "Oberlaa",
                target,
                Duration.ZERO,
            )
        val geometric = calculator.walkEstimate(route(journey = simple), emptyList(), settings)
        assertEquals(WalkTimeSource.ESTIMATED, geometric.source)
        assertTrue(geometric.duration > Duration.ZERO)
        val unknown = calculator.walkEstimate(route(journey = simple, origin = Place("?", null)), emptyList(), settings)
        assertEquals(RouteStatusCalculator.DEFAULT_WALK, unknown.duration)
    }

    @Test
    fun `status filters departures to the boarding and computes the arrival`() {
        val status =
            calculator.status(
                route(),
                listOf(departure(3), departure(8), departure(9, line = "U3"), departure(12, direction = Direction.OUTBOUND), departure(14)),
                disruptions = emptyList(),
                acceptedTrips = emptyList(),
                settings = settings,
                now = now,
                dataFetchedAt = now.minusSeconds(20),
            )
        val plan = assertIs<LeavePlan.Go>(status.plan)
        assertEquals(now.plus(min(8)), plan.chosen.departure.time)
        assertEquals(now.plus(min(8 + 11)), plan.chosen.arrival, "5 min ride + 6 min walk")
        assertEquals(listOf(now.plus(min(14))), plan.alternatives.map { it.departure.time })
        assertEquals(Duration.ofSeconds(20), status.dataAge)
        assertTrue(status.hasRealtimeSource)
        assertNull(status.occurrence)
        assertFalse(status.isActiveNow)
        assertEquals(now, status.evaluatedAt)
    }

    @Test
    fun `active arrive-by window chooses the latest departure that is on time`() {
        val schedule = Schedule(Schedule.WORKDAYS, ScheduleMode.ArriveBy(LocalTime.of(7, 45), lead = min(30)))
        val status =
            calculator.status(
                route(schedule = schedule),
                listOf(departure(5), departure(10), departure(15)),
                emptyList(),
                emptyList(),
                settings,
                now,
                null,
            )
        assertTrue(status.isActiveNow)
        val plan = assertIs<LeavePlan.Go>(status.plan)
        // 07:30 + 11 min = 07:41 is in time, 07:35 + 11 = 07:46 is not.
        assertEquals(now.plus(min(10)), plan.chosen.departure.time)
        assertNull(status.dataAge)
    }

    @Test
    fun `disruptions are narrowed to the journey`() {
        val onLine = Disruption("a", DisruptionKind.LONG, "U1", "", setOf("U1"), emptySet())
        val other = Disruption("b", DisruptionKind.LONG, "U6", "", setOf("U6"), emptySet())
        val status = calculator.status(route(), listOf(departure(8)), listOf(onLine, other), emptyList(), settings, now, now)
        assertEquals(listOf(onLine), status.disruptions)
    }

    @Test
    fun `routes without real-time source keep every departure`() {
        val train =
            Journey(
                listOf(
                    Leg.Walk(LegPoint("Home", null), LegPoint("Meidling", null), min(3)),
                    Leg.Transit(LegPoint("Meidling", null), LegPoint("Baden", null), min(30), "R 2", TransportType.TRAIN, "Baden"),
                ),
            )
        val status =
            calculator.status(
                route(journey = train),
                listOf(departure(9, line = "R 2", stop = 0)),
                emptyList(),
                emptyList(),
                settings,
                now,
                null,
            )
        assertFalse(status.hasRealtimeSource)
        assertIs<LeavePlan.Go>(status.plan)
    }

    @Test
    fun `route validation`() {
        assertThrows<IllegalArgumentException> { Route(1, " ", home, office, plannedJourney, null) }
        assertThrows<IllegalArgumentException> { Route(1, "x", home, office, plannedJourney, null, buffer = min(-1)) }
        assertThrows<IllegalArgumentException> { Route(1, "x", home, office, plannedJourney, null, manualWalk = min(-1)) }
    }
}

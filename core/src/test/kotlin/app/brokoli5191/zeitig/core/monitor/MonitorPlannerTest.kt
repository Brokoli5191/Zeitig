package app.brokoli5191.zeitig.core.monitor

import app.brokoli5191.zeitig.core.journey.Journey
import app.brokoli5191.zeitig.core.journey.LegPoint
import app.brokoli5191.zeitig.core.journey.Place
import app.brokoli5191.zeitig.core.model.Departure
import app.brokoli5191.zeitig.core.model.Direction
import app.brokoli5191.zeitig.core.model.GeoPoint
import app.brokoli5191.zeitig.core.model.LineTarget
import app.brokoli5191.zeitig.core.model.StopId
import app.brokoli5191.zeitig.core.model.TransportType
import app.brokoli5191.zeitig.core.reminder.ReminderState
import app.brokoli5191.zeitig.core.route.Route
import app.brokoli5191.zeitig.core.route.RouteStatus
import app.brokoli5191.zeitig.core.route.RouteStatusCalculator
import app.brokoli5191.zeitig.core.schedule.Schedule
import app.brokoli5191.zeitig.core.schedule.ScheduleMode
import app.brokoli5191.zeitig.core.walk.WalkSettings
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MonitorPlannerTest {
    private val planner = MonitorPlanner()
    private val calculator = RouteStatusCalculator()
    private val now = OffsetDateTime.parse("2026-11-23T07:30:00+01:00").toInstant()

    private fun route(
        id: Long,
        window: Pair<LocalTime, LocalTime>?,
        notifications: Boolean = true,
    ): Route {
        val target = LineTarget(StopId(id.toInt()), "49", Direction.OUTBOUND)
        return Route(
            id = id,
            name = "R$id",
            origin = Place("Home", null),
            destination = Place("Dest", null),
            journey =
                Journey.simple(
                    Place("Home", null),
                    LegPoint("Stop", GeoPoint(48.2, 16.3), target.stopId),
                    "49",
                    TransportType.TRAM,
                    "Hütteldorf",
                    target,
                    Duration.ZERO,
                ),
            schedule = window?.let { Schedule(setOf(DayOfWeek.MONDAY), ScheduleMode.Window(it.first, it.second)) },
            manualWalk = Duration.ofMinutes(4),
            notificationsEnabled = notifications,
        )
    }

    private fun status(
        route: Route,
        departureIn: Long,
    ): RouteStatus {
        val departure =
            Departure(
                StopId(route.id.toInt()),
                "49",
                "Hütteldorf",
                Direction.OUTBOUND,
                TransportType.TRAM,
                now.plus(Duration.ofMinutes(departureIn)),
                null,
            )
        return calculator.status(route, listOf(departure), emptyList(), emptyList(), WalkSettings(), now, now)
    }

    private val window = LocalTime.of(7, 15) to LocalTime.of(8, 30)

    @Test
    fun `active routes are ordered by leave time and the first is primary`() {
        val later = status(route(1, window), departureIn = 20)
        val sooner = status(route(2, window), departureIn = 10)
        val outside = status(route(3, LocalTime.of(17, 0) to LocalTime.of(18, 0)), departureIn = 5)
        val muted = status(route(4, window, notifications = false), departureIn = 6)
        val decision = planner.decide(listOf(later, sooner, outside, muted), emptySet(), emptyMap(), now, now)
        assertEquals(listOf(2L, 1L), decision.active.map { it.route.id })
        assertEquals(2L, decision.primary?.route?.id)
        assertFalse(decision.stop)
        // Route 2 leaves at +5 min: its 5-minute reminder is due right now.
        assertEquals(listOf(5), decision.reminders[2L]?.map { it.minutesBefore })
        assertEquals(now.plus(Duration.ofMinutes(2)), decision.nextWakeUp, "route 2's 3-minute reminder")
    }

    @Test
    fun `manually watched routes count as active outside their window`() {
        val outside = status(route(3, null), departureIn = 30)
        val decision = planner.decide(listOf(outside), setOf(3L), emptyMap(), now, now)
        assertEquals(listOf(3L), decision.active.map { it.route.id })
        assertTrue(decision.reminders.isEmpty())
    }

    @Test
    fun `stops when nothing is active or the session is too long`() {
        val active = status(route(1, window), departureIn = 20)
        assertTrue(planner.decide(emptyList(), emptySet(), emptyMap(), now, now).stop)
        assertTrue(planner.decide(listOf(active), emptySet(), emptyMap(), now.minus(Duration.ofHours(4)), now).stop)
    }

    @Test
    fun `keeps running while watched routes still wait for data, but not past the session limit`() {
        val waiting = planner.decide(emptyList(), setOf(3L), emptyMap(), now, now, awaitingData = true)
        assertFalse(waiting.stop)
        assertEquals(null, waiting.primary)
        assertTrue(planner.decide(emptyList(), setOf(3L), emptyMap(), now.minus(Duration.ofHours(4)), now, awaitingData = true).stop)
    }

    @Test
    fun `reminder state carries over between evaluations`() {
        val route = route(2, window)
        val first = planner.decide(listOf(status(route, 10)), emptySet(), emptyMap(), now, now)
        val second = planner.decide(listOf(status(route, 10)), emptySet(), first.reminderStates, now, now.plusSeconds(10))
        assertTrue(second.reminders.isEmpty(), "the 5-minute reminder is not repeated")
        assertEquals(setOf(5), second.reminderStates.getValue(2L).sent)
        assertTrue(first.reminderStates.getValue(2L) != ReminderState())
    }
}

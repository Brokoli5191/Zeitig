package app.brokoli5191.zeitig.core.reminder

import app.brokoli5191.zeitig.core.engine.LeaveOption
import app.brokoli5191.zeitig.core.model.Departure
import app.brokoli5191.zeitig.core.model.Direction
import app.brokoli5191.zeitig.core.model.StopId
import app.brokoli5191.zeitig.core.model.TransportType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemindersTest {
    private val leaveAt = Instant.parse("2026-11-23T06:40:00Z")
    private val tracker = ReminderTracker(ReminderPlan())

    private fun option(
        plannedMinute: Long = 47,
        leave: Instant = leaveAt,
    ) = LeaveOption(
        Departure(
            StopId(1),
            "49",
            "Hütteldorf",
            Direction.OUTBOUND,
            TransportType.TRAM,
            Instant.parse("2026-11-23T06:00:00Z").plus(Duration.ofMinutes(plannedMinute)),
            null,
        ),
        leave,
        null,
    )

    private fun minutesBefore(m: Long): Instant = leaveAt.minus(Duration.ofMinutes(m))

    @Test
    fun `default plan is 5, 3, 1 and now`() {
        assertEquals(listOf(5, 3, 1, 0), ReminderPlan().offsets)
        assertEquals(listOf(10, 2, 0), ReminderPlan(listOf(0, 2, 10, 2)).offsets)
        assertThrows<IllegalArgumentException> { ReminderPlan(emptyList()) }
        assertThrows<IllegalArgumentException> { ReminderPlan(listOf(61)) }
    }

    @Test
    fun `each reminder fires once at its moment`() {
        var state = ReminderState()
        val fired = mutableListOf<Int>()
        for (instant in listOf(
            minutesBefore(6),
            minutesBefore(5),
            minutesBefore(5),
            minutesBefore(3),
            minutesBefore(1),
            leaveAt,
            leaveAt.plusSeconds(20),
        )) {
            val (events, next) = tracker.evaluate(option(), state, instant)
            fired += events.map { it.minutesBefore }
            state = next
        }
        assertEquals(listOf(5, 3, 1, 0), fired)
    }

    @Test
    fun `a late start delivers only the most recent reminder`() {
        val (events, state) = tracker.evaluate(option(), ReminderState(), minutesBefore(2).plusSeconds(30))
        assertEquals(listOf(3), events.map { it.minutesBefore })
        assertEquals(setOf(5, 3), state.sent)
    }

    @Test
    fun `a reminder far overdue is skipped silently, leave-now never`() {
        val (skipped, state) = tracker.evaluate(option(), ReminderState(), minutesBefore(3).plusSeconds(100))
        assertTrue(skipped.isEmpty(), "the 3-minute reminder is 100 s late")
        assertEquals(setOf(5, 3), state.sent)
        val (leaveNow, _) = tracker.evaluate(option(), state, leaveAt.plusSeconds(120))
        assertEquals(listOf(0), leaveNow.map { it.minutesBefore })
    }

    @Test
    fun `a different departure resets the reminders`() {
        val (_, state) = tracker.evaluate(option(), ReminderState(), minutesBefore(5))
        val later = option(plannedMinute = 55, leave = leaveAt.plus(Duration.ofMinutes(8)))
        val (events, next) = tracker.evaluate(later, state, minutesBefore(5).plusSeconds(1))
        assertTrue(events.isEmpty())
        assertEquals(later.key, next.optionKey)
        assertTrue(next.sent.isEmpty())
    }

    @Test
    fun `no option clears the state`() {
        val (events, state) = tracker.evaluate(null, ReminderState("x", setOf(5)), leaveAt)
        assertTrue(events.isEmpty())
        assertEquals(ReminderState(), state)
        assertNull(tracker.nextDue(null, state))
    }

    @Test
    fun `next due moment for alarm scheduling`() {
        assertEquals(minutesBefore(5), tracker.nextDue(option(), ReminderState()))
        assertEquals(minutesBefore(1), tracker.nextDue(option(), ReminderState(option().key, setOf(5, 3))))
        assertNull(tracker.nextDue(option(), ReminderState(option().key, setOf(5, 3, 1, 0))))
        assertEquals(minutesBefore(5), tracker.nextDue(option(), ReminderState("other", setOf(5, 3, 1, 0))))
    }
}

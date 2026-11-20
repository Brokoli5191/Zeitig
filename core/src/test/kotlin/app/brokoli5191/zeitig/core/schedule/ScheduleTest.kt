package app.brokoli5191.zeitig.core.schedule

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScheduleTest {
    private val calendar = ScheduleCalendar()
    private val total = Duration.ofMinutes(25)
    private val morning = Schedule(Schedule.WORKDAYS, ScheduleMode.Window(LocalTime.of(7, 15), LocalTime.of(8, 30)))

    private fun at(iso: String): Instant = OffsetDateTime.parse(iso).toInstant()

    @Test
    fun `window occurrence on a workday`() {
        val occurrence = calendar.occurrenceOn(morning, LocalDate.of(2026, 11, 23), total)!!
        assertEquals(at("2026-11-23T07:15:00+01:00"), occurrence.start)
        assertEquals(at("2026-11-23T08:30:00+01:00"), occurrence.end)
        assertNull(occurrence.arriveBy)
        assertNull(calendar.occurrenceOn(morning, LocalDate.of(2026, 11, 22), total), "Sunday")
        assertEquals(5, morning.tripsPerWeek)
    }

    @Test
    fun `window running past midnight ends the next day`() {
        val late = Schedule(setOf(DayOfWeek.FRIDAY), ScheduleMode.Window(LocalTime.of(23, 30), LocalTime.of(0, 30)))
        val occurrence = calendar.occurrenceOn(late, LocalDate.of(2026, 11, 27), total)!!
        assertEquals(at("2026-11-28T00:30:00+01:00"), occurrence.end)
        // Saturday 00:10 still belongs to Friday's occurrence.
        assertEquals(occurrence, calendar.current(late, at("2026-11-28T00:10:00+01:00"), total))
    }

    @Test
    fun `arrive-by starts the monitoring lead plus travel time before arrival`() {
        val office = Schedule(Schedule.WORKDAYS, ScheduleMode.ArriveBy(LocalTime.of(8, 0), lead = Duration.ofMinutes(30)))
        val occurrence = calendar.occurrenceOn(office, LocalDate.of(2026, 11, 23), total)!!
        assertEquals(at("2026-11-23T08:00:00+01:00"), occurrence.arriveBy)
        assertEquals(at("2026-11-23T07:05:00+01:00"), occurrence.start)
        assertEquals(occurrence.arriveBy, occurrence.end)
    }

    @Test
    fun `current and next occurrences`() {
        val inside = at("2026-11-23T07:40:00+01:00")
        assertTrue(inside in calendar.current(morning, inside, total)!!)
        val after = at("2026-11-23T09:00:00+01:00")
        assertNull(calendar.current(morning, after, total))
        assertEquals(at("2026-11-24T07:15:00+01:00"), calendar.next(morning, after, total)!!.start)
        // Friday evening → Monday morning.
        assertEquals(at("2026-11-30T07:15:00+01:00"), calendar.next(morning, at("2026-11-27T20:00:00+01:00"), total)!!.start)
        // Inside a window, "next" is the running one.
        assertEquals(at("2026-11-23T07:15:00+01:00"), calendar.next(morning, inside, total)!!.start)
        assertNull(calendar.next(Schedule(emptySet(), morning.mode), inside, total))
        assertFalse(at("2026-11-23T08:30:00+01:00") in calendar.current(morning, inside, total)!!, "end is exclusive")
    }

    @Test
    fun `spring forward moves times in the gap forward`() {
        val sunday = LocalDate.of(2026, 3, 29)
        val gap = Schedule(setOf(DayOfWeek.SUNDAY), ScheduleMode.Window(LocalTime.of(2, 15), LocalTime.of(2, 45)))
        val occurrence = calendar.occurrenceOn(gap, sunday, total)!!
        assertEquals(at("2026-03-29T03:15:00+02:00"), occurrence.start)
        assertEquals(at("2026-03-29T03:45:00+02:00"), occurrence.end)

        val across = Schedule(setOf(DayOfWeek.SUNDAY), ScheduleMode.Window(LocalTime.of(1, 30), LocalTime.of(3, 30)))
        val short = calendar.occurrenceOn(across, sunday, total)!!
        assertEquals(Duration.ofHours(1), Duration.between(short.start, short.end), "the night is an hour shorter")
    }

    @Test
    fun `fall back uses the earlier offset for ambiguous times`() {
        val sunday = LocalDate.of(2026, 10, 25)
        val ambiguous = Schedule(setOf(DayOfWeek.SUNDAY), ScheduleMode.Window(LocalTime.of(2, 30), LocalTime.of(4, 0)))
        val occurrence = calendar.occurrenceOn(ambiguous, sunday, total)!!
        assertEquals(at("2026-10-25T02:30:00+02:00"), occurrence.start)
        assertEquals(Duration.ofMinutes(150), Duration.between(occurrence.start, occurrence.end), "the night is an hour longer")
    }

    @Test
    fun `arrive-by on the last Sunday of March keeps the arrival exact`() {
        val sunday = Schedule(setOf(DayOfWeek.SUNDAY), ScheduleMode.ArriveBy(LocalTime.of(3, 30), lead = Duration.ofMinutes(10)))
        val occurrence = calendar.occurrenceOn(sunday, LocalDate.of(2026, 3, 29), total)!!
        assertEquals(at("2026-03-29T03:30:00+02:00"), occurrence.arriveBy)
        assertEquals(at("2026-03-29T01:55:00+01:00"), occurrence.start, "35 real minutes earlier, across the jump")
    }

    @Test
    fun `invalid modes are rejected`() {
        assertThrows<IllegalArgumentException> { ScheduleMode.Window(LocalTime.NOON, LocalTime.NOON) }
        assertThrows<IllegalArgumentException> { ScheduleMode.ArriveBy(LocalTime.NOON, Duration.ofMinutes(-1)) }
    }
}

package app.brokoli5191.zeitig.core.schedule

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** When a route is taken: on which weekdays, and either in a leave window or to arrive by a time. */
public data class Schedule(
    val days: Set<DayOfWeek>,
    val mode: ScheduleMode,
) {
    /** "How often per week" – one trip per selected weekday. */
    val tripsPerWeek: Int get() = days.size

    public companion object {
        public val WORKDAYS: Set<DayOfWeek> =
            setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    }
}

public sealed interface ScheduleMode {
    /** Leave somewhere between [start] and [end]; an [end] before [start] runs past midnight. */
    public data class Window(
        val start: LocalTime,
        val end: LocalTime,
    ) : ScheduleMode {
        init {
            require(start != end) { "an empty window" }
        }
    }

    /** Be at the destination by [arrival]; monitoring starts [lead] before the planned departure. */
    public data class ArriveBy(
        val arrival: LocalTime,
        val lead: Duration = Duration.ofMinutes(30),
    ) : ScheduleMode {
        init {
            require(!lead.isNegative) { "negative lead" }
        }
    }
}

/** One concrete occurrence of a schedule: the monitoring interval and, if any, the arrival deadline. */
public data class Occurrence(
    val start: Instant,
    val end: Instant,
    val arriveBy: Instant?,
) {
    public operator fun contains(instant: Instant): Boolean = !instant.isBefore(start) && instant.isBefore(end)
}

/**
 * Turns a [Schedule] into concrete instants in [zone]. Local times that fall into the spring-forward
 * gap move forward by the gap; ambiguous autumn times use the earlier (summer-time) offset – the
 * behaviour of [ZonedDateTime.of], made explicit by tests.
 */
public class ScheduleCalendar(
    private val zone: ZoneId = VIENNA,
) {
    /** The occurrence that starts on [date], or null when [date] is not a scheduled weekday. */
    public fun occurrenceOn(
        schedule: Schedule,
        date: LocalDate,
        plannedTotal: Duration,
    ): Occurrence? {
        if (date.dayOfWeek !in schedule.days) return null
        return when (val mode = schedule.mode) {
            is ScheduleMode.Window -> {
                val start = ZonedDateTime.of(date, mode.start, zone).toInstant()
                val endDate = if (mode.end.isAfter(mode.start)) date else date.plusDays(1)
                val end = ZonedDateTime.of(endDate, mode.end, zone).toInstant()
                Occurrence(start, end, arriveBy = null)
            }

            is ScheduleMode.ArriveBy -> {
                val arrival = ZonedDateTime.of(date, mode.arrival, zone).toInstant()
                Occurrence(arrival - plannedTotal - mode.lead, arrival, arriveBy = arrival)
            }
        }
    }

    /** The occurrence containing [now], if any (an occurrence may have started yesterday). */
    public fun current(
        schedule: Schedule,
        now: Instant,
        plannedTotal: Duration,
    ): Occurrence? {
        val today = now.atZone(zone).toLocalDate()
        return listOf(today.minusDays(1), today, today.plusDays(1))
            .mapNotNull { occurrenceOn(schedule, it, plannedTotal) }
            .firstOrNull { now in it }
    }

    /** The current occurrence or the next one to start after [now]; null for a schedule without days. */
    public fun next(
        schedule: Schedule,
        now: Instant,
        plannedTotal: Duration,
    ): Occurrence? {
        if (schedule.days.isEmpty()) return null
        current(schedule, now, plannedTotal)?.let { return it }
        val today = now.atZone(zone).toLocalDate()
        return (0L..8L)
            .asSequence()
            .mapNotNull { occurrenceOn(schedule, today.plusDays(it), plannedTotal) }
            .firstOrNull { it.start.isAfter(now) }
    }

    public companion object {
        public val VIENNA: ZoneId = ZoneId.of("Europe/Vienna")
    }
}

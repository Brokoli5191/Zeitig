package app.brokoli5191.zeitig.data.db

import app.brokoli5191.zeitig.core.journey.Place
import app.brokoli5191.zeitig.core.model.GeoPoint
import app.brokoli5191.zeitig.core.reminder.ReminderPlan
import app.brokoli5191.zeitig.core.route.Route
import app.brokoli5191.zeitig.core.schedule.Schedule
import app.brokoli5191.zeitig.core.schedule.ScheduleMode
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime

internal object RouteMapping {
    private const val MODE_WINDOW = "window"
    private const val MODE_ARRIVE_BY = "arrive_by"

    fun toDomain(entity: RouteEntity): Route =
        Route(
            id = entity.id,
            name = entity.name,
            origin = Place(entity.originName, point(entity.originLat, entity.originLon)),
            destination = Place(entity.destinationName, point(entity.destinationLat, entity.destinationLon)),
            journey = JourneyJson.decode(entity.journeyJson),
            schedule = schedule(entity),
            reminders =
                entity.reminders
                    .split(',')
                    .mapNotNull { it.trim().toIntOrNull() }
                    .filter { it in 0..ReminderPlan.MAX_MINUTES }
                    .ifEmpty { ReminderPlan.DEFAULT }
                    .let(::ReminderPlan),
            buffer = Duration.ofSeconds(entity.bufferSeconds.toLong().coerceAtLeast(0)),
            accessible = entity.accessible,
            manualWalk = entity.manualWalkSeconds?.let { Duration.ofSeconds(it.toLong().coerceAtLeast(0)) },
            notificationsEnabled = entity.notificationsEnabled,
        )

    fun toEntity(
        route: Route,
        sortOrder: Int,
        createdAt: Long,
    ): RouteEntity {
        val mode = route.schedule?.mode
        return RouteEntity(
            id = route.id,
            name = route.name.trim(),
            originName = route.origin.name,
            originLat = route.origin.location?.latitude,
            originLon = route.origin.location?.longitude,
            destinationName = route.destination.name,
            destinationLat = route.destination.location?.latitude,
            destinationLon = route.destination.location?.longitude,
            journeyJson = JourneyJson.encode(route.journey),
            scheduleDays = route.schedule?.days?.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) } ?: 0,
            scheduleMode =
                when (mode) {
                    is ScheduleMode.Window -> MODE_WINDOW
                    is ScheduleMode.ArriveBy -> MODE_ARRIVE_BY
                    null -> null
                },
            windowStartMinute = (mode as? ScheduleMode.Window)?.start?.let(::minuteOfDay),
            windowEndMinute = (mode as? ScheduleMode.Window)?.end?.let(::minuteOfDay),
            arriveByMinute = (mode as? ScheduleMode.ArriveBy)?.arrival?.let(::minuteOfDay),
            leadMinutes = (mode as? ScheduleMode.ArriveBy)?.lead?.toMinutes()?.toInt() ?: DEFAULT_LEAD,
            reminders = route.reminders.offsets.joinToString(","),
            bufferSeconds = route.buffer.seconds.toInt(),
            accessible = route.accessible,
            manualWalkSeconds = route.manualWalk?.seconds?.toInt(),
            notificationsEnabled = route.notificationsEnabled,
            sortOrder = sortOrder,
            createdAt = createdAt,
        )
    }

    private fun schedule(entity: RouteEntity): Schedule? {
        val days = DayOfWeek.entries.filter { entity.scheduleDays and (1 shl (it.value - 1)) != 0 }.toSet()
        val mode =
            when (entity.scheduleMode) {
                MODE_WINDOW -> {
                    val start = entity.windowStartMinute?.let(::time) ?: return null
                    val end = entity.windowEndMinute?.let(::time) ?: return null
                    if (start == end) return null
                    ScheduleMode.Window(start, end)
                }

                MODE_ARRIVE_BY -> {
                    ScheduleMode.ArriveBy(
                        entity.arriveByMinute?.let(::time) ?: return null,
                        Duration.ofMinutes(entity.leadMinutes.toLong().coerceAtLeast(0)),
                    )
                }

                else -> {
                    return null
                }
            }
        return Schedule(days, mode)
    }

    private fun point(
        lat: Double?,
        lon: Double?,
    ): GeoPoint? = if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) GeoPoint(lat, lon) else null

    private fun minuteOfDay(time: LocalTime): Int = time.hour * 60 + time.minute

    private fun time(minute: Int): LocalTime = LocalTime.of((minute / 60).coerceIn(0, 23), (minute % 60).coerceIn(0, 59))

    const val DEFAULT_LEAD: Int = 30
}

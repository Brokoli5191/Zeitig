package app.brokoli5191.zeitig.alarms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.AlarmManagerCompat
import androidx.core.content.edit
import app.brokoli5191.zeitig.core.route.Route
import app.brokoli5191.zeitig.core.schedule.ScheduleCalendar
import app.brokoli5191.zeitig.notify.Intents
import java.time.Duration
import java.time.Instant

/**
 * One alarm per route at the start of its next schedule window. With exact alarms the receiver
 * starts the monitoring service at the exact minute; without, an inexact window alarm (≥ 10 min
 * wide since Android 12) fires up to ten minutes early and posts a "tap to start" notification.
 */
class WindowScheduler(
    private val context: Context,
) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences("window_alarms", Context.MODE_PRIVATE)
    private val calendar = ScheduleCalendar()

    fun canScheduleExact(): Boolean = alarms?.let(AlarmManagerCompat::canScheduleExactAlarms) == true

    fun rescheduleAll(
        routes: List<Route>,
        now: Instant,
    ) {
        val alarms = alarms ?: return
        val scheduled = mutableSetOf<String>()
        for (route in routes) {
            val schedule = route.schedule ?: continue
            if (!route.notificationsEnabled) continue
            val running = calendar.current(schedule, now, route.journey.plannedTotal)
            val next =
                if (running != null) {
                    calendar.next(schedule, running.end, route.journey.plannedTotal)
                } else {
                    calendar.next(schedule, now, route.journey.plannedTotal)
                } ?: continue
            val intent = pending(route.id)
            val exact =
                canScheduleExact() &&
                    runCatching {
                        alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.start.toEpochMilli(), intent)
                    }.isSuccess // The user can revoke the permission between the check and the call.
            if (!exact) {
                alarms.setWindow(AlarmManager.RTC_WAKEUP, (next.start - INEXACT_WINDOW).toEpochMilli(), INEXACT_WINDOW.toMillis(), intent)
            }
            scheduled += route.id.toString()
        }
        // Cancel alarms of routes that were deleted or lost their schedule.
        prefs.getStringSet(KEY, emptySet()).orEmpty().minus(scheduled).forEach { alarms.cancel(pending(it.toLong())) }
        prefs.edit { putStringSet(KEY, scheduled) }
    }

    private fun pending(routeId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            routeId.toInt(),
            Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_WINDOW).putExtra(Intents.EXTRA_ROUTE_ID, routeId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private companion object {
        const val KEY = "scheduled"
        val INEXACT_WINDOW: Duration = Duration.ofMinutes(10)
    }
}

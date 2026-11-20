package app.brokoli5191.zeitig.notify

import android.Manifest
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import app.brokoli5191.zeitig.R
import app.brokoli5191.zeitig.core.engine.LeaveOption
import app.brokoli5191.zeitig.core.engine.LeavePlan
import app.brokoli5191.zeitig.core.model.Disruption
import app.brokoli5191.zeitig.core.route.Route
import app.brokoli5191.zeitig.core.route.RouteStatus
import app.brokoli5191.zeitig.ui.components.icon
import app.brokoli5191.zeitig.ui.format.formatClock
import app.brokoli5191.zeitig.ui.format.minutesUntil
import java.time.Duration
import java.time.Instant
import java.util.Locale

/** Builds and posts every notification Zeitig shows. */
class Notifications(
    private val context: Context,
) {
    private val manager = NotificationManagerCompat.from(context)

    /** The app-level switch and, from Android 13, the runtime permission. */
    fun canPost(): Boolean =
        manager.areNotificationsEnabled() &&
            (
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            )

    /** Whether the user lets the countdown appear as a Live Update (status-bar chip, lock screen). */
    fun canPromote(): Boolean = manager.canPostPromotedNotifications()

    // --- ongoing countdown (the monitoring service's foreground notification) -------------------

    fun ongoing(
        status: RouteStatus?,
        others: Int,
        now: Instant,
        sessionStart: Instant,
    ): Notification {
        val builder =
            NotificationCompat
                .Builder(context, Channels.LIVE)
                .setSmallIcon(R.drawable.ic_timer)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setDeleteIntent(Intents.stopMonitoring(context))
                .addAction(R.drawable.ic_close, context.getString(R.string.action_stop), Intents.stopMonitoring(context))
        if (status == null) {
            return builder
                .setContentTitle(context.getString(R.string.live_waiting))
                .setContentIntent(Intents.openApp(context))
                .build()
        }
        val route = status.route
        builder.setContentIntent(Intents.openRoute(context, route.id))
        val chosen = (status.plan as? LeavePlan.Go)?.chosen
        if (chosen == null) {
            return builder
                .setContentTitle(route.name)
                .setContentText(
                    context.getString(if (status.plan is LeavePlan.TooLate) R.string.too_late_short else R.string.no_service_short),
                )
                .build()
        }
        val minutes = minutesUntil(now, chosen.leaveAt)
        builder
            .setContentTitle(leaveTitle(route, minutes))
            .setContentText(details(chosen))
            .setSubText(if (others > 0) context.resources.getQuantityString(R.plurals.other_routes, others, others) else null)
            .addAction(R.drawable.ic_directions_walk, context.getString(R.string.action_leaving), Intents.startTrip(context, route.id))
            .setRequestPromotedOngoing(true)
        if (chosen.leaveAt.isAfter(now)) {
            // The system renders the countdown – exact to the second even while the app sleeps.
            builder
                .setWhen(chosen.leaveAt.toEpochMilli())
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
        }
        val total = Duration.between(sessionStart, chosen.leaveAt).toMillis().coerceAtLeast(1)
        val done = Duration.between(sessionStart, now).toMillis().coerceIn(0, total)
        builder.setStyle(
            NotificationCompat
                .ProgressStyle()
                .setProgressSegments(listOf(NotificationCompat.ProgressStyle.Segment(PROGRESS_MAX)))
                .setProgress((done * PROGRESS_MAX / total).toInt())
                .setProgressTrackerIcon(IconCompat.createWithResource(context, R.drawable.ic_directions_walk))
                .setProgressEndIcon(IconCompat.createWithResource(context, chosen.departure.type.icon())),
        )
        return builder.build()
    }

    // --- reminders -------------------------------------------------------------------------------

    fun reminder(
        route: Route,
        option: LeaveOption,
        minutesBefore: Int,
    ) {
        val title = leaveTitle(route, minutesBefore.toLong())
        post(
            reminderId(route.id),
            NotificationCompat
                .Builder(context, Channels.ALERTS)
                .setSmallIcon(R.drawable.ic_timer)
                .setContentTitle(title)
                .setContentText(details(option))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setTimeoutAfter(Duration.ofMinutes(3).toMillis())
                .setContentIntent(Intents.openRoute(context, route.id))
                .addAction(R.drawable.ic_directions_walk, context.getString(R.string.action_leaving), Intents.startTrip(context, route.id))
                .build(),
        )
    }

    fun missed(
        route: Route,
        next: LeaveOption?,
        now: Instant,
    ) {
        val text =
            if (next == null) {
                context.getString(R.string.too_late_short)
            } else {
                context.getString(
                    R.string.notify_missed_next,
                    next.departure.line,
                    clock(next.departure.time),
                    minutesUntil(now, next.leaveAt),
                )
            }
        post(
            reminderId(route.id),
            NotificationCompat
                .Builder(context, Channels.ALERTS)
                .setSmallIcon(R.drawable.ic_timer)
                .setContentTitle(context.getString(R.string.notify_missed, route.name))
                .setContentText(text)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(Intents.openRoute(context, route.id))
                .build(),
        )
    }

    fun disruption(
        route: Route,
        disruption: Disruption,
    ) {
        post(
            DISRUPTION_BASE + (disruption.id.hashCode() and 0x0FFF_FFFF) % DISRUPTION_RANGE,
            NotificationCompat
                .Builder(context, Channels.DISRUPTIONS)
                .setSmallIcon(R.drawable.ic_warning_filled)
                .setContentTitle(context.getString(R.string.notify_disruption, route.name))
                .setContentText(disruption.title)
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        listOf(disruption.title, disruption.description).filter {
                            it.isNotBlank()
                        }.joinToString("\n"),
                    ),
                )
                .setAutoCancel(true)
                .setContentIntent(Intents.openRoute(context, route.id))
                .build(),
        )
    }

    /** Without exact alarms the service cannot start itself – a tap on this starts it. */
    fun windowStarting(route: Route) {
        post(
            WINDOW_BASE + route.id.toInt(),
            NotificationCompat
                .Builder(context, Channels.WINDOW)
                .setSmallIcon(R.drawable.ic_timer)
                .setContentTitle(context.getString(R.string.notify_window, route.name))
                .setContentText(context.getString(R.string.notify_window_text))
                .setAutoCancel(true)
                .setTimeoutAfter(Duration.ofHours(2).toMillis())
                .setContentIntent(Intents.startMonitoring(context, route.id))
                .addAction(R.drawable.ic_timer, context.getString(R.string.action_start_live), Intents.startMonitoring(context, route.id))
                .build(),
        )
    }

    // --- trip measurement --------------------------------------------------------------------------

    fun trip(
        route: Route,
        startedAt: Instant,
        distanceMeters: Double?,
    ): Notification =
        NotificationCompat
            .Builder(context, Channels.TRIP)
            .setSmallIcon(R.drawable.ic_directions_walk)
            .setContentTitle(context.getString(R.string.notify_trip, route.name))
            .setContentText(
                distanceMeters?.let { context.getString(R.string.notify_trip_distance, it.toInt()) }
                    ?: context.getString(R.string.notify_trip_manual),
            ).setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setWhen(startedAt.toEpochMilli())
            .setUsesChronometer(true)
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setContentIntent(Intents.openRoute(context, route.id))
            .addAction(R.drawable.ic_flag, context.getString(R.string.action_arrived), Intents.arrived(context))
            .addAction(R.drawable.ic_close, context.getString(R.string.cancel), Intents.cancelTrip(context))
            .build()

    fun tripResult(
        route: Route,
        text: String,
    ) {
        post(
            TRIP_RESULT,
            NotificationCompat
                .Builder(context, Channels.TRIP)
                .setSmallIcon(R.drawable.ic_flag)
                .setContentTitle(route.name)
                .setContentText(text)
                .setAutoCancel(true)
                .setTimeoutAfter(Duration.ofMinutes(10).toMillis())
                .setContentIntent(Intents.openRoute(context, route.id))
                .build(),
        )
    }

    /** Updates a notification that a foreground service already shows. */
    fun show(
        id: Int,
        notification: Notification,
    ) = post(id, notification)

    fun cancel(id: Int) = manager.cancel(id)

    private fun leaveTitle(
        route: Route,
        minutes: Long,
    ): String =
        if (minutes <= 0) {
            context.getString(R.string.notify_leave_now, route.name)
        } else {
            context.getString(R.string.notify_leave_in, route.name, minutes)
        }

    private fun details(option: LeaveOption): String {
        val departure = option.departure
        val arrival = option.arrival
        return if (arrival != null) {
            context.getString(R.string.notify_details_arrival, departure.line, departure.towards, clock(departure.time), clock(arrival))
        } else {
            context.getString(R.string.notify_details, departure.line, departure.towards, clock(departure.time))
        }
    }

    private fun clock(instant: Instant): String {
        val locale = context.resources.configuration.locales[0] ?: Locale.ROOT
        return formatClock(instant, locale, DateFormat.is24HourFormat(context))
    }

    @Suppress("MissingPermission") // checked by canPost()
    private fun post(
        id: Int,
        notification: Notification,
    ) {
        if (!manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        manager.notify(id, notification)
    }

    companion object {
        const val ONGOING = 1
        const val TRIP_ONGOING = 2
        const val TRIP_RESULT = 3
        private const val PROGRESS_MAX = 100
        private const val REMINDER_BASE = 1_000
        private const val WINDOW_BASE = 100_000
        private const val DISRUPTION_BASE = 200_000
        private const val DISRUPTION_RANGE = 100_000

        fun reminderId(routeId: Long): Int = REMINDER_BASE + routeId.toInt()
    }
}

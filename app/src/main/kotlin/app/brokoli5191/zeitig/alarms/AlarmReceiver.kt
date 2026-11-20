package app.brokoli5191.zeitig.alarms

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.brokoli5191.zeitig.ZeitigApplication
import app.brokoli5191.zeitig.monitor.MonitoringService
import app.brokoli5191.zeitig.notify.Intents
import app.brokoli5191.zeitig.notify.Notifications
import kotlinx.coroutines.launch

/** Window starts and service wake-ups. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val container = (context.applicationContext as ZeitigApplication).container
        when (intent.action) {
            ACTION_WAKE -> {
                // Normally the running service just re-evaluates. If the process had died, an
                // exact alarm may restart it (allowed); an inexact one cannot and does nothing.
                if (!container.statusBoard.wakeUp() && WindowScheduler(context).canScheduleExact()) {
                    MonitoringService.start(context)
                }
            }

            ACTION_WINDOW -> {
                val routeId = intent.getLongExtra(Intents.EXTRA_ROUTE_ID, 0L)
                val pending = goAsync()
                container.applicationScope.launch {
                    try {
                        val route = container.routes.route(routeId)
                        if (route != null && route.notificationsEnabled) {
                            if (WindowScheduler(context).canScheduleExact()) {
                                MonitoringService.start(context)
                            } else {
                                Notifications(context).windowStarting(route)
                            }
                        }
                        WindowScheduler(context).rescheduleAll(container.routes.routes(), container.clock.instant())
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_WAKE = "app.brokoli5191.zeitig.alarm.WAKE"
        const val ACTION_WINDOW = "app.brokoli5191.zeitig.alarm.WINDOW"
    }
}

/** Alarms do not survive reboots, clock changes or app updates – schedule them again. */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action !in HANDLED) return
        val container = (context.applicationContext as ZeitigApplication).container
        val pending = goAsync()
        container.applicationScope.launch {
            try {
                WindowScheduler(context).rescheduleAll(container.routes.routes(), container.clock.instant())
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        // A string constant: below Android 12 the permission broadcast simply never arrives.
        @SuppressLint("InlinedApi")
        val HANDLED =
            setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
                AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            )
    }
}

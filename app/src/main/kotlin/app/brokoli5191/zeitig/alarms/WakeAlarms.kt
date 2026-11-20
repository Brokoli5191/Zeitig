package app.brokoli5191.zeitig.alarms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.AlarmManagerCompat
import java.time.Duration
import java.time.Instant

/**
 * Backup wake-ups for the running monitoring service. With the exact-alarm permission the next
 * reminder is scheduled exactly; without it, an inexact allow-while-idle alarm wakes the device a
 * few minutes early and the service's wake lock covers the rest.
 */
object WakeAlarms {
    private const val REQUEST = 42
    private val EARLY: Duration = Duration.ofMinutes(9)

    fun schedule(
        context: Context,
        at: Instant?,
    ) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = pending(context)
        if (at == null) {
            alarms.cancel(intent)
            return
        }
        val exact =
            AlarmManagerCompat.canScheduleExactAlarms(alarms) &&
                runCatching { alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), intent) }.isSuccess
        if (!exact) alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, (at - EARLY).toEpochMilli(), intent)
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pending(context))
    }

    private fun pending(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST,
            Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_WAKE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}

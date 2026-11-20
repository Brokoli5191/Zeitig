package app.brokoli5191.zeitig.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import app.brokoli5191.zeitig.R

/** Notification channels; users can tune each in the system settings. */
object Channels {
    const val ALERTS = "leave_alerts"
    const val LIVE = "live_countdown"
    const val TRIP = "trip_measurement"
    const val DISRUPTIONS = "disruptions"
    const val WINDOW = "schedule_start"

    fun create(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(ALERTS, context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = context.getString(R.string.channel_alerts_description)
                },
                // Live Updates need an importance above MIN; LOW keeps the 30-second updates silent.
                NotificationChannel(LIVE, context.getString(R.string.channel_live), NotificationManager.IMPORTANCE_LOW).apply {
                    description = context.getString(R.string.channel_live_description)
                },
                NotificationChannel(TRIP, context.getString(R.string.channel_trip), NotificationManager.IMPORTANCE_LOW).apply {
                    description = context.getString(R.string.channel_trip_description)
                },
                NotificationChannel(
                    DISRUPTIONS,
                    context.getString(R.string.channel_disruptions),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = context.getString(R.string.channel_disruptions_description)
                },
                NotificationChannel(WINDOW, context.getString(R.string.channel_window), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = context.getString(R.string.channel_window_description)
                },
            ),
        )
    }
}

package app.brokoli5191.zeitig.notify

import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.AlarmManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/** Runtime state of everything notification-related the user can grant or refuse. */
data class NotificationStatus(
    val notificationsAllowed: Boolean,
    /** Null below Android 12, where exact alarms need no permission. */
    val exactAlarms: Boolean?,
    /** Null below Android 16, where Live Updates do not exist. */
    val liveUpdates: Boolean?,
)

object NotificationSettings {
    fun status(context: Context): NotificationStatus {
        val notifications = Notifications(context)
        return NotificationStatus(
            notificationsAllowed = notifications.canPost(),
            exactAlarms =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(AlarmManager::class.java)?.let(AlarmManagerCompat::canScheduleExactAlarms) == true
                } else {
                    null
                },
            liveUpdates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) notifications.canPromote() else null,
        )
    }

    /** "Alarms & reminders" for this app. */
    fun openExactAlarms(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            open(context, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()))
        }
    }

    /** The Live Updates switch; not every device has the screen, so fall back to the app's notification settings. */
    fun openLiveUpdates(context: Context) {
        val promotion =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            } else {
                null
            }
        if (promotion == null || !open(context, promotion)) openNotifications(context)
    }

    fun openNotifications(context: Context) {
        open(context, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
    }

    private fun open(
        context: Context,
        intent: Intent,
    ): Boolean =
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
}

/** Re-reads the status whenever the screen resumes (the user may come back from system settings). */
@Composable
fun rememberNotificationStatus(): NotificationStatus {
    val context = LocalContext.current
    var status by remember { mutableStateOf(NotificationSettings.status(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { status = NotificationSettings.status(context) }
    return status
}

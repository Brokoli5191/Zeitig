package app.brokoli5191.zeitig.notify

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import app.brokoli5191.zeitig.MainActivity
import app.brokoli5191.zeitig.monitor.MonitoringService
import app.brokoli5191.zeitig.trip.TripService

/** Every PendingIntent the app hands out, in one place (immutable, unique request codes). */
object Intents {
    const val EXTRA_ROUTE_ID = "route_id"
    const val ACTION_START_TRIP = "app.brokoli5191.zeitig.action.START_TRIP"
    const val ACTION_WATCH = "app.brokoli5191.zeitig.action.WATCH"

    private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    fun openRoute(
        context: Context,
        routeId: Long,
    ): PendingIntent =
        PendingIntent.getActivity(
            context,
            code(1, routeId),
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_ROUTE_ID, routeId)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            FLAGS,
        )

    fun openApp(context: Context): PendingIntent =
        PendingIntent.getActivity(context, 2, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), FLAGS)

    /**
     * "I'm leaving". With location permission the trip service starts straight from the
     * notification (an allowed while-in-use start); without it the app opens and asks first.
     */
    fun startTrip(
        context: Context,
        routeId: Long,
    ): PendingIntent =
        if (hasLocation(context)) {
            PendingIntent.getForegroundService(context, code(3, routeId), TripService.startIntent(context, routeId), FLAGS)
        } else {
            PendingIntent.getActivity(
                context,
                code(4, routeId),
                Intent(context, MainActivity::class.java).setAction(ACTION_START_TRIP).putExtra(EXTRA_ROUTE_ID, routeId),
                FLAGS,
            )
        }

    fun arrived(context: Context): PendingIntent = PendingIntent.getService(context, 5, TripService.arrivedIntent(context), FLAGS)

    fun cancelTrip(context: Context): PendingIntent = PendingIntent.getService(context, 6, TripService.cancelIntent(context), FLAGS)

    fun startMonitoring(
        context: Context,
        routeId: Long? = null,
    ): PendingIntent =
        PendingIntent.getForegroundService(
            context,
            code(7, routeId ?: 0),
            MonitoringService.startIntent(context, routeId),
            FLAGS,
        )

    fun stopMonitoring(context: Context): PendingIntent = PendingIntent.getService(context, 8, MonitoringService.stopIntent(context), FLAGS)

    fun hasLocation(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun code(
        kind: Int,
        routeId: Long,
    ): Int = (routeId.toInt() shl 4) or kind
}

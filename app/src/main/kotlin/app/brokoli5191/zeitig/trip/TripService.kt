package app.brokoli5191.zeitig.trip

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import app.brokoli5191.zeitig.R
import app.brokoli5191.zeitig.ZeitigApplication
import app.brokoli5191.zeitig.core.engine.LeavePlan
import app.brokoli5191.zeitig.core.model.GeoPoint
import app.brokoli5191.zeitig.core.route.Route
import app.brokoli5191.zeitig.core.trip.TripSession
import app.brokoli5191.zeitig.core.walk.TripVerdict
import app.brokoli5191.zeitig.data.repo.ArrivalSource
import app.brokoli5191.zeitig.monitor.MonitorPrefs
import app.brokoli5191.zeitig.notify.Channels
import app.brokoli5191.zeitig.notify.Intents
import app.brokoli5191.zeitig.notify.Notifications
import app.brokoli5191.zeitig.ui.format.wholeMinutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Measures one walk to the stop: foreground service of type location, started only from the app or
 * from a notification action (both grant while-in-use location). Position fixes are compared with
 * the stop and dropped; only the duration is stored.
 */
class TripService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val container get() = (application as ZeitigApplication).container
    private var session: TripSession? = null
    private var route: Route? = null
    private var plannedLeaveAt: Instant? = null
    private var measuring: Job? = null
    private val now: Instant get() = container.clock.instant()

    private val listener =
        LocationListenerCompat { location: Location ->
            val current = session ?: return@LocationListenerCompat
            handle(
                current.onLocation(
                    GeoPoint(location.latitude, location.longitude),
                    if (location.hasAccuracy()) location.accuracy else null,
                    now,
                ),
            )
        }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_ARRIVED -> session?.let { handle(it.onManualArrival(now)) } ?: stopSelf()
            ACTION_CANCEL -> finish(null)
            ACTION_START -> start(intent.getLongExtra(Intents.EXTRA_ROUTE_ID, 0L))
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun start(routeId: Long) {
        val notifications = Notifications(this)
        // A second tap on "I'm leaving" for the same walk keeps the running measurement.
        if (measuring?.isActive == true && session?.routeId == routeId) return
        val placeholder =
            NotificationCompat
                .Builder(this, Channels.TRIP)
                .setSmallIcon(R.drawable.ic_directions_walk)
                .setContentTitle(getString(R.string.notify_trip_starting))
                .setOngoing(true)
                .build()
        try {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
            ServiceCompat.startForeground(this, Notifications.TRIP_ONGOING, placeholder, type)
        } catch (_: SecurityException) {
            // No while-in-use location (permission revoked or not started from a visible context).
            stopSelf()
            return
        }
        getSystemService(LocationManager::class.java)?.removeUpdates(listener)
        measuring?.cancel()
        measuring =
            scope.launch {
                measure(routeId, notifications)
            }
    }

    private suspend fun measure(
        routeId: Long,
        notifications: Notifications,
    ) {
        val loaded = container.routes.route(routeId)
        if (loaded == null) {
            finish(null)
            return
        }
        route = loaded
        plannedLeaveAt =
            container.statusBoard.routes.value
                .firstOrNull { it.route.id == routeId }
                ?.status
                ?.plan
                ?.let { (it as? LeavePlan.Go)?.chosen?.leaveAt }
        val target = loaded.journey.firstTransit?.from?.location
        val started = now
        session = TripSession(routeId, started, target)
        container.statusBoard.setTripRoute(routeId)
        // The user is on the way: no more reminders for this route in the current window.
        MonitorPrefs(this@TripService).unwatch(routeId, started)
        container.statusBoard.wakeUp()
        update(notifications, loaded, started, null)
        requestLocations()
        while (currentCoroutineContext().isActive && session?.state == null) {
            delay(TICK_MS)
            session?.let { handle(it.tick(now)) }
        }
    }

    @SuppressLint("MissingPermission") // only started with location permission, see Intents.startTrip
    private fun requestLocations() {
        if (!Intents.hasLocation(this)) return
        val manager = getSystemService(LocationManager::class.java) ?: return
        val providers =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && manager.hasProvider(LocationManager.FUSED_PROVIDER)) {
                listOf(LocationManager.FUSED_PROVIDER)
            } else {
                listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter {
                    runCatching { manager.isProviderEnabled(it) }.getOrDefault(false)
                }
            }
        providers.forEach { manager.requestLocationUpdates(it, INTERVAL_MS, MIN_DISTANCE_M, listener, Looper.getMainLooper()) }
    }

    private fun handle(state: TripSession.State) {
        val route = route ?: return
        when (state) {
            is TripSession.State.Walking -> session?.let { update(Notifications(this), route, it.startedAt, state.distanceMeters) }
            is TripSession.State.Arrived -> finish(state)
            TripSession.State.TimedOut -> finish(state)
        }
    }

    private fun update(
        notifications: Notifications,
        route: Route,
        startedAt: Instant,
        distance: Double?,
    ) {
        notifications.show(Notifications.TRIP_ONGOING, notifications.trip(route, startedAt, distance))
    }

    private fun finish(state: TripSession.State?) {
        getSystemService(LocationManager::class.java)?.removeUpdates(listener)
        val route = route
        val session = session
        if (route != null && session != null && state is TripSession.State.Arrived) {
            val notifications = Notifications(this)
            scope.launch {
                val verdict =
                    container.routes.recordTrip(
                        routeId = route.id,
                        startedAt = session.startedAt,
                        walk = state.duration,
                        plannedLeaveAt = plannedLeaveAt,
                        arrival = if (state.manual) ArrivalSource.MANUAL else ArrivalSource.GEOFENCE,
                    )
                val minutes = state.duration.wholeMinutes()
                notifications.tripResult(
                    route,
                    when (verdict) {
                        TripVerdict.Accepted -> getString(R.string.trip_recorded, minutes)
                        is TripVerdict.Outlier -> getString(R.string.trip_outlier, minutes)
                        TripVerdict.Implausible -> getString(R.string.trip_implausible)
                    },
                )
                stop()
            }
        } else {
            if (route != null &&
                state == TripSession.State.TimedOut
            ) {
                Notifications(this).tripResult(route, getString(R.string.trip_timed_out))
            }
            stop()
        }
    }

    private fun stop() {
        measuring?.cancel()
        session = null
        container.statusBoard.setTripRoute(null)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        getSystemService(LocationManager::class.java)?.removeUpdates(listener)
        container.statusBoard.setTripRoute(null)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_START = "app.brokoli5191.zeitig.trip.START"
        private const val ACTION_ARRIVED = "app.brokoli5191.zeitig.trip.ARRIVED"
        private const val ACTION_CANCEL = "app.brokoli5191.zeitig.trip.CANCEL"
        private const val INTERVAL_MS = 5_000L
        private const val MIN_DISTANCE_M = 5f
        private const val TICK_MS = 15_000L

        fun startIntent(
            context: Context,
            routeId: Long,
        ): Intent = Intent(context, TripService::class.java).setAction(ACTION_START).putExtra(Intents.EXTRA_ROUTE_ID, routeId)

        fun arrivedIntent(context: Context): Intent = Intent(context, TripService::class.java).setAction(ACTION_ARRIVED)

        fun cancelIntent(context: Context): Intent = Intent(context, TripService::class.java).setAction(ACTION_CANCEL)

        /** From a visible activity (in-app "I'm leaving"). */
        fun start(
            context: Context,
            routeId: Long,
        ) = ContextCompat.startForegroundService(context, startIntent(context, routeId))
    }
}

package app.brokoli5191.zeitig.monitor

import android.app.Notification
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.brokoli5191.zeitig.ZeitigApplication
import app.brokoli5191.zeitig.alarms.WakeAlarms
import app.brokoli5191.zeitig.core.engine.LeaveOption
import app.brokoli5191.zeitig.core.engine.LeavePlan
import app.brokoli5191.zeitig.core.engine.Replanner
import app.brokoli5191.zeitig.core.monitor.MonitorDecision
import app.brokoli5191.zeitig.core.monitor.MonitorPlanner
import app.brokoli5191.zeitig.core.reminder.ReminderState
import app.brokoli5191.zeitig.core.route.Route
import app.brokoli5191.zeitig.core.schedule.ScheduleCalendar
import app.brokoli5191.zeitig.live.RouteStatuses
import app.brokoli5191.zeitig.notify.Intents
import app.brokoli5191.zeitig.notify.Notifications
import app.brokoli5191.zeitig.widget.ZeitigWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.Instant

/**
 * Foreground service (type specialUse) that runs while a route's window is active or the user
 * watches a route: keeps the countdown notification current, fires the 5/3/1/now reminders,
 * reports missed departures and new disruptions. It polls every 30 s only while the screen is on;
 * with the screen off it stays off the network except right before a reminder.
 */
class MonitoringService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null
    private val screenEvents = Channel<Unit>(Channel.CONFLATED)
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastOngoing: Notification? = null
    private lateinit var prefs: MonitorPrefs
    private lateinit var notifications: Notifications

    private val container get() = (application as ZeitigApplication).container

    private val screenReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                screenEvents.trySend(Unit)
            }
        }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = MonitorPrefs(this)
        notifications = Notifications(this)
        val filter = IntentFilter(Intent.ACTION_SCREEN_ON).apply { addAction(Intent.ACTION_USER_PRESENT) }
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val now = container.clock.instant()
        // STOP and UNWATCH arrive through startService(), so they need no startForeground().
        when (intent?.action) {
            ACTION_STOP -> {
                prefs.stopAll(now)
                stop()
                return START_NOT_STICKY
            }

            ACTION_UNWATCH -> {
                prefs.unwatch(intent.getLongExtra(Intents.EXTRA_ROUTE_ID, 0L), now)
                if (loop?.isActive == true) screenEvents.trySend(Unit) else stopSelf()
                return START_NOT_STICKY
            }
        }
        // Every startForegroundService() call must be answered with startForeground(); reuse the
        // current countdown so a second start does not flash the placeholder.
        ServiceCompat.startForeground(
            this,
            Notifications.ONGOING,
            lastOngoing ?: notifications.ongoing(null, 0, now, now),
            // The specialUse type exists from Android 14; earlier versions take the manifest's types.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        intent?.getLongExtra(Intents.EXTRA_ROUTE_ID, 0L)?.takeIf { it != 0L }?.let { prefs.watch(it, now) }
        container.statusBoard.setMonitoring(true)
        if (loop?.isActive != true) loop = scope.launch { run() }
        screenEvents.trySend(Unit)
        return START_NOT_STICKY
    }

    private suspend fun run() {
        val sessionStart = container.clock.instant()
        val planner = MonitorPlanner()
        val statuses = RouteStatuses(container)
        val calendar = ScheduleCalendar()
        val power = getSystemService(PowerManager::class.java)
        var reminderStates = emptyMap<Long, ReminderState>()
        val previousChosen = HashMap<Long, LeaveOption>()
        var last: MonitorDecision? = null
        while (currentCoroutineContext().isActive) {
            val now = container.clock.instant()
            val watched = prefs.watched(now)
            val candidates =
                container.routes.routes().filter {
                    it.notificationsEnabled &&
                        (it.id in watched || windowWanted(it, calendar, now))
                }
            val dueSoon = last?.nextWakeUp?.let { Duration.between(now, it) <= REFRESH_BEFORE_REMINDER } ?: true
            val interactive = power?.isInteractive != false
            val live = statuses.evaluateOnce(candidates, refresh = interactive || dueSoon)
            val statusList = live.mapNotNull { it.status }
            val decision =
                planner.decide(statusList, watched, reminderStates, sessionStart, now, awaitingData = statusList.size < candidates.size)
            reminderStates = decision.reminderStates
            announce(decision, previousChosen, now)
            showOngoing(decision, now, sessionStart)
            container.statusBoard.publish(live, decision.active.mapTo(HashSet()) { it.route.id })
            ZeitigWidget.refresh(this)
            if (decision.stop) {
                prefs.clearWatched()
                stop()
                return
            }
            last = decision
            waitForNext(decision.nextWakeUp, interactive, power)
        }
    }

    /** A window-active route runs unless the user stopped the countdown after this window began. */
    private fun windowWanted(
        route: Route,
        calendar: ScheduleCalendar,
        now: Instant,
    ): Boolean {
        val occurrence = route.schedule?.let { calendar.current(it, now, route.journey.plannedTotal) } ?: return false
        return prefs.allows(route.id, occurrence.start)
    }

    private fun announce(
        decision: MonitorDecision,
        previousChosen: MutableMap<Long, LeaveOption>,
        now: Instant,
    ) {
        val tripRoute = container.statusBoard.tripRoute.value
        val fresh = prefs.unannounced(decision.active.flatMap { status -> status.disruptions.map { it.id } }, now).toMutableSet()
        for (status in decision.active) {
            val route = status.route
            val chosen = (status.plan as? LeavePlan.Go)?.chosen
            if (route.id != tripRoute) {
                // Only the latest crossed threshold: after a gap (Doze, process restart) one alert, not three.
                decision.reminders[route.id]?.lastOrNull()?.let { notifications.reminder(route, it.option, it.minutesBefore) }
                Replanner.missed(previousChosen[route.id], status.plan, now, tripStarted = false)?.let {
                    notifications.missed(route, it.next, now)
                }
            }
            if (chosen != null) previousChosen[route.id] = chosen else previousChosen.remove(route.id)
            status.disruptions.filter { fresh.remove(it.id) }.forEach { notifications.disruption(route, it) }
        }
    }

    private fun showOngoing(
        decision: MonitorDecision,
        now: Instant,
        sessionStart: Instant,
    ) {
        val notification = notifications.ongoing(decision.primary, (decision.active.size - 1).coerceAtLeast(0), now, sessionStart)
        lastOngoing = notification
        notifications.show(Notifications.ONGOING, notification)
    }

    /** Sleeps until the next poll (screen on), the next reminder, a screen-on event or an alarm ping. */
    private suspend fun waitForNext(
        nextWakeUp: Instant?,
        interactive: Boolean,
        power: PowerManager?,
    ) {
        val now = container.clock.instant()
        val pollAt = if (interactive) now + POLL_INTERVAL else null
        val target = listOfNotNull(pollAt, nextWakeUp).minOrNull() ?: (now + IDLE_CHECK)
        val wait = Duration.between(now, target).coerceAtLeast(Duration.ofSeconds(1))
        WakeAlarms.schedule(this, nextWakeUp)
        // In the last minutes before a reminder keep the CPU awake so the reminder is not late.
        if (power != null && nextWakeUp != null && Duration.between(now, nextWakeUp) <= WAKE_LOCK_WINDOW) {
            holdWakeLock(power, Duration.between(now, nextWakeUp) + WAKE_LOCK_SLACK)
        }
        try {
            withTimeoutOrNull(wait.toMillis()) { merge(screenEvents.receiveAsFlow(), container.statusBoard.wakeUps).first() }
        } finally {
            releaseWakeLock()
        }
    }

    private fun holdWakeLock(
        power: PowerManager,
        timeout: Duration,
    ) {
        val lock = wakeLock ?: power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "zeitig:reminder").also { wakeLock = it }
        if (!lock.isHeld) lock.acquire(timeout.coerceAtMost(WAKE_LOCK_WINDOW + WAKE_LOCK_SLACK).toMillis())
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
    }

    private fun stop() {
        loop?.cancel()
        releaseWakeLock()
        WakeAlarms.cancel(this)
        lastOngoing = null
        container.statusBoard.setMonitoring(false)
        ZeitigWidget.refresh(this)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        unregisterReceiver(screenReceiver)
        releaseWakeLock()
        container.statusBoard.setMonitoring(false)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_START = "app.brokoli5191.zeitig.monitor.START"
        private const val ACTION_STOP = "app.brokoli5191.zeitig.monitor.STOP"
        private const val ACTION_UNWATCH = "app.brokoli5191.zeitig.monitor.UNWATCH"
        private val POLL_INTERVAL: Duration = Duration.ofSeconds(30)
        private val IDLE_CHECK: Duration = Duration.ofMinutes(5)
        private val WAKE_LOCK_WINDOW: Duration = Duration.ofMinutes(10)
        private val WAKE_LOCK_SLACK: Duration = Duration.ofSeconds(90)
        private val REFRESH_BEFORE_REMINDER: Duration = Duration.ofSeconds(45)

        fun startIntent(
            context: Context,
            routeId: Long? = null,
        ): Intent =
            Intent(context, MonitoringService::class.java).setAction(ACTION_START).apply {
                routeId?.let { putExtra(Intents.EXTRA_ROUTE_ID, it) }
            }

        fun stopIntent(context: Context): Intent = Intent(context, MonitoringService::class.java).setAction(ACTION_STOP)

        fun unwatchIntent(
            context: Context,
            routeId: Long,
        ): Intent = Intent(context, MonitoringService::class.java).setAction(ACTION_UNWATCH).putExtra(Intents.EXTRA_ROUTE_ID, routeId)

        /** Starts or updates the service; callers must be allowed to start a foreground service. */
        fun start(
            context: Context,
            routeId: Long? = null,
        ) = ContextCompat.startForegroundService(context, startIntent(context, routeId))
    }
}

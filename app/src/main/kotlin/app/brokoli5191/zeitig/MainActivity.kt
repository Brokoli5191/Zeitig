package app.brokoli5191.zeitig

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withStarted
import app.brokoli5191.zeitig.core.schedule.ScheduleCalendar
import app.brokoli5191.zeitig.data.reference.RefreshResult
import app.brokoli5191.zeitig.locale.AppLanguages
import app.brokoli5191.zeitig.monitor.MonitorPrefs
import app.brokoli5191.zeitig.monitor.MonitoringService
import app.brokoli5191.zeitig.notify.Intents
import app.brokoli5191.zeitig.notify.Notifications
import app.brokoli5191.zeitig.trip.TripService
import app.brokoli5191.zeitig.ui.navigation.PermissionRequests
import app.brokoli5191.zeitig.ui.navigation.ZeitigNavHost
import app.brokoli5191.zeitig.ui.theme.LocalDarkTheme
import app.brokoli5191.zeitig.ui.theme.ZeitigTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class MainActivity :
    ComponentActivity(),
    PermissionRequests {
    private var pendingLocationResult: ((Boolean) -> Unit)? = null
    private var pendingNotificationResult: ((Boolean) -> Unit)? = null
    private val openRoute = Channel<Long>(Channel.BUFFERED)
    private val openRouteRequests = openRoute.receiveAsFlow()
    private val importRoute = Channel<String>(Channel.BUFFERED)
    private val importRequests = importRoute.receiveAsFlow()

    private val locationPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            pendingLocationResult?.invoke(result.values.any { it })
            pendingLocationResult = null
        }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            pendingNotificationResult?.invoke(granted)
            pendingNotificationResult = null
        }

    private val container get() = (application as ZeitigApplication).container

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguages.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // After a configuration change the back stack is restored; handling the intent again would
        // open the same route or import twice.
        if (savedInstanceState == null) handle(intent)
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            val appearance = settings?.appearance ?: return@setContent
            ZeitigTheme(appearance) {
                val dark = LocalDarkTheme.current
                DisposableEffect(dark) {
                    // System bar icons follow the app's theme, not only the system's.
                    val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                    onDispose {}
                }
                ZeitigNavHost(container, permissions = this, openRouteRequests = openRouteRequests, importRequests = importRequests)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        sharedRoute(intent)?.let {
            importRoute.trySend(it)
            return
        }
        val routeId = intent?.getLongExtra(Intents.EXTRA_ROUTE_ID, 0L)?.takeIf { it != 0L } ?: return
        openRoute.trySend(routeId)
        if (intent.action == Intents.ACTION_WATCH) {
            lifecycleScope.launch { lifecycle.withStarted { MonitoringService.start(this@MainActivity, routeId) } }
        }
        if (intent.action == Intents.ACTION_START_TRIP) {
            // A location service may only start while the activity is visible.
            lifecycleScope.launch {
                lifecycle.withStarted {
                    location { granted -> if (granted) TripService.start(this@MainActivity, routeId) }
                }
            }
        }
    }

    /** A route or place shared from a map app: share sheet text, or a geo: link opened with Zeitig. */
    private fun sharedRoute(intent: Intent?): String? =
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                listOfNotNull(intent.getStringExtra(Intent.EXTRA_SUBJECT), intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString())
                    .joinToString("\n")
                    .takeIf { it.isNotBlank() }
            }

            Intent.ACTION_VIEW -> {
                intent.data?.takeIf { it.scheme == "geo" }?.toString()
            }

            else -> {
                null
            }
        }

    override fun onStart() {
        super.onStart()
        // At most weekly and only on unmetered networks: refresh the bundled stop data.
        val connectivity = getSystemService(android.net.ConnectivityManager::class.java)
        if (connectivity?.isActiveNetworkMetered == false) {
            container.applicationScope.launch {
                if (container.referenceRefresher.refreshIfDue() is RefreshResult.Updated) container.referenceData.invalidate()
            }
        }
        // Without exact alarms a window cannot start the countdown by itself; opening the app can.
        // A window the user already stopped stays stopped.
        lifecycleScope.launch {
            val now = container.clock.instant()
            val calendar = ScheduleCalendar()
            val prefs = MonitorPrefs(this@MainActivity)
            val wanted =
                container.routes.routes().any { route ->
                    val occurrence = route.schedule?.let { calendar.current(it, now, route.journey.plannedTotal) }
                    route.notificationsEnabled && occurrence != null && prefs.allows(route.id, occurrence.start)
                }
            if (wanted && !container.statusBoard.monitoring.value && hasNotificationPermission()) {
                lifecycle.withStarted { MonitoringService.start(this@MainActivity) }
            }
        }
    }

    override fun location(onResult: (Boolean) -> Unit) {
        if (Intents.hasLocation(this)) {
            onResult(true)
            return
        }
        pendingLocationResult = onResult
        locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    override fun notifications(onResult: (Boolean) -> Unit) {
        if (hasNotificationPermission()) {
            onResult(true)
            return
        }
        // Below Android 13 there is no runtime permission: only the user's switch in the settings.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            onResult(false)
            return
        }
        pendingNotificationResult = onResult
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun hasNotificationPermission(): Boolean = Notifications(this).canPost()
}

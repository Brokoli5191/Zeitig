package app.brokoli5191.zeitig.tile

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.brokoli5191.zeitig.MainActivity
import app.brokoli5191.zeitig.R
import app.brokoli5191.zeitig.ZeitigApplication
import app.brokoli5191.zeitig.core.engine.LeavePlan
import app.brokoli5191.zeitig.monitor.MonitoringService
import app.brokoli5191.zeitig.notify.Intents
import app.brokoli5191.zeitig.ui.format.minutesUntil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Quick Settings tile: shows the most relevant route and starts or stops the live countdown. Uses
 * only what is already known (no network from the tile).
 */
class ZeitigTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val container get() = (application as ZeitigApplication).container

    override fun onStartListening() {
        super.onStartListening()
        scope.launch { render() }
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            if (container.statusBoard.monitoring.value) {
                startService(MonitoringService.stopIntent(this@ZeitigTileService))
            } else {
                val route = container.routes.routes().firstOrNull { it.notificationsEnabled }
                when {
                    route == null -> openApp(null)

                    // Whether a tile tap may start a foreground service from the background is not
                    // documented; if the system refuses, the app opens and starts it visibly.
                    runCatching { MonitoringService.start(this@ZeitigTileService, route.id) }.isFailure -> openApp(route.id)
                }
            }
            render()
        }
    }

    private suspend fun render() {
        val tile = qsTile ?: return
        val monitoring = container.statusBoard.monitoring.value
        val now = container.clock.instant()
        val live =
            container.statusBoard.routes.value
                .firstNotNullOfOrNull { live -> (live.status?.plan as? LeavePlan.Go)?.chosen?.let { live.route to it } }
        tile.label = live?.first?.name ?: container.routes.routes().firstOrNull()?.name ?: getString(R.string.app_name)
        val subtitle =
            when {
                live != null -> {
                    val minutes = minutesUntil(now, live.second.leaveAt)
                    if (minutes <=
                        0
                    ) {
                        getString(R.string.leave_now_badge)
                    } else {
                        getString(R.string.tile_leave_in, live.second.departure.line, minutes)
                    }
                }

                monitoring -> {
                    getString(R.string.tile_watching)
                }

                else -> {
                    getString(R.string.tile_start)
                }
            }
        // Below Android 10 tiles have no subtitle; the label alone has to do.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = subtitle
        tile.state = if (monitoring) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    private fun openApp(watchRouteId: Long?) {
        val intent =
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).apply {
                if (watchRouteId != null) setAction(Intents.ACTION_WATCH).putExtra(Intents.EXTRA_ROUTE_ID, watchRouteId)
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

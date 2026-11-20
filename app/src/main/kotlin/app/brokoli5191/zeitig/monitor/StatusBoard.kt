package app.brokoli5191.zeitig.monitor

import app.brokoli5191.zeitig.live.LiveRoute
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-process board of the latest route statuses computed by the monitoring service, read by the
 * widget and the Quick Settings tile. Also carries wake-up pings from alarms to the running service.
 */
class StatusBoard {
    private val _routes = MutableStateFlow<List<LiveRoute>>(emptyList())
    val routes: StateFlow<List<LiveRoute>> = _routes.asStateFlow()

    private val _monitoring = MutableStateFlow(false)
    val monitoring: StateFlow<Boolean> = _monitoring.asStateFlow()

    private val _active = MutableStateFlow<Set<Long>>(emptySet())

    /** Routes the running countdown covers right now (empty while it is not running). */
    val active: StateFlow<Set<Long>> = _active.asStateFlow()

    private val _tripRoute = MutableStateFlow<Long?>(null)

    /** The route whose walk is being measured right now, if any. */
    val tripRoute: StateFlow<Long?> = _tripRoute.asStateFlow()

    private val _wakeUps = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val wakeUps: SharedFlow<Unit> = _wakeUps.asSharedFlow()

    fun publish(
        routes: List<LiveRoute>,
        active: Set<Long>,
    ) {
        _routes.value = routes
        _active.value = active
    }

    fun setMonitoring(running: Boolean) {
        _monitoring.value = running
        if (!running) _active.value = emptySet()
    }

    fun setTripRoute(routeId: Long?) {
        _tripRoute.value = routeId
    }

    /** Returns false when no service listens (process was just started by the alarm). */
    fun wakeUp(): Boolean = _wakeUps.tryEmit(Unit) && _monitoring.value
}

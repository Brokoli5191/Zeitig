package app.brokoli5191.zeitig.ui.route

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.brokoli5191.zeitig.AppContainer
import app.brokoli5191.zeitig.live.RouteStatuses
import app.brokoli5191.zeitig.ui.format.VIENNA
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RouteDetailViewModel(
    private val container: AppContainer,
    private val routeId: Long,
) : ViewModel() {
    private val route = container.routes.observeRoute(routeId)
    private val seconds =
        flow {
            while (true) {
                emit(container.clock.instant())
                delay(1_000)
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<RouteDetailUiState> =
        combine(
            route,
            RouteStatuses(container).observe(route.filterNotNull().map { listOf(it) }).onStart { emit(emptyList()) },
            container.routes.observeTrips(routeId),
            seconds,
        ) { route, live, trips, now ->
            val current = live.firstOrNull { it.route.id == routeId }
            RouteDetailUiState(
                loading = route == null,
                route = route,
                status = current?.status,
                failure = current?.failure,
                stats = container.statistics.compute(trips, VIENNA),
                now = now,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RouteDetailUiState())

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            container.routes.delete(routeId)
            onDeleted()
        }
    }

    fun resetLearning() {
        viewModelScope.launch { container.routes.resetLearning(routeId) }
    }
}

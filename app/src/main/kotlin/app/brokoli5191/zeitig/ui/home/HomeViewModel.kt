package app.brokoli5191.zeitig.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.brokoli5191.zeitig.AppContainer
import app.brokoli5191.zeitig.live.RouteStatuses
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(
    container: AppContainer,
) : ViewModel() {
    private val clock = container.clock
    private val seconds =
        flow {
            while (true) {
                emit(clock.instant())
                delay(1_000)
            }
        }

    val state: StateFlow<HomeUiState> =
        combine(
            RouteStatuses(container).observe(container.routes.observeRoutes()).onStart { emit(emptyList()) },
            container.routes.observeRoutes(),
            seconds,
        ) { live, routes, now ->
            val byId = live.associateBy { it.route.id }
            HomeUiState(
                loading = false,
                routes =
                    routes.map { route ->
                        byId[route.id]?.let { RouteCardUi(route, it.status, it.failure) } ?: RouteCardUi(route, null)
                    },
                now = now,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState())

    private companion object {
        // Keep polling through configuration changes, stop shortly after the screen is gone.
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

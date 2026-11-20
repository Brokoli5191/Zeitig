package app.brokoli5191.zeitig.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.brokoli5191.zeitig.AppContainer
import app.brokoli5191.zeitig.data.reference.RefreshResult
import app.brokoli5191.zeitig.data.settings.AppSettings
import app.brokoli5191.zeitig.data.settings.Appearance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

data class DataStatus(
    val lastCheck: Instant? = null,
    val refreshing: Boolean = false,
    val lastResult: RefreshResult? = null,
)

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val data: DataStatus = DataStatus(),
)

class SettingsViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val data = MutableStateFlow(DataStatus(lastCheck = container.referenceRefresher.lastCheck()))

    val state: StateFlow<SettingsUiState> =
        combine(container.settings.settings, data) { settings, data -> SettingsUiState(settings, data) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { container.settings.update(transform) }
    }

    fun updateAppearance(transform: (Appearance) -> Appearance) = update { it.copy(appearance = transform(it.appearance)) }

    fun refreshData() {
        if (data.value.refreshing) return
        data.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            val result = container.referenceRefresher.refreshIfDue(force = true)
            if (result is RefreshResult.Updated) container.referenceData.invalidate()
            data.update { DataStatus(container.referenceRefresher.lastCheck(), refreshing = false, lastResult = result) }
        }
    }
}

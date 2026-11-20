package app.brokoli5191.zeitig.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.brokoli5191.zeitig.R
import app.brokoli5191.zeitig.data.reference.RefreshResult
import app.brokoli5191.zeitig.data.settings.AppSettings
import app.brokoli5191.zeitig.data.settings.DistanceUnit
import app.brokoli5191.zeitig.ui.components.ConnectedChoice
import app.brokoli5191.zeitig.ui.components.RowItem
import app.brokoli5191.zeitig.ui.components.SectionHeader
import app.brokoli5191.zeitig.ui.components.ValueSlider
import app.brokoli5191.zeitig.ui.format.formatClock
import app.brokoli5191.zeitig.ui.format.formatDate
import app.brokoli5191.zeitig.ui.format.uiLocale
import app.brokoli5191.zeitig.ui.theme.rememberHaptics
import java.util.Locale
import kotlin.math.roundToInt

data class SettingsActions(
    val onBack: () -> Unit = {},
    val onAppearance: () -> Unit = {},
    val onAbout: () -> Unit = {},
    val onUpdate: ((AppSettings) -> AppSettings) -> Unit = {},
    val onRefreshData: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val settings = state.settings
    val haptics = rememberHaptics()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            NavRow(
                R.drawable.ic_palette,
                stringResource(R.string.appearance),
                stringResource(R.string.appearance_summary),
                actions.onAppearance,
            )

            SectionHeader(stringResource(R.string.section_routing))
            SwitchRow(
                icon = R.drawable.ic_route,
                title = stringResource(R.string.routing_online),
                summary = stringResource(R.string.routing_online_summary),
                checked = settings.routingEnabled,
                onChange = {
                    haptics.toggle(it)
                    actions.onUpdate { s -> s.copy(routingEnabled = it) }
                },
            )
            SwitchRow(
                icon = R.drawable.ic_map,
                title = stringResource(R.string.map_tiles),
                summary = stringResource(R.string.map_tiles_summary),
                checked = settings.mapTilesEnabled,
                onChange = {
                    haptics.toggle(it)
                    actions.onUpdate { s -> s.copy(mapTilesEnabled = it) }
                },
            )
            SwitchRow(
                icon = R.drawable.ic_accessible,
                title = stringResource(R.string.accessible_default),
                summary = stringResource(R.string.accessible_default_summary),
                checked = settings.accessibleByDefault,
                onChange = {
                    haptics.toggle(it)
                    actions.onUpdate { s -> s.copy(accessibleByDefault = it) }
                },
            )

            SectionHeader(stringResource(R.string.section_walking))
            SliderRow(
                title = stringResource(R.string.walk_speed, String.format(uiLocale(), "%.1f", settings.walkSpeed)),
                value = settings.walkSpeed.toFloat(),
                range = 0.8f..1.8f,
                steps = 9,
                onChange = { v -> actions.onUpdate { it.copy(walkSpeed = (v * 10).roundToInt() / 10.0) } },
            )
            SliderRow(
                title = stringResource(R.string.detour_factor, String.format(uiLocale(), "%.2f", settings.detourFactor)),
                value = settings.detourFactor.toFloat(),
                range = 1.1f..1.6f,
                steps = 9,
                onChange = { v -> actions.onUpdate { it.copy(detourFactor = (v * 20).roundToInt() / 20.0) } },
            )
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.units), style = MaterialTheme.typography.bodyLarge)
                ConnectedChoice(
                    options = DistanceUnit.entries,
                    selected = settings.units,
                    label = { stringResource(if (it == DistanceUnit.METRIC) R.string.units_metric else R.string.units_imperial) },
                    onSelect = { unit -> actions.onUpdate { it.copy(units = unit) } },
                )
            }

            SectionHeader(stringResource(R.string.section_reminders))
            SliderRow(
                title = stringResource(R.string.default_buffer, settings.defaultBufferSeconds / 60),
                value = (settings.defaultBufferSeconds / 60).toFloat(),
                range = 0f..10f,
                steps = 9,
                onChange = { v -> actions.onUpdate { it.copy(defaultBufferSeconds = v.roundToInt() * 60) } },
            )

            SectionHeader(stringResource(R.string.section_data))
            RowItem(
                headlineContent = { Text(stringResource(R.string.data_refresh)) },
                supportingContent = { Text(dataSummary(state.data)) },
                leadingContent = { Icon(painterResource(R.drawable.ic_refresh), contentDescription = null) },
                modifier = Modifier.clickable(role = Role.Button, enabled = !state.data.refreshing, onClick = actions.onRefreshData),
            )
            NavRow(R.drawable.ic_info, stringResource(R.string.about), stringResource(R.string.about_summary), actions.onAbout)
        }
    }
}

@Composable
private fun dataSummary(data: DataStatus): String {
    val locale: Locale = uiLocale()
    return when {
        data.refreshing -> {
            stringResource(R.string.data_refreshing)
        }

        data.lastResult is RefreshResult.Updated -> {
            stringResource(R.string.data_updated)
        }

        data.lastResult is RefreshResult.UpToDate -> {
            stringResource(R.string.data_up_to_date)
        }

        data.lastResult is RefreshResult.Failed -> {
            stringResource(R.string.data_refresh_failed)
        }

        data.lastCheck != null -> {
            stringResource(
                R.string.data_last_check,
                formatDate(data.lastCheck, locale),
                formatClock(data.lastCheck, locale),
            )
        }

        else -> {
            stringResource(R.string.data_bundled)
        }
    }
}

@Composable
fun NavRow(
    icon: Int,
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    RowItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) },
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    )
}

@Composable
fun SwitchRow(
    icon: Int?,
    title: String,
    summary: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    RowItem(
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        leadingContent = icon?.let { { Icon(painterResource(it), contentDescription = null) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.clickable(role = Role.Switch) { onChange(!checked) },
    )
}

@Composable
private fun SliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        ValueSlider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
    }
}

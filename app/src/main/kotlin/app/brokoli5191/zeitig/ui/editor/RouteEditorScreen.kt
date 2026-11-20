package app.brokoli5191.zeitig.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.brokoli5191.zeitig.R
import app.brokoli5191.zeitig.core.journey.Journey
import app.brokoli5191.zeitig.core.routing.Itinerary
import app.brokoli5191.zeitig.ui.components.ConnectedChoice
import app.brokoli5191.zeitig.ui.components.LineBadge
import app.brokoli5191.zeitig.ui.components.MultiToggle
import app.brokoli5191.zeitig.ui.components.RowItem
import app.brokoli5191.zeitig.ui.components.SectionCard
import app.brokoli5191.zeitig.ui.components.ValueSlider
import app.brokoli5191.zeitig.ui.expressive.DelayedLoadingIndicator
import app.brokoli5191.zeitig.ui.format.clockTime
import app.brokoli5191.zeitig.ui.format.uiLocale
import app.brokoli5191.zeitig.ui.format.wholeMinutes
import app.brokoli5191.zeitig.ui.route.message
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteEditorScreen(
    state: EditorUiState,
    actions: EditorActions,
    modifier: Modifier = Modifier,
    map: @Composable (Journey, Modifier) -> Unit = { _, _ -> },
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.routeId == 0L) R.string.route_new else R.string.route_edit)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.cancel))
                    }
                },
                actions = { TextButton(onClick = actions.onSave, enabled = state.canSave) { Text(stringResource(R.string.save)) } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = actions.onName,
                label = { Text(stringResource(R.string.route_name)) },
                placeholder = { Text(stringResource(R.string.route_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.routingEnabled) PlannedRouteSection(state, actions, map) else SimpleRouteSection(state, actions)
            WhenSection(state, actions)
            RemindersSection(state, actions)
        }
    }
    state.search?.let { PlaceSearchDialog(it, actions) }
}

/** All editor callbacks in one object, so the screen stays stateless and previewable. */
data class EditorActions(
    val onBack: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onName: (String) -> Unit = {},
    val onPick: (PlaceTarget) -> Unit = {},
    val onPlan: () -> Unit = {},
    val onSelect: (Int) -> Unit = {},
    val onLine: (app.brokoli5191.zeitig.core.network.LineAtStop) -> Unit = {},
    val onTimeMode: (TimeMode) -> Unit = {},
    val onDays: (Set<DayOfWeek>) -> Unit = {},
    val onWindowStart: (LocalTime) -> Unit = {},
    val onWindowEnd: (LocalTime) -> Unit = {},
    val onArriveBy: (LocalTime) -> Unit = {},
    val onReminder: (Int) -> Unit = {},
    val onBuffer: (Int) -> Unit = {},
    val onAccessible: (Boolean) -> Unit = {},
    val onNotifications: (Boolean) -> Unit = {},
    val onQuery: (String) -> Unit = {},
    val onStation: (app.brokoli5191.zeitig.core.search.StationMatch) -> Unit = {},
    val onAddress: (app.brokoli5191.zeitig.core.routing.GeocodeMatch) -> Unit = {},
    val onMyLocation: () -> Unit = {},
    val onCoordinates: (String) -> Boolean = { false },
    val onCloseSearch: () -> Unit = {},
)

@Composable
private fun PlaceField(
    label: String,
    place: PlaceUi?,
    icon: Int,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(place?.name ?: stringResource(R.string.place_choose), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun PlannedRouteSection(
    state: EditorUiState,
    actions: EditorActions,
    map: @Composable (Journey, Modifier) -> Unit,
) {
    SectionCard(title = stringResource(R.string.section_way)) {
        PlaceField(stringResource(R.string.place_from), state.origin, R.drawable.ic_home) { actions.onPick(PlaceTarget.ORIGIN) }
        PlaceField(stringResource(R.string.place_to), state.destination, R.drawable.ic_flag) { actions.onPick(PlaceTarget.DESTINATION) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.accessible_routing), modifier = Modifier.weight(1f))
            Switch(checked = state.accessible, onCheckedChange = actions.onAccessible)
        }
        Button(onClick = actions.onPlan, enabled = state.canSearch, modifier = Modifier.fillMaxWidth()) {
            Icon(painterResource(R.drawable.ic_search), contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.plan_search))
        }
        if (state.planning) DelayedLoadingIndicator(Modifier.align(Alignment.CenterHorizontally))
        state.planFailure?.let { Text(stringResource(it.message()), color = MaterialTheme.colorScheme.error) }
        state.itineraries.forEachIndexed { index, itinerary ->
            ItineraryOption(itinerary, selected = index == state.selected, onClick = { actions.onSelect(index) })
        }
        state.selected?.let { state.itineraries.getOrNull(it) }?.let {
            map(it.journey, Modifier.fillMaxWidth().height(200.dp))
        }
        Text(
            stringResource(R.string.attribution_routing),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ItineraryOption(
    itinerary: Itinerary,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(if (selected) 24.dp else 16.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (itinerary.departure != Instant.EPOCH) {
                Text(
                    stringResource(
                        R.string.itinerary_times,
                        clockTime(itinerary.departure),
                        clockTime(itinerary.arrival),
                        itinerary.duration.wholeMinutes(),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
            } else {
                Text(stringResource(R.string.itinerary_saved), style = MaterialTheme.typography.titleMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                itinerary.journey.transitLegs.forEach { LineBadge(it.line, it.type, plannerColor = it.color) }
                Text(
                    if (itinerary.transfers ==
                        0
                    ) {
                        stringResource(R.string.direct)
                    } else {
                        pluralStringResource(R.plurals.transfers, itinerary.transfers, itinerary.transfers)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SimpleRouteSection(
    state: EditorUiState,
    actions: EditorActions,
) {
    SectionCard(title = stringResource(R.string.section_way)) {
        Text(
            stringResource(R.string.simple_route_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PlaceField(stringResource(R.string.place_from_optional), state.origin, R.drawable.ic_home) { actions.onPick(PlaceTarget.ORIGIN) }
        PlaceField(stringResource(R.string.place_stop), state.boarding, R.drawable.ic_location_on) { actions.onPick(PlaceTarget.BOARDING) }
        if (state.boardingLines.isNotEmpty()) {
            Text(stringResource(R.string.choose_line), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.boardingLines.forEach { line ->
                    FilterChip(
                        selected = line == state.boardingLine,
                        onClick = { actions.onLine(line) },
                        leadingIcon = { LineBadge(line.line.name, line.line.type) },
                        label = { Text(stringResource(R.string.towards, line.towards)) },
                    )
                }
            }
        } else if (state.boarding != null) {
            Text(stringResource(R.string.no_lines_at_stop), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhenSection(
    state: EditorUiState,
    actions: EditorActions,
) {
    val locale = uiLocale()
    SectionCard(title = stringResource(R.string.section_when)) {
        ConnectedChoice(
            options = TimeMode.entries,
            selected = state.timeMode,
            label = {
                stringResource(
                    when (it) {
                        TimeMode.WINDOW -> R.string.time_mode_window
                        TimeMode.ARRIVE_BY -> R.string.time_mode_arrive
                        TimeMode.NONE -> R.string.time_mode_none
                    },
                )
            },
            onSelect = actions.onTimeMode,
        )
        if (state.timeMode != TimeMode.NONE) {
            MultiToggle(
                options = DayOfWeek.entries,
                selected = state.days,
                label = { it.getDisplayName(TextStyle.SHORT_STANDALONE, locale).trimEnd('.') },
                onChange = actions.onDays,
            )
            Text(
                pluralStringResource(R.plurals.trips_per_week, state.days.size, state.days.size),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        when (state.timeMode) {
            TimeMode.WINDOW -> {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TimeField(stringResource(R.string.window_from), state.windowStart, actions.onWindowStart, Modifier.weight(1f))
                    TimeField(stringResource(R.string.window_to), state.windowEnd, actions.onWindowEnd, Modifier.weight(1f))
                }
            }

            TimeMode.ARRIVE_BY -> {
                TimeField(stringResource(R.string.arrive_by), state.arriveBy, actions.onArriveBy, Modifier.fillMaxWidth())
            }

            TimeMode.NONE -> {
                Text(stringResource(R.string.time_mode_none_hint), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeField(
    label: String,
    time: LocalTime,
    onTime: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    Surface(
        modifier = modifier.clickable(role = Role.Button) { open = true },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(DateTimeFormatter.ofPattern("HH:mm").format(time), style = MaterialTheme.typography.headlineSmall)
        }
    }
    if (open) {
        val pickerState = rememberTimePickerState(time.hour, time.minute, is24Hour = true)
        TimePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    open = false
                    onTime(LocalTime.of(pickerState.hour, pickerState.minute))
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) } },
            title = { Text(label) },
        ) { TimePicker(pickerState) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RemindersSection(
    state: EditorUiState,
    actions: EditorActions,
) {
    SectionCard(title = stringResource(R.string.section_reminders)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.notifications_for_route), modifier = Modifier.weight(1f))
            Switch(checked = state.notifications, onCheckedChange = actions.onNotifications)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            REMINDER_CHOICES.forEach { minutes ->
                FilterChip(
                    selected = minutes in state.reminders,
                    onClick = { actions.onReminder(minutes) },
                    label = {
                        Text(
                            if (minutes ==
                                0
                            ) {
                                stringResource(R.string.reminder_now)
                            } else {
                                stringResource(R.string.reminder_minutes, minutes)
                            },
                        )
                    },
                    enabled = state.notifications,
                )
            }
        }
        Text(stringResource(R.string.buffer_minutes, state.bufferMinutes), style = MaterialTheme.typography.bodyLarge)
        ValueSlider(
            value = state.bufferMinutes.toFloat(),
            onValueChange = { actions.onBuffer(it.toInt()) },
            valueRange = 0f..10f,
            steps = 9,
        )
        Text(
            stringResource(R.string.buffer_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val REMINDER_CHOICES = listOf(15, 10, 5, 3, 2, 1, 0)

@Composable
private fun PlaceSearchDialog(
    search: PlaceSearchUi,
    actions: EditorActions,
) {
    var coordinates by rememberSaveable { mutableStateOf("") }
    var coordinatesInvalid by rememberSaveable { mutableStateOf(false) }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = actions.onCloseSearch,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(
                Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = actions.onCloseSearch) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                    OutlinedTextField(
                        value = search.query,
                        onValueChange = actions.onQuery,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = {
                            Text(
                                stringResource(
                                    if (search.target ==
                                        PlaceTarget.BOARDING
                                    ) {
                                        R.string.search_stop_hint
                                    } else {
                                        R.string.search_place_hint
                                    },
                                ),
                            )
                        },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                    )
                }
                if (search.target != PlaceTarget.BOARDING) {
                    RowItem(
                        headlineContent = { Text(stringResource(R.string.my_location_once)) },
                        supportingContent = {
                            when {
                                search.locating -> Text(stringResource(R.string.locating))
                                search.locationFailed -> Text(stringResource(R.string.location_failed))
                                else -> Text(stringResource(R.string.my_location_hint))
                            }
                        },
                        leadingContent = { Icon(painterResource(R.drawable.ic_my_location), contentDescription = null) },
                        modifier = Modifier.clickable(role = Role.Button, onClick = actions.onMyLocation),
                    )
                }
                if (search.stations.isNotEmpty()) {
                    Text(
                        stringResource(R.string.search_stops),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                search.stations.forEach { match ->
                    RowItem(
                        headlineContent = { Text(match.station.name) },
                        leadingContent = { Icon(painterResource(R.drawable.ic_location_on), contentDescription = null) },
                        modifier = Modifier.clickable(role = Role.Button) { actions.onStation(match) },
                    )
                }
                if (search.searchingOnline) DelayedLoadingIndicator(Modifier.align(Alignment.CenterHorizontally))
                if (search.addresses.isNotEmpty()) {
                    Text(
                        stringResource(R.string.search_addresses),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                search.addresses.forEach { match ->
                    RowItem(
                        headlineContent = { Text(match.name) },
                        supportingContent = {
                            listOfNotNull(match.postalCode, match.area).joinToString(" ").takeIf { it.isNotBlank() }?.let { Text(it) }
                        },
                        leadingContent = { Icon(painterResource(R.drawable.ic_pin_drop), contentDescription = null) },
                        modifier = Modifier.clickable(role = Role.Button) { actions.onAddress(match) },
                    )
                }
                if (search.target != PlaceTarget.BOARDING) {
                    Text(
                        stringResource(R.string.coordinates_manual),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = coordinates,
                            onValueChange = {
                                coordinates = it
                                coordinatesInvalid = false
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            isError = coordinatesInvalid,
                            placeholder = { Text("48.2082, 16.3738") },
                            supportingText = { if (coordinatesInvalid) Text(stringResource(R.string.coordinates_invalid)) },
                        )
                        TextButton(onClick = {
                            coordinatesInvalid = !actions.onCoordinates(coordinates)
                        }) { Text(stringResource(R.string.ok)) }
                    }
                }
            }
        }
    }
}

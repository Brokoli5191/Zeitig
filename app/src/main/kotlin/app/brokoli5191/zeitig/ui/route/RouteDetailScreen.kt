package app.brokoli5191.zeitig.ui.route

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.brokoli5191.zeitig.R
import app.brokoli5191.zeitig.core.provider.FailureReason
import app.brokoli5191.zeitig.core.route.Route
import app.brokoli5191.zeitig.core.route.RouteStatus
import app.brokoli5191.zeitig.core.stats.RouteStats
import app.brokoli5191.zeitig.core.stats.Trend
import app.brokoli5191.zeitig.core.walk.WalkTimeSource
import app.brokoli5191.zeitig.ui.components.DisruptionBanner
import app.brokoli5191.zeitig.ui.components.HeroNumber
import app.brokoli5191.zeitig.ui.components.LegTimeline
import app.brokoli5191.zeitig.ui.components.RealtimeIndicator
import app.brokoli5191.zeitig.ui.components.SectionCard
import app.brokoli5191.zeitig.ui.expressive.DelayedLoadingIndicator
import app.brokoli5191.zeitig.ui.format.clockTime
import app.brokoli5191.zeitig.ui.format.minutesUntil
import app.brokoli5191.zeitig.ui.format.uiLocale
import app.brokoli5191.zeitig.ui.format.wholeMinutes
import app.brokoli5191.zeitig.ui.navigation.sharedRouteBounds
import app.brokoli5191.zeitig.ui.status.LeaveDisplay
import app.brokoli5191.zeitig.ui.status.scheduleText
import java.time.DayOfWeek
import java.time.Instant
import java.time.format.TextStyle
import kotlin.math.roundToInt

data class RouteDetailUiState(
    val loading: Boolean = true,
    val route: Route? = null,
    val status: RouteStatus? = null,
    val failure: FailureReason? = null,
    val stats: RouteStats? = null,
    val now: Instant = Instant.EPOCH,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteDetailScreen(
    state: RouteDetailUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShareEta: (String) -> Unit,
    onResetLearning: () -> Unit,
    modifier: Modifier = Modifier,
    showBack: Boolean = true,
    map: @Composable (Route, Modifier) -> Unit = { _, _ -> },
) {
    val route = state.route
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val display = LeaveDisplay.of(state.status, state.now, state.failure)
    val etaText = etaShareText(display, route)
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(route?.name.orEmpty()) },
                subtitle = { route?.let { Text(stringResource(R.string.route_from_to, it.origin.name, it.destination.name)) } },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = onBack) {
                            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                        }
                    }
                },
                actions = {
                    if (etaText != null) {
                        IconButton(onClick = {
                            onShareEta(etaText)
                        }) { Icon(painterResource(R.drawable.ic_share), contentDescription = stringResource(R.string.share_eta)) }
                    }
                    IconButton(onClick = onEdit, enabled = route != null) {
                        Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.edit))
                    }
                    IconButton(onClick = { confirmDelete = true }, enabled = route != null) {
                        Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.delete))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        if (route == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (state.loading) DelayedLoadingIndicator()
            }
            return@Scaffold
        }
        Column(
            modifier =
                Modifier.fillMaxSize().padding(
                    padding,
                ).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Hero(route, display, state.status)
            Alternatives(display, state.now)
            DisruptionBanner(state.status?.disruptions.orEmpty())
            map(route, Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(28.dp)))
            SectionCard(title = stringResource(R.string.section_way)) {
                LegTimeline(route.journey, accessWalk = state.status?.walk?.duration)
            }
            state.status?.let { WalkCard(it, onResetLearning) }
            state.stats?.takeIf { it.trips > 0 }?.let { StatsCard(it) }
            ScheduleCard(route)
        }
    }
    if (confirmDelete && route != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_route_title)) },
            text = { Text(stringResource(R.string.delete_route_body, route.name)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun Hero(
    route: Route,
    display: LeaveDisplay,
    status: RouteStatus?,
) {
    val now = display is LeaveDisplay.LeaveNow
    val colors = MaterialTheme.colorScheme
    val container = if (now) colors.tertiaryContainer else colors.primaryContainer
    val onContainer = if (now) colors.onTertiaryContainer else colors.onPrimaryContainer
    Surface(
        modifier = Modifier.fillMaxWidth().sharedRouteBounds(route.id),
        shape = RoundedCornerShape(32.dp),
        color = container,
        contentColor = onContainer,
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (display) {
                is LeaveDisplay.LeaveIn -> {
                    Text(stringResource(R.string.leave_in), style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HeroNumber(display.minutes, color = onContainer)
                        Text(
                            stringResource(R.string.unit_min),
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                    DepartureLine(
                        display.option.leaveAt,
                        display.option.departure.line,
                        display.option.departure.towards,
                        display.option.departure.time,
                        display.option.arrival,
                    )
                    display.lateBy?.let {
                        Text(
                            stringResource(R.string.late_by, it.wholeMinutes()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                is LeaveDisplay.LeaveNow -> {
                    Text(stringResource(R.string.leave_now), style = MaterialTheme.typography.displayMediumEmphasized)
                    DepartureLine(
                        display.option.leaveAt,
                        display.option.departure.line,
                        display.option.departure.towards,
                        display.option.departure.time,
                        display.option.arrival,
                    )
                }

                is LeaveDisplay.TooLate -> {
                    Text(stringResource(R.string.too_late_title), style = MaterialTheme.typography.headlineMediumEmphasized)
                    Text(stringResource(R.string.too_late_body), style = MaterialTheme.typography.bodyLarge)
                }

                LeaveDisplay.NoService -> {
                    Text(stringResource(R.string.no_service_title), style = MaterialTheme.typography.headlineMediumEmphasized)
                    Text(
                        stringResource(
                            if (status?.hasRealtimeSource ==
                                false
                            ) {
                                R.string.no_service_timetable_body
                            } else {
                                R.string.no_service_body
                            },
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }

                LeaveDisplay.Loading -> {
                    Text(stringResource(R.string.loading_departures), style = MaterialTheme.typography.titleMedium)
                    DelayedLoadingIndicator()
                }

                is LeaveDisplay.Unavailable -> {
                    Text(stringResource(R.string.data_unavailable_title), style = MaterialTheme.typography.headlineSmallEmphasized)
                    Text(stringResource(display.reason.message()), style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (status != null && (display is LeaveDisplay.LeaveIn || display is LeaveDisplay.LeaveNow)) {
                val realtime =
                    (display as? LeaveDisplay.LeaveIn)?.option?.departure?.isRealtime
                        ?: (display as LeaveDisplay.LeaveNow).option.departure.isRealtime
                RealtimeIndicator(realtime)
            }
        }
    }
}

@Composable
private fun DepartureLine(
    leaveAt: Instant,
    line: String,
    towards: String,
    departure: Instant,
    arrival: Instant?,
) {
    Text(
        text =
            if (arrival != null) {
                stringResource(R.string.leave_details_arrival, clockTime(leaveAt), line, towards, clockTime(departure), clockTime(arrival))
            } else {
                stringResource(R.string.leave_details, clockTime(leaveAt), line, towards, clockTime(departure))
            },
        style = MaterialTheme.typography.bodyLarge,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Alternatives(
    display: LeaveDisplay,
    now: Instant,
) {
    val (chosen, alternatives) =
        when (display) {
            is LeaveDisplay.LeaveIn -> display.option to display.alternatives
            is LeaveDisplay.LeaveNow -> display.option to display.alternatives
            else -> return
        }
    if (alternatives.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.alternatives),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            alternatives.forEach { option ->
                val delta = minutesUntil(chosen.departure.time, option.departure.time)
                val leaveIn = minutesUntil(now, option.leaveAt)
                SuggestionChip(
                    onClick = {},
                    label = { Text(stringResource(R.string.alternative_chip, delta, leaveIn)) },
                )
            }
        }
    }
}

@Composable
private fun WalkCard(
    status: RouteStatus,
    onResetLearning: () -> Unit,
) {
    SectionCard(title = stringResource(R.string.section_walk)) {
        Text(
            text = stringResource(R.string.walk_minutes, status.walk.duration.wholeMinutes()),
            style = MaterialTheme.typography.headlineSmallEmphasized,
        )
        Text(
            text =
                when (status.walk.source) {
                    WalkTimeSource.LEARNED -> pluralStringResource(R.plurals.walk_learned, status.walk.samples, status.walk.samples)
                    WalkTimeSource.PLANNED -> stringResource(R.string.walk_planned)
                    WalkTimeSource.ESTIMATED -> stringResource(R.string.walk_estimated)
                    WalkTimeSource.MANUAL -> stringResource(R.string.walk_manual)
                },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (status.walk.source == WalkTimeSource.LEARNED) {
            TextButton(onClick = onResetLearning) { Text(stringResource(R.string.walk_reset)) }
        }
    }
}

@Composable
private fun StatsCard(stats: RouteStats) {
    SectionCard(title = stringResource(R.string.section_stats)) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            stats.onTimeRate?.let { rate ->
                Column {
                    Text("${(rate * 100).roundToInt()} %", style = MaterialTheme.typography.headlineSmallEmphasized)
                    Text(stringResource(R.string.stats_on_time), style = MaterialTheme.typography.bodySmall)
                }
            }
            stats.medianWalk?.let { median ->
                Column {
                    Text(
                        stringResource(R.string.walk_minutes, median.wholeMinutes()),
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                    )
                    Text(stringResource(R.string.stats_median_walk), style = MaterialTheme.typography.bodySmall)
                }
            }
            Column {
                Icon(
                    painterResource(
                        when (stats.walkTrend) {
                            Trend.FASTER -> R.drawable.ic_trending_down
                            Trend.SLOWER -> R.drawable.ic_trending_up
                            else -> R.drawable.ic_trending_flat
                        },
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                )
                Text(
                    stringResource(
                        when (stats.walkTrend) {
                            Trend.FASTER -> R.string.trend_faster
                            Trend.SLOWER -> R.string.trend_slower
                            Trend.STEADY -> R.string.trend_steady
                            Trend.UNKNOWN -> R.string.trend_unknown
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        WeekdayBars(stats.tripsByWeekday)
        Text(
            pluralStringResource(R.plurals.stats_trips, stats.trips, stats.trips),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Trips per weekday as seven small bars. */
@Composable
private fun WeekdayBars(counts: Map<DayOfWeek, Int>) {
    val max = (counts.values.maxOrNull() ?: 0).coerceAtLeast(1)
    val bar = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val locale = uiLocale()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.fillMaxWidth().height(48.dp)) {
            val slot = size.width / 7f
            val width = slot * 0.5f
            DayOfWeek.entries.forEachIndexed { index, day ->
                val x = slot * index + (slot - width) / 2
                drawRoundRect(track, Offset(x, 0f), Size(width, size.height), CornerRadius(width / 2))
                val h = size.height * (counts[day] ?: 0) / max
                if (h > 0f) drawRoundRect(bar, Offset(x, size.height - h), Size(width, h), CornerRadius(width / 2))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            DayOfWeek.entries.forEach { day ->
                Text(
                    day.getDisplayName(TextStyle.NARROW_STANDALONE, locale),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ScheduleCard(route: Route) {
    SectionCard(title = stringResource(R.string.section_schedule)) {
        Text(
            text = route.schedule?.let { scheduleText(it) } ?: stringResource(R.string.schedule_none),
            style = MaterialTheme.typography.bodyLarge,
        )
        route.schedule?.let {
            Text(
                pluralStringResource(R.plurals.trips_per_week, it.tripsPerWeek, it.tripsPerWeek),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            text =
                if (route.notificationsEnabled) {
                    stringResource(R.string.reminders_summary, route.reminders.offsets.filter { it > 0 }.joinToString(", "))
                } else {
                    stringResource(R.string.reminders_off)
                },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun etaShareText(
    display: LeaveDisplay,
    route: Route?,
): String? {
    route ?: return null
    val option =
        when (display) {
            is LeaveDisplay.LeaveIn -> display.option
            is LeaveDisplay.LeaveNow -> display.option
            else -> return null
        }
    val arrival = option.arrival ?: return null
    return stringResource(
        R.string.eta_share_text,
        clockTime(arrival),
        route.destination.name,
        option.departure.line,
        clockTime(option.departure.time),
    )
}

fun FailureReason.message(): Int =
    when (this) {
        FailureReason.NETWORK -> R.string.failure_network
        FailureReason.RATE_LIMITED -> R.string.failure_rate_limited
        FailureReason.SERVER -> R.string.failure_server
        FailureReason.UNKNOWN_STOP -> R.string.failure_unknown_stop
        FailureReason.INVALID_REQUEST, FailureReason.PARSE -> R.string.failure_parse
    }

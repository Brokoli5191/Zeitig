package app.brokoli5191.zeitig.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.brokoli5191.zeitig.R
import app.brokoli5191.zeitig.core.provider.FailureReason
import app.brokoli5191.zeitig.core.route.Route
import app.brokoli5191.zeitig.core.route.RouteStatus
import app.brokoli5191.zeitig.ui.components.LineBadge
import app.brokoli5191.zeitig.ui.components.LineColors
import app.brokoli5191.zeitig.ui.components.RealtimeIndicator
import app.brokoli5191.zeitig.ui.components.RollingNumber
import app.brokoli5191.zeitig.ui.components.icon
import app.brokoli5191.zeitig.ui.components.pressMorphBackground
import app.brokoli5191.zeitig.ui.expressive.BadgeShape
import app.brokoli5191.zeitig.ui.expressive.DelayedLoadingIndicator
import app.brokoli5191.zeitig.ui.expressive.ShapeContainer
import app.brokoli5191.zeitig.ui.format.clockTime
import app.brokoli5191.zeitig.ui.navigation.sharedRouteBounds
import app.brokoli5191.zeitig.ui.status.LeaveDisplay
import app.brokoli5191.zeitig.ui.status.ScheduleLabel
import java.time.Instant

data class RouteCardUi(
    val route: Route,
    val status: RouteStatus?,
    val failure: FailureReason? = null,
)

data class HomeUiState(
    val loading: Boolean = true,
    val routes: List<RouteCardUi> = emptyList(),
    val now: Instant = Instant.EPOCH,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onAddRoute: () -> Unit,
    onOpenRoute: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()
    val expandedFab by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                subtitle = { Text(stringResource(R.string.app_claim)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.settings))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddRoute,
                expanded = expandedFab,
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                text = { Text(stringResource(R.string.route_add)) },
            )
        },
    ) { padding ->
        when {
            state.loading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { DelayedLoadingIndicator() }
            }

            state.routes.isEmpty() -> {
                EmptyState(Modifier.padding(padding), onAddRoute)
            }

            else -> {
                LazyColumn(
                    state = listState,
                    contentPadding =
                        PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = padding.calculateTopPadding() + 8.dp,
                            bottom =
                                padding.calculateBottomPadding() + 96.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.routes, key = { it.route.id }) { card ->
                        RouteCard(card, state.now, onClick = { onOpenRoute(card.route.id) }, modifier = Modifier.animateItem())
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    modifier: Modifier,
    onAddRoute: () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ShapeContainer(BadgeShape.SUNNY, MaterialTheme.colorScheme.primaryContainer, size = 112.dp) {
            Icon(
                painterResource(R.drawable.ic_directions_walk),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(48.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.headlineSmallEmphasized,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.home_empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun RouteCard(
    card: RouteCardUi,
    now: Instant,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val display = LeaveDisplay.of(card.status, now, card.failure)
    val first = card.route.journey.firstTransit
    val active = card.status?.isActiveNow == true
    val background = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val onBackground = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .sharedRouteBounds(card.route.id)
                .pressMorphBackground(interaction, background)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
                .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val lineColor = first?.let { LineColors.of(it.line, it.type, it.color) } ?: MaterialTheme.colorScheme.secondaryContainer
        ShapeContainer(BadgeShape.COOKIE, lineColor, size = 52.dp) {
            Icon(
                painterResource(first?.type?.icon() ?: R.drawable.ic_directions_walk),
                contentDescription = null,
                tint = LineColors.onColor(lineColor),
                modifier = Modifier.size(28.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                card.route.name,
                style = MaterialTheme.typography.titleLarge,
                color = onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle(display, card),
                style = MaterialTheme.typography.bodyMedium,
                color = onBackground.copy(alpha = 0.8f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                card.route.journey.transitLegs.take(3).forEach { LineBadge(it.line, it.type, plannerColor = it.color) }
                val chosen = (display as? LeaveDisplay.LeaveIn)?.option ?: (display as? LeaveDisplay.LeaveNow)?.option
                if (chosen != null) RealtimeIndicator(chosen.departure.isRealtime, showLabel = false)
                if (card.status?.disruptions?.isNotEmpty() == true) {
                    Icon(
                        painterResource(R.drawable.ic_warning_filled),
                        contentDescription = stringResource(R.string.cd_disruption),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            card.route.schedule?.let { ScheduleLabel(it, color = onBackground.copy(alpha = 0.7f)) }
        }
        LeaveBadge(display, onBackground)
    }
}

@Composable
private fun subtitle(
    display: LeaveDisplay,
    card: RouteCardUi,
): String {
    val route = card.route
    return when (display) {
        is LeaveDisplay.LeaveIn -> {
            stringResource(
                R.string.leave_at_for,
                clockTime(display.option.leaveAt),
                display.option.departure.line,
                display.option.departure.towards,
            )
        }

        is LeaveDisplay.LeaveNow -> {
            stringResource(R.string.leave_now_for, display.option.departure.line, display.option.departure.towards)
        }

        is LeaveDisplay.TooLate -> {
            stringResource(R.string.too_late_short)
        }

        LeaveDisplay.NoService -> {
            stringResource(R.string.no_service_short)
        }

        LeaveDisplay.Loading -> {
            stringResource(R.string.route_from_to, route.origin.name, route.destination.name)
        }

        is LeaveDisplay.Unavailable -> {
            stringResource(R.string.data_unavailable_short)
        }
    }
}

@Composable
private fun LeaveBadge(
    display: LeaveDisplay,
    color: androidx.compose.ui.graphics.Color,
) {
    Column(horizontalAlignment = Alignment.End) {
        when (display) {
            is LeaveDisplay.LeaveIn -> {
                RollingNumber(display.minutes, style = MaterialTheme.typography.displaySmallEmphasized, color = color)
                Text(stringResource(R.string.unit_min), style = MaterialTheme.typography.labelMedium, color = color)
            }

            is LeaveDisplay.LeaveNow -> {
                Text(
                    stringResource(R.string.leave_now_badge),
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            else -> {
                Text("–", style = MaterialTheme.typography.displaySmall, color = color.copy(alpha = 0.5f))
            }
        }
    }
}

package app.brokoli5191.zeitig.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.brokoli5191.zeitig.R
import app.brokoli5191.zeitig.core.model.TransportType
import app.brokoli5191.zeitig.ui.theme.tabular

/** Line number on its line colour, e.g. a red "U1" or "49". */
@Composable
fun LineBadge(
    line: String,
    type: TransportType,
    modifier: Modifier = Modifier,
    plannerColor: Int? = null,
) {
    val color = LineColors.of(line, type, plannerColor)
    val description = stringResource(R.string.cd_line, stringResource(type.label()), line)
    Box(
        modifier =
            modifier
                .defaultMinSize(minWidth = 36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color)
                .padding(horizontal = 8.dp, vertical = 3.dp)
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = line,
            color = LineColors.onColor(color),
            style = MaterialTheme.typography.labelLarge.tabular(),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

/** Real-time dot: filled when the time is a live prediction, hollow when it is the timetable. */
@Composable
fun RealtimeIndicator(
    realtime: Boolean,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
) {
    val color = if (realtime) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .then(if (realtime) Modifier.background(color) else Modifier.border(1.5.dp, color, CircleShape)),
        )
        if (showLabel) {
            Text(
                text = stringResource(if (realtime) R.string.realtime else R.string.scheduled_only),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun TransportIcon(
    type: TransportType,
    modifier: Modifier = Modifier,
) {
    Icon(painterResource(type.icon()), contentDescription = null, modifier = modifier)
}

fun TransportType.icon(): Int =
    when (this) {
        TransportType.METRO -> R.drawable.ic_subway
        TransportType.TRAM -> R.drawable.ic_tram
        TransportType.BUS, TransportType.NIGHT_BUS, TransportType.ON_DEMAND_BUS -> R.drawable.ic_directions_bus
        TransportType.TRAIN -> R.drawable.ic_train
        TransportType.OTHER -> R.drawable.ic_route
    }

fun TransportType.label(): Int =
    when (this) {
        TransportType.METRO -> R.string.type_metro
        TransportType.TRAM -> R.string.type_tram
        TransportType.BUS -> R.string.type_bus
        TransportType.NIGHT_BUS -> R.string.type_night_bus
        TransportType.ON_DEMAND_BUS -> R.string.type_on_demand_bus
        TransportType.TRAIN -> R.string.type_train
        TransportType.OTHER -> R.string.type_other
    }

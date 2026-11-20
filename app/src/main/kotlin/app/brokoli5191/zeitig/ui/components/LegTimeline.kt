package app.brokoli5191.zeitig.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.brokoli5191.zeitig.R
import app.brokoli5191.zeitig.core.journey.Journey
import app.brokoli5191.zeitig.core.journey.Leg
import app.brokoli5191.zeitig.ui.format.wholeMinutes
import java.time.Duration

/** The journey leg by leg: walk, ride (line → headsign), transfer walk, … */
@Composable
fun LegTimeline(
    journey: Journey,
    modifier: Modifier = Modifier,
    accessWalk: Duration? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        journey.legs.forEachIndexed { index, leg ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                when (leg) {
                    is Leg.Walk -> {
                        Icon(painterResource(R.drawable.ic_directions_walk), contentDescription = null, modifier = Modifier.size(24.dp))
                        val minutes = (if (index == 0 && accessWalk != null) accessWalk else leg.duration).wholeMinutes()
                        Text(
                            text = stringResource(R.string.leg_walk, minutes, leg.to.name),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    is Leg.Transit -> {
                        LineBadge(leg.line, leg.type, plannerColor = leg.color)
                        Column(Modifier.padding(start = 2.dp)) {
                            Text(
                                text = stringResource(R.string.leg_ride, leg.from.name, leg.to.name),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text =
                                    if (leg.duration > Duration.ZERO) {
                                        stringResource(R.string.leg_ride_detail, leg.headsign, leg.duration.wholeMinutes())
                                    } else {
                                        stringResource(R.string.leg_towards, leg.headsign)
                                    },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

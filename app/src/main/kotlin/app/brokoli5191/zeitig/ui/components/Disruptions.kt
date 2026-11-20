package app.brokoli5191.zeitig.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.brokoli5191.zeitig.R
import app.brokoli5191.zeitig.core.model.Disruption
import app.brokoli5191.zeitig.core.model.DisruptionKind

/** Disruptions and elevator outages on the route; tap to expand the operator's full text. */
@Composable
fun DisruptionBanner(
    disruptions: List<Disruption>,
    modifier: Modifier = Modifier,
) {
    if (disruptions.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    val elevators = disruptions.count { it.kind == DisruptionKind.ELEVATOR || it.kind == DisruptionKind.ESCALATOR }
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.disruption_toggle)) { expanded = !expanded },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    painterResource(if (elevators == disruptions.size) R.drawable.ic_elevator else R.drawable.ic_warning_filled),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text =
                        if (elevators == disruptions.size) {
                            pluralStringResource(R.plurals.elevator_outages, elevators, elevators)
                        } else {
                            pluralStringResource(R.plurals.disruptions, disruptions.size, disruptions.size)
                        },
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    painterResource(if (expanded) R.drawable.ic_keyboard_arrow_up else R.drawable.ic_keyboard_arrow_down),
                    contentDescription = null,
                )
            }
            disruptions.forEach { disruption ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(disruption.title, style = MaterialTheme.typography.bodyMedium)
                    if (expanded && disruption.description.isNotBlank()) {
                        Text(disruption.description, style = MaterialTheme.typography.bodySmall)
                    } else if (!expanded && disruption.description.isNotBlank()) {
                        Text(
                            disruption.description,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

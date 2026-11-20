package app.brokoli5191.zeitig.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.brokoli5191.zeitig.ui.theme.rememberHaptics

/** Single choice as a connected expressive button group (shape morphs on press and selection). */
@Composable
fun <T> ConnectedChoice(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        options.forEachIndexed { index, option ->
            ToggleButton(
                checked = option == selected,
                onCheckedChange = {
                    if (option != selected) {
                        haptics.tick()
                        onSelect(option)
                    }
                },
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                shapes =
                    when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
            ) { Text(label(option), maxLines = 1, overflow = TextOverflow.Ellipsis, autoSize = LabelAutoSize) }
        }
    }
}

/** Multi choice, e.g. weekdays: each toggle morphs on its own. */
@Composable
fun <T> MultiToggle(
    options: List<T>,
    selected: Set<T>,
    label: @Composable (T) -> String,
    onChange: (Set<T>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { option ->
            val checked = option in selected
            ToggleButton(
                checked = checked,
                onCheckedChange = { on ->
                    haptics.toggle(on)
                    onChange(if (on) selected + option else selected - option)
                },
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp),
            ) { Text(label(option), maxLines = 1, autoSize = LabelAutoSize) }
        }
    }
}

/** Labels shrink (down to 10 sp) before they would be cut – four choices must fit a phone. */
private val LabelAutoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 14.sp, stepSize = 0.5.sp)

package app.brokoli5191.zeitig.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import app.brokoli5191.zeitig.ui.theme.LocalMotionMode
import app.brokoli5191.zeitig.ui.theme.MotionMode
import app.brokoli5191.zeitig.ui.theme.tabular

/**
 * A number whose digits roll vertically when they change (down when counting down). Each digit
 * animates on its own, so 10 → 9 does not move the whole number. Reduced motion cross-fades,
 * motion off just swaps.
 */
@Composable
fun RollingNumber(
    value: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    val motion = LocalMotionMode.current
    val text = value.toString()
    Row(modifier) {
        text.forEachIndexed { index, char ->
            val position = text.length - index
            AnimatedContent(
                targetState = char,
                transitionSpec = { digitTransition(motion) },
                label = "digit$position",
            ) { digit ->
                Text(text = digit.toString(), style = style.tabular(), color = color)
            }
        }
    }
}

private fun AnimatedContentTransitionScope<Char>.digitTransition(motion: MotionMode): ContentTransform {
    val down = targetState < initialState
    return when (motion) {
        MotionMode.OFF -> {
            ContentTransform(fadeIn(tween0), fadeOut(tween0), sizeTransform = SizeTransform(clip = false))
        }

        MotionMode.REDUCED -> {
            fadeIn() togetherWith fadeOut()
        }

        MotionMode.FULL -> {
            (slideInVertically { h -> if (down) -h else h } + fadeIn()) togetherWith
                (slideOutVertically { h -> if (down) h else -h } + fadeOut())
        }
    }.using(SizeTransform(clip = true))
}

private val tween0 = androidx.compose.animation.core.snap<Float>()

/** Convenience for the hero countdown style. */
@Composable
fun HeroNumber(
    value: Long,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    RollingNumber(value = value, modifier = modifier, style = MaterialTheme.typography.displayLargeEmphasized, color = color)
}

package app.brokoli5191.zeitig.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.MorphPolygonShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.toPolygonShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.brokoli5191.zeitig.ui.theme.LocalMotionMode
import app.brokoli5191.zeitig.ui.theme.MotionMode

/**
 * Press feedback for large tappable surfaces: the corners tighten and the surface shrinks a little
 * on a spatial spring. Both happen in the draw/layer phase – no recomposition per frame.
 */
@Composable
fun Modifier.pressMorphBackground(
    interactionSource: InteractionSource,
    color: Color,
    restRadius: Dp = 28.dp,
    pressedRadius: Dp = 14.dp,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val motion = LocalMotionMode.current
    val progress = remember { Animatable(0f) }
    val spec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    LaunchedEffect(pressed, motion) {
        val target = if (pressed) 1f else 0f
        if (motion == MotionMode.FULL) progress.animateTo(target, spec) else progress.snapTo(target)
    }
    val shape =
        remember(restRadius, pressedRadius) {
            MorphPolygonShape(
                start = RoundedCornerShape(restRadius).toPolygonShape(),
                end = RoundedCornerShape(pressedRadius).toPolygonShape(),
                progress = { progress.value },
            )
        }
    return this
        .graphicsLayer {
            val scale = 1f - PRESS_SHRINK * progress.value
            scaleX = scale
            scaleY = scale
        }.drawBehind { drawOutline(shape.createOutline(size, layoutDirection, this), color) }
}

private const val PRESS_SHRINK = 0.025f

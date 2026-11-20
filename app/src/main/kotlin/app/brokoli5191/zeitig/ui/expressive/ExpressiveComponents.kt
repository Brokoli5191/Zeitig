@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.brokoli5191.zeitig.ui.expressive

// The only place that touches experimental Material 3 Expressive APIs (LoadingIndicator,
// MaterialShapes). When they become stable, only this file changes.

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Shows a loading indicator only if loading takes longer than [delayMillis] – no flash otherwise. */
@Composable
fun DelayedLoadingIndicator(
    modifier: Modifier = Modifier,
    delayMillis: Long = 300,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMillis)
        visible = true
    }
    if (visible) LoadingIndicator(modifier = modifier)
}

/** Shape vocabulary of the app, mapped to Material's expressive shape set. */
enum class BadgeShape { COOKIE, SUNNY, CLOVER, PILL_SOFT, ARCH, SQUARE_SOFT }

@Composable
fun badgeShape(kind: BadgeShape): Shape =
    when (kind) {
        BadgeShape.COOKIE -> MaterialShapes.Cookie9Sided
        BadgeShape.SUNNY -> MaterialShapes.Sunny
        BadgeShape.CLOVER -> MaterialShapes.Clover4Leaf
        BadgeShape.PILL_SOFT -> MaterialShapes.Pill
        BadgeShape.ARCH -> MaterialShapes.Arch
        BadgeShape.SQUARE_SOFT -> MaterialShapes.Square
    }.toShape()

/** An icon container in one of the expressive shapes. */
@Composable
fun ShapeContainer(
    kind: BadgeShape,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.size(size).clip(badgeShape(kind)).background(color),
        contentAlignment = Alignment.Center,
    ) { content() }
}

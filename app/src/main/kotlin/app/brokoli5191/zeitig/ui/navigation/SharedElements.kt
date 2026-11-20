package app.brokoli5191.zeitig.ui.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import app.brokoli5191.zeitig.ui.theme.LocalMotionMode
import app.brokoli5191.zeitig.ui.theme.MotionMode

val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalEntryVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Shared bounds between a route card and the route's detail hero. A no-op outside navigation (e.g.
 * screenshot tests) and when motion is off.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedRouteBounds(routeId: Long): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val visibility = LocalEntryVisibilityScope.current ?: return this
    if (LocalMotionMode.current == MotionMode.OFF) return this
    return with(shared) {
        this@sharedRouteBounds.sharedBounds(
            sharedContentState = rememberSharedContentState(key = "route-$routeId"),
            animatedVisibilityScope = visibility,
        )
    }
}

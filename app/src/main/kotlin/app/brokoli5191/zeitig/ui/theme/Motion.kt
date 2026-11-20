package app.brokoli5191.zeitig.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import app.brokoli5191.zeitig.data.settings.MotionPreference

/** How much the UI may move: expressive springs, calm standard motion, or none at all. */
enum class MotionMode { FULL, REDUCED, OFF }

val LocalMotionMode = staticCompositionLocalOf { MotionMode.FULL }

/** "Follow system": an animator duration scale of 0 (also set by "Remove animations") means reduced. */
@Composable
fun resolveMotionMode(preference: MotionPreference): MotionMode =
    when (preference) {
        MotionPreference.FULL -> {
            MotionMode.FULL
        }

        MotionPreference.REDUCED -> {
            MotionMode.REDUCED
        }

        MotionPreference.OFF -> {
            MotionMode.OFF
        }

        MotionPreference.SYSTEM -> {
            val resolver = LocalContext.current.contentResolver
            val scale = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            if (scale == 0f) MotionMode.REDUCED else MotionMode.FULL
        }
    }

fun motionSchemeFor(mode: MotionMode): MotionScheme =
    when (mode) {
        MotionMode.FULL -> MotionScheme.expressive()
        MotionMode.REDUCED -> MotionScheme.standard()
        MotionMode.OFF -> NoMotionScheme
    }

/** Every spec snaps: components still change state, they just do not animate. */
private object NoMotionScheme : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = snap()
}

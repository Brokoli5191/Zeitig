package app.brokoli5191.zeitig.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** Semantic, optional haptics: confirm, reject, toggles. Never on scroll. */
class Haptics(
    private val feedback: HapticFeedback,
    private val enabled: Boolean,
) {
    fun confirm() = perform(HapticFeedbackType.Confirm)

    fun reject() = perform(HapticFeedbackType.Reject)

    fun toggle(on: Boolean) = perform(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)

    fun tick() = perform(HapticFeedbackType.SegmentTick)

    private fun perform(type: HapticFeedbackType) {
        if (enabled) feedback.performHapticFeedback(type)
    }
}

val LocalHapticsEnabled = staticCompositionLocalOf { true }

@Composable
fun rememberHaptics(): Haptics {
    val feedback = LocalHapticFeedback.current
    val enabled = LocalHapticsEnabled.current
    return remember(feedback, enabled) { Haptics(feedback, enabled) }
}

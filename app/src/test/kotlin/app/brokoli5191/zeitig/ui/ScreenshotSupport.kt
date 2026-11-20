package app.brokoli5191.zeitig.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import app.brokoli5191.zeitig.data.settings.Appearance
import app.brokoli5191.zeitig.data.settings.ContrastPreference
import app.brokoli5191.zeitig.data.settings.MotionPreference
import app.brokoli5191.zeitig.data.settings.ThemeMode
import app.brokoli5191.zeitig.ui.theme.ZeitigTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import org.robolectric.RuntimeEnvironment

/**
 * The visual variants every main screen is recorded in. Seed colours instead of wallpaper colours
 * keep the images deterministic; motion is off so frames are final.
 */
enum class Variant(
    val suffix: String,
    val qualifiers: String,
    val appearance: Appearance,
    val fontScale: Float = 1f,
) {
    LIGHT_EN("light_en", "+en-notnight", base(ThemeMode.LIGHT)),
    DARK_EN("dark_en", "+en-night", base(ThemeMode.DARK)),
    LIGHT_DE("light_de", "+de-notnight", base(ThemeMode.LIGHT)),
    DARK_DE("dark_de", "+de-night", base(ThemeMode.DARK)),
    HIGH_CONTRAST("highcontrast_en", "+en-notnight", base(ThemeMode.LIGHT).copy(contrast = ContrastPreference.HIGH)),
    PURE_BLACK("pureblack_en", "+en-night", base(ThemeMode.DARK).copy(pureBlack = true)),
    FONT_200("font200_de", "+de-notnight", base(ThemeMode.LIGHT), fontScale = 2f),
}

private fun base(theme: ThemeMode) =
    Appearance(
        theme = theme,
        dynamicColor = false,
        motion = MotionPreference.OFF,
        contrast = ContrastPreference.STANDARD,
    )

@OptIn(ExperimentalRoborazziApi::class)
fun ComposeContentTestRule.snapshot(
    screen: String,
    variant: Variant,
    content: @Composable () -> Unit,
) {
    RuntimeEnvironment.setQualifiers(variant.qualifiers)
    setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, variant.fontScale)) {
            ZeitigTheme(variant.appearance) { content() }
        }
    }
    waitForIdle()
    onRoot().captureRoboImage("${roborazziSystemPropertyOutputDirectory()}/${screen}_${variant.suffix}.png")
}

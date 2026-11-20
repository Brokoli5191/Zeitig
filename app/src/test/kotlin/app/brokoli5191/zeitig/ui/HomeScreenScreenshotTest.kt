package app.brokoli5191.zeitig.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.brokoli5191.zeitig.ui.home.HomeScreen
import app.brokoli5191.zeitig.ui.theme.ZeitigTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class HomeScreenScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyLight() = capture("home_empty_light_en", dark = false)

    @Test
    @Config(qualifiers = "+night")
    fun emptyDark() = capture("home_empty_dark_en", dark = true)

    @Test
    @Config(qualifiers = "+de")
    fun emptyLightGerman() = capture("home_empty_light_de", dark = false)

    private fun capture(
        name: String,
        dark: Boolean,
    ) {
        composeRule.setContent {
            ZeitigTheme(darkTheme = dark, dynamicColor = false) { HomeScreen() }
        }
        @OptIn(ExperimentalRoborazziApi::class)
        composeRule.onRoot().captureRoboImage("${roborazziSystemPropertyOutputDirectory()}/$name.png")
    }
}

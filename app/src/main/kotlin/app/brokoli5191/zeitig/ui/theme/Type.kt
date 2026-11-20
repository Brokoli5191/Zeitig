package app.brokoli5191.zeitig.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle

/** Tabular figures keep live numbers from jittering while they count. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

/** The Material 3 type scale with tabular figures on the styles used for times and countdowns. */
fun zeitigTypography(base: Typography = Typography()): Typography =
    base.copy(
        displayLarge = base.displayLarge.tabular(),
        displayMedium = base.displayMedium.tabular(),
        displaySmall = base.displaySmall.tabular(),
        displayLargeEmphasized = base.displayLargeEmphasized.tabular(),
        displayMediumEmphasized = base.displayMediumEmphasized.tabular(),
        displaySmallEmphasized = base.displaySmallEmphasized.tabular(),
        headlineLargeEmphasized = base.headlineLargeEmphasized.tabular(),
        titleMedium = base.titleMedium.tabular(),
        labelLarge = base.labelLarge.tabular(),
    )

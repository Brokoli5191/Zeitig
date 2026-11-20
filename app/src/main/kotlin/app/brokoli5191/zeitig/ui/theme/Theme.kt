package app.brokoli5191.zeitig.ui.theme

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import app.brokoli5191.zeitig.data.settings.Appearance
import app.brokoli5191.zeitig.data.settings.ContrastPreference
import app.brokoli5191.zeitig.data.settings.SchemeStyle
import app.brokoli5191.zeitig.data.settings.ThemeMode
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme

/** True while the dark scheme is in use, whichever setting caused it. */
val LocalDarkTheme = staticCompositionLocalOf { false }

/**
 * Applies the appearance settings: theme mode, dynamic or seed colours with a scheme style,
 * contrast, pure black, motion and haptics. Everything is plain Compose state, so changes apply
 * instantly without recreating the activity.
 */
@Composable
fun ZeitigTheme(
    appearance: Appearance = Appearance(),
    systemDark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val dark =
        when (appearance.theme) {
            ThemeMode.SYSTEM -> systemDark
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
    val contrast =
        when (appearance.contrast) {
            ContrastPreference.SYSTEM -> systemContrastLevel()
            ContrastPreference.STANDARD -> 0.0
            ContrastPreference.MEDIUM -> 0.5
            ContrastPreference.HIGH -> 1.0
        }
    val context = LocalContext.current
    val canUseDynamic = appearance.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !LocalInspectionMode.current
    val scheme =
        when {
            // Platform dynamic colours already follow the system contrast setting on Android 14+.
            canUseDynamic && appearance.contrast == ContrastPreference.SYSTEM -> {
                platformDynamic(context, dark)
            }

            canUseDynamic -> {
                dynamicColorScheme(
                    seedColor = platformDynamic(context, false).primary,
                    isDark = dark,
                    isAmoled = false,
                    style = PaletteStyle.TonalSpot,
                    contrastLevel = contrast,
                )
            }

            else -> {
                dynamicColorScheme(
                    seedColor = Color(appearance.seed),
                    isDark = dark,
                    isAmoled = false,
                    style = appearance.style.toPaletteStyle(),
                    contrastLevel = contrast,
                )
            }
        }.let { if (dark && appearance.pureBlack) it.pureBlack() else it }

    val motion = resolveMotionMode(appearance.motion)
    CompositionLocalProvider(
        LocalMotionMode provides motion,
        LocalHapticsEnabled provides appearance.haptics,
        LocalDarkTheme provides dark,
    ) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = motionSchemeFor(motion),
            typography = zeitigTypography(),
            content = content,
        )
    }
}

private fun platformDynamic(
    context: Context,
    dark: Boolean,
): ColorScheme =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        error("dynamic colour needs Android 12")
    }

fun SchemeStyle.toPaletteStyle(): PaletteStyle =
    when (this) {
        SchemeStyle.TONAL_SPOT -> PaletteStyle.TonalSpot
        SchemeStyle.VIBRANT -> PaletteStyle.Vibrant
        SchemeStyle.EXPRESSIVE -> PaletteStyle.Expressive
        SchemeStyle.FIDELITY -> PaletteStyle.Fidelity
    }

/** AMOLED: true black behind everything, containers stay just above it for structure. */
fun ColorScheme.pureBlack(): ColorScheme =
    copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF0B0B0B),
        surfaceContainer = Color(0xFF111111),
        surfaceContainerHigh = Color(0xFF181818),
        surfaceContainerHighest = Color(0xFF1F1F1F),
    )

/** Android 14+ system contrast (−1…1) mapped to Material's 0…1; earlier versions: standard. */
@Composable
private fun systemContrastLevel(): Double {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || LocalInspectionMode.current) return 0.0
    val context = LocalContext.current
    val manager = context.getSystemService(UiModeManager::class.java) ?: return 0.0
    var contrast by remember { mutableFloatStateOf(manager.contrast) }
    DisposableEffect(manager) {
        val listener = UiModeManager.ContrastChangeListener { contrast = it }
        manager.addContrastChangeListener(context.mainExecutor, listener)
        onDispose { manager.removeContrastChangeListener(listener) }
    }
    return contrast.coerceIn(0f, 1f).toDouble()
}

package app.brokoli5191.zeitig.ui.settings

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerRounding
import androidx.compose.foundation.shape.MorphPolygonShape
import androidx.compose.foundation.shape.PolygonShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.toPolygonShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.brokoli5191.zeitig.R
import app.brokoli5191.zeitig.core.model.TransportType
import app.brokoli5191.zeitig.data.settings.Appearance
import app.brokoli5191.zeitig.data.settings.ContrastPreference
import app.brokoli5191.zeitig.data.settings.MotionPreference
import app.brokoli5191.zeitig.data.settings.SchemeStyle
import app.brokoli5191.zeitig.data.settings.ThemeMode
import app.brokoli5191.zeitig.locale.AppLanguage
import app.brokoli5191.zeitig.ui.components.ConnectedChoice
import app.brokoli5191.zeitig.ui.components.LineBadge
import app.brokoli5191.zeitig.ui.components.RealtimeIndicator
import app.brokoli5191.zeitig.ui.components.RollingNumber
import app.brokoli5191.zeitig.ui.components.SectionHeader
import app.brokoli5191.zeitig.ui.theme.LocalMotionMode
import app.brokoli5191.zeitig.ui.theme.MotionMode
import app.brokoli5191.zeitig.ui.theme.rememberHaptics

data class AppearanceActions(
    val onBack: () -> Unit = {},
    val onAppearance: ((Appearance) -> Appearance) -> Unit = {},
    val onLanguage: (AppLanguage) -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    appearance: Appearance,
    language: AppLanguage,
    actions: AppearanceActions,
    modifier: Modifier = Modifier,
    dynamicColorAvailable: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val haptics = rememberHaptics()
    val update = actions.onAppearance
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.appearance)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            PreviewCard(Modifier.padding(16.dp))

            Labeled(stringResource(R.string.theme)) {
                ConnectedChoice(
                    options = ThemeMode.entries,
                    selected = appearance.theme,
                    label = {
                        stringResource(
                            when (it) {
                                ThemeMode.SYSTEM -> R.string.option_system
                                ThemeMode.LIGHT -> R.string.theme_light
                                ThemeMode.DARK -> R.string.theme_dark
                            },
                        )
                    },
                    onSelect = { mode -> update { it.copy(theme = mode) } },
                )
            }
            SwitchRow(
                R.drawable.ic_dark_mode,
                stringResource(R.string.pure_black),
                stringResource(R.string.pure_black_summary),
                appearance.pureBlack,
            ) { on ->
                haptics.toggle(on)
                update { it.copy(pureBlack = on) }
            }

            SectionHeader(stringResource(R.string.section_colours))
            if (dynamicColorAvailable) {
                SwitchRow(
                    R.drawable.ic_palette,
                    stringResource(R.string.dynamic_colour),
                    stringResource(R.string.dynamic_colour_summary),
                    appearance.dynamicColor,
                ) { on ->
                    haptics.toggle(on)
                    update { it.copy(dynamicColor = on) }
                }
            } else {
                Text(
                    stringResource(R.string.dynamic_colour_unavailable),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!appearance.dynamicColor || !dynamicColorAvailable) {
                Labeled(stringResource(R.string.seed_colour)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Appearance.SEEDS.forEach { seed ->
                            SeedSwatch(Color(seed), selected = seed == appearance.seed) {
                                haptics.tick()
                                update { it.copy(seed = seed) }
                            }
                        }
                    }
                }
                Labeled(stringResource(R.string.scheme_style)) {
                    ConnectedChoice(
                        options = SchemeStyle.entries,
                        selected = appearance.style,
                        label = {
                            stringResource(
                                when (it) {
                                    SchemeStyle.TONAL_SPOT -> R.string.style_tonal_spot
                                    SchemeStyle.VIBRANT -> R.string.style_vibrant
                                    SchemeStyle.EXPRESSIVE -> R.string.style_expressive
                                    SchemeStyle.FIDELITY -> R.string.style_fidelity
                                },
                            )
                        },
                        onSelect = { style -> update { it.copy(style = style) } },
                    )
                }
            }
            Labeled(stringResource(R.string.contrast)) {
                ConnectedChoice(
                    options = ContrastPreference.entries,
                    selected = appearance.contrast,
                    label = {
                        stringResource(
                            when (it) {
                                ContrastPreference.SYSTEM -> R.string.option_system
                                ContrastPreference.STANDARD -> R.string.contrast_standard
                                ContrastPreference.MEDIUM -> R.string.contrast_medium
                                ContrastPreference.HIGH -> R.string.contrast_high
                            },
                        )
                    },
                    onSelect = { contrast -> update { it.copy(contrast = contrast) } },
                )
            }

            SectionHeader(stringResource(R.string.section_motion))
            Labeled(stringResource(R.string.motion)) {
                ConnectedChoice(
                    options = MotionPreference.entries,
                    selected = appearance.motion,
                    label = {
                        stringResource(
                            when (it) {
                                MotionPreference.SYSTEM -> R.string.option_system
                                MotionPreference.FULL -> R.string.motion_full
                                MotionPreference.REDUCED -> R.string.motion_reduced
                                MotionPreference.OFF -> R.string.motion_off
                            },
                        )
                    },
                    onSelect = { motion -> update { it.copy(motion = motion) } },
                )
            }
            SwitchRow(
                R.drawable.ic_vibration,
                stringResource(R.string.haptics),
                stringResource(R.string.haptics_summary),
                appearance.haptics,
            ) { on ->
                if (on) haptics.toggle(true)
                update { it.copy(haptics = on) }
            }

            SectionHeader(stringResource(R.string.section_language))
            Labeled(stringResource(R.string.language)) {
                ConnectedChoice(
                    options = AppLanguage.entries,
                    selected = language,
                    label = {
                        stringResource(
                            when (it) {
                                AppLanguage.SYSTEM -> R.string.option_system
                                AppLanguage.GERMAN -> R.string.language_german
                                AppLanguage.ENGLISH -> R.string.language_english
                            },
                        )
                    },
                    onSelect = actions.onLanguage,
                )
            }
        }
    }
}

@Composable
private fun Labeled(
    label: String,
    content: @Composable () -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        content()
    }
}

/** A miniature of the real UI that reacts instantly to every change on this screen. */
@Composable
private fun PreviewCard(modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.preview_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RollingNumber(
                    4,
                    style = MaterialTheme.typography.displayMediumEmphasized,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    stringResource(R.string.unit_min),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                LineBadge("49", TransportType.TRAM)
                LineBadge("U3", TransportType.METRO)
                RealtimeIndicator(true)
            }
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                Text(
                    stringResource(R.string.preview_body),
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** Seed colour: a circle that morphs into a soft star when selected (draw phase only). */
@Composable
private fun SeedSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val progress = remember { Animatable(if (selected) 1f else 0f) }
    val motion = LocalMotionMode.current
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(selected, motion) {
        val target = if (selected) 1f else 0f
        if (motion == MotionMode.FULL) progress.animateTo(target, spec) else progress.snapTo(target)
    }
    val shape =
        remember {
            MorphPolygonShape(
                start = RoundedCornerShape(50).toPolygonShape(),
                end = PolygonShape.star(numPoints = 8, innerRadiusRatio = 0.78f, outerRounding = CornerRounding.fraction(0.4f)),
                progress = { progress.value },
            )
        }
    val ring = MaterialTheme.colorScheme.onSurface
    val description = stringResource(R.string.cd_seed_colour)
    Box(
        modifier =
            Modifier
                .size(40.dp)
                .semantics {
                    contentDescription = description
                    this.selected = selected
                }.clickable(
                    interactionSource =
                        remember {
                            MutableInteractionSource()
                        },
                    indication = null,
                    role = Role.RadioButton,
                    onClick = onClick,
                )
                .drawBehind {
                    drawOutline(shape.createOutline(size, layoutDirection, this), color)
                    if (selected) drawCircle(ring, radius = size.minDimension / 8)
                },
    )
}

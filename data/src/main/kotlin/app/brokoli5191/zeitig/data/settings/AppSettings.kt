package app.brokoli5191.zeitig.data.settings

import app.brokoli5191.zeitig.core.reminder.ReminderPlan
import app.brokoli5191.zeitig.core.walk.WalkSettings

public enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Scheme styles offered for seed colours (Material Color Utilities variants). */
public enum class SchemeStyle { TONAL_SPOT, VIBRANT, EXPRESSIVE, FIDELITY }

public enum class ContrastPreference { SYSTEM, STANDARD, MEDIUM, HIGH }

public enum class MotionPreference { SYSTEM, FULL, REDUCED, OFF }

public enum class DistanceUnit { METRIC, IMPERIAL }

public data class Appearance(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val pureBlack: Boolean = false,
    val dynamicColor: Boolean = true,
    val seed: Int = SEEDS.first(),
    val style: SchemeStyle = SchemeStyle.TONAL_SPOT,
    val contrast: ContrastPreference = ContrastPreference.SYSTEM,
    val motion: MotionPreference = MotionPreference.SYSTEM,
    val haptics: Boolean = true,
) {
    public companion object {
        /** Curated seeds: teal (brand), indigo, violet, rose, amber, forest, ocean, graphite. */
        public val SEEDS: List<Int> =
            listOf(
                0xFF006A60.toInt(),
                0xFF4355B9.toInt(),
                0xFF6750A4.toInt(),
                0xFFA43C6C.toInt(),
                0xFF8B5000.toInt(),
                0xFF386A20.toInt(),
                0xFF00629E.toInt(),
                0xFF5D5F65.toInt(),
            )
    }
}

public data class AppSettings(
    val appearance: Appearance = Appearance(),
    val walkSpeed: Double = WalkSettings.DEFAULT_SPEED,
    val detourFactor: Double = WalkSettings.DEFAULT_DETOUR,
    /** Opt-in: journey planning and address search send coordinates to Transitous. */
    val routingEnabled: Boolean = false,
    /** Map tiles come from basemap.at; off shows a schematic map without network. */
    val mapTilesEnabled: Boolean = true,
    val defaultReminders: List<Int> = ReminderPlan.DEFAULT,
    val defaultBufferSeconds: Int = 60,
    val units: DistanceUnit = DistanceUnit.METRIC,
    val accessibleByDefault: Boolean = false,
) {
    val walkSettings: WalkSettings get() = WalkSettings(walkSpeed, detourFactor)
}

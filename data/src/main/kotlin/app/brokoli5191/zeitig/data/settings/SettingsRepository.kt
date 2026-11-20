package app.brokoli5191.zeitig.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.brokoli5191.zeitig.core.reminder.ReminderPlan
import app.brokoli5191.zeitig.core.walk.WalkSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** All user settings in one Preferences DataStore; unknown or broken values fall back to defaults. */
public class SettingsRepository(
    private val store: DataStore<Preferences>,
) {
    public val settings: Flow<AppSettings> = store.data.map(::read)

    public suspend fun current(): AppSettings = settings.first()

    public suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            val next = transform(read(prefs))
            write(prefs, next)
        }
    }

    private fun read(prefs: Preferences): AppSettings {
        val defaults = AppSettings()
        val appearance =
            Appearance(
                theme = enumOr(prefs[Keys.THEME], defaults.appearance.theme),
                pureBlack = prefs[Keys.PURE_BLACK] ?: defaults.appearance.pureBlack,
                dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: defaults.appearance.dynamicColor,
                seed = prefs[Keys.SEED] ?: defaults.appearance.seed,
                style = enumOr(prefs[Keys.STYLE], defaults.appearance.style),
                contrast = enumOr(prefs[Keys.CONTRAST], defaults.appearance.contrast),
                motion = enumOr(prefs[Keys.MOTION], defaults.appearance.motion),
                haptics = prefs[Keys.HAPTICS] ?: defaults.appearance.haptics,
            )
        val speed = prefs[Keys.WALK_SPEED]?.takeIf { it in 0.3..3.0 } ?: WalkSettings.DEFAULT_SPEED
        val detour = prefs[Keys.DETOUR]?.takeIf { it in 1.0..3.0 } ?: WalkSettings.DEFAULT_DETOUR
        val reminders =
            prefs[Keys.REMINDERS]
                ?.split(',')
                ?.mapNotNull { it.trim().toIntOrNull() }
                ?.filter { it in 0..ReminderPlan.MAX_MINUTES }
                ?.takeIf { it.isNotEmpty() } ?: defaults.defaultReminders
        return AppSettings(
            appearance = appearance,
            walkSpeed = speed,
            detourFactor = detour,
            routingEnabled = prefs[Keys.ROUTING] ?: defaults.routingEnabled,
            mapTilesEnabled = prefs[Keys.MAP_TILES] ?: defaults.mapTilesEnabled,
            defaultReminders = reminders,
            defaultBufferSeconds = prefs[Keys.BUFFER]?.coerceIn(0, MAX_BUFFER_SECONDS) ?: defaults.defaultBufferSeconds,
            units = enumOr(prefs[Keys.UNITS], defaults.units),
            accessibleByDefault = prefs[Keys.ACCESSIBLE] ?: defaults.accessibleByDefault,
        )
    }

    private fun write(
        prefs: androidx.datastore.preferences.core.MutablePreferences,
        s: AppSettings,
    ) {
        prefs[Keys.THEME] = s.appearance.theme.name
        prefs[Keys.PURE_BLACK] = s.appearance.pureBlack
        prefs[Keys.DYNAMIC_COLOR] = s.appearance.dynamicColor
        prefs[Keys.SEED] = s.appearance.seed
        prefs[Keys.STYLE] = s.appearance.style.name
        prefs[Keys.CONTRAST] = s.appearance.contrast.name
        prefs[Keys.MOTION] = s.appearance.motion.name
        prefs[Keys.HAPTICS] = s.appearance.haptics
        prefs[Keys.WALK_SPEED] = s.walkSpeed
        prefs[Keys.DETOUR] = s.detourFactor
        prefs[Keys.ROUTING] = s.routingEnabled
        prefs[Keys.MAP_TILES] = s.mapTilesEnabled
        prefs[Keys.REMINDERS] = s.defaultReminders.joinToString(",")
        prefs[Keys.BUFFER] = s.defaultBufferSeconds
        prefs[Keys.UNITS] = s.units.name
        prefs[Keys.ACCESSIBLE] = s.accessibleByDefault
    }

    private inline fun <reified E : Enum<E>> enumOr(
        value: String?,
        default: E,
    ): E = value?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

    private object Keys {
        val THEME = stringPreferencesKey("appearance.theme")
        val PURE_BLACK = booleanPreferencesKey("appearance.pure_black")
        val DYNAMIC_COLOR = booleanPreferencesKey("appearance.dynamic_color")
        val SEED = intPreferencesKey("appearance.seed")
        val STYLE = stringPreferencesKey("appearance.style")
        val CONTRAST = stringPreferencesKey("appearance.contrast")
        val MOTION = stringPreferencesKey("appearance.motion")
        val HAPTICS = booleanPreferencesKey("appearance.haptics")
        val WALK_SPEED = doublePreferencesKey("walk.speed")
        val DETOUR = doublePreferencesKey("walk.detour")
        val ROUTING = booleanPreferencesKey("routing.enabled")
        val MAP_TILES = booleanPreferencesKey("map.tiles")
        val REMINDERS = stringPreferencesKey("reminders.default")
        val BUFFER = intPreferencesKey("buffer.default_seconds")
        val UNITS = stringPreferencesKey("units")
        val ACCESSIBLE = booleanPreferencesKey("accessible.default")
    }

    private companion object {
        const val MAX_BUFFER_SECONDS = 30 * 60
    }
}

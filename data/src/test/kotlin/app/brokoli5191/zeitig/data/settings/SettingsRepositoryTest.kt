package app.brokoli5191.zeitig.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals

class SettingsRepositoryTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store by lazy {
        PreferenceDataStoreFactory.create(scope = scope) { temp.newFile("settings.preferences_pb").also { it.delete() } }
    }
    private val repository by lazy { SettingsRepository(store) }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun defaultsWithoutStoredValues() =
        runBlocking<Unit> {
            val settings = repository.current()
            assertEquals(AppSettings(), settings)
            assertEquals(1.3, settings.walkSettings.speedMetersPerSecond)
            assertEquals(false, settings.routingEnabled, "online routing is opt-in")
        }

    @Test
    fun updatesPersistEveryField() =
        runBlocking<Unit> {
            val wanted =
                AppSettings(
                    appearance =
                        Appearance(
                            ThemeMode.DARK,
                            true,
                            false,
                            Appearance.SEEDS[3],
                            SchemeStyle.FIDELITY,
                            ContrastPreference.HIGH,
                            MotionPreference.REDUCED,
                            false,
                        ),
                    walkSpeed = 1.5,
                    detourFactor = 1.2,
                    routingEnabled = true,
                    mapTilesEnabled = false,
                    defaultReminders = listOf(10, 2, 0),
                    defaultBufferSeconds = 120,
                    units = DistanceUnit.IMPERIAL,
                    accessibleByDefault = true,
                )
            repository.update { wanted }
            assertEquals(wanted, repository.current())
        }

    @Test
    fun brokenValuesFallBackToDefaults() =
        runBlocking<Unit> {
            store.edit {
                it[stringPreferencesKey("appearance.theme")] = "PURPLE"
                it[stringPreferencesKey("reminders.default")] = "x,99,-3"
            }
            val settings = repository.current()
            assertEquals(ThemeMode.SYSTEM, settings.appearance.theme)
            assertEquals(listOf(5, 3, 1, 0), settings.defaultReminders)
        }
}

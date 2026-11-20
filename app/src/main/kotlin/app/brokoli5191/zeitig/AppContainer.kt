package app.brokoli5191.zeitig

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import app.brokoli5191.zeitig.core.network.TransitNetwork
import app.brokoli5191.zeitig.core.route.RouteStatusCalculator
import app.brokoli5191.zeitig.core.stats.RouteStatistics
import app.brokoli5191.zeitig.data.db.ZeitigDatabase
import app.brokoli5191.zeitig.data.live.LiveDepartures
import app.brokoli5191.zeitig.data.map.MapStyleRepository
import app.brokoli5191.zeitig.data.net.HttpClients
import app.brokoli5191.zeitig.data.net.RateLimiter
import app.brokoli5191.zeitig.data.ogd.WienerLinienApi
import app.brokoli5191.zeitig.data.ogd.WienerLinienProvider
import app.brokoli5191.zeitig.data.reference.ReferenceDataRepository
import app.brokoli5191.zeitig.data.reference.ReferenceRefresher
import app.brokoli5191.zeitig.data.reference.SnapshotSource
import app.brokoli5191.zeitig.data.repo.RouteRepository
import app.brokoli5191.zeitig.data.routing.TransitousPlanner
import app.brokoli5191.zeitig.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * Manual dependency graph. Everything long-lived is created here exactly once; screens,
 * services and widgets receive what they need from this container instead of a DI framework.
 */
class AppContainer(
    context: Context,
) {
    val appContext: Context = context.applicationContext
    val clock: Clock = Clock.systemDefaultZone()
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val httpClient by lazy { HttpClients.create(BuildConfig.VERSION_NAME) }

    /** The one and only gate in front of wienerlinien.at (≥ 15 s between requests). */
    private val wienerLinienLimiter = RateLimiter(15.seconds)
    private val transitousLimiter = RateLimiter(1.seconds)

    val wienerLinienApi by lazy { WienerLinienApi(httpClient, wienerLinienLimiter, clock) }
    val liveDepartures by lazy { LiveDepartures(WienerLinienProvider(wienerLinienApi), wienerLinienApi, clock) }

    private val ogdDirectory = File(appContext.filesDir, "ogd")
    private val bundledSnapshot = SnapshotSource { name -> appContext.assets.open("ogd/$name") }
    val referenceData by lazy { ReferenceDataRepository(bundledSnapshot, File(ogdDirectory, "current"), Dispatchers.IO) }
    val referenceRefresher by lazy {
        ReferenceRefresher(httpClient, wienerLinienLimiter, clock, bundledSnapshot, ogdDirectory, Dispatchers.IO)
    }

    suspend fun network(): TransitNetwork = referenceData.network()

    val planner by lazy { TransitousPlanner(httpClient, transitousLimiter, { network() }) }

    private val database by lazy { ZeitigDatabase.create(appContext) }
    val routes by lazy { RouteRepository(database.routes(), database.trips(), clock) }

    val settings by lazy {
        SettingsRepository(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) {
                appContext.preferencesDataStoreFile("settings")
            },
        )
    }

    val mapStyles by lazy { MapStyleRepository(httpClient, File(appContext.cacheDir, "map"), clock, Dispatchers.IO) }

    val statusCalculator = RouteStatusCalculator()
    val statistics = RouteStatistics
}

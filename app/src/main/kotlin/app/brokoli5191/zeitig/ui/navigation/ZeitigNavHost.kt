package app.brokoli5191.zeitig.ui.navigation

import android.app.Activity
import android.content.Intent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import app.brokoli5191.zeitig.AppContainer
import app.brokoli5191.zeitig.BuildConfig
import app.brokoli5191.zeitig.R
import app.brokoli5191.zeitig.locale.AppLanguages
import app.brokoli5191.zeitig.location.OneShotLocation
import app.brokoli5191.zeitig.monitor.MonitoringService
import app.brokoli5191.zeitig.notify.NotificationSettings
import app.brokoli5191.zeitig.notify.rememberNotificationStatus
import app.brokoli5191.zeitig.trip.TripService
import app.brokoli5191.zeitig.ui.editor.EditorActions
import app.brokoli5191.zeitig.ui.editor.RouteEditorScreen
import app.brokoli5191.zeitig.ui.editor.RouteEditorViewModel
import app.brokoli5191.zeitig.ui.expressive.detailPaneMetadata
import app.brokoli5191.zeitig.ui.expressive.listPaneMetadata
import app.brokoli5191.zeitig.ui.expressive.rememberListDetailStrategy
import app.brokoli5191.zeitig.ui.factory
import app.brokoli5191.zeitig.ui.home.HomeScreen
import app.brokoli5191.zeitig.ui.home.HomeViewModel
import app.brokoli5191.zeitig.ui.map.RouteMap
import app.brokoli5191.zeitig.ui.route.RouteDetailScreen
import app.brokoli5191.zeitig.ui.route.RouteDetailViewModel
import app.brokoli5191.zeitig.ui.settings.AboutScreen
import app.brokoli5191.zeitig.ui.settings.AppearanceActions
import app.brokoli5191.zeitig.ui.settings.AppearanceScreen
import app.brokoli5191.zeitig.ui.settings.LibraryUi
import app.brokoli5191.zeitig.ui.settings.LicencesScreen
import app.brokoli5191.zeitig.ui.settings.SettingsActions
import app.brokoli5191.zeitig.ui.settings.SettingsScreen
import app.brokoli5191.zeitig.ui.settings.SettingsViewModel
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.util.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ZeitigNavHost(
    container: AppContainer,
    permissions: PermissionRequests,
    openRouteRequests: Flow<Long>,
    importRequests: Flow<String>,
    modifier: Modifier = Modifier,
) {
    val requestLocationPermission = permissions::location
    val backStack = rememberNavBackStack(HomeKey)
    LaunchedEffect(importRequests) {
        importRequests.collect { text ->
            backStack.removeAll { it is EditorKey }
            backStack.add(EditorKey(sharedText = text))
        }
    }
    LaunchedEffect(openRouteRequests) {
        openRouteRequests.collect { id ->
            backStack.removeAll { it is RouteKey || it is EditorKey }
            backStack.add(RouteKey(id))
        }
    }
    val countdownRoutes by container.statusBoard.active.collectAsStateWithLifecycle()
    val notificationStatus = rememberNotificationStatus()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listDetail = rememberListDetailStrategy<NavKey>()
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val tiles = settings?.mapTilesEnabled ?: true
    val shareTitle = stringResource(R.string.share_eta)
    val myLocationLabel = stringResource(R.string.my_location)

    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))

    fun share(text: String) =
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
                shareTitle,
            ),
        )

    SharedTransitionLayout(modifier) {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
                sceneStrategies = listOf(listDetail, SinglePaneSceneStrategy()),
                sharedTransitionScope = this,
                entryProvider =
                    entryProvider {
                        entry<HomeKey>(metadata = listPaneMetadata()) {
                            Visible {
                                val vm: HomeViewModel = viewModel(factory = factory { HomeViewModel(it) })
                                val state by vm.state.collectAsStateWithLifecycle()
                                HomeScreen(
                                    state = state,
                                    onAddRoute = { backStack.add(EditorKey()) },
                                    onOpenRoute = { id ->
                                        backStack.removeAll { it is RouteKey }
                                        backStack.add(RouteKey(id))
                                    },
                                    onOpenSettings = { backStack.add(SettingsKey) },
                                )
                            }
                        }
                        entry<RouteKey>(metadata = detailPaneMetadata()) { key ->
                            Visible {
                                val vm: RouteDetailViewModel =
                                    viewModel(key = "route-${key.id}", factory = factory { RouteDetailViewModel(it, key.id) })
                                val state by vm.state.collectAsStateWithLifecycle()
                                RouteDetailScreen(
                                    state = state,
                                    onBack = { backStack.removeLastOrNull() },
                                    onEdit = { backStack.add(EditorKey(key.id)) },
                                    onDelete = { vm.delete { backStack.removeAll { it == key } } },
                                    onShareEta = ::share,
                                    onResetLearning = vm::resetLearning,
                                    watching = key.id in countdownRoutes,
                                    onWatch = { on ->
                                        if (on) {
                                            // Without notifications the countdown still runs in the app, widget and tile.
                                            permissions.notifications { MonitoringService.start(context, key.id) }
                                        } else {
                                            context.startService(MonitoringService.unwatchIntent(context, key.id))
                                        }
                                    },
                                    onStartTrip = {
                                        requestLocationPermission { granted ->
                                            if (granted) TripService.start(context, key.id)
                                        }
                                    },
                                    map = { route, mod -> RouteMap(route.journey, tiles, mod) },
                                )
                            }
                        }
                        entry<EditorKey> { key ->
                            val vm: RouteEditorViewModel =
                                viewModel(
                                    key = "editor-${key.id}-${key.sharedText.hashCode()}",
                                    factory = factory { RouteEditorViewModel(it, key.id, key.sharedText) },
                                )
                            val state by vm.state.collectAsStateWithLifecycle()
                            RouteEditorScreen(
                                state = state,
                                actions =
                                    EditorActions(
                                        onBack = { backStack.removeLastOrNull() },
                                        onSave = {
                                            vm.save { id ->
                                                backStack.removeLastOrNull()
                                                if (key.id == 0L) backStack.add(RouteKey(id))
                                            }
                                        },
                                        onName = vm::setName,
                                        onPick = vm::openSearch,
                                        onPlan = vm::plan,
                                        onSelect = vm::select,
                                        onLine = vm::chooseLine,
                                        onTimeMode = vm::setTimeMode,
                                        onDays = vm::setDays,
                                        onWindowStart = { vm.setWindow(start = it) },
                                        onWindowEnd = { vm.setWindow(end = it) },
                                        onArriveBy = vm::setArriveBy,
                                        onReminder = vm::toggleReminder,
                                        onBuffer = vm::setBuffer,
                                        onAccessible = vm::setAccessible,
                                        onNotifications = vm::setNotifications,
                                        onQuery = vm::query,
                                        onStation = vm::chooseStation,
                                        onAddress = vm::chooseAddress,
                                        onMyLocation = {
                                            requestLocationPermission { granted ->
                                                if (!granted) {
                                                    vm.locationFailed()
                                                } else {
                                                    vm.locating(true)
                                                    scope.launch {
                                                        val point = OneShotLocation.current(context)
                                                        if (point ==
                                                            null
                                                        ) {
                                                            vm.locationFailed()
                                                        } else {
                                                            vm.chooseCoordinates(myLocationLabel, point)
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        onCoordinates = { text ->
                                            val point = parseCoordinates(text)
                                            if (point != null) vm.chooseCoordinates(text.trim(), point)
                                            point != null
                                        },
                                        onCloseSearch = vm::closeSearch,
                                        onRetryImport = { vm.importShared() },
                                        onPlanOnline = vm::enableOnlinePlanning,
                                        onDismissImport = vm::dismissImport,
                                    ),
                                map = { journey, mod -> RouteMap(journey, tiles, mod) },
                            )
                        }
                        entry<SettingsKey> {
                            val vm: SettingsViewModel = viewModel(factory = factory { SettingsViewModel(it) })
                            val state by vm.state.collectAsStateWithLifecycle()
                            SettingsScreen(
                                state = state,
                                actions =
                                    SettingsActions(
                                        onBack = { backStack.removeLastOrNull() },
                                        onAllowNotifications = {
                                            permissions.notifications { granted ->
                                                if (!granted) NotificationSettings.openNotifications(context)
                                            }
                                        },
                                        onExactAlarms = { NotificationSettings.openExactAlarms(context) },
                                        onLiveUpdates = { NotificationSettings.openLiveUpdates(context) },
                                        onAppearance = { backStack.add(AppearanceKey) },
                                        onAbout = { backStack.add(AboutKey) },
                                        onUpdate = vm::update,
                                        onRefreshData = vm::refreshData,
                                    ),
                                notifications = notificationStatus,
                            )
                        }
                        entry<AppearanceKey> {
                            val vm: SettingsViewModel = viewModel(factory = factory { SettingsViewModel(it) })
                            val state by vm.state.collectAsStateWithLifecycle()
                            var language by remember { mutableStateOf(AppLanguages.current(context)) }
                            AppearanceScreen(
                                appearance = state.settings.appearance,
                                language = language,
                                actions =
                                    AppearanceActions(
                                        onBack = { backStack.removeLastOrNull() },
                                        onAppearance = vm::updateAppearance,
                                        onLanguage = { chosen ->
                                            language = chosen
                                            if (AppLanguages.set(context, chosen)) (context as? Activity)?.recreate()
                                        },
                                    ),
                            )
                        }
                        entry<AboutKey> {
                            AboutScreen(
                                version = BuildConfig.VERSION_NAME,
                                onBack = { backStack.removeLastOrNull() },
                                onOpenUrl = ::open,
                                onLicences = { backStack.add(LicencesKey) },
                            )
                        }
                        entry<LicencesKey> {
                            val libraries =
                                remember {
                                    Libs
                                        .Builder()
                                        .withContext(context)
                                        .build()
                                        .libraries
                                        .map { lib ->
                                            LibraryUi(
                                                name = lib.name,
                                                version = lib.artifactVersion,
                                                licenses = lib.licenses.joinToString { it.name },
                                                licenseText = lib.licenses.firstOrNull()?.licenseContent,
                                            )
                                        }.sortedBy { it.name.lowercase() }
                                }
                            LicencesScreen(libraries, onBack = { backStack.removeLastOrNull() })
                        }
                    },
            )
        }
    }
}

/** Runtime permission requests, implemented by the activity. Callbacks get whether it was granted. */
interface PermissionRequests {
    fun location(onResult: (Boolean) -> Unit)

    fun notifications(onResult: (Boolean) -> Unit)
}

/** Provides the entry's animated scope for shared bounds; harmless where none exists. */
@Composable
private fun Visible(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalEntryVisibilityScope provides LocalNavAnimatedContentScope.current, content = content)
}

/** "48.2082, 16.3738" or "48.2082 16.3738" → a point; null for anything else. */
fun parseCoordinates(text: String): app.brokoli5191.zeitig.core.model.GeoPoint? {
    val parts = text.trim().split(Regex("""[,;\s]+""")).filter { it.isNotEmpty() }
    if (parts.size != 2) return null
    val lat = parts[0].toDoubleOrNull() ?: return null
    val lon = parts[1].toDoubleOrNull() ?: return null
    if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
    return app.brokoli5191.zeitig.core.model.GeoPoint(lat, lon)
}

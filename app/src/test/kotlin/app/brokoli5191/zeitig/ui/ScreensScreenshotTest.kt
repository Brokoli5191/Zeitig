package app.brokoli5191.zeitig.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.brokoli5191.zeitig.data.settings.Appearance
import app.brokoli5191.zeitig.locale.AppLanguage
import app.brokoli5191.zeitig.ui.editor.EditorActions
import app.brokoli5191.zeitig.ui.editor.EditorUiState
import app.brokoli5191.zeitig.ui.editor.PlaceUi
import app.brokoli5191.zeitig.ui.editor.RouteEditorScreen
import app.brokoli5191.zeitig.ui.editor.TimeMode
import app.brokoli5191.zeitig.ui.home.HomeScreen
import app.brokoli5191.zeitig.ui.home.HomeUiState
import app.brokoli5191.zeitig.ui.home.RouteCardUi
import app.brokoli5191.zeitig.ui.map.SchematicRouteMap
import app.brokoli5191.zeitig.ui.route.RouteDetailScreen
import app.brokoli5191.zeitig.ui.route.RouteDetailUiState
import app.brokoli5191.zeitig.ui.settings.AboutScreen
import app.brokoli5191.zeitig.ui.settings.AppearanceActions
import app.brokoli5191.zeitig.ui.settings.AppearanceScreen
import app.brokoli5191.zeitig.ui.settings.SettingsActions
import app.brokoli5191.zeitig.ui.settings.SettingsScreen
import app.brokoli5191.zeitig.ui.settings.SettingsUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Every main screen in every [Variant]; the images are the README screenshots. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class ScreensScreenshotTest(
    private val variant: Variant,
) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun home() =
        compose.snapshot("home", variant) {
            HomeScreen(
                state =
                    HomeUiState(
                        loading = false,
                        routes =
                            listOf(
                                RouteCardUi(Samples.work, Samples.workStatus()),
                                RouteCardUi(Samples.gym, Samples.gymStatus()),
                                RouteCardUi(Samples.uni, null),
                            ),
                        now = Samples.now,
                    ),
                onAddRoute = {},
                onOpenRoute = {},
                onOpenSettings = {},
            )
        }

    @Test
    fun homeEmpty() =
        compose.snapshot("home_empty", variant) {
            HomeScreen(HomeUiState(loading = false), onAddRoute = {}, onOpenRoute = {}, onOpenSettings = {})
        }

    @Test
    fun routeDetail() =
        compose.snapshot("route", variant) {
            RouteDetailScreen(
                state =
                    RouteDetailUiState(
                        loading = false,
                        route = Samples.work,
                        status = Samples.workStatus(),
                        stats = Samples.stats,
                        now = Samples.now,
                    ),
                onBack = {},
                onEdit = {},
                onDelete = {},
                onShareEta = {},
                onResetLearning = {},
                map = { route, modifier -> SchematicRouteMap(route.journey, "map", modifier) },
            )
        }

    @Test
    fun editor() =
        compose.snapshot("editor", variant) {
            RouteEditorScreen(
                state =
                    EditorUiState(
                        name = "Arbeit",
                        origin = PlaceUi("Home", null),
                        destination = PlaceUi("Office", null),
                        timeMode = TimeMode.ARRIVE_BY,
                        routingEnabled = true,
                        itineraries = Samples.itineraries,
                        selected = 0,
                    ),
                actions = EditorActions(),
                map = { journey, modifier -> SchematicRouteMap(journey, "map", modifier) },
            )
        }

    @Test
    fun settings() =
        compose.snapshot("settings", variant) {
            SettingsScreen(SettingsUiState(), SettingsActions())
        }

    @Test
    fun appearance() =
        compose.snapshot("appearance", variant) {
            AppearanceScreen(Appearance(dynamicColor = false), AppLanguage.SYSTEM, AppearanceActions(), dynamicColorAvailable = true)
        }

    @Test
    fun about() =
        compose.snapshot("about", variant) {
            AboutScreen(version = "0.1.0", onBack = {}, onOpenUrl = {}, onLicences = {})
        }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> = Variant.entries.map { arrayOf<Any>(it) }
    }
}

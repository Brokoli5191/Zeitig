package app.brokoli5191.zeitig.data.ogd

import app.brokoli5191.zeitig.core.model.Diva
import app.brokoli5191.zeitig.core.model.StopId
import app.brokoli5191.zeitig.core.provider.DepartureRequest
import app.brokoli5191.zeitig.core.provider.FailureReason
import app.brokoli5191.zeitig.core.provider.ProviderResult
import app.brokoli5191.zeitig.data.Fixtures
import app.brokoli5191.zeitig.data.net.HttpClients
import app.brokoli5191.zeitig.data.net.RateLimiter
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration

class WienerLinienApiTest {
    private val server = MockWebServer()
    private lateinit var api: WienerLinienApi

    @Before
    fun setUp() {
        server.start()
        api =
            WienerLinienApi(
                client = HttpClients.create("1.2.3"),
                rateLimiter = RateLimiter(Duration.ZERO),
                clock = Clock.fixed(Instant.parse("2026-11-20T21:00:00Z"), ZoneOffset.UTC),
                baseUrl = server.url("/ogd_realtime/"),
            )
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun respond(
        body: String,
        code: Int = 200,
    ) = server.enqueue(
        MockResponse
            .Builder()
            .code(code)
            .body(body)
            .build(),
    )

    @Test
    fun monitorRequestCarriesStopsTrafficInfoAndHeaders() =
        runBlocking<Unit> {
            respond(Fixtures.text("monitor_stopids.json"))
            val result = api.monitor(listOf(StopId(4116), StopId(4113), StopId(4113)), listOf(Diva(60201198)))
            assertIs<ProviderResult.Success>(result)
            val request = server.takeRequest()
            assertEquals("/ogd_realtime/monitor", request.url.encodedPath)
            assertEquals(listOf("4113", "4116"), request.url.queryParameterValues("stopId"))
            assertEquals(listOf("60201198"), request.url.queryParameterValues("diva"))
            assertEquals(
                listOf("stoerungkurz", "stoerunglang", "aufzugsinfo"),
                request.url.queryParameterValues("activateTrafficInfo"),
            )
            assertEquals("application/json", request.headers["Accept"])
            assertTrue(request.headers["User-Agent"]!!.startsWith("Zeitig/1.2.3"))
        }

    @Test
    fun trafficInfoListFiltersByLinesAndStops() =
        runBlocking<Unit> {
            respond(Fixtures.text("trafficinfolist_aufzug.json"))
            api.trafficInfoList(lines = listOf("U4", "13A", "U4"), stopIds = listOf(StopId(4103)), categories = listOf("aufzugsinfo"))
            val url = server.takeRequest().url
            assertEquals("/ogd_realtime/trafficInfoList", url.encodedPath)
            assertEquals(listOf("13A", "U4"), url.queryParameterValues("relatedLine"))
            assertEquals(listOf("4103"), url.queryParameterValues("relatedStop"))
            assertEquals(listOf("aufzugsinfo"), url.queryParameterValues("name"))
        }

    @Test
    fun providerOmitsTrafficInfoWhenNotRequested() =
        runBlocking<Unit> {
            respond(Fixtures.text("monitor_stopids.json"))
            val provider = WienerLinienProvider(api)
            assertEquals("wienerlinien", provider.id)
            provider.departures(DepartureRequest(stopIds = setOf(StopId(4113)), includeDisruptions = false))
            assertTrue(server.takeRequest().url.queryParameterValues("activateTrafficInfo").isEmpty())
        }

    @Test
    fun httpStatusCodesMapToFailureReasons() =
        runBlocking<Unit> {
            respond("", code = 429)
            respond("", code = 503)
            respond("", code = 404)
            assertEquals(FailureReason.RATE_LIMITED, (api.monitor(listOf(StopId(1))) as ProviderResult.Failure).reason)
            assertEquals(FailureReason.SERVER, (api.monitor(listOf(StopId(1))) as ProviderResult.Failure).reason)
            assertEquals(FailureReason.INVALID_REQUEST, (api.monitor(listOf(StopId(1))) as ProviderResult.Failure).reason)
        }

    @Test
    fun connectionFailureIsANetworkFailure() =
        runBlocking<Unit> {
            val url = server.url("/ogd_realtime/")
            server.close()
            val offline =
                WienerLinienApi(HttpClients.create("1"), RateLimiter(Duration.ZERO), Clock.systemUTC(), url)
            val result = offline.monitor(listOf(StopId(1)))
            assertEquals(FailureReason.NETWORK, assertIs<ProviderResult.Failure>(result).reason)
        }
}

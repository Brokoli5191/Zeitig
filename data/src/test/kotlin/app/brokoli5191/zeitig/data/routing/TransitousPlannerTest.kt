package app.brokoli5191.zeitig.data.routing

import app.brokoli5191.zeitig.core.journey.Leg
import app.brokoli5191.zeitig.core.model.Direction
import app.brokoli5191.zeitig.core.model.GeoPoint
import app.brokoli5191.zeitig.core.model.TransportType
import app.brokoli5191.zeitig.core.network.TransitNetwork
import app.brokoli5191.zeitig.core.provider.FailureReason
import app.brokoli5191.zeitig.core.routing.GeocodeResult
import app.brokoli5191.zeitig.core.routing.PlaceKind
import app.brokoli5191.zeitig.core.routing.PlanRequest
import app.brokoli5191.zeitig.core.routing.PlanResult
import app.brokoli5191.zeitig.data.Fixtures
import app.brokoli5191.zeitig.data.net.HttpClients
import app.brokoli5191.zeitig.data.net.RateLimiter
import app.brokoli5191.zeitig.data.reference.DirectorySnapshotSource
import app.brokoli5191.zeitig.data.reference.SnapshotLoader
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration

class TransitousPlannerTest {
    private val server = MockWebServer()
    private var network: TransitNetwork? = null
    private lateinit var planner: TransitousPlanner

    private val kagran = GeoPoint(48.2433, 16.4339)
    private val meidling = GeoPoint(48.1745, 16.3337)
    private val time = Instant.parse("2026-11-23T06:30:00Z")

    @Before
    fun setUp() {
        server.start()
        planner = TransitousPlanner(HttpClients.create("test"), RateLimiter(Duration.ZERO), { network }, server.url("/api/"))
    }

    @After
    fun tearDown() = server.close()

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

    private fun fixture(name: String) = Fixtures.text("transitous/$name")

    @Test
    fun planRequestCarriesAllParameters() =
        runBlocking<Unit> {
            respond(fixture("plan_transfer_kagran_meidling.json"))
            planner.plan(PlanRequest(kagran, meidling, time, arriveBy = true, accessible = true, language = "en", itineraries = 3))
            val url = server.takeRequest().url
            assertEquals("/api/v6/plan", url.encodedPath)
            assertEquals("48.243300,16.433900", url.queryParameter("fromPlace"))
            assertEquals("48.174500,16.333700", url.queryParameter("toPlace"))
            assertEquals("2026-11-23T06:30:00Z", url.queryParameter("time"))
            assertEquals("true", url.queryParameter("arriveBy"))
            assertEquals("WHEELCHAIR", url.queryParameter("pedestrianProfile"))
            assertEquals("3", url.queryParameter("numItineraries"))
            assertEquals("en", url.queryParameter("language"))
        }

    @Test
    fun transferJourneyIsMappedLegByLeg() =
        runBlocking<Unit> {
            respond(fixture("plan_transfer_kagran_meidling.json"))
            val result = assertIs<PlanResult.Success>(planner.plan(PlanRequest(kagran, meidling, time)))
            assertEquals(3, result.itineraries.size)
            val first = result.itineraries.first()
            assertEquals(Instant.parse("2026-11-23T06:30:00Z"), first.departure)
            assertEquals(Instant.parse("2026-11-23T06:59:00Z"), first.arrival)
            assertEquals(1, first.transfers)
            assertFalse(first.realtime)
            val legs = first.journey.legs
            assertEquals(listOf("Walk", "Transit", "Walk", "Transit", "Walk"), legs.map { it::class.simpleName })
            val u1 = legs[1] as Leg.Transit
            assertEquals("U1", u1.line)
            assertEquals(TransportType.METRO, u1.type)
            assertEquals("Kagran", u1.from.name, "the 'Wien ' prefix is dropped")
            assertTrue(u1.geometry.size > 10)
            assertNotNull(u1.color)
            assertEquals(TransportType.TRAIN, (legs[3] as Leg.Transit).type)
            assertNull(u1.boarding, "without a network nothing is matched")
        }

    @Test
    fun wienerLinienLegsAreMatchedToStopPoints() =
        runBlocking<Unit> {
            network = SnapshotLoader.load(DirectorySnapshotSource(Fixtures.bundledSnapshot))
            respond(fixture("plan_wheelchair_schwedenplatz_westbahnhof.json"))
            val result =
                assertIs<PlanResult.Success>(planner.plan(PlanRequest(GeoPoint(48.2117, 16.3779), GeoPoint(48.1966, 16.3380), time)))
            val journey = result.itineraries.first().journey
            val (u4, u6) = journey.transitLegs
            assertEquals("U4", u4.line)
            val boarding = assertNotNull(u4.boarding)
            assertEquals(Direction.INBOUND, boarding.direction, "U4 towards Hütteldorf is 'R'")
            assertEquals(boarding.stopId, u4.from.stopId)
            assertNotNull(u4.to.stopId)
            assertEquals("U6", u6.line)
            assertNotNull(u6.boarding)
            assertTrue(journey.interchangeStops.all { it.stopId != null })
        }

    @Test
    fun foreignOperatorsStayUnmatched() =
        runBlocking<Unit> {
            network = SnapshotLoader.load(DirectorySnapshotSource(Fixtures.bundledSnapshot))
            respond(fixture("plan_transfer_kagran_meidling.json"))
            val journey = assertIs<PlanResult.Success>(planner.plan(PlanRequest(kagran, meidling, time))).itineraries.first().journey
            assertNotNull(journey.firstTransit?.boarding)
            assertNull(journey.transitLegs[1].boarding, "an ÖBB train has no Wiener Linien real-time")
        }

    @Test
    fun arriveByFixtureParses() =
        runBlocking<Unit> {
            respond(fixture("plan_arriveby_stephansplatz_hbf.json"))
            val result =
                assertIs<PlanResult.Success>(
                    planner.plan(PlanRequest(GeoPoint(48.2085, 16.3721), GeoPoint(48.1859, 16.3773), time, arriveBy = true)),
                )
            assertTrue(result.itineraries.all { it.arrival <= Instant.parse("2026-11-23T06:45:00Z") })
            assertTrue(result.itineraries.all { it.journey.lines == setOf("U1") })
        }

    @Test
    fun errorsMapToFailureReasons() =
        runBlocking<Unit> {
            respond(fixture("plan_bad_request.json"), code = 404)
            respond("", code = 503)
            respond("", code = 429)
            respond("{ not json")
            val request = PlanRequest(kagran, meidling, time)
            val notFound = assertIs<PlanResult.Failure>(planner.plan(request))
            assertEquals(FailureReason.INVALID_REQUEST, notFound.reason)
            assertEquals("unknown feed id \"\"", notFound.message)
            assertEquals(FailureReason.SERVER, (planner.plan(request) as PlanResult.Failure).reason)
            assertEquals(FailureReason.RATE_LIMITED, (planner.plan(request) as PlanResult.Failure).reason)
            assertEquals(FailureReason.PARSE, (planner.plan(request) as PlanResult.Failure).reason)
        }

    @Test
    fun offlineIsANetworkFailure() =
        runBlocking<Unit> {
            val url = server.url("/api/")
            server.close()
            val offline = TransitousPlanner(HttpClients.create("test"), RateLimiter(Duration.ZERO), { null }, url)
            assertEquals(FailureReason.NETWORK, (offline.plan(PlanRequest(kagran, meidling, time)) as PlanResult.Failure).reason)
            assertEquals(FailureReason.NETWORK, (offline.geocode("Karlsplatz") as GeocodeResult.Failure).reason)
        }

    @Test
    fun geocodeCollapsesDuplicatesAndNamesTheDistrict() =
        runBlocking<Unit> {
            respond(fixture("geocode_mariahilfer_strasse.json"))
            val matches = assertIs<GeocodeResult.Success>(planner.geocode("Mariahilfer Straße 85")).matches
            val first = matches.first()
            assertEquals("Mariahilfer Straße 85-87", first.name)
            assertEquals(PlaceKind.ADDRESS, first.kind)
            assertEquals("Mariahilf", first.area)
            assertEquals("1060", first.postalCode)
            assertEquals(1, matches.count { it.name == first.name }, "entries a few metres apart collapse")
            val url = server.takeRequest().url
            assertEquals("/api/v1/geocode", url.encodedPath)
            assertEquals("Mariahilfer Straße 85", url.queryParameter("text"))
            assertEquals("48.208200,16.373800", url.queryParameter("place"))
        }

    @Test
    fun geocodeDropsFarAwayMatches() =
        runBlocking<Unit> {
            respond(fixture("geocode_stop_karlsplatz.json"))
            val matches = assertIs<GeocodeResult.Success>(planner.geocode("Karlsplatz", limit = 5)).matches
            assertTrue(matches.isNotEmpty())
            assertTrue(matches.all { it.location.distanceTo(TransitousPlanner.VIENNA) < 80_000 }, matches.toString())
            assertTrue(matches.none { it.name.contains("Stachus") || it.name.contains("Essen") })
            assertEquals(PlaceKind.STOP, matches.first().kind)
        }

    @Test
    fun geocodeIgnoresTooShortQueries() =
        runBlocking<Unit> {
            assertEquals(GeocodeResult.Success(emptyList()), planner.geocode(" a "))
            assertEquals(0, server.requestCount)
        }
}

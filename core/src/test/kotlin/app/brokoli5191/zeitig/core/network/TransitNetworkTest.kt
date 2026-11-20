package app.brokoli5191.zeitig.core.network

import app.brokoli5191.zeitig.core.journey.Journey
import app.brokoli5191.zeitig.core.journey.Leg
import app.brokoli5191.zeitig.core.journey.LegPoint
import app.brokoli5191.zeitig.core.model.Direction
import app.brokoli5191.zeitig.core.model.Diva
import app.brokoli5191.zeitig.core.model.GeoPoint
import app.brokoli5191.zeitig.core.model.Line
import app.brokoli5191.zeitig.core.model.LinePattern
import app.brokoli5191.zeitig.core.model.LineTarget
import app.brokoli5191.zeitig.core.model.Station
import app.brokoli5191.zeitig.core.model.StopId
import app.brokoli5191.zeitig.core.model.StopPoint
import app.brokoli5191.zeitig.core.model.TransportType
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A miniature U1: A – B – C (both directions, platform per direction) plus a short turn B – C and
 * tram 2 stopping at B.
 */
class TransitNetworkTest {
    private val a = Diva(1)
    private val b = Diva(2)
    private val c = Diva(3)

    private fun point(
        id: Int,
        diva: Diva,
        name: String,
        lat: Double,
    ) = StopPoint(StopId(id), diva, name, "Wien", GeoPoint(lat, 16.37))

    private val stopPoints =
        listOf(
            point(11, a, "Alpha", 48.200), // A, towards C
            point(12, a, "Alpha", 48.2001), // A, towards A (terminus)
            point(21, b, "Beta", 48.210),
            point(22, b, "Beta", 48.2101),
            point(31, c, "Gamma", 48.220),
            point(32, c, "Gamma", 48.2201),
            point(23, b, "Beta", 48.2102), // tram 2
            point(99, Diva(9), "Nowhere", 48.5),
            StopPoint(StopId(98), null, "No location", null, null),
        )
    private val stations =
        listOf(
            Station(a, "Alpha", "Wien", GeoPoint(48.2, 16.37)),
            Station(b, "Beta", "Wien", GeoPoint(48.21, 16.37)),
            Station(c, "Gamma", "Wien", GeoPoint(48.22, 16.37)),
        )
    private val u1 = Line(301, "U1", 1, true, TransportType.METRO)
    private val tram2 = Line(102, "2", 102, true, TransportType.TRAM)
    private val patterns =
        listOf(
            LinePattern(301, 1, Direction.OUTBOUND, listOf(StopId(11), StopId(21), StopId(31))),
            LinePattern(301, 2, Direction.INBOUND, listOf(StopId(32), StopId(22), StopId(12))),
            LinePattern(301, 3, Direction.OUTBOUND, listOf(StopId(21), StopId(31))),
            LinePattern(102, 1, Direction.OUTBOUND, listOf(StopId(23), StopId(99))),
            LinePattern(777, 1, Direction.OUTBOUND, listOf(StopId(11), StopId(21))), // unknown line
        )
    private val network = TransitNetwork(stopPoints, stations, listOf(u1, tram2), patterns)

    @Test
    fun `lines at a stop point carry the terminus as towards`() {
        val atB = network.linesAt(StopId(21))
        assertEquals(1, atB.size)
        assertEquals("Gamma", atB.single().towards)
        assertEquals(LineTarget(StopId(21), "U1", Direction.OUTBOUND), atB.single().target)
        assertTrue(network.linesAt(StopId(31)).isEmpty(), "nobody departs at the terminus")
        assertTrue(network.linesAt(StopId(12345)).isEmpty())
    }

    @Test
    fun `station lists every line and direction, sorted`() {
        val atB = network.linesAtStation(b)
        assertEquals(
            listOf("U1" to Direction.OUTBOUND, "U1" to Direction.INBOUND, "2" to Direction.OUTBOUND),
            atB.map {
                it.line.name to
                    it.direction
            },
        )
        assertEquals("Alpha", atB.single { it.direction == Direction.INBOUND }.towards)
        assertEquals(3, network.stopPointsOf(b).size)
        assertTrue(network.linesAtStation(Diva(42)).isEmpty())
    }

    @Test
    fun `line lookup ignores case and formatting`() {
        assertEquals(u1, network.lineByName("u1"))
        assertNull(network.lineByName("U9"))
    }

    @Test
    fun `boarding match picks the platform heading towards the next stop`() {
        val atB = GeoPoint(48.21005, 16.37)
        assertEquals(
            LineTarget(StopId(21), "U1", Direction.OUTBOUND),
            network.matchBoarding("U1", boarding = atB, next = GeoPoint(48.220, 16.37)),
        )
        assertEquals(
            LineTarget(StopId(22), "U1", Direction.INBOUND),
            network.matchBoarding("U1", boarding = atB, next = GeoPoint(48.200, 16.37)),
        )
    }

    @Test
    fun `boarding match fails for unknown lines or far-away stops`() {
        assertNull(network.matchBoarding("U9", GeoPoint(48.21, 16.37), null))
        assertNull(network.matchBoarding("U1", GeoPoint(48.30, 16.37), null))
        // Without a next stop the closest boardable platform wins.
        assertEquals(StopId(11), network.matchBoarding("U1", GeoPoint(48.2000, 16.37), null)?.stopId)
    }

    @Test
    fun `nearest stop of a line`() {
        assertEquals(StopId(31), network.nearestStopOfLine("U1", GeoPoint(48.2199, 16.37)))
        assertNull(network.nearestStopOfLine("U1", GeoPoint(48.4, 16.37)))
        assertNull(network.nearestStopOfLine("U9", GeoPoint(48.22, 16.37)))
    }

    @Test
    fun `attaching a planned journey adds boarding targets and stop ids`() {
        val walk = Leg.Walk(LegPoint("Home", GeoPoint(48.209, 16.37)), LegPoint("Beta", GeoPoint(48.2100, 16.37)), Duration.ofMinutes(3))
        val u1 =
            Leg.Transit(
                from = LegPoint("Beta", GeoPoint(48.2100, 16.37)),
                to = LegPoint("Gamma", GeoPoint(48.2200, 16.37)),
                duration = Duration.ofMinutes(2),
                line = "U1",
                type = TransportType.METRO,
                headsign = "Gamma",
            )
        val train = u1.copy(line = "REX 7", from = LegPoint("Gamma", GeoPoint(48.22, 16.37)), to = LegPoint("Far", GeoPoint(48.5, 16.37)))
        val noLocation = u1.copy(from = LegPoint("Somewhere", null))
        val attached = network.attach(Journey(listOf(walk, u1, train, noLocation)))
        val matched = attached.legs[1] as Leg.Transit
        assertEquals(LineTarget(StopId(21), "U1", Direction.OUTBOUND), matched.boarding)
        assertEquals(StopId(21), matched.from.stopId)
        assertEquals(StopId(31), matched.to.stopId)
        assertEquals(train, attached.legs[2], "other operators stay on the timetable")
        assertEquals(noLocation, attached.legs[3])
        assertEquals(walk, attached.legs[0])
    }

    @Test
    fun `search is weighted by stop points per station`() {
        assertEquals(b, network.search.search("Beta").first().station.diva)
        assertEquals(3, network.search.size)
    }
}

package app.brokoli5191.zeitig.core.routing

import app.brokoli5191.zeitig.core.journey.Journey
import app.brokoli5191.zeitig.core.journey.Leg
import app.brokoli5191.zeitig.core.journey.LegPoint
import app.brokoli5191.zeitig.core.model.GeoPoint
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PolylineTest {
    @Test
    fun `decodes the reference example of the format`() {
        val points = Polyline.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@", precision = 5)
        assertEquals(listOf(GeoPoint(38.5, -120.2), GeoPoint(40.7, -120.95), GeoPoint(43.252, -126.453)), points)
    }

    @Test
    fun `round-trips at precision six`() {
        val route = listOf(GeoPoint(48.208469, 16.371939), GeoPoint(48.201254, 16.368858), GeoPoint(48.187104, 16.373314))
        assertEquals(route, Polyline.decode(Polyline.encode(route, 6), 6))
        assertEquals("", Polyline.encode(emptyList()))
        assertTrue(Polyline.decode("").isEmpty())
    }

    @Test
    fun `truncated input keeps the complete points`() {
        val encoded = Polyline.encode(listOf(GeoPoint(48.2, 16.37), GeoPoint(48.21, 16.38)), 6)
        assertEquals(1, Polyline.decode(encoded.dropLast(2), 6).size)
    }

    @Test
    fun `out-of-range points are dropped and bad precision rejected`() {
        // Encoded with six decimals but read with five: every value grows tenfold.
        assertTrue(Polyline.decode(Polyline.encode(listOf(GeoPoint(80.0, 170.0)), 6), 5).isEmpty())
        assertThrows<IllegalArgumentException> { Polyline.decode("abc", 0) }
    }

    @Test
    fun `plan requests are bounded and itineraries expose duration`() {
        assertThrows<IllegalArgumentException> { PlanRequest(GeoPoint(48.2, 16.3), GeoPoint(48.1, 16.3), Instant.EPOCH, itineraries = 0) }
        val leg = Leg.Walk(LegPoint("A", null), LegPoint("B", null), Duration.ofMinutes(9))
        val itinerary = Itinerary(Journey(listOf(leg)), Instant.EPOCH, Instant.EPOCH.plusSeconds(540), realtime = false)
        assertEquals(Duration.ofMinutes(9), itinerary.duration)
        assertEquals(0, itinerary.transfers)
    }
}

package app.brokoli5191.zeitig.core.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeoPointTest {
    private val stephansplatz = GeoPoint(48.20849, 16.37208)
    private val karlsplatz = GeoPoint(48.20066, 16.36968)

    @Test
    fun `haversine distance between two city stations`() {
        val d = stephansplatz.distanceTo(karlsplatz)
        assertTrue(abs(d - 888.0) < 10, "was $d")
    }

    @Test
    fun `distance is symmetric and zero to itself`() {
        assertEquals(stephansplatz.distanceTo(karlsplatz), karlsplatz.distanceTo(stephansplatz), 1e-9)
        assertEquals(0.0, stephansplatz.distanceTo(stephansplatz), 1e-9)
    }

    @Test
    fun `antipodal points are half the circumference apart`() {
        val d = GeoPoint(0.0, 0.0).distanceTo(GeoPoint(0.0, 180.0))
        assertEquals(Math.PI * GeoPoint.EARTH_RADIUS_M, d, 1.0)
    }

    @Test
    fun `rejects coordinates outside WGS84 ranges`() {
        assertThrows<IllegalArgumentException> { GeoPoint(91.0, 0.0) }
        assertThrows<IllegalArgumentException> { GeoPoint(0.0, -181.0) }
    }
}

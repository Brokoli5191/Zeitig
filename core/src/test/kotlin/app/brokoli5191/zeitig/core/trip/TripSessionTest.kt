package app.brokoli5191.zeitig.core.trip

import app.brokoli5191.zeitig.core.model.GeoPoint
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TripSessionTest {
    private val start = Instant.parse("2026-11-23T06:36:00Z")
    private val stop = GeoPoint(48.2030, 16.3375)

    private fun at(seconds: Long) = start.plusSeconds(seconds)

    @Test
    fun `arrives within forty metres of the stop`() {
        val session = TripSession(1, start, stop)
        assertIs<TripSession.State.Walking>(session.onLocation(GeoPoint(48.2010, 16.3330), 8f, at(60)))
        val arrived = assertIs<TripSession.State.Arrived>(session.onLocation(GeoPoint(48.20325, 16.3376), 10f, at(330)))
        assertEquals(Duration.ofSeconds(330), arrived.duration)
        assertEquals(false, arrived.manual)
        // Later fixes do not change a finished trip.
        assertSame(arrived, session.onLocation(GeoPoint(48.2010, 16.3330), 5f, at(400)))
        assertEquals(arrived, session.state)
    }

    @Test
    fun `an imprecise fix near the stop proves nothing`() {
        val session = TripSession(1, start, stop)
        val walking = assertIs<TripSession.State.Walking>(session.onLocation(stop, 120f, at(200)))
        assertTrue(walking.distanceMeters!! < 1.0)
        assertIs<TripSession.State.Arrived>(session.onLocation(stop, null, at(210)))
    }

    @Test
    fun `manual arrival and timeouts`() {
        val manual = TripSession(1, start, null)
        assertNull(assertIs<TripSession.State.Walking>(manual.onLocation(stop, 5f, at(10))).distanceMeters)
        assertEquals(TripSession.State.Arrived(Duration.ofSeconds(300), manual = true), manual.onManualArrival(at(300)))

        val forgotten = TripSession(1, start, stop)
        assertIs<TripSession.State.Walking>(forgotten.tick(at(60)))
        assertEquals(TripSession.State.TimedOut, forgotten.tick(at(31 * 60)))
        assertEquals(TripSession.State.TimedOut, forgotten.onManualArrival(at(32 * 60)))

        assertEquals(TripSession.State.TimedOut, TripSession(1, start, stop).onLocation(stop, 5f, at(40 * 60)))
        assertEquals(TripSession.State.TimedOut, TripSession(1, start, stop).onManualArrival(at(40 * 60)))
    }

    @Test
    fun `tick reports the last known distance`() {
        val session = TripSession(1, start, stop)
        session.onLocation(GeoPoint(48.2010, 16.3330), 8f, at(30))
        val walking = assertIs<TripSession.State.Walking>(session.tick(at(45)))
        assertEquals(Duration.ofSeconds(45), walking.elapsed)
        assertTrue(walking.distanceMeters!! > 300)
    }
}

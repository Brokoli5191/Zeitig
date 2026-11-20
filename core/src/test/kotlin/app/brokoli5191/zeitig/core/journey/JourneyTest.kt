package app.brokoli5191.zeitig.core.journey

import app.brokoli5191.zeitig.core.model.Direction
import app.brokoli5191.zeitig.core.model.Disruption
import app.brokoli5191.zeitig.core.model.DisruptionKind
import app.brokoli5191.zeitig.core.model.GeoPoint
import app.brokoli5191.zeitig.core.model.LineTarget
import app.brokoli5191.zeitig.core.model.StopId
import app.brokoli5191.zeitig.core.model.TransportType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JourneyTest {
    private fun min(m: Long) = Duration.ofMinutes(m)

    private val home = LegPoint("Home", GeoPoint(48.2085, 16.3721))
    private val stephansplatz = LegPoint("Stephansplatz", GeoPoint(48.2084, 16.3719), StopId(4116))
    private val karlsplatz = LegPoint("Karlsplatz", GeoPoint(48.2007, 16.3697), StopId(4119))
    private val karlsplatzU4 = LegPoint("Karlsplatz", GeoPoint(48.2005, 16.3693), StopId(4410))
    private val pilgramgasse = LegPoint("Pilgramgasse", GeoPoint(48.1925, 16.3542), StopId(4417))
    private val office = LegPoint("Office", GeoPoint(48.1920, 16.3530))

    private val journey =
        Journey(
            listOf(
                Leg.Walk(home, stephansplatz, min(4), 250.0),
                Leg.Transit(
                    stephansplatz,
                    karlsplatz,
                    min(2),
                    "U1",
                    TransportType.METRO,
                    "Oberlaa",
                    LineTarget(StopId(4116), "U1", Direction.INBOUND),
                ),
                Leg.Walk(karlsplatz, karlsplatzU4, min(3)),
                Leg.Transit(karlsplatzU4, pilgramgasse, min(4), "U4", TransportType.METRO, "Hütteldorf"),
                Leg.Walk(pilgramgasse, office, min(2)),
            ),
        )

    @Test
    fun `durations split at the first boarding`() {
        assertEquals(min(4), journey.plannedAccessWalk)
        assertEquals(min(11), journey.plannedOnward)
        assertEquals(min(15), journey.plannedTotal)
        assertEquals(1, journey.transfers)
        assertEquals(setOf("U1", "U4"), journey.lines)
        assertEquals("U1", journey.firstTransit?.line)
        assertEquals(home, journey.origin)
        assertEquals(office, journey.destination)
        assertEquals(listOf(stephansplatz, karlsplatz, karlsplatzU4, pilgramgasse), journey.interchangeStops)
    }

    @Test
    fun `relevant disruptions are those on its lines or at its interchanges`() {
        val u4 = Disruption("a", DisruptionKind.LONG, "U4", "", setOf("U4"), emptySet())
        val u6 = Disruption("b", DisruptionKind.LONG, "U6", "", setOf("U6"), emptySet())
        val elevatorHere = Disruption("c", DisruptionKind.ELEVATOR, "Karlsplatz", "", setOf("U4"), setOf(StopId(4410)))
        val elevatorElsewhere = Disruption("d", DisruptionKind.ELEVATOR, "Keplerplatz", "", setOf("U1"), setOf(StopId(4103)))
        val stopText = Disruption("e", DisruptionKind.SHORT, "Haltestelle verlegt", "", emptySet(), setOf(StopId(4417)))
        assertEquals(
            listOf(u4, elevatorHere, stopText),
            journey.relevantDisruptions(listOf(u4, u6, elevatorHere, elevatorElsewhere, stopText)),
        )
    }

    @Test
    fun `walking-only journeys have no boarding`() {
        val walk = Journey(listOf(Leg.Walk(home, office, min(25))))
        assertNull(walk.firstTransit)
        assertEquals(0, walk.transfers)
        assertEquals(min(25), walk.plannedAccessWalk)
        assertEquals(Duration.ZERO, walk.plannedOnward)
    }

    @Test
    fun `simple journey for offline routes`() {
        val target = LineTarget(StopId(1450), "49", Direction.OUTBOUND)
        val simple =
            Journey.simple(
                Place("Home", GeoPoint(48.2, 16.3)),
                LegPoint("Hütteldorfer Straße", GeoPoint(48.199, 16.312), StopId(1450)),
                "49",
                TransportType.TRAM,
                "Hütteldorf",
                target,
                min(5),
            )
        assertEquals(target, simple.firstTransit?.boarding)
        assertEquals(min(5), simple.plannedAccessWalk)
        assertEquals(Duration.ZERO, simple.plannedOnward)
    }

    @Test
    fun `a journey needs legs`() {
        assertThrows<IllegalArgumentException> { Journey(emptyList()) }
    }
}

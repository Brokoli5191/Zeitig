package app.brokoli5191.zeitig.core.model

import app.brokoli5191.zeitig.core.provider.DepartureRequest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModelTest {
    private val t0 = Instant.parse("2026-11-23T06:40:00Z")

    private fun departure(
        line: String = "49",
        stop: Int = 1450,
        direction: Direction? = Direction.OUTBOUND,
        planned: Instant = t0,
        realtime: Instant? = null,
    ) = Departure(StopId(stop), line, "Hütteldorf", direction, TransportType.TRAM, planned, realtime)

    @Test
    fun `time falls back to the timetable when there is no real-time value`() {
        val scheduled = departure()
        assertFalse(scheduled.isRealtime)
        assertEquals(t0, scheduled.time)
        val live = departure(realtime = t0.plusSeconds(90))
        assertTrue(live.isRealtime)
        assertEquals(t0.plusSeconds(90), live.time)
    }

    @Test
    fun `departure matches line, stop and direction of a target`() {
        val target = LineTarget(StopId(1450), "49", Direction.OUTBOUND)
        assertTrue(departure().matches(target))
        assertTrue(departure(line = "49").matches(target.copy(line = "49")))
        assertTrue(departure(direction = null).matches(target), "unknown direction is not excluded")
        assertTrue(departure().matches(target.copy(direction = null)))
        assertFalse(departure(direction = Direction.INBOUND).matches(target))
        assertFalse(departure(stop = 1451).matches(target))
        assertFalse(departure(line = "N49").matches(target))
        assertTrue(departure(line = "u4").matches(LineTarget(StopId(1450), "U4", Direction.OUTBOUND)))
    }

    @Test
    fun `stop board filters and sorts per target`() {
        val target = LineTarget(StopId(1450), "49", Direction.OUTBOUND)
        val late = departure(planned = t0.plusSeconds(600))
        val early = departure(planned = t0.plusSeconds(60))
        val other = departure(line = "52")
        val elevator =
            Disruption("a", DisruptionKind.ELEVATOR, "Aufzug", "", emptySet(), setOf(StopId(1450)))
        val unrelated = Disruption("b", DisruptionKind.LONG, "U6", "", setOf("U6"), emptySet())
        val board = StopBoard(t0, listOf(late, other, early), listOf(elevator, unrelated))
        assertEquals(listOf(early, late), board.departuresFor(target))
        assertEquals(listOf(elevator), board.disruptionsFor(target))
    }

    @Test
    fun `elevator outages only count for their stop unless they name no stop`() {
        val target = LineTarget(StopId(4103), "U1", Direction.OUTBOUND)
        val atStop = Disruption("e1", DisruptionKind.ELEVATOR, "", "", setOf("U1"), setOf(StopId(4103)))
        val elsewhere = Disruption("e2", DisruptionKind.ELEVATOR, "", "", setOf("U1"), setOf(StopId(4110)))
        val lineOnly = Disruption("e3", DisruptionKind.ESCALATOR, "", "", setOf("U1"), emptySet())
        val shortText = Disruption("s", DisruptionKind.SHORT, "", "", emptySet(), setOf(StopId(4103)))
        assertTrue(atStop.affects(target))
        assertFalse(elsewhere.affects(target))
        assertTrue(lineOnly.affects(target))
        assertTrue(shortText.affects(target))
    }

    @Test
    fun `transport types map from OGD values`() {
        assertEquals(TransportType.METRO, TransportType.fromOgd("ptMetro"))
        assertEquals(TransportType.TRAM, TransportType.fromOgd("ptTram"))
        assertEquals(TransportType.TRAM, TransportType.fromOgd("ptTramWLB"))
        assertEquals(TransportType.BUS, TransportType.fromOgd("ptBusCity"))
        assertEquals(TransportType.NIGHT_BUS, TransportType.fromOgd("ptBusNight"))
        assertEquals(TransportType.ON_DEMAND_BUS, TransportType.fromOgd("ptRufBus"))
        assertEquals(TransportType.TRAIN, TransportType.fromOgd("ptTrainS"))
        assertEquals(TransportType.OTHER, TransportType.fromOgd("ptHovercraft"))
        assertEquals(TransportType.OTHER, TransportType.fromOgd(null))
    }

    @Test
    fun `directions map from OGD flags`() {
        assertEquals(Direction.OUTBOUND, Direction.fromOgd("H"))
        assertEquals(Direction.INBOUND, Direction.fromOgd(" r "))
        assertEquals(Direction.OUTBOUND, Direction.fromOgd("1"))
        assertEquals(Direction.INBOUND, Direction.fromOgd("2"))
        assertNull(Direction.fromOgd("X"))
        assertNull(Direction.fromOgd(null))
    }

    @Test
    fun `identifiers print their raw value`() {
        assertEquals("4427", StopId(4427).toString())
        assertEquals("60201198", Diva(60201198).toString())
    }

    @Test
    fun `a request needs at least one stop or station`() {
        assertThrows<IllegalArgumentException> { DepartureRequest() }
        assertEquals(setOf(Diva(1)), DepartureRequest(stations = setOf(Diva(1))).stations)
    }
}

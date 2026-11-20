package app.brokoli5191.zeitig.data.ogd

import app.brokoli5191.zeitig.core.model.Direction
import app.brokoli5191.zeitig.core.model.DisruptionKind
import app.brokoli5191.zeitig.core.model.LineTarget
import app.brokoli5191.zeitig.core.model.StopBoard
import app.brokoli5191.zeitig.core.model.StopId
import app.brokoli5191.zeitig.core.model.TransportType
import app.brokoli5191.zeitig.core.provider.FailureReason
import app.brokoli5191.zeitig.core.provider.ProviderResult
import app.brokoli5191.zeitig.data.Fixtures
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MonitorMapperTest {
    private val received = Instant.parse("2026-11-20T21:00:00Z")

    private fun board(fixture: String): StopBoard {
        val result = MonitorMapper.parse(Fixtures.text(fixture), received)
        return assertIs<ProviderResult.Success>(result).board
    }

    @Test
    fun recordedStationResponseYieldsAllPlatforms() {
        val board = board("monitor_diva_schwedenplatz.json")
        assertEquals(Instant.parse("2026-11-20T20:58:03Z"), board.fetchedAt)
        val lines = board.departures.map { it.line }.toSet()
        assertEquals(setOf("1", "2", "U1", "U4"), lines)
        val u4ToHeiligenstadt = board.departuresFor(LineTarget(StopId(4427), "U4", Direction.OUTBOUND))
        assertTrue(u4ToHeiligenstadt.isNotEmpty())
        assertTrue(u4ToHeiligenstadt.all { it.towards == "Heiligenstadt" && it.type == TransportType.METRO })
        assertEquals(u4ToHeiligenstadt.sortedBy { it.time }, u4ToHeiligenstadt)
    }

    @Test
    fun recordedResponseKeepsScheduledOnlyDepartures() {
        val board = board("monitor_diva_schwedenplatz.json")
        val inbound = board.departuresFor(LineTarget(StopId(4410), "U4", Direction.INBOUND))
        assertTrue(inbound.any { !it.isRealtime }, "one U4 departure in the recording has no timeReal")
        inbound.filterNot { it.isRealtime }.forEach { assertEquals(it.planned, it.time) }
    }

    @Test
    fun longDisruptionIsAttachedToItsLine() {
        val board = board("monitor_diva_schwedenplatz.json")
        val disruption = board.disruptions.single()
        assertEquals(DisruptionKind.LONG, disruption.kind)
        assertEquals("1: Gleisbauarbeiten", disruption.title)
        assertEquals(setOf("1"), disruption.relatedLines)
        assertEquals(Instant.parse("2026-10-20T10:58:00Z"), disruption.start)
        assertTrue(disruption.affects(LineTarget(StopId(42), "1", Direction.OUTBOUND)))
        assertFalse(disruption.affects(LineTarget(StopId(4427), "U4", Direction.OUTBOUND)))
    }

    @Test
    fun elevatorOutageIsLinkedToTheStopPointsThatReferenceIt() {
        val board = board("monitor_elevator_keplerplatz.json")
        val elevator = board.disruptions.single()
        assertEquals(DisruptionKind.ELEVATOR, elevator.kind)
        assertEquals(setOf(StopId(4103), StopId(4126)), elevator.relatedStops)
        assertTrue(elevator.description.contains("AUFZUGSERNEUERUNG"))
        assertTrue(elevator.affects(LineTarget(StopId(4103), "U1", Direction.OUTBOUND)))
        assertFalse(elevator.affects(LineTarget(StopId(4113), "U1", Direction.OUTBOUND)))
    }

    @Test
    fun unknownStopReturnsAnEmptyBoard() {
        val board = board("monitor_invalid.json")
        assertTrue(board.departures.isEmpty())
        assertTrue(board.disruptions.isEmpty())
    }

    @Test
    fun syntheticResponseIsParsedTolerantly() {
        val board = board("monitor_scheduled_only.json")
        // Three valid departures; the broken timestamp and the null departureTime are dropped.
        assertEquals(3, board.departures.size)
        val (first, second, third) = board.departures
        assertEquals(StopId(1450), first.stopId, "string rbl is accepted")
        assertNull(first.realtime)
        assertEquals(Instant.parse("2026-03-29T00:58:00Z"), first.time)
        assertEquals("Breitensee S", second.towards, "vehicle overrides the line's destination")
        assertEquals(true, second.barrierFree)
        assertEquals(Instant.parse("2026-03-29T01:03:00Z"), second.planned)
        assertNull(third.realtime, "an empty timeReal means scheduled only")
        assertEquals(Instant.parse("2026-03-29T00:55:00Z"), board.fetchedAt)
    }

    @Test
    fun rateLimitCodeMapsToRateLimited() {
        val result = MonitorMapper.parse(Fixtures.text("monitor_rate_limited.json"), received)
        assertEquals(FailureReason.RATE_LIMITED, assertIs<ProviderResult.Failure>(result).reason)
    }

    @Test
    fun unknownStopCodeMapsToUnknownStop() {
        val result = MonitorMapper.parse(Fixtures.text("monitor_unknown_stop.json"), received)
        assertEquals(FailureReason.UNKNOWN_STOP, assertIs<ProviderResult.Failure>(result).reason)
    }

    @Test
    fun malformedJsonIsAParseFailure() {
        val result = MonitorMapper.parse(Fixtures.text("monitor_malformed.json"), received)
        assertEquals(FailureReason.PARSE, assertIs<ProviderResult.Failure>(result).reason)
    }

    @Test
    fun otherServerCodesMapToServerOrInvalidRequest() {
        fun reasonFor(code: Int) =
            assertIs<ProviderResult.Failure>(
                MonitorMapper.parse("""{"message":{"value":"x","messageCode":$code}}""", received),
            ).reason
        assertEquals(FailureReason.SERVER, reasonFor(311))
        assertEquals(FailureReason.SERVER, reasonFor(322))
        assertEquals(FailureReason.INVALID_REQUEST, reasonFor(320))
        assertEquals(FailureReason.INVALID_REQUEST, reasonFor(321))
        assertEquals(FailureReason.SERVER, reasonFor(999))
    }

    @Test
    fun responseWithoutDataIsAParseFailure() {
        val result = MonitorMapper.parse("""{"message":{"value":"OK","messageCode":1}}""", received)
        assertEquals(FailureReason.PARSE, assertIs<ProviderResult.Failure>(result).reason)
    }

    @Test
    fun trafficInfoListParsesElevatorOutages() {
        val board = board("trafficinfolist_aufzug.json")
        assertTrue(board.departures.isEmpty())
        assertTrue(board.disruptions.isNotEmpty())
        assertTrue(board.disruptions.all { it.kind == DisruptionKind.ELEVATOR })
        assertTrue(board.disruptions.any { StopId(4103) in it.relatedStops || StopId(4126) in it.relatedStops })
    }
}

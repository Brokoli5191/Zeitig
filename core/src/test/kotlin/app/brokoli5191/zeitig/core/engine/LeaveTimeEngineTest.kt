package app.brokoli5191.zeitig.core.engine

import app.brokoli5191.zeitig.core.model.Departure
import app.brokoli5191.zeitig.core.model.Direction
import app.brokoli5191.zeitig.core.model.Disruption
import app.brokoli5191.zeitig.core.model.DisruptionKind
import app.brokoli5191.zeitig.core.model.StopId
import app.brokoli5191.zeitig.core.model.TransportType
import app.brokoli5191.zeitig.core.walk.WalkTimeSource
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

class LeaveTimeEngineTest {
    private val engine = LeaveTimeEngine()
    private val now = Instant.parse("2026-11-23T06:30:00Z")

    private fun dep(
        minutesFromNow: Long,
        realtime: Boolean = true,
        line: String = "49",
        plannedOffsetSeconds: Long = 0,
    ): Departure {
        val time = now.plus(Duration.ofMinutes(minutesFromNow))
        return Departure(
            stopId = StopId(1450),
            line = line,
            towards = "Hütteldorf",
            direction = Direction.OUTBOUND,
            type = TransportType.TRAM,
            planned = time.minusSeconds(plannedOffsetSeconds),
            realtime = if (realtime) time else null,
        )
    }

    private fun input(
        vararg departures: Departure,
        walk: Long = 6,
        buffer: Long = 1,
        onward: Duration? = null,
        arriveBy: Instant? = null,
        source: WalkTimeSource = WalkTimeSource.LEARNED,
        disruptions: List<Disruption> = emptyList(),
        at: Instant = now,
    ) = LeaveInput(
        now = at,
        departures = departures.toList(),
        walkTime = Duration.ofMinutes(walk),
        walkSource = source,
        buffer = Duration.ofMinutes(buffer),
        onward = onward,
        arriveBy = arriveBy,
        disruptions = disruptions,
    )

    @Test
    fun `chooses the first departure that can still be reached`() {
        // Walk 6 + buffer 1 = 7 minutes: the departure in 5 minutes is gone, the one in 9 is not.
        val plan = assertIs<LeavePlan.Go>(engine.plan(input(dep(5), dep(9), dep(15), dep(21), dep(27), dep(33))))
        assertEquals(now.plus(Duration.ofMinutes(2)), plan.chosen.leaveAt)
        assertEquals(listOf(15L, 21L, 27L), plan.alternatives.map { Duration.between(now, it.departure.time).toMinutes() })
        assertEquals(ConfidenceLevel.HIGH, plan.confidence.level)
        assertNull(plan.lateBy)
        assertNull(plan.chosen.arrival)
    }

    @Test
    fun `input order does not matter and duplicates collapse`() {
        val a = dep(12)
        val plan = assertIs<LeavePlan.Go>(engine.plan(input(dep(20), a, a, dep(9))))
        assertEquals(now.plus(Duration.ofMinutes(9)), plan.chosen.departure.time)
        assertEquals(2, plan.alternatives.size)
    }

    @Test
    fun `departures in the past or beyond the horizon are ignored`() {
        val plan = engine.plan(input(dep(-3), dep(61)))
        assertSame(LeavePlan.NoService, plan)
        assertSame(LeavePlan.NoService, engine.plan(input()))
    }

    @Test
    fun `too late for everything within the horizon is a state of its own`() {
        // Ten minutes of walking, the only departures leave in 4 and 8 minutes.
        val plan = assertIs<LeavePlan.TooLate>(engine.plan(input(dep(4), dep(8), walk = 10)))
        assertEquals(2, plan.missed.size)
        assertEquals(now.minus(Duration.ofMinutes(7)), plan.missed.first().leaveAt)
    }

    @Test
    fun `the leave-now moment stays for at most the buffer`() {
        val tram = dep(10)
        val leaveAt = now.plus(Duration.ofMinutes(3))
        val justAfter = leaveAt.plusSeconds(30)
        assertEquals(tram, assertIs<LeavePlan.Go>(engine.plan(input(tram, dep(20), at = justAfter))).chosen.departure)
        // Without a buffer there is no grace: the next departure is recommended.
        val strict = assertIs<LeavePlan.Go>(engine.plan(input(tram, dep(20), walk = 7, buffer = 0, at = justAfter)))
        assertEquals(now.plus(Duration.ofMinutes(20)), strict.chosen.departure.time)
        // Grace never exceeds a minute even with a large buffer.
        val fiveMinutesLate = now.plus(Duration.ofMinutes(5)).plusSeconds(5)
        val bigBuffer = assertIs<LeavePlan.Go>(engine.plan(input(dep(15), dep(30), walk = 6, buffer = 5, at = fiveMinutesLate)))
        assertEquals(now.plus(Duration.ofMinutes(30)), bigBuffer.chosen.departure.time)
    }

    @Test
    fun `arrive-by picks the latest departure that still arrives in time`() {
        val onward = Duration.ofMinutes(20)
        val deadline = now.plus(Duration.ofMinutes(45))
        val plan = assertIs<LeavePlan.Go>(engine.plan(input(dep(10), dep(18), dep(25), dep(32), onward = onward, arriveBy = deadline)))
        assertEquals(now.plus(Duration.ofMinutes(25)), plan.chosen.departure.time)
        assertEquals(now.plus(Duration.ofMinutes(45)), plan.chosen.arrival)
        assertEquals(listOf(32L), plan.alternatives.map { Duration.between(now, it.departure.time).toMinutes() })
        assertNull(plan.lateBy)
    }

    @Test
    fun `arrive-by that cannot be met reports how late you will be`() {
        val plan =
            assertIs<LeavePlan.Go>(
                engine.plan(input(dep(10), dep(18), onward = Duration.ofMinutes(30), arriveBy = now.plus(Duration.ofMinutes(35)))),
            )
        assertEquals(now.plus(Duration.ofMinutes(10)), plan.chosen.departure.time)
        assertEquals(Duration.ofMinutes(5), plan.lateBy)
    }

    @Test
    fun `arrive-by without a known onward time behaves like depart-at`() {
        val plan = assertIs<LeavePlan.Go>(engine.plan(input(dep(10), dep(18), arriveBy = now.plus(Duration.ofMinutes(15)))))
        assertEquals(now.plus(Duration.ofMinutes(10)), plan.chosen.departure.time)
    }

    @Test
    fun `confidence reflects real-time, walk-time source and disruptions`() {
        fun level(
            realtime: Boolean,
            source: WalkTimeSource,
            disrupted: Boolean = false,
        ): ConfidenceLevel {
            val disruptions = if (disrupted) listOf(Disruption("x", DisruptionKind.LONG, "", "", setOf("49"), emptySet())) else emptyList()
            return assertIs<LeavePlan.Go>(
                engine.plan(input(dep(20, realtime), source = source, disruptions = disruptions)),
            ).confidence.level
        }
        assertEquals(ConfidenceLevel.HIGH, level(true, WalkTimeSource.LEARNED))
        assertEquals(ConfidenceLevel.MEDIUM, level(true, WalkTimeSource.ESTIMATED))
        assertEquals(ConfidenceLevel.MEDIUM, level(false, WalkTimeSource.LEARNED))
        assertEquals(ConfidenceLevel.LOW, level(false, WalkTimeSource.PLANNED))
        assertEquals(ConfidenceLevel.LOW, level(true, WalkTimeSource.LEARNED, disrupted = true))
    }

    @Test
    fun `scheduled-only departures are usable but flagged`() {
        val plan = assertIs<LeavePlan.Go>(engine.plan(input(dep(12, realtime = false))))
        assertEquals(false, plan.confidence.realtime)
    }

    @Test
    fun `instants make the spring-forward night trivial`() {
        // 01:58 CET; the tram leaves at 03:05 CEST, i.e. seven minutes later.
        val at = OffsetDateTime.parse("2026-03-29T01:58:00+01:00").toInstant()
        val tram = dep(0).copy(planned = OffsetDateTime.parse("2026-03-29T03:05:00+02:00").toInstant(), realtime = null)
        val plan = assertIs<LeavePlan.Go>(engine.plan(input(tram, walk = 4, buffer = 1, at = at)))
        assertEquals(OffsetDateTime.parse("2026-03-29T03:00:00+02:00").toInstant(), plan.chosen.leaveAt)
        assertEquals(Duration.ofMinutes(2), Duration.between(at, plan.chosen.leaveAt))
    }

    @Test
    fun `instants make the fall-back night trivial`() {
        // 02:50 CEST, then the clock jumps back to 02:00 CET; a 02:10 CET departure is 20 minutes away.
        val at = OffsetDateTime.parse("2026-10-25T02:50:00+02:00").toInstant()
        val tram = dep(0).copy(planned = OffsetDateTime.parse("2026-10-25T02:10:00+01:00").toInstant(), realtime = null)
        val plan = assertIs<LeavePlan.Go>(engine.plan(input(tram, walk = 5, buffer = 1, at = at)))
        assertEquals(Duration.ofMinutes(14), Duration.between(at, plan.chosen.leaveAt))
    }

    @Test
    fun `option key ignores real-time drift`() {
        val early = LeaveOption(dep(10, plannedOffsetSeconds = 0), now, null)
        val drifted = LeaveOption(early.departure.copy(realtime = early.departure.time.plusSeconds(90)), now, null)
        assertEquals(early.key, drifted.key)
    }

    @Test
    fun `negative durations are rejected`() {
        assertThrows<IllegalArgumentException> { input(walk = -1) }
        assertThrows<IllegalArgumentException> { input(buffer = -1) }
        assertThrows<IllegalArgumentException> { input(onward = Duration.ofMinutes(-1)) }
    }

    @Test
    fun `replanner reports a missed departure once its moment passed`() {
        val first = assertIs<LeavePlan.Go>(engine.plan(input(dep(9), dep(15))))
        val later = now.plus(Duration.ofMinutes(4))
        val replanned = engine.plan(input(dep(9), dep(15), at = later))
        val missed = Replanner.missed(first.chosen, replanned, later, tripStarted = false)
        assertEquals(first.chosen, missed?.previous)
        assertEquals(now.plus(Duration.ofMinutes(15)), missed?.next?.departure?.time)
    }

    @Test
    fun `replanner stays quiet when nothing was missed`() {
        val first = assertIs<LeavePlan.Go>(engine.plan(input(dep(9), dep(15))))
        assertNull(Replanner.missed(null, first, now, tripStarted = false))
        assertNull(Replanner.missed(first.chosen, first, now.plus(Duration.ofMinutes(10)), tripStarted = true))
        assertNull(Replanner.missed(first.chosen, first, first.chosen.leaveAt.plusSeconds(30), tripStarted = false))
        assertNull(Replanner.missed(first.chosen, first, first.chosen.leaveAt.plusSeconds(120), tripStarted = false))
        val gone = Replanner.missed(first.chosen, LeavePlan.NoService, now.plus(Duration.ofMinutes(30)), tripStarted = false)
        assertNull(gone?.next)
    }
}

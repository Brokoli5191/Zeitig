package app.brokoli5191.zeitig.data.live

import app.brokoli5191.zeitig.core.model.Departure
import app.brokoli5191.zeitig.core.model.Direction
import app.brokoli5191.zeitig.core.model.Disruption
import app.brokoli5191.zeitig.core.model.DisruptionKind
import app.brokoli5191.zeitig.core.model.StopBoard
import app.brokoli5191.zeitig.core.model.StopId
import app.brokoli5191.zeitig.core.model.TransportType
import app.brokoli5191.zeitig.core.provider.DepartureProvider
import app.brokoli5191.zeitig.core.provider.DepartureRequest
import app.brokoli5191.zeitig.core.provider.FailureReason
import app.brokoli5191.zeitig.core.provider.ProviderResult
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LiveDeparturesTest {
    private class MutableClock(
        var now: Instant,
    ) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId?): Clock = this

        override fun instant(): Instant = now
    }

    private val clock = MutableClock(Instant.parse("2026-11-23T06:30:00Z"))
    private val requests = mutableListOf<Set<StopId>>()
    private var fail = false

    private val provider =
        object : DepartureProvider {
            override val id = "fake"

            override suspend fun departures(request: DepartureRequest): ProviderResult {
                requests += request.stopIds
                if (fail) return ProviderResult.Failure(FailureReason.NETWORK)
                val departures =
                    request.stopIds.map {
                        Departure(it, "49", "Hütteldorf", Direction.OUTBOUND, TransportType.TRAM, clock.now.plusSeconds(300), null)
                    }
                val disruptions =
                    listOf(
                        Disruption("lift", DisruptionKind.ELEVATOR, "Aufzug", "", emptySet(), setOf(StopId(1))),
                        Disruption("line", DisruptionKind.LONG, "49", "", setOf("49"), emptySet()),
                    )
                return ProviderResult.Success(StopBoard(clock.now, departures, disruptions))
            }
        }

    private val live = LiveDepartures(provider, api = null, clock = clock)

    @Test
    fun freshStopsComeFromMemory() =
        runTest {
            live.snapshot(setOf(StopId(1), StopId(2)))
            clock.now = clock.now.plusSeconds(10)
            val again = live.snapshot(setOf(StopId(2)))
            assertEquals(listOf(setOf(StopId(1), StopId(2))), requests)
            assertEquals(1, again.departures.size)
            assertEquals(listOf("line"), again.disruptions.map { it.id }, "stop 2 sees only the general disruption")
        }

    @Test
    fun onlyStaleStopsAreFetchedInOneBatch() =
        runTest {
            live.snapshot(setOf(StopId(1)))
            clock.now = clock.now.plusSeconds(30)
            val snapshot = live.snapshot(setOf(StopId(1), StopId(3)))
            assertEquals(setOf(StopId(1), StopId(3)), requests.last())
            assertEquals(2, snapshot.departures.size)
            assertEquals(setOf("lift", "line"), snapshot.disruptions.map { it.id }.toSet())
            assertEquals(clock.now, snapshot.fetchedAt)
        }

    @Test
    fun failuresKeepOldDataAndReportTheReason() =
        runTest {
            live.snapshot(setOf(StopId(1)))
            val fetched = clock.now
            clock.now = clock.now.plusSeconds(60)
            fail = true
            val snapshot = live.snapshot(setOf(StopId(1)))
            assertEquals(FailureReason.NETWORK, snapshot.failure)
            assertEquals(1, snapshot.departures.size)
            assertEquals(fetched, snapshot.fetchedAt)
            fail = false
            assertNull(live.snapshot(setOf(StopId(1)), forceRefresh = true).failure)
        }

    @Test
    fun pollingEmitsUntilCollectionStops() =
        runTest {
            val emissions = live.poll(setOf(StopId(1)), interval = Duration.ofSeconds(30)).take(3).toList()
            assertEquals(3, emissions.size)
        }
}

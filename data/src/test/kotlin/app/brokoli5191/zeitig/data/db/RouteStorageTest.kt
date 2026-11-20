package app.brokoli5191.zeitig.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.brokoli5191.zeitig.core.journey.Journey
import app.brokoli5191.zeitig.core.journey.Leg
import app.brokoli5191.zeitig.core.journey.LegPoint
import app.brokoli5191.zeitig.core.journey.Place
import app.brokoli5191.zeitig.core.model.Direction
import app.brokoli5191.zeitig.core.model.GeoPoint
import app.brokoli5191.zeitig.core.model.LineTarget
import app.brokoli5191.zeitig.core.model.StopId
import app.brokoli5191.zeitig.core.model.TransportType
import app.brokoli5191.zeitig.core.reminder.ReminderPlan
import app.brokoli5191.zeitig.core.route.Route
import app.brokoli5191.zeitig.core.schedule.Schedule
import app.brokoli5191.zeitig.core.schedule.ScheduleMode
import app.brokoli5191.zeitig.core.walk.TripVerdict
import app.brokoli5191.zeitig.data.repo.ArrivalSource
import app.brokoli5191.zeitig.data.repo.RouteRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class RouteStorageTest {
    private val db =
        Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ZeitigDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    private val clock = Clock.fixed(Instant.parse("2026-11-20T21:00:00Z"), ZoneOffset.UTC)
    private val repository = RouteRepository(db.routes(), db.trips(), clock)

    @After
    fun close() = db.close()

    private val journey =
        Journey(
            listOf(
                Leg.Walk(
                    LegPoint("Home", GeoPoint(48.21, 16.37)),
                    LegPoint("Stephansplatz", GeoPoint(48.2085, 16.3721), StopId(4116)),
                    Duration.ofMinutes(4),
                    260.0,
                    listOf(GeoPoint(48.21, 16.37), GeoPoint(48.2085, 16.3721)),
                ),
                Leg.Transit(
                    from = LegPoint("Stephansplatz", GeoPoint(48.2085, 16.3721), StopId(4116)),
                    to = LegPoint("Hauptbahnhof", GeoPoint(48.1871, 16.3733), StopId(4123)),
                    duration = Duration.ofMinutes(5),
                    line = "U1",
                    type = TransportType.METRO,
                    headsign = "Oberlaa",
                    boarding = LineTarget(StopId(4116), "U1", Direction.INBOUND),
                    intermediateStops = listOf(LegPoint("Karlsplatz", GeoPoint(48.2012, 16.3688))),
                    geometry = listOf(GeoPoint(48.2085, 16.3721), GeoPoint(48.1871, 16.3733)),
                    color = 0xFFE3000F.toInt(),
                ),
            ),
        )

    private fun route(schedule: Schedule?) =
        Route(
            id = 0,
            name = "Arbeit",
            origin = Place("Home", GeoPoint(48.21, 16.37)),
            destination = Place("Office", null),
            journey = journey,
            schedule = schedule,
            reminders = ReminderPlan(listOf(10, 5, 0)),
            buffer = Duration.ofSeconds(90),
            accessible = true,
            manualWalk = null,
        )

    @Test
    fun journeyJsonRoundTrips() {
        assertEquals(journey, JourneyJson.decode(JourneyJson.encode(journey)))
    }

    @Test
    fun routesRoundTripWithEveryScheduleKind() =
        runBlocking<Unit> {
            val window = Schedule(Schedule.WORKDAYS, ScheduleMode.Window(LocalTime.of(7, 15), LocalTime.of(8, 30)))
            val arriveBy = Schedule(setOf(DayOfWeek.SATURDAY), ScheduleMode.ArriveBy(LocalTime.of(9, 0), Duration.ofMinutes(20)))
            val ids = listOf(window, arriveBy, null).map { repository.save(route(it)) }
            val stored = repository.routes()
            assertEquals(ids, stored.map { it.id })
            assertEquals(route(window).copy(id = ids[0]), stored[0])
            assertEquals(arriveBy, stored[1].schedule)
            assertNull(stored[2].schedule)
            assertEquals(listOf(10, 5, 0), stored[0].reminders.offsets)
        }

    @Test
    fun updateKeepsOrderAndReorderWorks() =
        runBlocking<Unit> {
            val a = repository.save(route(null))
            val b = repository.save(route(null).copy(name = "Uni"))
            repository.save(repository.route(a)!!.copy(name = "Büro"))
            assertEquals(listOf("Büro", "Uni"), repository.observeRoutes().first().map { it.name })
            repository.reorder(listOf(b, a))
            assertEquals(listOf(b, a), repository.routes().map { it.id })
            repository.delete(b)
            assertEquals(listOf(a), repository.routes().map { it.id })
        }

    @Test
    fun tripsAreJudgedPrunedAndCascadeDeleted() =
        runBlocking<Unit> {
            val id = repository.save(route(null))
            val start = Instant.parse("2026-11-20T06:40:00Z")
            repeat(52) { i ->
                val verdict =
                    repository.recordTrip(
                        id,
                        start.plus(Duration.ofDays(i.toLong())),
                        Duration.ofSeconds(300L + i),
                        start,
                        ArrivalSource.GEOFENCE,
                    )
                assertEquals(TripVerdict.Accepted, verdict)
            }
            assertEquals(50, repository.observeTrips(id).first().size)
            val outlier = repository.recordTrip(id, start.plus(Duration.ofDays(60)), Duration.ofMinutes(40), null, ArrivalSource.MANUAL)
            assertIs<TripVerdict.Outlier>(outlier)
            val trips = repository.observeTrips(id).first()
            assertEquals(50, trips.size)
            assertTrue(trips.last().let { !it.accepted && it.plannedLeaveAt == null })
            assertEquals(49, repository.acceptedWalks(id).size)

            repository.resetLearning(id)
            assertTrue(repository.acceptedWalks(id).isEmpty())
            repository.recordTrip(id, start, Duration.ofMinutes(5), null, ArrivalSource.MANUAL)
            repository.delete(id)
            assertTrue(db.trips().all(id).isEmpty(), "trips go with their route")
        }
}

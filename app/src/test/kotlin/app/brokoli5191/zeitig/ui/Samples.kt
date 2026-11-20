package app.brokoli5191.zeitig.ui

import app.brokoli5191.zeitig.core.journey.Journey
import app.brokoli5191.zeitig.core.journey.Leg
import app.brokoli5191.zeitig.core.journey.LegPoint
import app.brokoli5191.zeitig.core.journey.Place
import app.brokoli5191.zeitig.core.model.Departure
import app.brokoli5191.zeitig.core.model.Direction
import app.brokoli5191.zeitig.core.model.Disruption
import app.brokoli5191.zeitig.core.model.DisruptionKind
import app.brokoli5191.zeitig.core.model.GeoPoint
import app.brokoli5191.zeitig.core.model.LineTarget
import app.brokoli5191.zeitig.core.model.StopId
import app.brokoli5191.zeitig.core.model.TransportType
import app.brokoli5191.zeitig.core.route.Route
import app.brokoli5191.zeitig.core.route.RouteStatus
import app.brokoli5191.zeitig.core.route.RouteStatusCalculator
import app.brokoli5191.zeitig.core.routing.Itinerary
import app.brokoli5191.zeitig.core.schedule.Schedule
import app.brokoli5191.zeitig.core.schedule.ScheduleMode
import app.brokoli5191.zeitig.core.stats.RouteStatistics
import app.brokoli5191.zeitig.core.stats.TripRecord
import app.brokoli5191.zeitig.core.walk.WalkSettings
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** Fixed, realistic sample data: a Monday morning in Vienna at 07:30. */
object Samples {
    val now: Instant = Instant.parse("2026-11-23T06:30:00Z")

    private fun min(m: Long) = Duration.ofMinutes(m)

    private val home = GeoPoint(48.2010, 16.3330)
    private val burggasse = GeoPoint(48.2030, 16.3375)
    private val volkstheater = GeoPoint(48.2055, 16.3580)
    private val stephansplatz = GeoPoint(48.2085, 16.3721)
    private val office = GeoPoint(48.2110, 16.3790)

    val workJourney =
        Journey(
            listOf(
                Leg.Walk(
                    LegPoint("Home", home),
                    LegPoint("Burggasse-Stadthalle", burggasse, StopId(4927)),
                    min(6),
                    420.0,
                    listOf(home, GeoPoint(48.2022, 16.3350), burggasse),
                ),
                Leg.Transit(
                    LegPoint("Burggasse-Stadthalle", burggasse, StopId(4927)),
                    LegPoint("Volkstheater", volkstheater, StopId(4909)),
                    min(5),
                    "U6",
                    TransportType.METRO,
                    "Floridsdorf",
                    LineTarget(StopId(4927), "U6", Direction.OUTBOUND),
                    geometry = listOf(burggasse, GeoPoint(48.2045, 16.3480), volkstheater),
                ),
                Leg.Walk(LegPoint("Volkstheater", volkstheater), LegPoint("Volkstheater", volkstheater, StopId(4210)), min(3)),
                Leg.Transit(
                    LegPoint("Volkstheater", volkstheater, StopId(4210)),
                    LegPoint("Stephansplatz", stephansplatz, StopId(4116)),
                    min(3),
                    "U3",
                    TransportType.METRO,
                    "Simmering",
                    geometry = listOf(volkstheater, GeoPoint(48.2075, 16.3650), stephansplatz),
                ),
                Leg.Walk(
                    LegPoint("Stephansplatz", stephansplatz),
                    LegPoint("Office", office),
                    min(6),
                    480.0,
                    listOf(stephansplatz, office),
                ),
            ),
        )

    val tramJourney =
        Journey.simple(
            Place("Home", home),
            LegPoint("Urban-Loritz-Platz", GeoPoint(48.2008, 16.3378), StopId(1450)),
            "49",
            TransportType.TRAM,
            "Hütteldorf",
            LineTarget(StopId(1450), "49", Direction.OUTBOUND),
            Duration.ZERO,
        )

    val work =
        Route(
            id = 1,
            name = "Arbeit",
            origin = Place("Home", home),
            destination = Place("Office", office),
            journey = workJourney,
            schedule = Schedule(Schedule.WORKDAYS, ScheduleMode.ArriveBy(LocalTime.of(8, 0))),
        )

    val gym =
        Route(
            id = 2,
            name = "Kletterhalle",
            origin = Place("Home", home),
            destination = Place("Hütteldorf", null),
            journey = tramJourney,
            schedule =
                Schedule(
                    setOf(java.time.DayOfWeek.TUESDAY, java.time.DayOfWeek.THURSDAY),
                    ScheduleMode.Window(LocalTime.of(18, 0), LocalTime.of(19, 0)),
                ),
        )

    val uni =
        Route(
            id = 3,
            name = "Uni",
            origin = Place("Home", home),
            destination = Place("Schottentor", null),
            journey = tramJourney.copy(),
            schedule = null,
        )

    private fun departure(
        stop: Int,
        line: String,
        towards: String,
        type: TransportType,
        minutes: Long,
        realtime: Boolean = true,
    ) = Departure(
        StopId(stop),
        line,
        towards,
        Direction.OUTBOUND,
        type,
        now.plus(min(minutes)),
        if (realtime) now.plus(min(minutes)) else null,
    )

    private val calculator = RouteStatusCalculator()

    val disruption =
        Disruption(
            "I1",
            DisruptionKind.ELEVATOR,
            "Volkstheater",
            "U3 Bahnsteig Richtung Simmering – Aufzug außer Betrieb bis 18.12.",
            setOf("U3"),
            setOf(StopId(4210)),
        )

    fun workStatus(withDisruption: Boolean = true): RouteStatus =
        calculator.status(
            work,
            listOf(
                departure(4927, "U6", "Floridsdorf", TransportType.METRO, 9),
                departure(4927, "U6", "Floridsdorf", TransportType.METRO, 13),
                departure(4927, "U6", "Floridsdorf", TransportType.METRO, 18),
            ),
            if (withDisruption) listOf(disruption) else emptyList(),
            listOf(min(6), Duration.ofSeconds(330), Duration.ofSeconds(350), Duration.ofSeconds(340)),
            WalkSettings(),
            now,
            now.minusSeconds(12),
        )

    fun gymStatus(): RouteStatus =
        calculator.status(
            gym,
            listOf(
                departure(1450, "49", "Hütteldorf", TransportType.TRAM, 4),
                departure(1450, "49", "Hütteldorf", TransportType.TRAM, 12, realtime = false),
            ),
            emptyList(),
            emptyList(),
            WalkSettings(),
            now,
            now,
        )

    val trips =
        (0 until 14).map { day ->
            val start = now.minus(Duration.ofDays(day + 1L))
            TripRecord(
                start.plusSeconds((day % 3) * 40L - 30),
                Duration.ofSeconds(330L + (day % 4) * 15),
                accepted = true,
                plannedLeaveAt = start,
            )
        }

    val stats = RouteStatistics.compute(trips, ZoneId.of("Europe/Vienna"))

    val itineraries =
        listOf(
            Itinerary(workJourney, now.plus(min(3)), now.plus(min(26)), realtime = false),
            Itinerary(workJourney, now.plus(min(8)), now.plus(min(32)), realtime = false),
        )
}

package app.brokoli5191.zeitig.core.journey

import app.brokoli5191.zeitig.core.model.Disruption
import app.brokoli5191.zeitig.core.model.DisruptionKind
import app.brokoli5191.zeitig.core.model.GeoPoint
import app.brokoli5191.zeitig.core.model.LineTarget
import app.brokoli5191.zeitig.core.model.StopId
import app.brokoli5191.zeitig.core.model.TransportType
import java.time.Duration

/** A named place: home, office, or a stop. */
public data class Place(
    val name: String,
    val location: GeoPoint?,
)

/** A point where a leg starts or ends. [stopId] is the Wiener Linien stop point, when known. */
public data class LegPoint(
    val name: String,
    val location: GeoPoint?,
    val stopId: StopId? = null,
)

public sealed interface Leg {
    public val from: LegPoint
    public val to: LegPoint

    /** Planned duration of the leg (timetable / walking estimate of the planner). */
    public val duration: Duration

    /** Polyline for the map; may be empty. */
    public val geometry: List<GeoPoint>

    public data class Walk(
        override val from: LegPoint,
        override val to: LegPoint,
        override val duration: Duration,
        val distanceMeters: Double? = null,
        override val geometry: List<GeoPoint> = emptyList(),
    ) : Leg

    public data class Transit(
        override val from: LegPoint,
        override val to: LegPoint,
        override val duration: Duration,
        val line: String,
        val type: TransportType,
        val headsign: String,
        /** Real-time hook: the WL stop point/line/direction to watch for this boarding, if matched. */
        val boarding: LineTarget? = null,
        val intermediateStops: List<LegPoint> = emptyList(),
        override val geometry: List<GeoPoint> = emptyList(),
        val color: Int? = null,
    ) : Leg
}

/**
 * A planned way from A to B. The first transit leg decides when to leave; everything after it is
 * taken from the plan ("onward" time), everything before it is walking to the boarding stop.
 */
public data class Journey(
    val legs: List<Leg>,
) {
    init {
        require(legs.isNotEmpty()) { "A journey has at least one leg" }
    }

    val origin: LegPoint get() = legs.first().from
    val destination: LegPoint get() = legs.last().to

    val transitLegs: List<Leg.Transit> get() = legs.filterIsInstance<Leg.Transit>()
    val firstTransit: Leg.Transit? get() = transitLegs.firstOrNull()

    /** Planned walking before the first boarding (the part the app learns per user). */
    val plannedAccessWalk: Duration
        get() = legs.takeWhile { it !is Leg.Transit }.fold(Duration.ZERO) { acc, leg -> acc + leg.duration }

    /** From the first boarding until arrival at the destination, as planned. */
    val plannedOnward: Duration
        get() = legs.dropWhile { it !is Leg.Transit }.fold(Duration.ZERO) { acc, leg -> acc + leg.duration }

    val plannedTotal: Duration get() = legs.fold(Duration.ZERO) { acc, leg -> acc + leg.duration }

    val transfers: Int get() = (transitLegs.size - 1).coerceAtLeast(0)

    val lines: Set<String> get() = transitLegs.mapTo(LinkedHashSet()) { it.line }

    /** Stops where the user boards, changes or alights – where elevators and stairs matter. */
    val interchangeStops: List<LegPoint>
        get() = transitLegs.flatMap { listOf(it.from, it.to) }.distinctBy { it.stopId ?: it.name }

    /** Disruptions that touch any line of this journey or an elevator at an interchange stop. */
    public fun relevantDisruptions(all: List<Disruption>): List<Disruption> {
        val lineNames = lines.map { it.lowercase() }.toSet()
        val stops = interchangeStops.mapNotNull { it.stopId }.toSet()
        return all.filter { disruption ->
            val lineHit = disruption.relatedLines.any { it.lowercase() in lineNames }
            val stopHit = disruption.relatedStops.any { it in stops }
            when (disruption.kind) {
                DisruptionKind.ELEVATOR, DisruptionKind.ESCALATOR -> stopHit
                else -> lineHit || stopHit
            }
        }
    }

    public companion object {
        /**
         * The offline journey of a "simple" route: walk to a stop, ride one line, done. Used when
         * online routing is off; the ride and final walk are unknown and count as zero.
         */
        public fun simple(
            origin: Place,
            stop: LegPoint,
            line: String,
            type: TransportType,
            towards: String,
            boarding: LineTarget,
            plannedWalk: Duration,
        ): Journey =
            Journey(
                listOf(
                    Leg.Walk(LegPoint(origin.name, origin.location), stop, plannedWalk),
                    Leg.Transit(stop, LegPoint(towards, null), Duration.ZERO, line, type, towards, boarding),
                ),
            )
    }
}

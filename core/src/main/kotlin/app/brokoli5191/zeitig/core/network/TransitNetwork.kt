package app.brokoli5191.zeitig.core.network

import app.brokoli5191.zeitig.core.model.Direction
import app.brokoli5191.zeitig.core.model.Diva
import app.brokoli5191.zeitig.core.model.GeoPoint
import app.brokoli5191.zeitig.core.model.Line
import app.brokoli5191.zeitig.core.model.LinePattern
import app.brokoli5191.zeitig.core.model.LineTarget
import app.brokoli5191.zeitig.core.model.Station
import app.brokoli5191.zeitig.core.model.StopId
import app.brokoli5191.zeitig.core.model.StopPoint
import app.brokoli5191.zeitig.core.search.SearchText
import app.brokoli5191.zeitig.core.search.SearchableStation
import app.brokoli5191.zeitig.core.search.StopSearch

/** A line serving a stop point in one direction, with the terminus as the "towards" label. */
public data class LineAtStop(
    val line: Line,
    val direction: Direction,
    val stopId: StopId,
    val towards: String,
) {
    val target: LineTarget get() = LineTarget(stopId, line.name, direction)
}

/**
 * In-memory view of the Wiener Linien reference data: stations, stop points, lines and their stop
 * sequences. Pure and immutable, so it is built once per snapshot and shared.
 */
public class TransitNetwork(
    stopPoints: List<StopPoint>,
    stations: List<Station>,
    lines: List<Line>,
    patterns: List<LinePattern>,
) {
    public val stopPoints: Map<StopId, StopPoint> = stopPoints.associateBy { it.id }
    public val stations: Map<Diva, Station> = stations.associateBy { it.diva }
    public val lines: Map<Int, Line> = lines.associateBy { it.id }
    public val patterns: List<LinePattern> = patterns

    private val stopPointsByDiva: Map<Diva, List<StopPoint>> =
        stopPoints.filter { it.diva != null }.groupBy { it.diva!! }

    private val linesByStop: Map<StopId, List<LineAtStop>> = buildLinesByStop()

    /** Offline fuzzy search over stations, weighted by how many stop points a station has. */
    public val search: StopSearch =
        StopSearch(
            stations.map { station ->
                SearchableStation(
                    diva = station.diva,
                    name = station.name,
                    location = station.location,
                    weight = stopPointsByDiva[station.diva]?.size ?: 0,
                )
            },
        )

    public fun stopPointsOf(diva: Diva): List<StopPoint> = stopPointsByDiva[diva].orEmpty()

    /** Lines and directions serving [stopId], sorted like the operator sorts lines. */
    public fun linesAt(stopId: StopId): List<LineAtStop> = linesByStop[stopId].orEmpty()

    /** Lines and directions serving any stop point of the station [diva]. */
    public fun linesAtStation(diva: Diva): List<LineAtStop> =
        stopPointsOf(diva)
            .flatMap { linesAt(it.id) }
            .distinctBy { Triple(it.line.id, it.direction, it.stopId) }
            .sortedWith(compareBy<LineAtStop> { it.line.sortKey }.thenBy { it.direction })

    public fun lineByName(name: String): Line? {
        val wanted = SearchText.normalize(name)
        return lines.values.firstOrNull { SearchText.normalize(it.name) == wanted }
    }

    /**
     * Finds the stop point and direction for boarding [lineName] near [boarding], heading towards
     * [next] (the following stop of the trip). Used to attach real-time data to a planned leg.
     * Returns null when no stop point of that line lies within [maxDistanceMeters].
     */
    public fun matchBoarding(
        lineName: String,
        boarding: GeoPoint,
        next: GeoPoint?,
        maxDistanceMeters: Double = 250.0,
    ): LineTarget? {
        val line = lineByName(lineName) ?: return null
        var best: Pair<LineTarget, Double>? = null
        for (pattern in patterns) {
            if (pattern.lineId != line.id) continue
            for ((index, stopId) in pattern.stops.withIndex()) {
                if (index == pattern.stops.lastIndex) break // cannot board at the terminus
                val location = stopPoints[stopId]?.location ?: continue
                val distance = location.distanceTo(boarding)
                if (distance > maxDistanceMeters) continue
                val following = pattern.stops.drop(index + 1).firstNotNullOfOrNull { stopPoints[it]?.location }
                val directionPenalty =
                    if (next != null && following != null) following.distanceTo(next) else 0.0
                val score = distance + directionPenalty
                if (best == null || score < best.second) {
                    best = LineTarget(stopId, line.name, pattern.direction) to score
                }
            }
        }
        return best?.first
    }

    private fun buildLinesByStop(): Map<StopId, List<LineAtStop>> {
        val result = HashMap<StopId, MutableMap<Pair<Int, Direction>, LineAtStop>>()
        // Longest patterns first, so short turns never define the "towards" label.
        for (pattern in patterns.sortedByDescending { it.stops.size }) {
            val line = lines[pattern.lineId] ?: continue
            val terminus = pattern.stops.lastOrNull()?.let { stopPoints[it]?.name } ?: continue
            for (stopId in pattern.stops.dropLast(1)) {
                val perStop = result.getOrPut(stopId) { LinkedHashMap() }
                perStop.putIfAbsent(line.id to pattern.direction, LineAtStop(line, pattern.direction, stopId, terminus))
            }
        }
        return result.mapValues { (_, byLine) ->
            byLine.values.sortedWith(compareBy<LineAtStop> { it.line.sortKey }.thenBy { it.direction })
        }
    }
}

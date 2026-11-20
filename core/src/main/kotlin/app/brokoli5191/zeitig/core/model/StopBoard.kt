package app.brokoli5191.zeitig.core.model

import java.time.Instant

/** Snapshot of everything a provider knows about a set of stops at [fetchedAt]. */
public data class StopBoard(
    val fetchedAt: Instant,
    val departures: List<Departure>,
    val disruptions: List<Disruption>,
) {
    public fun departuresFor(target: LineTarget): List<Departure> = departures.filter { it.matches(target) }.sortedBy { it.time }

    public fun disruptionsFor(target: LineTarget): List<Disruption> = disruptions.filter { it.affects(target) }
}

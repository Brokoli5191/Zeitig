package app.brokoli5191.zeitig.core.model

import java.time.Instant

/**
 * One departure of one line at one stop.
 *
 * [realtime] is null when the operator only knows the timetable ("scheduled only"); consumers use
 * [time], which falls back to [planned].
 */
public data class Departure(
    val stopId: StopId,
    val line: String,
    val towards: String,
    val direction: Direction?,
    val type: TransportType,
    val planned: Instant,
    val realtime: Instant?,
    val barrierFree: Boolean? = null,
    val platform: String? = null,
) {
    val time: Instant get() = realtime ?: planned
    val isRealtime: Boolean get() = realtime != null

    /** True when this departure belongs to the line/direction a favourite tracks. */
    public fun matches(target: LineTarget): Boolean =
        stopId == target.stopId &&
            line.equals(target.line, ignoreCase = true) &&
            (target.direction == null || direction == null || direction == target.direction)
}

/** What a favourite follows: a line in one direction at one stop point. */
public data class LineTarget(
    val stopId: StopId,
    val line: String,
    val direction: Direction?,
)

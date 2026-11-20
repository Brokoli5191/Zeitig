package app.brokoli5191.zeitig.core.engine

import app.brokoli5191.zeitig.core.model.Departure
import app.brokoli5191.zeitig.core.model.Disruption
import app.brokoli5191.zeitig.core.walk.WalkTimeSource
import java.time.Duration
import java.time.Instant

/** Everything the engine needs for one evaluation. All durations are non-negative. */
public data class LeaveInput(
    val now: Instant,
    /** Departures of the boarding line/direction, live and scheduled, in any order. */
    val departures: List<Departure>,
    /** Walking time from the door to the boarding stop (learned or estimated). */
    val walkTime: Duration,
    val walkSource: WalkTimeSource,
    val buffer: Duration,
    /** Planned time from boarding to arrival at the destination, when a journey is known. */
    val onward: Duration? = null,
    /** Arrival deadline (arrive-by schedules). */
    val arriveBy: Instant? = null,
    /** Disruptions already filtered to the route. */
    val disruptions: List<Disruption> = emptyList(),
) {
    init {
        require(!walkTime.isNegative) { "negative walk time" }
        require(!buffer.isNegative) { "negative buffer" }
        require(onward == null || !onward.isNegative) { "negative onward time" }
    }
}

/** One catchable (or missed) departure with the moment to leave the door. */
public data class LeaveOption(
    val departure: Departure,
    val leaveAt: Instant,
    val arrival: Instant?,
) {
    /** Identity of the vehicle run, stable while its real-time prediction moves. */
    val key: String get() = "${departure.stopId}|${departure.line}|${departure.planned.epochSecond}"
}

public enum class ConfidenceLevel { HIGH, MEDIUM, LOW }

public data class Confidence(
    /** The chosen departure has a real-time prediction (not timetable only). */
    val realtime: Boolean,
    /** At least one disruption touches the route. */
    val disrupted: Boolean,
    val walkSource: WalkTimeSource,
) {
    val level: ConfidenceLevel
        get() =
            when {
                disrupted -> ConfidenceLevel.LOW
                realtime && walkSource == WalkTimeSource.LEARNED -> ConfidenceLevel.HIGH
                realtime || walkSource == WalkTimeSource.LEARNED -> ConfidenceLevel.MEDIUM
                else -> ConfidenceLevel.LOW
            }
}

public sealed interface LeavePlan {
    /** Leave at [chosen].leaveAt; [alternatives] are the following catchable departures. */
    public data class Go(
        val chosen: LeaveOption,
        val alternatives: List<LeaveOption>,
        val confidence: Confidence,
        /** Set when even the chosen departure misses the arrive-by deadline. */
        val lateBy: Duration? = null,
    ) : LeavePlan

    /** There are departures within the horizon, but you cannot make any of them. */
    public data class TooLate(
        val missed: List<LeaveOption>,
        val confidence: Confidence,
    ) : LeavePlan

    /** No departure at all within the horizon (night, outage, no data). */
    public data object NoService : LeavePlan
}

/**
 * `leaveAt = departure − walkTime − buffer`. Depart-at: the first departure whose leaveAt is not in
 * the past. Arrive-by: the latest such departure that still arrives in time (leave as late as
 * possible), else the earliest one with how late it will be.
 *
 * "Not in the past" allows a grace of `min(maxGrace, buffer)`: at the "leave now" moment the
 * recommendation stays on screen instead of flipping to the next departure a second later, and the
 * grace can never exceed the safety margin the user chose.
 */
public class LeaveTimeEngine(
    public val horizon: Duration = Duration.ofMinutes(60),
    public val maxAlternatives: Int = 3,
    public val maxGrace: Duration = Duration.ofSeconds(60),
) {
    public fun plan(input: LeaveInput): LeavePlan {
        val until = input.now + horizon
        val options =
            input.departures
                .asSequence()
                .filter { !it.time.isBefore(input.now) && !it.time.isAfter(until) }
                .distinctBy { "${it.stopId}|${it.line}|${it.planned}|${it.time}" }
                .sortedBy { it.time }
                .map { departure ->
                    LeaveOption(
                        departure = departure,
                        leaveAt = departure.time - input.walkTime - input.buffer,
                        arrival = input.onward?.let { departure.time + it },
                    )
                }.toList()
        if (options.isEmpty()) return LeavePlan.NoService

        val disrupted = input.disruptions.isNotEmpty()
        val grace = if (input.buffer < maxGrace) input.buffer else maxGrace
        val catchable = options.filter { !it.leaveAt.isBefore(input.now - grace) }
        if (catchable.isEmpty()) {
            return LeavePlan.TooLate(options, Confidence(options.first().departure.isRealtime, disrupted, input.walkSource))
        }

        val deadline = input.arriveBy
        val (chosen, lateBy) =
            if (deadline != null && catchable.first().arrival != null) {
                val inTime = catchable.lastOrNull { !it.arrival!!.isAfter(deadline) }
                if (inTime != null) {
                    inTime to null
                } else {
                    catchable.first() to Duration.between(deadline, catchable.first().arrival)
                }
            } else {
                catchable.first() to null
            }
        val alternatives = catchable.filter { it.departure.time.isAfter(chosen.departure.time) }.take(maxAlternatives)
        return LeavePlan.Go(
            chosen = chosen,
            alternatives = alternatives,
            confidence = Confidence(chosen.departure.isRealtime, disrupted, input.walkSource),
            lateBy = lateBy,
        )
    }
}

/** Detects that the user did not leave in time for the previously chosen departure. */
public object Replanner {
    public data class Missed(
        val previous: LeaveOption,
        val next: LeaveOption?,
    )

    /**
     * [previous] was the chosen option of an earlier evaluation. When its leave time has passed by
     * more than [grace] without a trip being started and [current] now points elsewhere, the user
     * missed it – report the new choice so notifications can say "missed – next one in …".
     */
    public fun missed(
        previous: LeaveOption?,
        current: LeavePlan,
        now: Instant,
        tripStarted: Boolean,
        grace: Duration = Duration.ofSeconds(60),
    ): Missed? {
        if (previous == null || tripStarted) return null
        if (now.isBefore(previous.leaveAt + grace)) return null
        val next =
            when (current) {
                is LeavePlan.Go -> current.chosen
                else -> null
            }
        if (next?.key == previous.key) return null
        return Missed(previous, next)
    }
}

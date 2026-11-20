package app.brokoli5191.zeitig.core.reminder

import app.brokoli5191.zeitig.core.engine.LeaveOption
import java.time.Duration
import java.time.Instant

/** Minutes before `leaveAt` at which to notify; `0` is "leave now". Default 5, 3, 1, 0. */
public data class ReminderPlan(
    val minutesBefore: List<Int> = DEFAULT,
) {
    init {
        require(minutesBefore.isNotEmpty()) { "at least one reminder" }
        require(minutesBefore.all { it in 0..MAX_MINUTES }) { "reminders must lie within 0..$MAX_MINUTES minutes" }
    }

    /** Descending, without duplicates: 5, 3, 1, 0. */
    val offsets: List<Int> get() = minutesBefore.distinct().sortedDescending()

    public companion object {
        public val DEFAULT: List<Int> = listOf(5, 3, 1, 0)
        public const val MAX_MINUTES: Int = 60
    }
}

/** A reminder that is due now. */
public data class ReminderEvent(
    val minutesBefore: Int,
    val option: LeaveOption,
)

/** What has been sent for the departure currently tracked. */
public data class ReminderState(
    val optionKey: String? = null,
    val sent: Set<Int> = emptySet(),
)

/**
 * Decides which reminders to fire. Each reminder fires at most once per tracked departure; when the
 * departure changes (missed, re-planned, or cancelled) the state resets. A reminder whose moment
 * passed more than [lateness] ago is skipped silently instead of arriving out of order – only the
 * most recent overdue one is delivered.
 */
public class ReminderTracker(
    private val plan: ReminderPlan,
    private val lateness: Duration = Duration.ofSeconds(90),
) {
    public fun evaluate(
        option: LeaveOption?,
        state: ReminderState,
        now: Instant,
    ): Pair<List<ReminderEvent>, ReminderState> {
        if (option == null) return emptyList<ReminderEvent>() to ReminderState()
        val current = if (state.optionKey == option.key) state else ReminderState(option.key)
        val overdue =
            plan.offsets.filter { minutes ->
                minutes !in current.sent && !now.isBefore(option.leaveAt - Duration.ofMinutes(minutes.toLong()))
            }
        if (overdue.isEmpty()) return emptyList<ReminderEvent>() to current
        // Smallest offset = most urgent. Everything overdue counts as handled.
        val latest = overdue.min()
        val dueAt = option.leaveAt - Duration.ofMinutes(latest.toLong())
        val events =
            if (Duration.between(dueAt, now) <= lateness || latest == 0) {
                listOf(ReminderEvent(latest, option))
            } else {
                emptyList()
            }
        return events to current.copy(sent = current.sent + overdue)
    }

    /** When the next reminder is due for [option], or null when all were sent. */
    public fun nextDue(
        option: LeaveOption?,
        state: ReminderState,
    ): Instant? {
        if (option == null) return null
        val sent = if (state.optionKey == option.key) state.sent else emptySet()
        return plan.offsets
            .filter { it !in sent }
            .map { option.leaveAt - Duration.ofMinutes(it.toLong()) }
            .minOrNull()
    }
}

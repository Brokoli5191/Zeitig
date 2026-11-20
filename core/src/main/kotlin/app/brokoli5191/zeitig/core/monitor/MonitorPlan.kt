package app.brokoli5191.zeitig.core.monitor

import app.brokoli5191.zeitig.core.engine.LeaveOption
import app.brokoli5191.zeitig.core.engine.LeavePlan
import app.brokoli5191.zeitig.core.reminder.ReminderEvent
import app.brokoli5191.zeitig.core.reminder.ReminderState
import app.brokoli5191.zeitig.core.reminder.ReminderTracker
import app.brokoli5191.zeitig.core.route.RouteStatus
import java.time.Duration
import java.time.Instant

/** What the monitoring service should do after one evaluation. */
public data class MonitorDecision(
    /** Routes inside their window (or watched manually), most urgent first. */
    val active: List<RouteStatus>,
    /** The route the ongoing notification is about. */
    val primary: RouteStatus?,
    /** Reminders to post now. */
    val reminders: Map<Long, List<ReminderEvent>>,
    val reminderStates: Map<Long, ReminderState>,
    /** When to evaluate again at the latest (a reminder, a window end, the next poll). */
    val nextWakeUp: Instant?,
    /** Nothing left to watch: stop the service. */
    val stop: Boolean,
)

/**
 * Pure decision logic of the monitoring service: which routes are live now, which reminders are due,
 * when to wake up next and when to stop. Polling cadence is left to the caller (screen state).
 */
public class MonitorPlanner(
    private val maxSession: Duration = Duration.ofHours(3),
) {
    /**
     * [awaitingData] is set when routes should be watched but have no status yet (first request
     * still failing, e.g. offline): keep running and retry instead of stopping.
     */
    public fun decide(
        statuses: List<RouteStatus>,
        manuallyWatched: Set<Long>,
        reminderStates: Map<Long, ReminderState>,
        sessionStart: Instant,
        now: Instant,
        awaitingData: Boolean = false,
    ): MonitorDecision {
        val active =
            statuses
                .filter { it.route.notificationsEnabled && (it.isActiveNow || it.route.id in manuallyWatched) }
                .sortedBy { leaveAt(it) ?: Instant.MAX }
        val reminders = HashMap<Long, List<ReminderEvent>>()
        val states = HashMap<Long, ReminderState>()
        var next: Instant? = null
        for (status in active) {
            val tracker = ReminderTracker(status.route.reminders)
            val option = chosen(status)
            val (events, state) = tracker.evaluate(option, reminderStates[status.route.id] ?: ReminderState(), now)
            if (events.isNotEmpty()) reminders[status.route.id] = events
            states[status.route.id] = state
            next = earliest(next, tracker.nextDue(option, state))
            next = earliest(next, status.occurrence?.end?.takeIf { it.isAfter(now) })
        }
        val sessionOver = Duration.between(sessionStart, now) >= maxSession
        return MonitorDecision(
            active = active,
            primary = active.firstOrNull { chosen(it) != null } ?: active.firstOrNull(),
            reminders = reminders,
            reminderStates = states,
            nextWakeUp = next,
            stop = (active.isEmpty() && !awaitingData) || sessionOver,
        )
    }

    private fun chosen(status: RouteStatus): LeaveOption? = (status.plan as? LeavePlan.Go)?.chosen

    private fun leaveAt(status: RouteStatus): Instant? = chosen(status)?.leaveAt

    private fun earliest(
        a: Instant?,
        b: Instant?,
    ): Instant? =
        when {
            a == null -> b
            b == null -> a
            else -> if (a.isBefore(b)) a else b
        }
}

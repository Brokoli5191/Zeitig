package app.brokoli5191.zeitig.core.route

import app.brokoli5191.zeitig.core.journey.Journey
import app.brokoli5191.zeitig.core.journey.Place
import app.brokoli5191.zeitig.core.reminder.ReminderPlan
import app.brokoli5191.zeitig.core.schedule.Schedule
import java.time.Duration

/** A saved commute: where from, where to, how, when, and how loudly to remind. */
public data class Route(
    val id: Long,
    val name: String,
    val origin: Place,
    val destination: Place,
    val journey: Journey,
    val schedule: Schedule?,
    val reminders: ReminderPlan = ReminderPlan(),
    val buffer: Duration = DEFAULT_BUFFER,
    /** Step-free routing and elevator warnings. */
    val accessible: Boolean = false,
    /** A walking time the user typed in; wins over everything learned or estimated. */
    val manualWalk: Duration? = null,
    val notificationsEnabled: Boolean = true,
) {
    init {
        require(name.isNotBlank()) { "a route needs a name" }
        require(!buffer.isNegative) { "negative buffer" }
        require(manualWalk == null || !manualWalk.isNegative) { "negative walk time" }
    }

    public companion object {
        public val DEFAULT_BUFFER: Duration = Duration.ofMinutes(1)
    }
}

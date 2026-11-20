package app.brokoli5191.zeitig.ui.status

import app.brokoli5191.zeitig.core.engine.LeaveOption
import app.brokoli5191.zeitig.core.engine.LeavePlan
import app.brokoli5191.zeitig.core.provider.FailureReason
import app.brokoli5191.zeitig.core.route.RouteStatus
import app.brokoli5191.zeitig.ui.format.minutesUntil
import java.time.Duration
import java.time.Instant

/** What the big number says – shared by home, detail, notifications, widget and tile. */
sealed interface LeaveDisplay {
    data class LeaveIn(
        val minutes: Long,
        val option: LeaveOption,
        val alternatives: List<LeaveOption>,
        val lateBy: Duration?,
    ) : LeaveDisplay

    data class LeaveNow(
        val option: LeaveOption,
        val alternatives: List<LeaveOption>,
    ) : LeaveDisplay

    data class TooLate(
        val nextAfter: LeaveOption?,
    ) : LeaveDisplay

    data object NoService : LeaveDisplay

    data object Loading : LeaveDisplay

    data class Unavailable(
        val reason: FailureReason,
    ) : LeaveDisplay

    companion object {
        fun of(
            status: RouteStatus?,
            now: Instant,
            failure: FailureReason? = null,
        ): LeaveDisplay {
            if (status == null) return if (failure != null) Unavailable(failure) else Loading
            return when (val plan = status.plan) {
                is LeavePlan.Go -> {
                    val minutes = minutesUntil(now, plan.chosen.leaveAt)
                    if (minutes <=
                        0
                    ) {
                        LeaveNow(plan.chosen, plan.alternatives)
                    } else {
                        LeaveIn(minutes, plan.chosen, plan.alternatives, plan.lateBy)
                    }
                }

                is LeavePlan.TooLate -> {
                    TooLate(null)
                }

                LeavePlan.NoService -> {
                    if (failure != null) Unavailable(failure) else NoService
                }
            }
        }
    }
}

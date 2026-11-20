package app.brokoli5191.zeitig.ui.status

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import app.brokoli5191.zeitig.R
import app.brokoli5191.zeitig.core.schedule.Schedule
import app.brokoli5191.zeitig.core.schedule.ScheduleMode
import app.brokoli5191.zeitig.ui.format.uiLocale
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** "Mo–Fr · 07:15–08:30" or "Mo–Fr · an 08:00". */
@Composable
fun ScheduleLabel(
    schedule: Schedule,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(scheduleText(schedule), modifier = modifier, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
}

@Composable
fun scheduleText(schedule: Schedule): String {
    val locale = uiLocale()
    val days = if (schedule.days.size == 7) stringResource(R.string.days_daily) else formatDays(schedule.days, locale)
    return when (val mode = schedule.mode) {
        is ScheduleMode.Window -> stringResource(R.string.schedule_window, days, time(mode.start, locale), time(mode.end, locale))
        is ScheduleMode.ArriveBy -> stringResource(R.string.schedule_arrive_by, days, time(mode.arrival, locale))
    }
}

private fun time(
    value: LocalTime,
    locale: Locale,
) = DateTimeFormatter.ofPattern("HH:mm", locale).format(value)

/** Consecutive days collapse into ranges: Mo–Fr, Sa, So → "Mo–Fr, Sa, So". */
fun formatDays(
    days: Set<DayOfWeek>,
    locale: Locale,
): String {
    fun name(day: DayOfWeek) = day.getDisplayName(TextStyle.SHORT_STANDALONE, locale).trimEnd('.')
    val sorted = days.sortedBy { it.value }
    val parts = mutableListOf<String>()
    var start: DayOfWeek? = null
    var previous: DayOfWeek? = null
    for (day in sorted + listOf(null)) {
        if (day != null && previous != null && day.value == previous.value + 1) {
            previous = day
            continue
        }
        if (start != null && previous != null) {
            parts +=
                if (start ==
                    previous
                ) {
                    name(start)
                } else if (previous.value - start.value ==
                    1
                ) {
                    "${name(start)}, ${name(previous)}"
                } else {
                    "${name(start)}–${name(previous)}"
                }
        }
        start = day
        previous = day
    }
    return parts.joinToString(", ")
}

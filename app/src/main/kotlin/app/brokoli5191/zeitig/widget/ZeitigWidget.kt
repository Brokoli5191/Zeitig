package app.brokoli5191.zeitig.widget

import android.content.Context
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.brokoli5191.zeitig.MainActivity
import app.brokoli5191.zeitig.R
import app.brokoli5191.zeitig.ZeitigApplication
import app.brokoli5191.zeitig.core.engine.LeavePlan
import app.brokoli5191.zeitig.core.schedule.ScheduleCalendar
import app.brokoli5191.zeitig.ui.format.VIENNA
import app.brokoli5191.zeitig.ui.format.formatClock
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.time.format.TextStyle as JavaTextStyle

/** What the widget shows; computed without network – live data only while monitoring runs. */
private sealed interface WidgetContent {
    data object Empty : WidgetContent

    data class Live(
        val route: String,
        /** Time left until leaving, measured with the app's clock when the content was built. */
        val remaining: Duration,
        val line: String,
        val towards: String,
        val departure: Instant,
        val realtime: Boolean,
    ) : WidgetContent

    data class Next(
        val route: String,
        val start: Instant?,
    ) : WidgetContent
}

class ZeitigWidget : GlanceAppWidget() {
    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val content = load(context)
        provideContent { GlanceTheme { Widget(context, content) } }
    }

    private suspend fun load(context: Context): WidgetContent {
        val container = (context.applicationContext as ZeitigApplication).container
        val now = container.clock.instant()
        // Live data exists only while the countdown runs; afterwards the board is stale.
        val board = if (container.statusBoard.monitoring.value) container.statusBoard.routes.value else emptyList()
        val live =
            board.firstNotNullOfOrNull { entry ->
                (entry.status?.plan as? LeavePlan.Go)?.chosen?.takeIf { it.leaveAt > now - Duration.ofMinutes(1) }?.let { entry to it }
            }
        if (live != null) {
            val (entry, option) = live
            return WidgetContent.Live(
                route = entry.route.name,
                remaining = Duration.between(now, option.leaveAt),
                line = option.departure.line,
                towards = option.departure.towards,
                departure = option.departure.time,
                realtime = option.departure.isRealtime,
            )
        }
        val routes = container.routes.routes()
        if (routes.isEmpty()) return WidgetContent.Empty
        val calendar = ScheduleCalendar()
        val next =
            routes
                .mapNotNull { route -> route.schedule?.let { calendar.next(it, now, route.journey.plannedTotal) }?.let { route to it } }
                .minByOrNull { it.second.start }
        return next?.let { WidgetContent.Next(it.first.name, it.second.start) } ?: WidgetContent.Next(routes.first().name, null)
    }

    companion object {
        fun refresh(context: Context) {
            val container = (context.applicationContext as ZeitigApplication).container
            container.applicationScope.launch { ZeitigWidget().updateAll(context) }
        }
    }
}

@Composable
private fun Widget(
    context: Context,
    content: WidgetContent,
) {
    val locale = context.resources.configuration.locales[0] ?: Locale.ROOT
    Column(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.primaryContainer)
                .cornerRadius(24.dp)
                .padding(16.dp)
                .clickable(actionStartActivity<MainActivity>()),
    ) {
        val strong = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        val normal = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = 13.sp)
        when (content) {
            WidgetContent.Empty -> {
                Text(context.getString(R.string.app_name), style = strong)
                Text(context.getString(R.string.widget_empty), style = normal)
            }

            is WidgetContent.Next -> {
                Text(content.route, style = strong)
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    content.start?.let {
                        val day = it.atZone(VIENNA).dayOfWeek.getDisplayName(JavaTextStyle.SHORT_STANDALONE, locale)
                        context.getString(
                            R.string.widget_next_window,
                            day,
                            DateTimeFormatter.ofPattern("HH:mm", locale).format(it.atZone(VIENNA)),
                        )
                    } ?: context.getString(R.string.schedule_none),
                    style = normal,
                )
            }

            is WidgetContent.Live -> {
                Text(content.route, style = strong)
                Spacer(GlanceModifier.height(4.dp))
                Text(context.getString(R.string.leave_in), style = normal)
                // A RemoteViews chronometer counts down by itself – no widget updates every second.
                val countdown =
                    RemoteViews(context.packageName, R.layout.widget_countdown).apply {
                        setChronometer(R.id.countdown, SystemClock.elapsedRealtime() + content.remaining.toMillis(), null, true)
                        setChronometerCountDown(R.id.countdown, true)
                    }
                AndroidRemoteViews(countdown)
                Text(
                    context.getString(
                        if (content.realtime) R.string.widget_departure_realtime else R.string.widget_departure_timetable,
                        content.line,
                        content.towards,
                        formatClock(content.departure, locale),
                    ),
                    style = normal,
                )
            }
        }
    }
}

class ZeitigWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ZeitigWidget()
}

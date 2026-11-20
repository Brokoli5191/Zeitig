package app.brokoli5191.zeitig.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.ceil

val VIENNA: ZoneId = ZoneId.of("Europe/Vienna")

/** The UI locale; German is formatted the Austrian way (de-AT: "Jänner"). */
@Composable
@ReadOnlyComposable
fun uiLocale(): Locale {
    val locales = LocalConfiguration.current.locales
    val locale = if (locales.isEmpty) Locale.ENGLISH else locales[0]
    return if (locale.language == "de" && locale.country.isEmpty()) Locale.forLanguageTag("de-AT") else locale
}

/** "07:32" in Vienna time, 12/24 h like the system. */
@Composable
fun clockTime(instant: Instant): String {
    val is24h = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    val pattern = if (is24h) "HH:mm" else "h:mm a"
    return DateTimeFormatter.ofPattern(pattern, uiLocale()).format(instant.atZone(VIENNA))
}

fun formatClock(
    instant: Instant,
    locale: Locale,
    is24h: Boolean = true,
): String = DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm a", locale).format(instant.atZone(VIENNA))

fun formatDate(
    instant: Instant,
    locale: Locale,
): String = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(instant.atZone(VIENNA))

/** Whole minutes until [target], rounded up so "leave in 1 min" never shows 0 too early. */
fun minutesUntil(
    now: Instant,
    target: Instant,
): Long {
    val seconds = Duration.between(now, target).seconds
    return if (seconds <= 0) 0 else ceil(seconds / 60.0).toLong()
}

fun Duration.wholeMinutes(): Long = ceil(seconds / 60.0).toLong().coerceAtLeast(0)

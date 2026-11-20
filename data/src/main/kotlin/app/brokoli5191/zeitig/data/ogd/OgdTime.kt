package app.brokoli5191.zeitig.data.ogd

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoField

/**
 * OGD timestamps look like `2026-11-20T22:03:30.000+0100`: ISO-like, but the offset has no colon.
 * Parsing keeps the offset (it is authoritative) and yields an [Instant]; presentation converts to
 * [VIENNA]. Variants with a colon, a `Z`, or without fraction are accepted too.
 */
internal object OgdTime {
    val VIENNA: ZoneId = ZoneId.of("Europe/Vienna")

    private val formatter: DateTimeFormatter =
        DateTimeFormatterBuilder()
            .append(DateTimeFormatter.ISO_LOCAL_DATE)
            .appendLiteral('T')
            .appendValue(ChronoField.HOUR_OF_DAY, 2)
            .appendLiteral(':')
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .optionalStart()
            .appendLiteral(':')
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
            .optionalEnd()
            .optionalEnd()
            .optionalStart()
            .appendOffset("+HH:MM", "Z")
            .optionalEnd()
            .optionalStart()
            .appendOffset("+HHMM", "Z")
            .optionalEnd()
            .toFormatter()

    /** Null for null, blank or unparseable input – a bad timestamp drops one departure, not the board. */
    fun parse(value: String?): Instant? {
        if (value.isNullOrBlank()) return null
        return try {
            val parsed = formatter.parseBest(value.trim(), OffsetDateTime::from, java.time.LocalDateTime::from)
            when (parsed) {
                is OffsetDateTime -> parsed.toInstant()
                is java.time.LocalDateTime -> parsed.atZone(VIENNA).toInstant()
                else -> null
            }
        } catch (_: DateTimeParseException) {
            null
        }
    }
}

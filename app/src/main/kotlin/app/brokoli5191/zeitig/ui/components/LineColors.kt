package app.brokoli5191.zeitig.ui.components

import androidx.compose.ui.graphics.Color
import app.brokoli5191.zeitig.core.model.TransportType

/**
 * Line colours as passengers know them from maps and platforms (wayfinding, not branding). The
 * planner's colour wins when it is present; otherwise U-Bahn lines get their known colours and
 * everything else a colour per transport type.
 */
object LineColors {
    private val metro =
        mapOf(
            "U1" to Color(0xFFE3000F),
            "U2" to Color(0xFFA762A3),
            "U3" to Color(0xFFEE7D00),
            "U4" to Color(0xFF319F49),
            "U5" to Color(0xFF008F95),
            "U6" to Color(0xFF9D6830),
        )

    fun of(
        line: String,
        type: TransportType,
        plannerColor: Int? = null,
    ): Color =
        plannerColor?.let(::Color)
            ?: metro[line.uppercase()]
            ?: when (type) {
                TransportType.METRO -> Color(0xFF5B5F66)
                TransportType.TRAM -> Color(0xFFC4161C)
                TransportType.BUS, TransportType.ON_DEMAND_BUS -> Color(0xFF1C5BA8)
                TransportType.NIGHT_BUS -> Color(0xFF2B2F6B)
                TransportType.TRAIN -> Color(0xFF0E7A8C)
                TransportType.OTHER -> Color(0xFF55606A)
            }

    /** Readable text on a line colour (WCAG relative luminance). */
    fun onColor(background: Color): Color {
        fun channel(c: Float) = if (c <= 0.03928f) c / 12.92f else Math.pow(((c + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
        val luminance = 0.2126f * channel(background.red) + 0.7152f * channel(background.green) + 0.0722f * channel(background.blue)
        return if (luminance > 0.4f) Color(0xFF111111) else Color.White
    }
}

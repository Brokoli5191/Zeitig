package app.brokoli5191.zeitig.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.brokoli5191.zeitig.core.journey.Journey
import app.brokoli5191.zeitig.core.journey.Leg
import app.brokoli5191.zeitig.core.model.GeoPoint
import app.brokoli5191.zeitig.ui.components.LineColors
import kotlin.math.cos
import kotlin.math.max

/**
 * The route drawn without map tiles: legs as coloured lines on a neutral plane, walks dashed.
 * Used when map tiles are switched off, while the real map loads, and in screenshot tests.
 */
@Composable
fun SchematicRouteMap(
    journey: Journey,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val background = MaterialTheme.colorScheme.surfaceContainerHigh
    val walkColor = MaterialTheme.colorScheme.onSurfaceVariant
    val stopFill = MaterialTheme.colorScheme.surface
    val stopStroke = MaterialTheme.colorScheme.onSurface
    val legs = journey.legs.map { leg -> leg to geometryOf(leg) }.filter { it.second.size >= 2 }
    val points = legs.flatMap { it.second }
    Box(modifier.background(background).semantics { this.contentDescription = contentDescription }) {
        if (points.isEmpty()) return@Box
        Canvas(Modifier.matchParentSize()) {
            val minLat = points.minOf { it.latitude }
            val maxLat = points.maxOf { it.latitude }
            val minLon = points.minOf { it.longitude }
            val maxLon = points.maxOf { it.longitude }
            // Equirectangular projection, longitude scaled by cos(latitude) so Vienna is not stretched.
            val lonScale = cos(Math.toRadians((minLat + maxLat) / 2))
            val spanX = max((maxLon - minLon) * lonScale, 1e-6)
            val spanY = max(maxLat - minLat, 1e-6)
            val padding = size.minDimension * 0.12f
            val scale = minOf((size.width - 2 * padding) / spanX, (size.height - 2 * padding) / spanY)
            val offsetX = (size.width - spanX * scale) / 2
            val offsetY = (size.height - spanY * scale) / 2

            fun project(p: GeoPoint) =
                Offset(
                    (offsetX + (p.longitude - minLon) * lonScale * scale).toFloat(),
                    (size.height - offsetY - (p.latitude - minLat) * scale).toFloat(),
                )
            val stroke = size.minDimension / 40f
            for ((leg, geometry) in legs) {
                val path = Path()
                geometry.forEachIndexed { i, p -> project(p).let { if (i == 0) path.moveTo(it.x, it.y) else path.lineTo(it.x, it.y) } }
                when (leg) {
                    is Leg.Walk -> {
                        drawPath(
                            path,
                            walkColor,
                            style =
                                Stroke(
                                    stroke * 0.6f,
                                    cap = StrokeCap.Round,
                                    pathEffect =
                                        PathEffect.dashPathEffect(
                                            floatArrayOf(
                                                stroke,
                                                stroke * 1.4f,
                                            ),
                                        ),
                                ),
                        )
                    }

                    is Leg.Transit -> {
                        drawPath(
                            path,
                            LineColors.of(leg.line, leg.type, leg.color),
                            style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
                        )
                    }
                }
            }
            for ((leg, geometry) in legs) {
                if (leg !is Leg.Transit) continue
                for (end in listOf(geometry.first(), geometry.last())) {
                    val c = project(end)
                    drawCircle(stopFill, stroke * 0.9f, c)
                    drawCircle(stopStroke, stroke * 0.9f, c, style = Stroke(stroke * 0.35f))
                }
            }
        }
    }
}

/** Geometry of a leg, or a straight line between its ends when the planner gave none. */
fun geometryOf(leg: Leg): List<GeoPoint> =
    leg.geometry.ifEmpty {
        listOfNotNull(leg.from.location) +
            ((leg as? Leg.Transit)?.intermediateStops?.mapNotNull { it.location }.orEmpty()) +
            listOfNotNull(leg.to.location)
    }

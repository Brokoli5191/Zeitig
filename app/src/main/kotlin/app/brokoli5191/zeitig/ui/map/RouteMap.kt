package app.brokoli5191.zeitig.ui.map

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.brokoli5191.zeitig.R
import app.brokoli5191.zeitig.ZeitigApplication
import app.brokoli5191.zeitig.core.journey.Journey
import app.brokoli5191.zeitig.core.journey.Leg
import app.brokoli5191.zeitig.data.map.BasemapStyle
import app.brokoli5191.zeitig.ui.components.LineColors
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * The route on a real map (MapLibre + basemap.at) when map tiles are enabled; otherwise – and while
 * the style loads, in previews and in tests – the schematic drawing.
 */
@Composable
fun RouteMap(
    journey: Journey,
    tilesEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.cd_route_map)
    if (!tilesEnabled || LocalInspectionMode.current) {
        SchematicRouteMap(journey, description, modifier)
        return
    }
    val context = LocalContext.current
    var style by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { style = (context.applicationContext as ZeitigApplication).container.mapStyles.style() }
    val loaded = style
    if (loaded == null) {
        SchematicRouteMap(journey, description, modifier)
        return
    }
    Box(modifier) {
        MapLibreRoute(journey, loaded)
        Text(
            BasemapStyle.ATTRIBUTION,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
        )
    }
}

@SuppressLint("ClickableViewAccessibility")
@Composable
private fun MapLibreRoute(
    journey: Journey,
    styleJson: String,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val walkColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val stopStroke = MaterialTheme.colorScheme.onSurface.toArgb()
    val paddingPx = with(LocalDensity.current) { 40.dp.roundToPx() }
    val mapView =
        remember {
            MapLibre.getInstance(context)
            MapView(context).apply { onCreate(Bundle()) }
        }
    DisposableEffect(lifecycle, mapView) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> mapView.onStart()
                    Lifecycle.Event.ON_RESUME -> mapView.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                    Lifecycle.Event.ON_STOP -> mapView.onStop()
                    else -> Unit
                }
            }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onStop()
            mapView.onDestroy()
        }
    }
    AndroidView(factory = { mapView }) { view ->
        view.getMapAsync { map ->
            map.uiSettings.isRotateGesturesEnabled = false
            map.uiSettings.isTiltGesturesEnabled = false
            map.setStyle(Style.Builder().fromJson(styleJson)) { style -> showRoute(map, style, journey, walkColor, stopStroke, paddingPx) }
        }
    }
}

private fun showRoute(
    map: MapLibreMap,
    style: Style,
    journey: Journey,
    walkColor: Int,
    stopStroke: Int,
    paddingPx: Int,
) {
    val features = mutableListOf<Feature>()
    val allPoints = mutableListOf<Point>()
    for (leg in journey.legs) {
        val points = geometryOf(leg).map { Point.fromLngLat(it.longitude, it.latitude) }
        if (points.size < 2) continue
        allPoints += points
        val feature = Feature.fromGeometry(LineString.fromLngLats(points))
        when (leg) {
            is Leg.Walk -> {
                feature.addBooleanProperty("walk", true)
            }

            is Leg.Transit -> {
                feature.addBooleanProperty("walk", false)
                feature.addStringProperty("color", hex(LineColors.of(leg.line, leg.type, leg.color).toArgb()))
                features += Feature.fromGeometry(points.first()).apply { addBooleanProperty("stop", true) }
                features += Feature.fromGeometry(points.last()).apply { addBooleanProperty("stop", true) }
            }
        }
        features += feature
    }
    val collection = FeatureCollection.fromFeatures(features)
    val source = style.getSourceAs<GeoJsonSource>(SOURCE)
    if (source != null) {
        source.setGeoJson(collection)
    } else {
        style.addSource(GeoJsonSource(SOURCE, collection))
        style.addLayer(
            LineLayer("zeitig-walk", SOURCE)
                .withFilter(Expression.eq(Expression.get("walk"), Expression.literal(true)))
                .withProperties(
                    PropertyFactory.lineColor(walkColor),
                    PropertyFactory.lineWidth(3f),
                    PropertyFactory.lineDasharray(arrayOf(1.5f, 1.5f)),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                ),
        )
        style.addLayer(
            LineLayer("zeitig-transit", SOURCE)
                .withFilter(Expression.eq(Expression.get("walk"), Expression.literal(false)))
                .withProperties(
                    PropertyFactory.lineColor(Expression.toColor(Expression.get("color"))),
                    PropertyFactory.lineWidth(6f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                ),
        )
        style.addLayer(
            CircleLayer("zeitig-stops", SOURCE)
                .withFilter(Expression.has("stop"))
                .withProperties(
                    PropertyFactory.circleRadius(6f),
                    PropertyFactory.circleColor(0xFFFFFFFF.toInt()),
                    PropertyFactory.circleStrokeColor(stopStroke),
                    PropertyFactory.circleStrokeWidth(2.5f),
                ),
        )
    }
    if (allPoints.size >= 2) {
        val bounds = LatLngBounds.Builder().includes(allPoints.map { LatLng(it.latitude(), it.longitude()) }).build()
        map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, paddingPx))
    }
}

private fun hex(argb: Int): String = String.format("#%06X", argb and 0xFFFFFF)

private const val SOURCE = "zeitig-route"

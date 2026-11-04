package io.github.numynox.nexus.abeona.ui.trip

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.doOnLayout
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.numynox.nexus.abeona.AbeonaApplication
import io.github.numynox.nexus.abeona.BuildConfig
import io.github.numynox.nexus.abeona.data.Leg
import io.github.numynox.nexus.abeona.ui.AbeonaColors
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.tan
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/** A leg's line on the map: its own polyline, or straight from stop to stop when it has none. */
internal fun Leg.points(): List<Pair<Double, Double>> {
    val line = polyline?.mapNotNull { p -> if (p.size >= 2) p[0] to p[1] else null }.orEmpty()
    if (line.size >= 2) return line
    val a = from.lon?.let { lon -> from.lat?.let { lon to it } }
    val b = to.lon?.let { lon -> to.lat?.let { lon to it } }
    return listOfNotNull(a, b)
}

private fun hex(color: Color) = "#%06X".format(0xFFFFFF and color.toArgb())

/**
 * CARTO's basemap with the websites' key, or OpenStreetMap without one, with
 * the trip on top: every ride as a line (dashed for walks), every stop where
 * someone gets on or off as a dot. All in one style document, so the map has
 * nothing to add after it loads.
 */
private fun tripStyle(legs: List<Leg>, dark: Boolean, ride: Color, walk: Color): String {
    val key = BuildConfig.CARTO_API_KEY
    val carto = key.isNotBlank()
    val tiles = if (carto) {
        val variant = if (dark) "dark_all" else "light_all"
        listOf("a", "b", "c", "d").map { "https://$it.basemaps.cartocdn.com/$variant/{z}/{x}/{y}@2x.png?key=$key" }
    } else {
        listOf("https://tile.openstreetmap.org/{z}/{x}/{y}.png")
    }
    val features = buildJsonObject {
        put("type", "FeatureCollection")
        putJsonArray("features") {
            legs.forEach { leg ->
                val points = leg.points()
                if (points.size < 2) return@forEach
                addJsonObject {
                    put("type", "Feature")
                    putJsonObject("properties") { put("walk", leg.isWalk) }
                    putJsonObject("geometry") {
                        put("type", "LineString")
                        putJsonArray("coordinates") { points.forEach { (lon, lat) -> addJsonArray { add(lon); add(lat) } } }
                    }
                }
            }
            legs.filter { !it.isWalk }.flatMap { listOf(it.from, it.to) }.distinctBy { it.name }.forEach { stop ->
                val lat = stop.lat ?: return@forEach
                val lon = stop.lon ?: return@forEach
                addJsonObject {
                    put("type", "Feature")
                    putJsonObject("properties") { put("stop", true) }
                    putJsonObject("geometry") {
                        put("type", "Point")
                        putJsonArray("coordinates") { add(lon); add(lat) }
                    }
                }
            }
        }
    }
    fun walkIs(value: Boolean) =
        JsonArray(listOf(JsonPrimitive("=="), JsonArray(listOf(JsonPrimitive("get"), JsonPrimitive("walk"))), JsonPrimitive(value)))
    return buildJsonObject {
        put("version", 8)
        putJsonObject("sources") {
            putJsonObject("base") {
                put("type", "raster")
                putJsonArray("tiles") { tiles.forEach { add(it) } }
                put("tileSize", 256)
                put("maxzoom", if (carto) 20 else 19)
                put("attribution", if (carto) "© OpenStreetMap contributors © CARTO" else "© OpenStreetMap contributors")
            }
            putJsonObject("trip") {
                put("type", "geojson")
                put("data", features)
            }
        }
        putJsonArray("layers") {
            addJsonObject {
                put("id", "base"); put("type", "raster"); put("source", "base")
                if (dark && !carto) putJsonObject("paint") { put("raster-brightness-max", 0.55); put("raster-saturation", -0.4) }
            }
            addJsonObject {
                put("id", "ride-casing"); put("type", "line"); put("source", "trip")
                put("filter", walkIs(false))
                putJsonObject("layout") { put("line-cap", "round"); put("line-join", "round") }
                putJsonObject("paint") { put("line-color", "#FFFFFF"); put("line-width", 8) }
            }
            addJsonObject {
                put("id", "ride"); put("type", "line"); put("source", "trip")
                put("filter", walkIs(false))
                putJsonObject("layout") { put("line-cap", "round"); put("line-join", "round") }
                putJsonObject("paint") { put("line-color", hex(ride)); put("line-width", 5) }
            }
            addJsonObject {
                put("id", "walk"); put("type", "line"); put("source", "trip")
                put("filter", walkIs(true))
                putJsonObject("paint") {
                    put("line-color", hex(walk)); put("line-width", 3)
                    putJsonArray("line-dasharray") { add(1.5); add(1.5) }
                }
            }
            addJsonObject {
                put("id", "stops"); put("type", "circle"); put("source", "trip")
                put("filter", JsonArray(listOf(JsonPrimitive("=="), JsonArray(listOf(JsonPrimitive("geometry-type"))), JsonPrimitive("Point"))))
                putJsonObject("paint") {
                    put("circle-radius", 6); put("circle-color", "#FFFFFF")
                    put("circle-stroke-color", hex(AbeonaColors.Ink)); put("circle-stroke-width", 3)
                }
            }
        }
    }.toString()
}

/**
 * The trip on a map, as a still preview: it takes no gestures, so it never
 * steals a scroll or a pager swipe. A tap opens it full screen, where it pans
 * and zooms until it is closed. A drawn route on a plain background where
 * MapLibre cannot run (the screenshot tests).
 */
@Composable
fun TripMap(legs: List<Leg>, modifier: Modifier = Modifier) {
    var fullScreen by rememberSaveable { mutableStateOf(false) }
    val shape = RoundedCornerShape(28.dp)
    Box(modifier.clip(shape)) {
        RouteMap(legs, Modifier.fillMaxSize(), interactive = false)
        // Over the map view, which would otherwise take the touch itself.
        Box(
            Modifier
                .fillMaxSize()
                .clickable(onClickLabel = "Open the map full screen") { fullScreen = true },
        )
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).size(36.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.OpenInFull, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
    if (fullScreen) {
        Dialog(
            onDismissRequest = { fullScreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                RouteMap(legs, Modifier.fillMaxSize(), interactive = true)
                FilledTonalIconButton(
                    onClick = { fullScreen = false },
                    modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(16.dp),
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close the map")
                }
            }
        }
    }
}

@Composable
private fun RouteMap(legs: List<Leg>, modifier: Modifier, interactive: Boolean) {
    val context = LocalContext.current
    if ((context.applicationContext as? AbeonaApplication)?.mapsAvailable == false) {
        SchematicTripMap(legs, modifier)
        return
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val ride = MaterialTheme.colorScheme.primary
    val walk = MaterialTheme.colorScheme.outline
    val padding = with(LocalDensity.current) { (if (interactive) 64.dp else 28.dp).roundToPx() }
    val mapView = remember { MapView(context).apply { onCreate(null) } }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView, legs, dark) {
        mapView.getMapAsync { map ->
            map.uiSettings.isCompassEnabled = false
            map.uiSettings.isRotateGesturesEnabled = false
            map.uiSettings.isTiltGesturesEnabled = false
            map.uiSettings.isLogoEnabled = false
            if (!interactive) map.uiSettings.setAllGesturesEnabled(false)
            map.setStyle(Style.Builder().fromJson(tripStyle(legs, dark, ride, walk))) {
                val points = legs.flatMap { it.points() }.map { (lon, lat) -> LatLng(lat, lon) }
                // Fitting bounds needs the view's real size; before layout it
                // is zero and the camera ends up at the wrong zoom.
                mapView.doOnLayout {
                    when {
                        points.size >= 2 -> map.moveCamera(
                            CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(points).build(), padding),
                        )
                        points.size == 1 -> map.moveCamera(CameraUpdateFactory.newLatLngZoom(points.first(), 12.0))
                    }
                }
            }
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

/** The route drawn by coordinates on a soft grid: no tiles, same shape. */
@Composable
private fun SchematicTripMap(legs: List<Leg>, modifier: Modifier) {
    val background = MaterialTheme.colorScheme.surfaceContainer
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val water = MaterialTheme.colorScheme.secondaryContainer
    val ride = MaterialTheme.colorScheme.primary
    val walk = MaterialTheme.colorScheme.outline
    val surface = MaterialTheme.colorScheme.surface
    val ink = MaterialTheme.colorScheme.onSurface
    Canvas(modifier.background(background)) {
        val step = 36.dp.toPx()
        var x = 0f
        while (x < size.width) { drawLine(grid, Offset(x, 0f), Offset(x, size.height)); x += step }
        var y = 0f
        while (y < size.height) { drawLine(grid, Offset(0f, y), Offset(size.width, y)); y += step }
        // A river, so it reads as a map.
        val river = Path().apply {
            moveTo(0f, size.height * 0.7f)
            cubicTo(size.width * 0.3f, size.height * 0.55f, size.width * 0.5f, size.height * 0.95f, size.width, size.height * 0.8f)
        }
        drawPath(river, water, style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round))

        // Web Mercator, so the shape matches the real map's.
        fun mercatorY(lat: Double) = ln(tan(PI / 4 + Math.toRadians(lat) / 2))
        val all = legs.flatMap { it.points() }
        if (all.isEmpty()) return@Canvas
        val minX = all.minOf { it.first }; val maxX = all.maxOf { it.first }
        val minY = all.minOf { mercatorY(it.second) }; val maxY = all.maxOf { mercatorY(it.second) }
        val pad = 28.dp.toPx()
        val spanX = (maxX - minX).coerceAtLeast(1e-6) * PI / 180
        val spanY = (maxY - minY).coerceAtLeast(1e-6)
        val scale = minOf((size.width - 2 * pad) / spanX, (size.height - 2 * pad) / spanY).toFloat()
        val offsetX = (size.width - spanX.toFloat() * scale) / 2
        val offsetY = (size.height - spanY.toFloat() * scale) / 2
        fun place(p: Pair<Double, Double>) = Offset(
            offsetX + ((p.first - minX) * PI / 180).toFloat() * scale,
            offsetY + (maxY - mercatorY(p.second)).toFloat() * scale,
        )
        legs.forEach { leg ->
            val points = leg.points().map(::place)
            if (points.size < 2) return@forEach
            val path = Path().apply { moveTo(points[0].x, points[0].y); points.drop(1).forEach { lineTo(it.x, it.y) } }
            if (leg.isWalk) {
                drawPath(path, walk, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))))
            } else {
                drawPath(path, surface, style = Stroke(9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(path, ride, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        legs.filter { !it.isWalk }.flatMap { listOf(it.from, it.to) }.forEach { stop ->
            val lat = stop.lat ?: return@forEach
            val lon = stop.lon ?: return@forEach
            val center = place(lon to lat)
            drawCircle(ink, radius = 7.dp.toPx(), center = center)
            drawCircle(surface, radius = 4.dp.toPx(), center = center)
        }
    }
}

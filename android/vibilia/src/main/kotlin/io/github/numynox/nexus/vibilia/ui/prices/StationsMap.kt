package io.github.numynox.nexus.vibilia.ui.prices

import android.graphics.PointF
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.numynox.nexus.core.ui.components.PriceText
import io.github.numynox.nexus.vibilia.BuildConfig
import io.github.numynox.nexus.vibilia.data.StationPrice
import io.github.numynox.nexus.vibilia.location.LatLng
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.geometry.LatLng as MapLatLng

/**
 * CARTO's basemap with the public key the websites use, or OpenStreetMap
 * without one (decisions/2026-09-05-carto-basemap-api-key.md). In dark mode
 * CARTO has a dark style; OpenStreetMap's tiles are dimmed instead.
 */
private fun basemapStyle(dark: Boolean): String {
    val key = BuildConfig.CARTO_API_KEY
    val carto = key.isNotBlank()
    val tiles = if (carto) {
        val variant = if (dark) "dark_all" else "light_all"
        listOf("a", "b", "c", "d").map { "https://$it.basemaps.cartocdn.com/$variant/{z}/{x}/{y}@2x.png?key=$key" }
    } else {
        listOf("https://tile.openstreetmap.org/{z}/{x}/{y}.png")
    }
    return buildJsonObject {
        put("version", 8)
        putJsonObject("sources") {
            putJsonObject("base") {
                put("type", "raster")
                putJsonArray("tiles") { tiles.forEach { add(it) } }
                put("tileSize", 256)
                put("maxzoom", if (carto) 20 else 19)
                put(
                    "attribution",
                    if (carto) "© OpenStreetMap contributors © CARTO" else "© OpenStreetMap contributors",
                )
            }
        }
        putJsonArray("layers") {
            add(
                buildJsonObject {
                    put("id", "base")
                    put("type", "raster")
                    put("source", "base")
                    if (dark && !carto) {
                        putJsonObject("paint") {
                            put("raster-brightness-max", 0.55)
                            put("raster-saturation", -0.4)
                        }
                    }
                },
            )
        }
    }.toString()
}

/**
 * The nearby stations on a map, each a price pill drawn in Compose and kept in
 * place through the map's projection. Tapping one selects it, and selecting
 * one in the list glides the map there.
 */
@Composable
fun StationsMap(
    stations: List<StationPrice>,
    userLocation: LatLng?,
    selectedId: String?,
    lowestPrice: Double?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val mapView = remember { MapView(context).apply { onCreate(null) } }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var cameraTick by remember { mutableIntStateOf(0) }
    var fitted by remember { mutableStateOf(false) }

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

    LaunchedEffect(mapView, dark) {
        mapView.getMapAsync { m ->
            m.uiSettings.isCompassEnabled = false
            m.uiSettings.isRotateGesturesEnabled = false
            m.uiSettings.isTiltGesturesEnabled = false
            m.uiSettings.isLogoEnabled = false
            m.addOnCameraMoveListener { cameraTick++ }
            m.addOnCameraIdleListener { cameraTick++ }
            m.setStyle(Style.Builder().fromJson(basemapStyle(dark))) {
                map = m
                cameraTick++
            }
        }
    }

    // Frame the user and every station once, when they first arrive.
    LaunchedEffect(map, stations, userLocation) {
        val m = map ?: return@LaunchedEffect
        if (fitted) return@LaunchedEffect
        val points = stations.mapNotNull { s -> s.lat?.let { lat -> s.lng?.let { MapLatLng(lat, it) } } } +
            listOfNotNull(userLocation?.let { MapLatLng(it.lat, it.lng) })
        when {
            points.size >= 2 -> {
                m.moveCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(points).build(), 120))
                fitted = true
            }
            points.size == 1 -> {
                m.moveCamera(CameraUpdateFactory.newLatLngZoom(points.first(), 13.0))
                fitted = true
            }
        }
        cameraTick++
    }

    LaunchedEffect(map, selectedId) {
        val m = map ?: return@LaunchedEffect
        val station = stations.firstOrNull { it.id == selectedId } ?: return@LaunchedEffect
        val lat = station.lat ?: return@LaunchedEffect
        val lng = station.lng ?: return@LaunchedEffect
        m.animateCamera(CameraUpdateFactory.newLatLng(MapLatLng(lat, lng)), 450)
    }

    Box(modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        val m = map ?: return@Box
        // Read the tick so every camera movement re-places the pills.
        @Suppress("UNUSED_VARIABLE") val tick = cameraTick
        userLocation?.let { location ->
            val point = m.projection.toScreenLocation(MapLatLng(location.lat, location.lng))
            UserDot(Modifier.centeredAt(point))
        }
        stations.sortedBy { it.id == selectedId }.forEach { station ->
            val lat = station.lat ?: return@forEach
            val lng = station.lng ?: return@forEach
            val point = m.projection.toScreenLocation(MapLatLng(lat, lng))
            val price = station.effectivePrice
            PricePill(
                price = price,
                cheapest = price != null && lowestPrice != null && kotlin.math.abs(price - lowestPrice) < 1e-6,
                selected = station.id == selectedId,
                onClick = { onSelect(station.id) },
                modifier = Modifier.centeredAt(point),
            )
        }
    }
}

/** Places a child with its centre on [point], in the map's pixel coordinates. */
private fun Modifier.centeredAt(point: PointF): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(point.x.toInt() - placeable.width / 2, point.y.toInt() - placeable.height / 2)
    }
}

@Composable
private fun PricePill(price: Double?, cheapest: Boolean, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val scale by animateFloatAsState(if (selected) 1.18f else 1f, label = "pill")
    val container by animateColorAsState(
        when {
            selected -> MaterialTheme.colorScheme.inverseSurface
            cheapest -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.surface
        },
        label = "pill colour",
    )
    val content = when {
        selected -> MaterialTheme.colorScheme.inverseOnSurface
        cheapest -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        onClick = onClick,
        modifier = modifier.scale(scale),
        shape = CircleShape,
        color = container,
        contentColor = content,
        shadowElevation = if (selected) 8.dp else 3.dp,
    ) {
        if (price != null) {
            PriceText(
                price,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = content,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        } else {
            Box(Modifier.size(12.dp).padding(4.dp))
        }
    }
}

@Composable
private fun UserDot(modifier: Modifier) {
    Box(
        modifier
            .size(18.dp)
            .background(MaterialTheme.colorScheme.surface, CircleShape)
            .padding(3.dp)
            .background(MaterialTheme.colorScheme.tertiary, CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape),
    )
}

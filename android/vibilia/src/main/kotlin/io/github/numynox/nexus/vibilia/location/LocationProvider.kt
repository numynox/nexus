package io.github.numynox.nexus.vibilia.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

data class LatLng(val lat: Double, val lng: Double)

/**
 * The phone's position from the platform LocationManager — deliberately not
 * Google Play services, so the app needs nothing from Google
 * (decisions/2026-10-19-native-android-apps.md).
 */
class LocationProvider(private val context: Context) {

    val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    fun hasPermission(): Boolean = permissions.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    suspend fun current(timeoutMillis: Long = 10_000): LatLng? {
        if (!hasPermission()) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val provider = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && manager.hasProvider(LocationManager.FUSED_PROVIDER) -> LocationManager.FUSED_PROVIDER
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> return null
        }
        val fresh = runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            ?.takeIf { System.currentTimeMillis() - it.time < 60_000 }
        if (fresh != null) return fresh.toLatLng()

        val location = withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine<Location?> { continuation ->
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                LocationManagerCompat.getCurrentLocation(manager, provider, signal, Executors.newSingleThreadExecutor()) {
                    if (continuation.isActive) continuation.resume(it)
                }
            }
        }
        return (location ?: runCatching { manager.getLastKnownLocation(provider) }.getOrNull())?.toLatLng()
    }

    private fun Location.toLatLng() = LatLng(latitude, longitude)
}

/** Great-circle distance in metres. */
fun LatLng.distanceTo(other: LatLng): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(other.lat - lat)
    val dLng = Math.toRadians(other.lng - lng)
    val a = kotlin.math.sin(dLat / 2).let { it * it } +
        kotlin.math.cos(Math.toRadians(lat)) * kotlin.math.cos(Math.toRadians(other.lat)) *
        kotlin.math.sin(dLng / 2).let { it * it }
    return 2 * r * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
}

package io.github.numynox.nexus.abeona.live

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import io.github.numynox.nexus.abeona.data.Trip
import io.github.numynox.nexus.abeona.domain.Position
import io.github.numynox.nexus.abeona.domain.TravelSignals
import io.github.numynox.nexus.abeona.domain.distanceMetres
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A position fix, and when it was taken. */
data class Fix(val position: Position, val at: Instant)

/**
 * What this phone knows beyond the timetable: the last on-board Wi-Fi status
 * and the last position fix. Written by the live companion (and the trip
 * screen for the Wi-Fi), read by every travel-day hero. Never stored, never
 * sent anywhere.
 */
object LiveSignals {
    /** Older signals are ignored: a train left, a fix went stale. */
    val FRESH: Duration = Duration.ofMinutes(3)

    private val _onboard = MutableStateFlow<OnboardStatus?>(null)
    val onboard: StateFlow<OnboardStatus?> = _onboard.asStateFlow()

    private val _fix = MutableStateFlow<Fix?>(null)
    val fix: StateFlow<Fix?> = _fix.asStateFlow()

    fun setOnboard(status: OnboardStatus?) {
        _onboard.value = status
    }

    fun setFix(fix: Fix?) {
        _fix.value = fix
    }

    fun clear() {
        _onboard.value = null
        _fix.value = null
    }

    /** The on-board status, if it is fresh and names one of [trip]'s rides. */
    fun onboardFor(trip: Trip, now: Instant, status: OnboardStatus? = onboard.value): OnboardStatus? =
        status?.takeIf { Duration.between(it.at, now) <= FRESH && matchOnboardLeg(trip.legs, it) != null }

    /** The travel-day signals for [trip] at [now], from what is fresh. */
    fun forTrip(trip: Trip, now: Instant, status: OnboardStatus? = onboard.value, fix: Fix? = this.fix.value): TravelSignals =
        TravelSignals(
            onboardLegIndex = onboardFor(trip, now, status)?.let { matchOnboardLeg(trip.legs, it) },
            position = fix?.takeIf { Duration.between(it.at, now) <= FRESH }?.position,
        )
}

/**
 * The phone's position during a trip, from the platform LocationManager (no
 * Google Play services, like Vibilia). Only while the live companion runs in
 * the foreground, every 30 seconds or 50 metres at most, and only with the
 * permission and the "Use location during a trip" setting.
 */
class TripLocation(private val context: Context) {
    private var listener: LocationListener? = null
    private var last: Location? = null

    fun hasPermission(): Boolean = PERMISSIONS.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (listener != null) return true
        if (!hasPermission()) return false
        val manager = context.getSystemService(LocationManager::class.java) ?: return false
        val provider = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && manager.hasProvider(LocationManager.FUSED_PROVIDER) -> LocationManager.FUSED_PROVIDER
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> return false
        }
        val l = LocationListener { location -> LiveSignals.setFix(Fix(Position(location.latitude, location.longitude, speedOf(location)), Instant.now())) }
        return runCatching {
            manager.requestLocationUpdates(provider, INTERVAL_MS, MIN_METRES, l, Looper.getMainLooper())
            listener = l
            true
        }.getOrDefault(false)
    }

    fun stop() {
        val l = listener ?: return
        context.getSystemService(LocationManager::class.java)?.removeUpdates(l)
        listener = null
        last = null
        LiveSignals.setFix(null)
    }

    /** The fix's own speed, else from the distance to the previous fix. */
    private fun speedOf(location: Location): Double? {
        val previous = last
        last = location
        if (location.hasSpeed()) return location.speed * 3.6
        if (previous == null) return null
        val seconds = (location.time - previous.time) / 1000.0
        if (seconds <= 0) return null
        return distanceMetres(previous.latitude, previous.longitude, location.latitude, location.longitude) / seconds * 3.6
    }

    companion object {
        val PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        private const val INTERVAL_MS = 30_000L
        private const val MIN_METRES = 50f
    }
}

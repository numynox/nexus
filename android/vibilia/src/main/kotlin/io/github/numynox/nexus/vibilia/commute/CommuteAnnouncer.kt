package io.github.numynox.nexus.vibilia.commute

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.numynox.nexus.vibilia.R
import io.github.numynox.nexus.vibilia.VibiliaApplication
import io.github.numynox.nexus.vibilia.data.CommuteExpectation
import io.github.numynox.nexus.vibilia.data.CommuteRepository
import io.github.numynox.nexus.vibilia.data.VibiliaPreferences
import io.github.numynox.nexus.vibilia.location.LatLng
import java.time.Duration
import java.time.Instant
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * "Die Ankunftszeit auf der Arbeit ist 7:45 Uhr. Gute Fahrt." — spoken through
 * the car when this phone connects to it inside a commute window, near where
 * that trip starts (decisions/2026-11-02-commute-arrival-announcement.md).
 *
 * The receiver only decides whether a connection is worth a look ([shouldListen]);
 * the minute of listening for the position and the speech run in
 * [CommuteAnnouncementService], a location foreground service, because a
 * receiver lives for seconds. Starting it from the background is allowed for a
 * Bluetooth broadcast that needs BLUETOOTH_CONNECT; reading the position from
 * there needs background location, which the switch asks for.
 */
object CommuteAnnouncer {
    internal const val ExtraCarId = "car_id"
    internal const val ExtraConnectedAt = "connected_at"

    val permissions: List<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(Manifest.permission.BLUETOOTH_CONNECT)
    }

    fun hasPermissions(context: Context): Boolean = permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /** Your commutes and their travel times, into the cache a connection reads. */
    suspend fun refresh(repository: CommuteRepository, preferences: VibiliaPreferences) {
        val commutes = runCatching { repository.commutes() }.getOrElse { return }
        preferences.setCachedCommutes(commutes)
        val expectations = commutes.flatMap { commute ->
            runCatching { repository.expectation(commute.carId) }.getOrDefault(emptyList())
        }
        preferences.setCachedCommuteExpectations(expectations)
    }

    /** Whether a connection to [carId] at [now] could be a commute this phone should announce. */
    suspend fun shouldListen(context: Context, preferences: VibiliaPreferences, carId: String, now: Instant): Boolean {
        if (!preferences.current().announceCommute || !hasPermissions(context)) return false
        val commute = preferences.cachedCommutes().firstOrNull { it.carId == carId } ?: return false
        val direction = CommutePlan.directionAt(commute, now) ?: return false
        return preferences.commuteAnnounced() != CommutePlan.announcedKey(carId, direction, now)
    }

    suspend fun onConnected(context: Context, preferences: VibiliaPreferences, carId: String, now: Instant = Instant.now()) {
        if (!shouldListen(context, preferences, carId, now)) return
        val intent = Intent(context, CommuteAnnouncementService::class.java)
            .putExtra(ExtraCarId, carId)
            .putExtra(ExtraConnectedAt, now.toEpochMilli())
        runCatching { ContextCompat.startForegroundService(context, intent) }
            .onFailure { Log.w("CommuteAnnouncer", "Could not start the announcement", it) }
    }
}

class CommuteAnnouncementService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var running: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val carId = intent?.getStringExtra(CommuteAnnouncer.ExtraCarId)
        val connectedAt = intent?.getLongExtra(CommuteAnnouncer.ExtraConnectedAt, 0L)?.takeIf { it > 0 }?.let(Instant::ofEpochMilli)
        val started = runCatching {
            ServiceCompat.startForeground(this, NotificationId, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        }
        if (started.isFailure || carId == null || connectedAt == null) {
            started.exceptionOrNull()?.let { Log.w("CommuteAnnouncer", "Not allowed to listen for the position", it) }
            stopSelf(startId)
            return START_NOT_STICKY
        }
        // A second connection while listening adds nothing: the first one is already on it.
        if (running?.isActive == true) return START_NOT_STICKY
        running = scope.launch {
            try {
                announce(carId, connectedAt)
            } finally {
                ServiceCompat.stopForeground(this@CommuteAnnouncementService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun announce(carId: String, connectedAt: Instant) {
        val container = (application as VibiliaApplication).container
        val preferences = container.preferences
        val commute = preferences.cachedCommutes().firstOrNull { it.carId == carId } ?: return
        val direction = CommutePlan.directionAt(commute, connectedAt) ?: return

        // The travel time from the server if it answers within the minute, else the cache.
        val fresh = scope.async(Dispatchers.IO) {
            withTimeoutOrNull(CommutePlan.ListenFor.toMillis()) {
                runCatching { container.commutes.expectation(carId) }.getOrNull()
            }
        }
        val fixes = listen(until = connectedAt.plus(CommutePlan.ListenFor))
        if (!CommutePlan.startsAtOrigin(commute, direction, fixes)) return

        val expectations: List<CommuteExpectation> = fresh.await()
            ?: preferences.cachedCommuteExpectations().filter { it.carId == carId }
        val seconds = CommutePlan.expectedSeconds(expectations, direction) ?: return
        val text = CommutePlan.announcement(direction, connectedAt.plusSeconds(seconds.toLong()))
        if (speak(text)) preferences.setCommuteAnnounced(CommutePlan.announcedKey(carId, direction, connectedAt))
    }

    /** Every position the phone reports until [until], plus a recent last-known one. */
    @SuppressLint("MissingPermission")
    private suspend fun listen(until: Instant): List<LatLng> {
        val manager = getSystemService(LocationManager::class.java) ?: return emptyList()
        val fixes = mutableListOf<LatLng>()
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                fixes += LatLng(location.latitude, location.longitude)
            }

            @Deprecated("Required below API 30")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        providers.forEach { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
                ?.takeIf { System.currentTimeMillis() - it.time < Duration.ofMinutes(2).toMillis() }
                ?.let { fixes += LatLng(it.latitude, it.longitude) }
            runCatching { manager.requestLocationUpdates(provider, 2_000L, 0f, listener, Looper.getMainLooper()) }
                .onFailure { Log.w("CommuteAnnouncer", "No updates from $provider", it) }
        }
        try {
            delay(Duration.between(Instant.now(), until).toMillis().coerceAtLeast(0))
        } finally {
            manager.removeUpdates(listener)
        }
        return fixes.toList()
    }

    /** Speaks [text] in German as navigation guidance, which the car plays. True once it was spoken. */
    private suspend fun speak(text: String): Boolean = withContext(Dispatchers.Main) {
        val audio = getSystemService(AudioManager::class.java)
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .build()
        var tts: TextToSpeech? = null
        try {
            val ready = withTimeoutOrNull(10_000) {
                suspendCancellableCoroutine<Boolean> { continuation ->
                    tts = TextToSpeech(this@CommuteAnnouncementService) { status ->
                        if (continuation.isActive) continuation.resume(status == TextToSpeech.SUCCESS)
                    }
                }
            } ?: false
            val engine = tts
            if (!ready || engine == null) return@withContext false
            engine.setLanguage(Locale.GERMANY)
            engine.setAudioAttributes(attributes)
            audio?.requestAudioFocus(focus)
            withTimeoutOrNull(30_000) {
                suspendCancellableCoroutine<Boolean> { continuation ->
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) = Unit
                        override fun onDone(utteranceId: String?) {
                            if (continuation.isActive) continuation.resume(true)
                        }

                        @Deprecated("Required by the abstract class")
                        override fun onError(utteranceId: String?) {
                            if (continuation.isActive) continuation.resume(false)
                        }
                    })
                    val queued = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
                    if (queued != TextToSpeech.SUCCESS && continuation.isActive) continuation.resume(false)
                }
            } ?: false
        } finally {
            audio?.abandonAudioFocusRequest(focus)
            tts?.shutdown()
        }
    }

    private fun notification(): android.app.Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(Channel, getString(R.string.commute_channel), NotificationManager.IMPORTANCE_LOW),
        )
        return NotificationCompat.Builder(this, Channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(this, R.color.vibilia_red))
            .setContentTitle(getString(R.string.commute_listening))
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private companion object {
        const val Channel = "commute"
        const val NotificationId = 3
    }
}

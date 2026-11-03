package io.github.numynox.nexus.abeona.live

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.numynox.nexus.abeona.AbeonaApplication
import io.github.numynox.nexus.abeona.MainActivity
import io.github.numynox.nexus.abeona.R
import io.github.numynox.nexus.abeona.data.Trip
import io.github.numynox.nexus.abeona.ui.common.TripFormat
import io.github.numynox.nexus.abeona.widget.NextTripWidget
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The live companion: during a trip, the phone itself polls the Edge Function
 * every two minutes, writes the live values back, and keeps one ongoing
 * notification current with the travel day — with a heads-up for the news
 * the alert settings ask for (delay, platform change, cancellation, a change
 * at risk or missed, the train binding lifted). Every 30 seconds it also
 * looks at the train's Wi-Fi and, if allowed, the phone's position. There is
 * no server-side monitoring (decisions/2026-11-02-abeona-train-companion.md,
 * decisions/2026-11-03-abeona-fare-rules-and-device-signals.md).
 */
object LiveCompanion {
    internal const val CHANNEL_LIVE = "live"
    internal const val CHANNEL_ALERTS = "alerts"
    internal const val ONGOING_ID = 7
    private const val ACTION_START = "io.github.numynox.nexus.abeona.live.START"
    private const val ACTION_STOP = "io.github.numynox.nexus.abeona.live.STOP"
    private const val EXTRA_TRIP = "trip_id"

    val REFRESH_EVERY: Duration = Duration.ofMinutes(2)
    /** How often the signals (on-board Wi-Fi, position) and the notification are looked at between refreshes. */
    val TICK: Duration = Duration.ofSeconds(30)
    /** How long after the final arrival the companion stays, for a late train's last minutes. */
    val LINGER: Duration = Duration.ofMinutes(15)

    private val _running = MutableStateFlow<String?>(null)
    /** The trip being followed, if any; the trip screen shows "Stop" for it. */
    val running: StateFlow<String?> = _running.asStateFlow()

    internal fun setRunning(tripId: String?) {
        _running.value = tripId
    }

    fun start(context: Context, tripId: String) {
        ContextCompat.startForegroundService(context, intent(context, ACTION_START).putExtra(EXTRA_TRIP, tripId))
    }

    fun stop(context: Context) {
        context.startService(intent(context, ACTION_STOP))
    }

    private fun intent(context: Context, action: String) = Intent(context, LiveCompanionService::class.java).setAction(action)

    internal fun isStart(intent: Intent?) = intent?.action == ACTION_START
    internal fun tripIdOf(intent: Intent?) = intent?.getStringExtra(EXTRA_TRIP)

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_LIVE, context.getString(R.string.companion_channel), NotificationManager.IMPORTANCE_LOW),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, context.getString(R.string.alerts_channel), NotificationManager.IMPORTANCE_HIGH),
        )
    }

    private fun openTrip(context: Context, tripId: String?) = PendingIntent.getActivity(
        context,
        tripId.hashCode(),
        MainActivity.intentFor(context, tripId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** The ongoing notification for [status]; built here so the screenshot test draws the real one. */
    fun ongoing(context: Context, tripId: String?, status: CompanionStatus, updated: Instant?): Notification {
        val stop = PendingIntent.getService(context, 1, intent(context, ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(context, CHANNEL_LIVE)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.abeona_green))
            .setContentTitle(status.title)
            .setContentText(status.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(status.text))
            .setSubText(updated?.let { "Live · ${TripFormat.time(it)}" })
            .setShowWhen(false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setContentIntent(openTrip(context, tripId))
            .addAction(0, "Stop", stop)
            .build()
    }

    fun alert(context: Context, tripId: String, alert: CompanionAlert): Notification =
        NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.abeona_green))
            .setContentTitle(alert.title)
            .setContentText(alert.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(openTrip(context, tripId))
            .setAutoCancel(true)
            .build()

    internal fun canNotify(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** A plain reminder when Android does not let the companion start from the background. */
    fun remind(context: Context, trip: Trip) {
        if (!canNotify(context)) return
        createChannels(context)
        val minutes = Duration.between(Instant.now(), trip.expectedDeparture).toMinutes().coerceAtLeast(0)
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.abeona_green))
            .setContentTitle("${trip.displayTitle} ${TripFormat.relative(minutes)}")
            .setContentText("Tap to follow it live: platform, delay and your changes.")
            .setContentIntent(openTrip(context, trip.id))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(trip.id.hashCode(), notification)
    }
}

class LiveCompanionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var following: Job? = null
    private val posted = mutableSetOf<String>()
    private val location by lazy { TripLocation(this) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LiveCompanion.createChannels(this)
        val tripId = LiveCompanion.tripIdOf(intent)
        if (!LiveCompanion.isStart(intent) || tripId == null) {
            finish()
            return START_NOT_STICKY
        }
        // Foreground first: Android allows a few seconds from startForegroundService.
        val placeholder = LiveCompanion.ongoing(this, tripId, CompanionStatus("Abeona", "Following your trip…", false), null)
        val withLocation = wantsLocation() && runCatching {
            ServiceCompat.startForeground(
                this,
                LiveCompanion.ONGOING_ID,
                placeholder,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        }.isSuccess
        // Started from the background (the auto-start), Android refuses the
        // location type: follow the trip without the position then.
        if (!withLocation) {
            ServiceCompat.startForeground(this, LiveCompanion.ONGOING_ID, placeholder, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        }
        LiveCompanion.setRunning(tripId)
        posted.clear()
        following?.cancel()
        location.stop()
        if (withLocation) location.start()
        following = scope.launch { follow(tripId) }
        return START_REDELIVER_INTENT
    }

    /** The setting is on and the permission granted; read once per start. */
    private fun wantsLocation(): Boolean {
        val container = (application as AbeonaApplication).container
        val on = kotlinx.coroutines.runBlocking { container.preferences.current().tripLocation }
        return on && location.hasPermission()
    }

    /**
     * Every [LiveCompanion.TICK]: the on-board Wi-Fi and the position into
     * the notification; every [LiveCompanion.REFRESH_EVERY]: the live values,
     * with alerts for what changed, as far as the alert settings ask.
     */
    private suspend fun follow(tripId: String) {
        val container = (application as AbeonaApplication).container
        container.core.auth.awaitUserId() ?: return finish()
        var trip = runCatching { container.trips.trip(tripId).value }.getOrNull() ?: return finish()
        var updated: Instant? = null
        var refreshedAt: Instant? = null
        while (scope.isActive) {
            val settings = container.preferences.current()
            var now = Instant.now()
            if (refreshedAt == null || !now.isBefore(refreshedAt.plus(LiveCompanion.REFRESH_EVERY))) {
                refreshedAt = now
                val fresh = runCatching { container.trips.refresh(trip) }.getOrNull()
                now = Instant.now()
                if (fresh != null) {
                    if (updated != null) companionAlerts(trip, fresh, now, settings.alerts).filter { posted.add(it.key) }.forEach { post(tripId, it) }
                    trip = fresh
                    updated = now
                    NextTripWidget.refresh(this)
                }
            }
            val onboard = runCatching { container.onboard.status() }.getOrNull()
            LiveSignals.setOnboard(onboard)
            val signals = LiveSignals.forTrip(trip, now)
            missedAlert(trip, now, settings.stationMinutes, signals, settings.alerts)?.takeIf { posted.add(it.key) }?.let { post(tripId, it) }
            val status = companionStatus(trip, now, settings.stationMinutes, signals, LiveSignals.onboardFor(trip, now))
            if (LiveCompanion.canNotify(this)) {
                NotificationManagerCompat.from(this).notify(LiveCompanion.ONGOING_ID, LiveCompanion.ongoing(this, tripId, status, updated))
            }
            if (status.finished && now.isAfter(trip.expectedArrival.plus(LiveCompanion.LINGER))) return finish()
            delay(LiveCompanion.TICK.toMillis())
        }
    }

    private fun post(tripId: String, alert: CompanionAlert) {
        if (!LiveCompanion.canNotify(this)) return
        NotificationManagerCompat.from(this).notify(alert.key.hashCode(), LiveCompanion.alert(this, tripId, alert))
    }

    private fun finish() {
        location.stop()
        LiveSignals.clear()
        LiveCompanion.setRunning(null)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Android 15 caps dataSync services at six hours a day; a longer trip ends the companion there. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        finish()
    }

    override fun onDestroy() {
        location.stop()
        LiveCompanion.setRunning(null)
        scope.cancel()
        super.onDestroy()
    }
}

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
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.numynox.nexus.abeona.AbeonaApplication
import io.github.numynox.nexus.abeona.MainActivity
import io.github.numynox.nexus.abeona.R
import io.github.numynox.nexus.abeona.data.Trip
import io.github.numynox.nexus.abeona.domain.followed
import io.github.numynox.nexus.abeona.ui.common.TripFormat
import io.github.numynox.nexus.abeona.widget.NextTripWidget
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The live companion: during a trip, the phone itself polls the Edge Function
 * every minute (as often as its cache lets a refresh be new), writes the live
 * values back, and keeps one ongoing notification current with the travel
 * day — with a heads-up for the news the alert settings ask for (delay,
 * platform change, cancellation, a change at risk or missed, the train
 * binding lifted). Every 15 seconds it also looks at the train's Wi-Fi and,
 * if allowed, the phone's position; between those the notification's clock
 * counts down by itself. There is no server-side monitoring
 * (decisions/2026-11-02-abeona-train-companion.md,
 * decisions/2026-11-03-abeona-fare-rules-and-device-signals.md,
 * decisions/2026-11-07-abeona-companion-dashboard.md).
 */
object LiveCompanion {
    /**
     * The ongoing notification's channel. "live" was IMPORTANCE_LOW: Android
     * files that as a silent notification and, by default, leaves it off the
     * lock screen, the always-on display and the status bar, which is where
     * the companion is needed. A channel's importance cannot be raised once it
     * exists, so this is a new one, and "live" is deleted.
     */
    internal const val CHANNEL_LIVE = "live-companion"
    private const val OLD_CHANNEL_LIVE = "live"
    internal const val CHANNEL_ALERTS = "alerts"
    internal const val ONGOING_ID = 7
    private const val ACTION_START = "io.github.numynox.nexus.abeona.live.START"
    private const val ACTION_STOP = "io.github.numynox.nexus.abeona.live.STOP"
    private const val EXTRA_TRIP = "trip_id"

    /** The Edge Function keeps a refresh for 60 s (abeona-transit/cache.ts): asking more often brings nothing new. */
    val REFRESH_EVERY: Duration = Duration.ofMinutes(1)
    /** How often the signals (on-board Wi-Fi, position) and the notification are looked at between refreshes. */
    val TICK: Duration = Duration.ofSeconds(15)
    /**
     * How long after the expected arrival the companion stays, for a late
     * train's last minutes and the way out of the station; then it stops, and
     * it cannot be started for that trip again.
     */
    val LINGER: Duration = Duration.ofMinutes(30)
    /** The longest the companion keeps the CPU awake; Android 15 stops a dataSync service after six hours anyway. */
    val MAX_AWAKE: Duration = Duration.ofHours(6)

    private val _running = MutableStateFlow<String?>(null)
    /** The trip being followed, if any; the trip screen shows "Stop" for it. */
    val running: StateFlow<String?> = _running.asStateFlow()

    internal fun setRunning(tripId: String?) {
        _running.value = tripId
    }

    private val _hasLocation = MutableStateFlow(false)
    /** The companion follows the phone's position too (it was allowed to when it started). */
    val hasLocation: StateFlow<Boolean> = _hasLocation.asStateFlow()

    internal fun setHasLocation(value: Boolean) {
        _hasLocation.value = value
    }

    /**
     * With the app in front: a companion that started from the background
     * (the alarm), where Android refuses it the position, starts again to
     * pick it up, if the setting asks for it.
     */
    fun takeLocationIfAllowed(context: Context, tripLocation: Boolean) {
        val tripId = running.value ?: return
        if (!tripLocation || hasLocation.value || !TripLocation(context).hasPermission()) return
        start(context, tripId)
    }

    fun start(context: Context, tripId: String) {
        ContextCompat.startForegroundService(context, intent(context, ACTION_START).putExtra(EXTRA_TRIP, tripId))
    }

    private val _stoppedByHand = MutableStateFlow<String?>(null)
    /** The trip whose companion was stopped by hand: opening it again does not start it again. */
    val stoppedByHand: StateFlow<String?> = _stoppedByHand.asStateFlow()

    /** Stops following, by hand (the trip screen, the notification's Stop): it stays off for this trip. */
    fun stop(context: Context) {
        context.startService(intent(context, ACTION_STOP))
    }

    internal fun markStoppedByHand() {
        _stoppedByHand.value = running.value
    }

    private fun intent(context: Context, action: String) = Intent(context, LiveCompanionService::class.java).setAction(action)

    internal fun isStart(intent: Intent?) = intent?.action == ACTION_START
    internal fun tripIdOf(intent: Intent?) = intent?.getStringExtra(EXTRA_TRIP)

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.deleteNotificationChannel(OLD_CHANNEL_LIVE)
        // Default importance, so it counts as an alerting notification (lock
        // screen, always-on display, status bar icon), but without a sound or
        // vibration of its own: it updates every 15 seconds.
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_LIVE, context.getString(R.string.companion_channel), NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
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

    /**
     * The ongoing notification for [status]; built here so the screenshot
     * test draws the real one. How fresh the live values are ends the last
     * line, after the speed, so the header takes no line of its own; what
     * placed the traveller is the app's to show (the dashboard's source
     * pill), and the title already counts down, to the minute.
     */
    fun ongoing(context: Context, tripId: String?, status: CompanionStatus, updated: Instant?, now: Instant = Instant.now()): Notification {
        val stop = PendingIntent.getService(context, 1, intent(context, ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(context, CHANNEL_LIVE)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.abeona_green))
            .setContentTitle(status.title)
            .setContentText(status.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedText(status.lines, updated?.let { freshness(it, now) })))
            .setShowWhen(false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            // No setSilent: NotificationCompat does that by putting the
            // notification into a group named "silent" and muting the group.
            // The channel has no sound or vibration instead, and the
            // notification stays a plain ongoing one, as a Live Update must be.
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            // Platform, train and times are not sensitive: show all of it locked.
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // A Live Update on Android 16+: kept at the top of the shade, on the
            // lock screen, and as a chip in the status bar, like a navigation app's.
            .setRequestPromotedOngoing(true)
            .setShortCriticalText(status.chip)
            .setContentIntent(openTrip(context, tripId))
            .addAction(0, "Stop", stop)
            .build()
    }

    /** The expanded notification's lines, with [freshness] at the end of the last one. */
    internal fun expandedText(lines: List<String>, freshness: String?): String {
        if (freshness == null) return lines.joinToString("\n")
        if (lines.isEmpty()) return freshness
        return (lines.dropLast(1) + "${lines.last()} · $freshness").joinToString("\n")
    }

    /** Until [LINGER] after the expected arrival: after that the companion stops, and cannot be started again. */
    fun canFollow(trip: Trip, now: Instant): Boolean = trip.expectedArrival.plus(LINGER).isAfter(now)

    /** Live values older than this are said to be old, so a failing refresh does not look live. */
    val STALE_AFTER: Duration = Duration.ofMinutes(3)

    /** "Live 10:00", or "Not updated since 10:00" when the refreshes stopped getting through. */
    internal fun freshness(updated: Instant, now: Instant): String =
        if (Duration.between(updated, now) > STALE_AFTER) "Not updated since ${TripFormat.time(updated)}" else "Live ${TripFormat.time(updated)}"

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
        NotificationManagerCompat.from(context).notify(trip.id.hashCode(), reminder(context, trip))
    }

    /** The reminder itself, with its "Follow live" button; built here so the screenshot test draws the real one. */
    fun reminder(context: Context, trip: Trip, now: Instant = Instant.now()): Notification {
        val minutes = Duration.between(now, trip.expectedDeparture).toMinutes().coerceAtLeast(0)
        return NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.abeona_green))
            .setContentTitle("${trip.displayTitle} ${TripFormat.relative(minutes)}")
            .setContentText("Tap Follow live for when to leave, the platform, the next stop and your changes.")
            .setShowWhen(false)
            .setContentIntent(openTrip(context, trip.id))
            // A tap on a notification may start a foreground service.
            .addAction(
                0,
                "Follow live",
                PendingIntent.getForegroundService(
                    context,
                    trip.id.hashCode(),
                    intent(context, ACTION_START).putExtra(EXTRA_TRIP, trip.id),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .setAutoCancel(true)
            .build()
    }
}

class LiveCompanionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    /**
     * Keeps the CPU on while a trip is followed: without it the phone dozes
     * with the screen off, the 30-second ticks stop, and the notification
     * freezes until the screen comes on. Partial only (the screen may sleep),
     * and capped, in case the companion never stops.
     */
    private val wakeLock by lazy {
        getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "abeona:live-companion")
            .apply { setReferenceCounted(false) }
    }
    private var following: Job? = null
    private val posted = mutableSetOf<String>()
    private val location by lazy { TripLocation(this) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LiveCompanion.createChannels(this)
        val tripId = LiveCompanion.tripIdOf(intent)
        if (!LiveCompanion.isStart(intent)) LiveCompanion.markStoppedByHand()
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
        LiveCompanion.setHasLocation(withLocation)
        // The reminder that asked for a tap, if Android refused the start before.
        NotificationManagerCompat.from(this).cancel(tripId.hashCode())
        wakeLock.acquire(LiveCompanion.MAX_AWAKE.toMillis())
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
        // Too late to start (a tapped reminder, a restart by Android): the trip is over.
        if (!LiveCompanion.canFollow(trip.followed, Instant.now())) return finish()
        var updated: Instant? = null
        var refreshedAt: Instant? = null
        // A refresh made elsewhere (the trip screen, the half-hourly job) is
        // taken at once: the notification and its alerts move with the app
        // instead of a tick or a minute later. Its own refreshes come back
        // here too, and are recognised by their time.
        val elsewhere = MutableStateFlow<Trip?>(null)
        val listener = scope.launch {
            container.trips.refreshed.collect { fresh -> if (fresh.id == tripId && isNews(fresh, trip)) elsewhere.value = fresh }
        }
        try {
            while (scope.isActive) {
                val settings = container.preferences.current()
                var now = Instant.now()
                val taken = elsewhere.getAndUpdate { null }
                val fresh = if (taken != null && isNews(taken, trip)) {
                    taken
                } else if (refreshedAt == null || !now.isBefore(refreshedAt.plus(LiveCompanion.REFRESH_EVERY))) {
                    refreshedAt = now
                    runCatching { container.trips.refresh(trip) }
                        .onFailure { android.util.Log.w("AbeonaCompanion", "Refresh failed", it) }
                        .getOrNull()
                        .also { now = Instant.now() }
                } else {
                    null
                }
                if (fresh != null) {
                    // Alerts compare ride by ride: not across a connection taken in between.
                    val sameJourney = fresh.missedLegIdx == trip.missedLegIdx && fresh.replacementLegs?.size == trip.replacementLegs?.size
                    if (updated != null && sameJourney) {
                        companionAlerts(trip.followed, fresh.followed, now, settings.alerts).filter { posted.add(it.key) }.forEach { post(tripId, it) }
                    }
                    trip = fresh
                    updated = now
                    refreshedAt = now
                    // Its own refresh echoed back through the store: nothing new.
                    elsewhere.update { if (it?.refreshedAt == fresh.refreshedAt) null else it }
                    NextTripWidget.refresh(this)
                }
                val onboard = runCatching { container.onboard.status() }.getOrNull()
                LiveSignals.setOnboard(onboard)
                // The trains actually taken: a connection from the alternatives replaces the plan's.
                val journey = trip.followed
                val signals = LiveSignals.forTrip(journey, now)
                missedAlert(journey, now, settings.stationMinutes, signals, settings.alerts)?.takeIf { posted.add(it.key) }?.let { post(tripId, it) }
                val status = companionStatus(journey, now, settings.stationMinutes, signals, LiveSignals.onboardFor(journey, now))
                if (LiveCompanion.canNotify(this)) {
                    NotificationManagerCompat.from(this).notify(
                        LiveCompanion.ONGOING_ID,
                        LiveCompanion.ongoing(this, tripId, status, updated),
                    )
                }
                if (!LiveCompanion.canFollow(journey, now)) return finish()
                // The next tick, or sooner when a refresh lands from elsewhere.
                withTimeoutOrNull(LiveCompanion.TICK.toMillis()) { elsewhere.first { it != null } }
            }
        } finally {
            listener.cancel()
        }
    }

    /** A refresh, or a connection taken or given up on the trip screen: what the companion follows changed. */
    private fun isNews(fresh: Trip, current: Trip): Boolean =
        fresh.refreshedAt != current.refreshedAt ||
            fresh.missedLegIdx != current.missedLegIdx ||
            fresh.replacementLegs != current.replacementLegs

    private fun post(tripId: String, alert: CompanionAlert) {
        if (!LiveCompanion.canNotify(this)) return
        NotificationManagerCompat.from(this).notify(alert.key.hashCode(), LiveCompanion.alert(this, tripId, alert))
    }

    private fun finish() {
        releaseWakeLock()
        location.stop()
        LiveSignals.clear()
        LiveCompanion.setRunning(null)
        LiveCompanion.setHasLocation(false)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Android 15 caps dataSync services at six hours a day; a longer trip ends the companion there. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        finish()
    }

    private fun releaseWakeLock() {
        if (wakeLock.isHeld) wakeLock.release()
    }

    override fun onDestroy() {
        releaseWakeLock()
        location.stop()
        LiveCompanion.setRunning(null)
        LiveCompanion.setHasLocation(false)
        scope.cancel()
        super.onDestroy()
    }
}

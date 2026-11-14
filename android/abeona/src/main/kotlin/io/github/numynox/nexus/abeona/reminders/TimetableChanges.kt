package io.github.numynox.nexus.abeona.reminders

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.numynox.nexus.abeona.MainActivity
import io.github.numynox.nexus.abeona.R
import io.github.numynox.nexus.abeona.data.AbeonaRepository
import io.github.numynox.nexus.abeona.data.Trip
import io.github.numynox.nexus.abeona.live.LiveCompanion
import io.github.numynox.nexus.abeona.ui.common.TripFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * "ICE 725 no longer runs as booked": said once, when looking a trip's
 * unfound rides up again (AbeonaRepository.refindTrip) found one missing from
 * the timetable on its day, or took the same trains at other times instead.
 * On the trip alerts channel the live companion uses.
 */
object TimetableChanges {
    private val day = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

    /** Title and text for what [refound] changed on [trip]; null when there is nothing to say. */
    fun message(trip: Trip, refound: AbeonaRepository.Refound, zone: ZoneId = ZoneId.systemDefault()): Pair<String, String>? {
        val adjusted = refound.adjusted
        val dropped = refound.dropped.firstOrNull() ?: trip.legs.firstOrNull { it.dropped }
        if (adjusted == null && refound.dropped.isEmpty()) return null
        val label = dropped?.label ?: "A train"
        val title = "$label no longer runs as booked"
        val text = if (adjusted != null) {
            val first = adjusted.firstOrNull { !it.isWalk } ?: adjusted.first()
            "Your trains still connect, at other times: from ${first.from.name}, " +
                adjusted.filter { !it.isWalk }.joinToString(" › ") { "${it.label} ${TripFormat.time(it.from.plannedAt)}" } +
                ". Abeona follows these now."
        } else {
            val on = dropped?.from?.plannedAt?.atZone(zone)?.let { " on ${day.format(it)}" }.orEmpty()
            "It is no longer in the timetable$on. Your train binding is lifted: choose another connection."
        }
        return title to text
    }

    fun build(context: Context, trip: Trip, title: String, text: String): Notification {
        val open = PendingIntent.getActivity(
            context,
            trip.id.hashCode(),
            MainActivity.intentFor(context, trip.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, LiveCompanion.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.abeona_green))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
    }

    fun notify(context: Context, trip: Trip, refound: AbeonaRepository.Refound) {
        val (title, text) = message(trip, refound) ?: return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(LiveCompanion.CHANNEL_ALERTS, context.getString(R.string.alerts_channel), NotificationManager.IMPORTANCE_HIGH),
        )
        NotificationManagerCompat.from(context).notify("timetable-${trip.id}".hashCode(), build(context, trip, title, text))
    }
}

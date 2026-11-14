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

/**
 * "ICE 725 no longer runs as booked": said once, when checking a trip
 * against the timetable (AbeonaRepository.checkTimetable) found a ride missing
 * from it on its day, or took the same trains at other times instead.
 * On the trip alerts channel the live companion uses.
 */
object TimetableChanges {
    /** Title and text for what [refound] changed on [trip]; null when there is nothing to say. */
    fun message(trip: Trip, refound: AbeonaRepository.Refound): Pair<String, String>? {
        val adjusted = refound.adjusted
        val dropped = refound.dropped.firstOrNull() ?: trip.legs.firstOrNull { it.dropped }
        if (adjusted == null && refound.dropped.isEmpty()) return null
        val title = "${dropped?.label ?: "A train"} is no longer in the timetable"
        val text = if (adjusted != null) {
            "Now: " + adjusted.filter { !it.isWalk }.joinToString(" › ") { "${it.label} ${TripFormat.time(it.from.plannedAt)} ${it.from.name}" }
        } else {
            "Choose another connection."
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

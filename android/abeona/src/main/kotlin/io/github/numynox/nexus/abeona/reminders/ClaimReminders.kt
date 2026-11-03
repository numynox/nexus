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
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.numynox.nexus.abeona.AbeonaApplication
import io.github.numynox.nexus.abeona.MainActivity
import io.github.numynox.nexus.abeona.R
import io.github.numynox.nexus.abeona.data.ClaimEntry
import io.github.numynox.nexus.abeona.data.toInstantOrNull
import io.github.numynox.nexus.abeona.ui.common.TripFormat
import io.github.numynox.nexus.core.data.isNetworkFailure
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * "You can claim compensation" — device-local and off by default, like the
 * BahnCard reminder. One daily job near [HOUR] asks abeona_claims_overview()
 * and speaks up for trips with something to claim and no claim sent: the day
 * after the trip, and four weeks, one week and a day before the three-month
 * deadline — not every day in between.
 */
object ClaimReminders {
    private const val WORK = "abeona-claim-reminder"
    private const val CHANNEL = "claims"
    private const val NOTIFICATION = 4712
    const val HOUR = 18

    /** Days before the deadline on which the reminder speaks up. */
    val NOTIFY_DAYS_LEFT = setOf(28, 7, 1)

    fun apply(context: Context, on: Boolean, now: LocalDateTime = LocalDateTime.now()) {
        val work = WorkManager.getInstance(context)
        if (!on) {
            work.cancelUniqueWork(WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<ClaimReminderWorker>(Duration.ofDays(1))
            .setInitialDelay(BahnCardReminders.delayUntil(HOUR, now))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        work.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Whether [entry] is worth a word today: the day after the trip, or one of the deadline days. */
    fun isDue(entry: ClaimEntry, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val tripDay = entry.plannedDeparture.toInstantOrNull()?.atZone(zone)?.toLocalDate()
        return entry.daysLeft in NOTIFY_DAYS_LEFT || tripDay?.plusDays(1) == today
    }

    /** "Frankfurt → Bamberg: 12,50 €, by 14 Jan 2027". */
    fun line(entry: ClaimEntry): String =
        "${entry.displayTitle}: ${entry.amountCents?.let(TripFormat::price) ?: "add the fare"}, by ${TripFormat.date(LocalDate.parse(entry.deadline))}"

    /** What the reminder says today, or null when nothing is due. */
    fun message(toClaim: List<ClaimEntry>, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): Pair<String, List<String>>? {
        val due = toClaim.filter { isDue(it, today, zone) }.sortedBy { it.daysLeft }
        if (due.isEmpty()) return null
        val total = due.mapNotNull { it.amountCents }.sum()
        val title = if (due.size == 1) {
            "Claim compensation for ${due.single().displayTitle}"
        } else {
            "Claim compensation for ${due.size} trips" + if (total > 0) " (${TripFormat.price(total)})" else ""
        }
        return title to due.map(::line)
    }

    /** The notification itself; built here so the screenshot test draws the real one. */
    fun build(context: Context, title: String, lines: List<String>, tripId: String? = null): Notification {
        val open = PendingIntent.getActivity(
            context,
            NOTIFICATION,
            MainActivity.intentFor(context, tripId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val style = if (lines.size == 1) {
            NotificationCompat.BigTextStyle().bigText(lines.single())
        } else {
            NotificationCompat.InboxStyle().also { inbox -> lines.take(6).forEach(inbox::addLine) }
        }
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.abeona_green))
            .setContentTitle(title)
            .setContentText(lines.first())
            .setStyle(style)
            .setContentIntent(open)
            .setShowWhen(false)
            .setAutoCancel(true)
            .build()
    }

    internal fun notify(context: Context, title: String, lines: List<String>, tripId: String?) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.claims_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        NotificationManagerCompat.from(context).notify(NOTIFICATION, build(context, title, lines, tripId))
    }
}

class ClaimReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as AbeonaApplication).container
        if (!container.preferences.current().claimReminder) return Result.success()
        container.core.auth.awaitUserId() ?: return Result.success()
        return try {
            val toClaim = container.repository.claimsOverview(LocalDate.now().year).toClaim
            ClaimReminders.message(toClaim)?.let { (title, lines) ->
                val single = toClaim.filter { ClaimReminders.isDue(it) }.singleOrNull()?.tripId
                ClaimReminders.notify(applicationContext, title, lines, single)
            }
            Result.success()
        } catch (failure: Exception) {
            if (failure.isNetworkFailure()) Result.retry() else Result.failure()
        }
    }
}

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
import io.github.numynox.nexus.abeona.data.BahnCardDeadline
import io.github.numynox.nexus.abeona.ui.common.TripFormat
import io.github.numynox.nexus.core.data.isNetworkFailure
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The "cancel your BahnCard in time" notification. Device-local and off by
 * default, like Annona's expiry reminder: the household shares the cards, but
 * each phone decides whether it reminds. One daily job near [HOUR] asks
 * abeona_bahncard_deadlines() and speaks up two weeks, one week, three days
 * and one day ahead, and on the day itself — not every day in between.
 */
object BahnCardReminders {
    private const val WORK = "abeona-bahncard-reminder"
    private const val CHANNEL = "bahncards"
    private const val NOTIFICATION = 4711
    const val HOUR = 9

    /** The days ahead of a deadline on which the reminder speaks up. */
    val NOTIFY_ON = setOf(14, 7, 3, 1, 0)

    fun apply(context: Context, on: Boolean, now: LocalDateTime = LocalDateTime.now()) {
        val work = WorkManager.getInstance(context)
        if (!on) {
            work.cancelUniqueWork(WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<BahnCardReminderWorker>(Duration.ofDays(1))
            .setInitialDelay(delayUntil(HOUR, now))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        work.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Until the next [hour] o'clock — today if it is still ahead, else tomorrow. */
    fun delayUntil(hour: Int, now: LocalDateTime): Duration {
        var next = now.toLocalDate().atTime(LocalTime.of(hour, 0))
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next)
    }

    /** "Cancel by 14 Feb (in 7 days)", or that the deadline has passed. */
    fun deadlineText(deadline: BahnCardDeadline): String {
        val day = TripFormat.date(LocalDate.parse(deadline.deadline))
        val days = deadline.daysLeft
        return when {
            days < 0 -> "The cancellation deadline was $day (${-days} ${if (days == -1) "day" else "days"} ago)"
            days == 0 -> "Cancel by today, $day"
            days == 1 -> "Cancel by tomorrow, $day"
            else -> "Cancel by $day (in $days days)"
        }
    }

    /** What a reminder says today, or null when no deadline is on one of the [NOTIFY_ON] days. */
    fun message(deadlines: List<BahnCardDeadline>): Pair<String, List<String>>? {
        val due = deadlines.filter { it.daysLeft in NOTIFY_ON }.sortedBy { it.daysLeft }
        if (due.isEmpty()) return null
        val title = if (due.size == 1) {
            val d = due.single()
            "${cardName(d)} renews unless cancelled"
        } else {
            "${due.size} BahnCards renew unless cancelled"
        }
        // One card is named in the title already; several are named line by line.
        return title to if (due.size == 1) {
            listOf("${deadlineText(due.single())}.")
        } else {
            due.map { "${cardName(it)}: ${deadlineText(it)}" }
        }
    }

    /** "BahnCard 25", or with the household's name for it "Ada (BahnCard 25)". */
    fun cardName(d: BahnCardDeadline): String {
        val kind = "BahnCard ${d.kind}"
        val name = d.displayName
        return if (name == kind) kind else "$name ($kind)"
    }

    /** The notification itself; built here so the screenshot test draws the real one. */
    fun build(context: Context, title: String, lines: List<String>): Notification {
        val open = PendingIntent.getActivity(
            context,
            NOTIFICATION,
            MainActivity.intentFor(context, null),
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
            // About a date weeks away; when it was posted says nothing.
            .setShowWhen(false)
            .setAutoCancel(true)
            .build()
    }

    internal fun notify(context: Context, title: String, lines: List<String>) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.bahncard_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        NotificationManagerCompat.from(context).notify(NOTIFICATION, build(context, title, lines))
    }
}

class BahnCardReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as AbeonaApplication).container
        if (!container.preferences.current().bahnCardReminder) return Result.success()
        container.core.auth.awaitUserId() ?: return Result.success()
        return try {
            BahnCardReminders.message(container.repository.bahnCardDeadlines())
                ?.let { (title, lines) -> BahnCardReminders.notify(applicationContext, title, lines) }
            Result.success()
        } catch (failure: Exception) {
            if (failure.isNetworkFailure()) Result.retry() else Result.failure()
        }
    }
}

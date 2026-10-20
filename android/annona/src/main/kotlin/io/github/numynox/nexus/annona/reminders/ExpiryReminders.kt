package io.github.numynox.nexus.annona.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
import io.github.numynox.nexus.annona.AnnonaApplication
import io.github.numynox.nexus.annona.MainActivity
import io.github.numynox.nexus.annona.R
import io.github.numynox.nexus.annona.data.AnnonaSettings
import io.github.numynox.nexus.annona.data.Item
import io.github.numynox.nexus.annona.data.expiryLabel
import io.github.numynox.nexus.core.data.isNetworkFailure
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The daily "use these up" notification — something a browser tab cannot do.
 * Device-local and off by default: the household shares the inventory, but
 * each phone decides whether it nags. WorkManager keeps one periodic job; it
 * runs near the chosen hour and asks Postgres what expires within the window.
 */
object ExpiryReminders {
    private const val WORK = "annona-expiry-reminder"
    private const val CHANNEL = "expiry"
    private const val NOTIFICATION = 1

    fun apply(context: Context, settings: AnnonaSettings, now: LocalDateTime = LocalDateTime.now()) {
        val work = WorkManager.getInstance(context)
        if (!settings.remindersOn) {
            work.cancelUniqueWork(WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<ExpiryReminderWorker>(Duration.ofDays(1))
            .setInitialDelay(delayUntil(settings.reminderHour, now))
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

    /** What a reminder says about [items], or null when nothing is due. */
    fun message(items: List<Item>, days: Int, today: LocalDate = LocalDate.now()): Pair<String, List<String>>? {
        val due = items.filter { item -> item.expirationDate?.let { !it.isAfter(today.plusDays(days.toLong())) } == true }
            .sortedBy { it.expirationDate }
        if (due.isEmpty()) return null
        val expired = due.count { it.expirationDate!!.isBefore(today) }
        val title = when {
            due.size == 1 -> "Use up ${due.single().displayName}"
            expired > 0 -> "${due.size} things to use up, $expired already expired"
            else -> "${due.size} things to use up soon"
        }
        return title to due.map { "${it.displayName} — ${expiryLabel(it.expirationDate, today).lowercase()}" }
    }

    internal fun notify(context: Context, title: String, lines: List<String>) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val style = NotificationCompat.InboxStyle().also { inbox -> lines.take(6).forEach(inbox::addLine) }
        if (lines.size > 6) style.setSummaryText("and ${lines.size - 6} more")
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.annona_wheat))
            .setContentTitle(title)
            .setContentText(lines.first())
            .setStyle(style)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION, notification)
    }
}

class ExpiryReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as AnnonaApplication).container
        val settings = container.preferences.current()
        if (!settings.remindersOn) return Result.success()
        container.core.auth.awaitUserId() ?: return Result.success()
        return try {
            ExpiryReminders.message(container.repository.activeItems(), settings.reminderDays)
                ?.let { (title, lines) -> ExpiryReminders.notify(applicationContext, title, lines) }
            Result.success()
        } catch (failure: Exception) {
            if (failure.isNetworkFailure()) Result.retry() else Result.failure()
        }
    }
}

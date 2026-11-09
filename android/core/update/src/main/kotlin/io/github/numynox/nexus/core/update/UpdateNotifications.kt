package io.github.numynox.nexus.core.update

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import io.github.numynox.nexus.core.data.AvailableUpdate

/**
 * Every notification of the in-app update, on one channel. Labels and short
 * status only; what happens on a tap is the system's to explain.
 */
internal object UpdateNotifications {
    private const val CHANNEL = "updates"

    const val AVAILABLE = 0x7570_0001
    const val PROGRESS = 0x7570_0002
    const val RESULT = 0x7570_0003

    private const val PREFERENCES = "nexus_update"
    private const val NOTIFIED_VERSION_CODE = "notified_version_code"

    /**
     * "Update available", once per release: a swiped-away notification does
     * not come back with the next daily check. Remembered only when it was
     * posted, so a phone that allows notifications later still hears of it.
     */
    fun availableOnce(context: Context, update: AvailableUpdate): Boolean {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (preferences.getLong(NOTIFIED_VERSION_CODE, 0) >= update.versionCode) return false
        val notification = available(context, update) ?: return false
        if (!post(context, AVAILABLE, notification)) return false
        preferences.edit { putLong(NOTIFIED_VERSION_CODE, update.versionCode) }
        return true
    }

    /**
     * Its tap is a broadcast to [UpdateReceiver], which starts the download:
     * since Android 12 a receiver started from a notification may not open
     * an activity, so the tap must not need one. Null without an APK.
     */
    fun available(context: Context, update: AvailableUpdate): Notification? {
        val apkUrl = update.apkUrl ?: return null
        val tap = PendingIntent.getBroadcast(
            context,
            AVAILABLE,
            Intent(context, UpdateReceiver::class.java)
                .setAction(UpdateReceiver.ACTION_DOWNLOAD)
                .putExtra(SelfUpdate.KEY_APK_URL, apkUrl)
                .putExtra(SelfUpdate.KEY_VERSION_CODE, update.versionCode)
                .putExtra(SelfUpdate.KEY_VERSION_NAME, update.versionName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return builder(context)
            .setContentTitle("Update available")
            .setContentText(label(context, update.versionName))
            .setContentIntent(tap)
            .addAction(0, "Install", tap)
            .setAutoCancel(true)
            .build()
    }

    /** The download's ongoing notification, also the foreground worker's. */
    fun progress(context: Context, versionName: String, percent: Int?): Notification {
        ensureChannel(context)
        return builder(context)
            .setContentTitle("Downloading…")
            .setContentText(label(context, versionName))
            .setProgress(100, percent ?: 0, percent == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
    }

    fun showProgress(context: Context, versionName: String, percent: Int?) {
        post(context, PROGRESS, progress(context, versionName, percent))
    }

    /** Android wants the user to confirm: the tap opens its prompt. */
    fun confirm(context: Context, confirmIntent: Intent, versionName: String) {
        val tap = PendingIntent.getActivity(context, RESULT, confirmIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        post(
            context,
            RESULT,
            builder(context)
                .setContentTitle("Install")
                .setContentText(label(context, versionName))
                .setContentIntent(tap)
                .setAutoCancel(true)
                .build(),
        )
    }

    /** "Install unknown apps" is off for this app: the tap opens the switch. */
    fun notAllowed(context: Context) {
        val tap = PendingIntent.getActivity(
            context,
            RESULT,
            SelfUpdate.allowInstallsIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        post(
            context,
            RESULT,
            builder(context).setContentTitle("Installation not allowed").setContentText(appName(context))
                .setContentIntent(tap).setAutoCancel(true).build(),
        )
    }

    fun failed(context: Context) {
        post(context, RESULT, builder(context).setContentTitle("Installation failed").setContentText(appName(context)).setAutoCancel(true).build())
    }

    fun cancel(context: Context, vararg ids: Int) {
        val manager = NotificationManagerCompat.from(context)
        ids.forEach(manager::cancel)
    }

    fun cancelAll(context: Context) = cancel(context, AVAILABLE, PROGRESS, RESULT)

    private fun builder(context: Context) = NotificationCompat.Builder(context, CHANNEL)
        .setSmallIcon(R.drawable.ic_update_notification)
        .setCategory(NotificationCompat.CATEGORY_STATUS)

    /** False when it could not be shown: notifications off, or the permission not granted. */
    private fun post(context: Context, id: Int, notification: Notification): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        ensureChannel(context)
        return try {
            manager.notify(id, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "App updates", NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun appName(context: Context): String = context.applicationInfo.loadLabel(context.packageManager).toString()

    private fun label(context: Context, versionName: String) = "${appName(context)} $versionName".trim()
}

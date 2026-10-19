package io.github.numynox.nexus.vibilia.tracking

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.numynox.nexus.core.data.isNetworkFailure
import io.github.numynox.nexus.vibilia.VibiliaApplication
import io.github.numynox.nexus.vibilia.data.DriveIntervalUpload
import java.time.Duration
import java.time.Instant

object DriveSync {
    private const val SYNC = "drive-sync"
    private const val HEARTBEAT = "drive-heartbeat"

    /** Uploads finished drives once the phone is online, after [delay]. */
    fun schedule(context: Context, delay: Duration = Duration.ZERO) {
        val request = OneTimeWorkRequestBuilder<DriveSyncWorker>()
            .setInitialDelay(delay)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(SYNC, ExistingWorkPolicy.REPLACE, request)
    }

    /** While a drive is open, notes every 15 minutes that it still is — the reboot fallback. */
    fun startHeartbeat(context: Context) {
        val request = PeriodicWorkRequestBuilder<DriveHeartbeatWorker>(Duration.ofMinutes(15)).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(HEARTBEAT, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun stopHeartbeat(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(HEARTBEAT)
    }
}

class DriveSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as VibiliaApplication).container
        val tracker = container.driveTracking.tracker
        val state = tracker.settle()
        state.closing?.let { closing ->
            val remaining = Duration.between(Instant.now(), closing.lastSeenAt.plus(DriveTracker.ReconnectGrace))
            DriveSync.schedule(applicationContext, remaining.plusSeconds(30).coerceAtLeast(Duration.ofSeconds(30)))
        }
        if (state.pending.isEmpty()) return Result.success()

        // Signed out: keep the queue. Signing in again starts a sync.
        val userId = container.auth.awaitUserId() ?: return Result.success()
        val deviceId = container.preferences.deviceId()
        val uploads = state.pending.map { DriveIntervalUpload(it.id, it.carId, userId, deviceId, it.startedAt, it.endedAt) }

        val batch = runCatching { container.drives.upload(uploads) }
        if (batch.isSuccess) {
            tracker.markUploaded(uploads.map { it.id }.toSet())
            return Result.success()
        }
        if (batch.exceptionOrNull()?.isNetworkFailure() == true) return Result.retry()

        // Something in the batch is refused for good — a car deleted or no
        // longer shared. Upload one by one and drop only what is refused, so
        // one bad drive cannot block the queue forever.
        val done = mutableSetOf<String>()
        for (upload in uploads) {
            val single = runCatching { container.drives.upload(listOf(upload)) }
            when {
                single.isSuccess -> done += upload.id
                single.exceptionOrNull()?.isNetworkFailure() == true -> {
                    tracker.markUploaded(done)
                    return Result.retry()
                }
                else -> done += upload.id
            }
        }
        tracker.markUploaded(done)
        return Result.success()
    }
}

class DriveHeartbeatWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val tracker = (applicationContext as VibiliaApplication).container.driveTracking.tracker
        tracker.heartbeat()
        if (tracker.current().open == null) DriveSync.stopHeartbeat(applicationContext)
        return Result.success()
    }
}

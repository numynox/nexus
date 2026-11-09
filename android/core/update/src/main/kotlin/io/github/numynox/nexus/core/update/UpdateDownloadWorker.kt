package io.github.numynox.nexus.core.update

import android.content.Context
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Downloads the release's APK, checks it, and hands it to PackageInstaller —
 * a foreground worker, so the download survives leaving the app.
 */
class UpdateDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val versionName get() = inputData.getString(SelfUpdate.KEY_VERSION_NAME).orEmpty()

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(null)

    private fun foregroundInfo(percent: Int?) = ForegroundInfo(
        UpdateNotifications.PROGRESS,
        UpdateNotifications.progress(applicationContext, versionName, percent),
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
    )

    override suspend fun doWork(): Result {
        val context = applicationContext
        val apkUrl = inputData.getString(SelfUpdate.KEY_APK_URL) ?: return Result.failure()
        val versionCode = inputData.getLong(SelfUpdate.KEY_VERSION_CODE, 0)

        // Installing replaces this process, sometimes before WorkManager has
        // recorded this work as done; it then runs again in the new version
        // and must end quietly.
        if (versionCode in 1..SelfUpdate.installedVersionCode(context)) return Result.success()

        if (!context.packageManager.canRequestPackageInstalls()) {
            UpdateNotifications.notAllowed(context)
            return Result.success()
        }

        // Without it (started from the background where Android refuses a
        // foreground service) the download still runs, as an ordinary worker.
        runCatching { setForeground(foregroundInfo(null)) }

        val apk = File(context.cacheDir, "update.apk")
        try {
            withContext(Dispatchers.IO) {
                ApkInstaller.download(apkUrl, apk) { percent ->
                    setProgress(workDataOf(SelfUpdate.KEY_PERCENT to (percent ?: -1)))
                    UpdateNotifications.showProgress(context, versionName, percent)
                }
            }
            when (ApkInstaller.verdict(context, apk)) {
                ApkVerdict.NotNewer -> return Result.success()
                ApkVerdict.Rejected -> {
                    Log.w(TAG, "Downloaded APK is not an update of ${context.packageName}")
                    UpdateNotifications.failed(context)
                    return Result.failure()
                }
                ApkVerdict.Install -> Unit
            }
            setProgress(workDataOf(SelfUpdate.KEY_PERCENT to 100))
            withContext(Dispatchers.IO) { ApkInstaller.install(context, apk, versionName) }
            return Result.success()
        } catch (failure: java.io.IOException) {
            Log.w(TAG, "Update download failed", failure)
            if (runAttemptCount < MAX_RETRIES) return Result.retry()
            UpdateNotifications.failed(context)
            return Result.failure()
        } catch (failure: SecurityException) {
            Log.w(TAG, "Update install refused", failure)
            UpdateNotifications.failed(context)
            return Result.failure()
        } finally {
            // The session holds its own copy once written; the file is not needed either way.
            apk.delete()
            UpdateNotifications.cancel(context, UpdateNotifications.PROGRESS)
        }
    }

    private companion object {
        const val TAG = "NexusUpdate"
        const val MAX_RETRIES = 2
    }
}

package io.github.numynox.nexus.core.update

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.provider.Settings
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import io.github.numynox.nexus.core.data.NexusConfig
import java.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** A download in progress; [percent] is null while the size is unknown. */
data class DownloadProgress(val percent: Int?)

/**
 * The pieces of the in-app update that workers, the receiver and [AppUpdates]
 * share: the two unique works, their input keys, and what Android reports
 * about the installed app.
 */
object SelfUpdate {
    internal const val CHECK_WORK = "nexus-update-check"
    internal const val DOWNLOAD_WORK = "nexus-update-download"

    internal const val KEY_REPOSITORY = "repository"
    internal const val KEY_TAG_PREFIX = "tag_prefix"
    internal const val KEY_APK_URL = "apk_url"
    internal const val KEY_VERSION_CODE = "version_code"
    internal const val KEY_VERSION_NAME = "version_name"
    internal const val KEY_PERCENT = "percent"

    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /**
     * Only a release build replaces itself: a debug build is another package
     * (`.debug`) and debug-signed, so a release APK could never update it.
     */
    fun selfInstalls(context: Context): Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) == 0

    fun installedVersionCode(context: Context): Long =
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode

    /** Android's "Install unknown apps" switch for this app. */
    fun allowInstallsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    /** The daily check, on any network. Its schedule survives updates and reboots. */
    internal fun scheduleChecks(context: Context, config: NexusConfig) {
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(Duration.ofDays(1))
            .setConstraints(online)
            .setInputData(workDataOf(KEY_REPOSITORY to config.releaseRepository, KEY_TAG_PREFIX to config.releaseTagPrefix))
            .build()
        runCatching {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(CHECK_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }

    /** Downloads [apkUrl] and installs it; a second tap starts over rather than queueing behind the first. */
    internal fun download(context: Context, apkUrl: String, versionCode: Long, versionName: String) {
        val request = OneTimeWorkRequestBuilder<UpdateDownloadWorker>()
            .setConstraints(online)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(workDataOf(KEY_APK_URL to apkUrl, KEY_VERSION_CODE to versionCode, KEY_VERSION_NAME to versionName))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(DOWNLOAD_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    /** The download while it is queued or running, else null. */
    internal fun progress(context: Context): Flow<DownloadProgress?> =
        runCatching { WorkManager.getInstance(context) }.fold(
            onSuccess = { work ->
                work.getWorkInfosForUniqueWorkFlow(DOWNLOAD_WORK).map { infos ->
                    infos.firstOrNull { !it.state.isFinished }?.let { info ->
                        DownloadProgress(info.progress.getInt(KEY_PERCENT, -1).takeIf { it >= 0 })
                    }
                }
            },
            // No WorkManager (a test without one): nothing is ever downloading.
            onFailure = { flowOf(null) },
        )
}

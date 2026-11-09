package io.github.numynox.nexus.core.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log

/**
 * Not exported: only this app's own PendingIntents reach it. It takes the
 * "Update available" tap, which starts the download, and PackageInstaller's
 * answer to a commit.
 */
class UpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_DOWNLOAD -> {
                val apkUrl = intent.getStringExtra(SelfUpdate.KEY_APK_URL) ?: return
                UpdateNotifications.cancel(context, UpdateNotifications.AVAILABLE)
                SelfUpdate.download(
                    context,
                    apkUrl,
                    intent.getLongExtra(SelfUpdate.KEY_VERSION_CODE, 0),
                    intent.getStringExtra(SelfUpdate.KEY_VERSION_NAME).orEmpty(),
                )
            }
            ACTION_INSTALL_STATUS -> onInstallStatus(context, intent)
        }
    }

    private fun onInstallStatus(context: Context, intent: Intent) {
        val versionName = intent.getStringExtra(SelfUpdate.KEY_VERSION_NAME).orEmpty()
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = intent.confirmIntent() ?: return
                UpdateNotifications.cancel(context, UpdateNotifications.PROGRESS)
                UpdateNotifications.confirm(context, confirm, versionName)
                // Straight to the prompt while the app is in front; from the
                // background Android refuses, and the notification remains.
                runCatching { context.startActivity(Intent(confirm).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            // This already runs in the new version's process.
            PackageInstaller.STATUS_SUCCESS -> UpdateNotifications.cancelAll(context)
            // Cancelled at the prompt: the banner and settings still offer it.
            PackageInstaller.STATUS_FAILURE_ABORTED -> UpdateNotifications.cancelAll(context)
            else -> {
                Log.w(TAG, "Install failed ($status): ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
                UpdateNotifications.cancel(context, UpdateNotifications.PROGRESS)
                UpdateNotifications.failed(context)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun Intent.confirmIntent(): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        else getParcelableExtra(Intent.EXTRA_INTENT)

    companion object {
        const val ACTION_DOWNLOAD = "io.github.numynox.nexus.update.DOWNLOAD"
        const val ACTION_INSTALL_STATUS = "io.github.numynox.nexus.update.INSTALL_STATUS"
        private const val TAG = "NexusUpdate"
    }
}

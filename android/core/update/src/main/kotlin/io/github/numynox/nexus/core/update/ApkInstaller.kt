package io.github.numynox.nexus.core.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Fetching, checking and installing the APK; the steps [UpdateDownloadWorker] runs in order. */
internal object ApkInstaller {

    /**
     * Downloads [url] into [target]. GitHub answers a release asset with a
     * redirect to its storage host. [onProgress] is called at most once a
     * second (Android drops faster notification updates), and at the start.
     */
    suspend fun download(url: String, target: File, onProgress: suspend (Int?) -> Unit) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 20_000
            readTimeout = 60_000
            setRequestProperty("User-Agent", "Nexus-Android")
        }
        try {
            if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}")
            val total = connection.contentLengthLong
            var read = 0L
            var lastReport = 0L
            onProgress(percentOf(0, total))
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        read += count
                        val now = System.currentTimeMillis()
                        if (now - lastReport >= 1_000) {
                            lastReport = now
                            onProgress(percentOf(read, total))
                        }
                    }
                }
            }
            if (total > 0 && read != total) throw IOException("Incomplete download: $read of $total bytes")
        } finally {
            connection.disconnect()
        }
    }

    /** Whether [apk] is this app, newer, and signed with this app's key. */
    fun verdict(context: Context, apk: File): ApkVerdict {
        val packageManager = context.packageManager
        val archive = packageManager.archiveInfo(apk) ?: return ApkVerdict.Rejected
        val installed = packageManager.installedInfo(context.packageName)
        return apkVerdict(
            ownPackage = context.packageName,
            installedVersionCode = installed.longVersionCode,
            installedSigners = installed.signers(),
            apkPackage = archive.packageName,
            apkVersionCode = archive.longVersionCode,
            apkSigners = archive.signers(),
        )
    }

    /**
     * Hands [apk] to PackageInstaller. The answer — done, needs the user's
     * confirmation, or failed — arrives at [UpdateReceiver]. Without asking
     * for the user's action Android may skip the prompt once this app is its
     * own installer (Android 12+).
     */
    fun install(context: Context, apk: File, versionName: String) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite("base.apk", 0, apk.length()).use { output ->
                    apk.inputStream().use { it.copyTo(output) }
                    session.fsync(output)
                }
                val answer = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    Intent(context, UpdateReceiver::class.java)
                        .setAction(UpdateReceiver.ACTION_INSTALL_STATUS)
                        .putExtra(SelfUpdate.KEY_VERSION_NAME, versionName),
                    // Mutable: PackageInstaller adds its status to it.
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                session.commit(answer.intentSender)
            }
        } catch (failure: Exception) {
            runCatching { installer.abandonSession(sessionId) }
            throw failure
        }
    }

    @Suppress("DEPRECATION")
    private fun PackageManager.archiveInfo(apk: File): PackageInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getPackageArchiveInfo(apk.path, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
        } else {
            getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES)
        }

    @Suppress("DEPRECATION")
    private fun PackageManager.installedInfo(packageName: String): PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
        } else {
            getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        }

    private fun PackageInfo.signers(): List<ByteArray>? = signingInfo?.apkContentsSigners?.map { it.toByteArray() }
}

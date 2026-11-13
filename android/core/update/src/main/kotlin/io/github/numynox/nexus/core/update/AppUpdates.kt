package io.github.numynox.nexus.core.update

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.SystemClock
import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.numynox.nexus.core.data.AvailableUpdate
import io.github.numynox.nexus.core.data.NexusConfig
import io.github.numynox.nexus.core.data.UpdateChecker
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The app's view of its own updates: whether a newer release exists, the
 * download while it runs, and the one action — install it. The download,
 * the check of the file and the install happen in [UpdateDownloadWorker];
 * PackageInstaller's answer arrives at [UpdateReceiver]. Each app's
 * container holds one.
 */
class AppUpdates(context: Context, private val config: NexusConfig, private val checker: UpdateChecker) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val started = AtomicBoolean(false)
    private val checking = Any()
    private var lastCheck: Long? = null

    private val _available = MutableStateFlow<AvailableUpdate?>(null)

    /** The newest release when it is newer than this build, else null. */
    val available: StateFlow<AvailableUpdate?> = _available.asStateFlow()

    val installedVersion: String get() = config.versionName

    /** False for a debug build, which can only point at the release page. */
    val selfInstalls: Boolean = SelfUpdate.selfInstalls(app)

    val download: Flow<DownloadProgress?> = SelfUpdate.progress(app)

    /**
     * When the app's UI starts: schedules the daily check and checks now.
     * Then again each time one of the app's screens comes to the front, at
     * most every [RECHECK_MS]: a foreground service (Abeona's live companion)
     * keeps the process alive while the app is closed and opened again, so
     * "once per process" could mean not until the daily check. A newer
     * release is notified once, whichever check finds it first.
     */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        if (selfInstalls) SelfUpdate.scheduleChecks(app, config)
        (app as? Application)?.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) = checkIfDue()
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                override fun onActivityResumed(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivityStopped(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
        checkIfDue()
    }

    private fun checkIfDue() {
        val now = SystemClock.elapsedRealtime()
        synchronized(checking) {
            if (!checkDue(lastCheck, now, RECHECK_MS)) return
            lastCheck = now
        }
        scope.launch {
            val update = checker.check(config.versionCode.toLong())
            _available.value = update
            if (update != null && selfInstalls) UpdateNotifications.availableOnce(app, update)
        }
    }

    /**
     * Installs [available]. Without "Install unknown apps" for this app it
     * opens that switch instead; the next tap downloads. From an [Activity]
     * [context], screens open in its task.
     */
    fun install(context: Context) {
        val update = _available.value ?: return
        val apkUrl = update.apkUrl
        if (!selfInstalls || apkUrl == null) {
            context.open(Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl ?: update.pageUrl)))
            return
        }
        if (!app.packageManager.canRequestPackageInstalls()) {
            context.open(SelfUpdate.allowInstallsIntent(app))
            return
        }
        UpdateNotifications.cancel(app, UpdateNotifications.AVAILABLE)
        SelfUpdate.download(app, apkUrl, update.versionCode, update.versionName)
    }

    private fun Context.open(intent: Intent) {
        if (this !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }
    }

    private companion object {
        /** The least time between two checks on coming to the front. */
        const val RECHECK_MS = 10 * 60_000L
    }
}

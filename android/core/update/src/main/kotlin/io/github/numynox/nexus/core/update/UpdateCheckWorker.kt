package io.github.numynox.nexus.core.update

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.numynox.nexus.core.data.UpdateChecker

/** The daily check: notifies a newer release, once. Offline it simply waits for tomorrow. */
class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repository = inputData.getString(SelfUpdate.KEY_REPOSITORY) ?: return Result.failure()
        val tagPrefix = inputData.getString(SelfUpdate.KEY_TAG_PREFIX) ?: return Result.failure()
        val update = UpdateChecker(repository, tagPrefix).check(SelfUpdate.installedVersionCode(applicationContext))
        if (update != null) UpdateNotifications.availableOnce(applicationContext, update)
        return Result.success()
    }
}

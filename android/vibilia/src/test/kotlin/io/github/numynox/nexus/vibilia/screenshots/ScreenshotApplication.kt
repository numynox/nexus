package io.github.numynox.nexus.vibilia.screenshots

import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserSession
import io.github.numynox.nexus.core.data.NexusJson
import io.github.numynox.nexus.vibilia.AppContainer
import io.github.numynox.nexus.vibilia.VibiliaApplication
import java.time.Instant
import kotlinx.coroutines.runBlocking

/**
 * The real app on a fake backend: signed in, with a car linked over Bluetooth
 * and a drive in progress, so every screen has something to show.
 */
class ScreenshotApplication : VibiliaApplication() {
    override val mapsAvailable: Boolean = false

    override fun onCreate() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            this,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        super.onCreate()
        runBlocking {
            container.supabase.auth.importSession(NexusJson.decodeFromString<UserSession>(FakeBackend.sessionJson), autoRefresh = false)
            container.preferences.setTrackDrives(true)
            container.preferences.setSelectedCar(FakeBackend.GOLF)
            container.preferences.setCachedLinks(FakeBackend.links)
            container.driveTracking.tracker.onConnected(
                FakeBackend.GOLF_RADIO,
                FakeBackend.links,
                recordDrives = true,
                now = Instant.now().minusSeconds(24 * 60),
            )
        }
    }

    override fun createContainer(): AppContainer = AppContainer(this, FakeBackend.engine(), FakeBackend.config)
}

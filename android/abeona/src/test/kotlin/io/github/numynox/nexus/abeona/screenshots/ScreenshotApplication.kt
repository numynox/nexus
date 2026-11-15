package io.github.numynox.nexus.abeona.screenshots

import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import io.github.numynox.nexus.abeona.AbeonaApplication
import io.github.numynox.nexus.abeona.AppContainer
import io.github.numynox.nexus.abeona.ui.scene.SceneClock
import io.github.numynox.nexus.core.testing.FakeSupabase
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking

/**
 * The real app on a fake backend, signed in; the map is drawn schematically
 * (no MapLibre on the JVM). The scene's clock says half past eleven (UTC) of
 * today, whenever the test runs, so its sky is the same daylight in every run.
 */
class ScreenshotApplication : AbeonaApplication() {
    override val mapsAvailable: Boolean = false

    override fun onCreate() {
        WorkManagerTestInitHelper.initializeTestWorkManager(this, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        super.onCreate()
        SceneClock.clock = Clock.fixed(LocalDate.now(ZoneOffset.UTC).atTime(11, 30).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        runBlocking { FakeSupabase.signIn(container.core.supabase) }
    }

    override fun createContainer(): AppContainer =
        AppContainer(this, FakeBackend.engine(), FakeBackend.config, onboardSource = { FakeBackend.onboard })
}

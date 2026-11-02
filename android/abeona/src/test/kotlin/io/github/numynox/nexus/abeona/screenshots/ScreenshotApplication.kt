package io.github.numynox.nexus.abeona.screenshots

import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import io.github.numynox.nexus.abeona.AbeonaApplication
import io.github.numynox.nexus.abeona.AppContainer
import io.github.numynox.nexus.core.testing.FakeSupabase
import kotlinx.coroutines.runBlocking

/** The real app on a fake backend, signed in; the map is drawn schematically (no MapLibre on the JVM). */
class ScreenshotApplication : AbeonaApplication() {
    override val mapsAvailable: Boolean = false

    override fun onCreate() {
        WorkManagerTestInitHelper.initializeTestWorkManager(this, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        super.onCreate()
        runBlocking { FakeSupabase.signIn(container.core.supabase) }
    }

    override fun createContainer(): AppContainer = AppContainer(this, FakeBackend.engine(), FakeBackend.config)
}

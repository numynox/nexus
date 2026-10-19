package io.github.numynox.nexus.annona.screenshots

import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.test.FakeImage
import coil3.test.FakeImageLoaderEngine
import io.github.numynox.nexus.annona.AnnonaApplication
import io.github.numynox.nexus.annona.AppContainer
import io.github.numynox.nexus.core.testing.FakeSupabase
import kotlinx.coroutines.runBlocking

/** The real app on a fake backend, signed in; product photos are flat colours from a fake Coil engine. */
class ScreenshotApplication : AnnonaApplication() {
    override fun onCreate() {
        WorkManagerTestInitHelper.initializeTestWorkManager(this, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        super.onCreate()
        val palette = listOf(0xFFE8C07A, 0xFF9CCC65, 0xFFEF8A65, 0xFF90CAF9, 0xFFCE93D8, 0xFFFFCC80, 0xFFA1887F)
        val engine = FakeImageLoaderEngine.Builder()
            .apply {
                palette.forEachIndexed { index, color ->
                    intercept({ data: Any -> (data.toString().hashCode() and 0x7fffffff) % palette.size == index }, FakeImage(color = color.toInt()))
                }
            }
            .default(FakeImage(color = palette[0].toInt()))
            .build()
        SingletonImageLoader.setUnsafe(ImageLoader.Builder(this).components { add(engine) }.build())
        runBlocking { FakeSupabase.signIn(container.core.supabase) }
    }

    override fun createContainer(): AppContainer = AppContainer(this, FakeBackend.engine(), FakeBackend.config)
}

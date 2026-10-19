package io.github.numynox.nexus.noctua.screenshots

import android.graphics.Color
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.test.FakeImage
import coil3.test.FakeImageLoaderEngine
import io.github.numynox.nexus.core.testing.FakeSupabase
import io.github.numynox.nexus.noctua.AppContainer
import io.github.numynox.nexus.noctua.NoctuaApplication
import kotlinx.coroutines.runBlocking

/**
 * The real app on a fake backend, signed in. Images come from a fake Coil
 * engine — a colour per picture — since the JVM test has no network.
 */
class ScreenshotApplication : NoctuaApplication() {
    override fun onCreate() {
        super.onCreate()
        val palette = listOf(0xFF5C6BC0, 0xFF26A69A, 0xFFEF8A65, 0xFF8D6E63, 0xFF42A5F5, 0xFFAB47BC, 0xFF9CCC65)
        val engine = FakeImageLoaderEngine.Builder()
            .intercept({ data: Any -> data.toString().contains("favicon") }, FakeImage(color = Color.rgb(0x3B, 0x44, 0xA6)))
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

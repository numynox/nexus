package io.github.numynox.nexus.noctua.screenshots

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.github.numynox.nexus.core.testing.ScreenshotRobot
import io.github.numynox.nexus.noctua.MainActivity
import java.util.concurrent.CountDownLatch
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The feed while its articles are on the way: the sections are known, the
 * articles are placeholders, and the bar under the header moves — nothing that
 * the load could then contradict.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ScreenshotApplication::class, qualifiers = "w412dp-h915dp-xxhdpi")
class LoadingScreenshot {
    private val hold = CountDownLatch(1)

    @get:Rule(order = 0)
    val slowArticles = object : ExternalResource() {
        override fun before() { FakeBackend.holdArticles = hold }
        override fun after() {
            hold.countDown()
            FakeBackend.holdArticles = null
        }
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private val robot = ScreenshotRobot(compose)

    @Test
    fun feedWhileLoading() = with(robot) {
        waitForText("Loading…")
        shot("21-feed-loading")
    }
}

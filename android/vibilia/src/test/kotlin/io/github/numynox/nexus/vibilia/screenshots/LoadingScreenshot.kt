package io.github.numynox.nexus.vibilia.screenshots

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.github.numynox.nexus.core.testing.ScreenshotRobot
import io.github.numynox.nexus.vibilia.MainActivity
import java.util.concurrent.CountDownLatch
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The prices screen while its data is on the way: the fuel card, the chart and
 * the stations as dim shapes of their own size, and no spinner — so nothing
 * moves when the real ones land.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ScreenshotApplication::class, qualifiers = "w412dp-h915dp-xxhdpi")
class LoadingScreenshot {
    private val hold = CountDownLatch(1)

    @get:Rule(order = 0)
    val slowPrices = object : ExternalResource() {
        override fun before() { FakeBackend.holdPrices = hold }
        override fun after() {
            hold.countDown()
            FakeBackend.holdPrices = null
        }
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private val robot = ScreenshotRobot(compose)

    @Test
    fun pricesWhileLoading() = with(robot) {
        waitFor(hasText("My stations"))
        shot("00-prices-loading")
    }
}

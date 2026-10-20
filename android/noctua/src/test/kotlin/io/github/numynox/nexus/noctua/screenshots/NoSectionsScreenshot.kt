package io.github.numynox.nexus.noctua.screenshots

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.github.numynox.nexus.core.testing.ScreenshotRobot
import io.github.numynox.nexus.noctua.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The feed before the first section exists: no articles, only the way to make one. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ScreenshotApplication::class, qualifiers = "w412dp-h915dp-xxhdpi")
class NoSectionsScreenshot {
    // Before the activity starts, so its first load already finds none.
    @get:Rule(order = 0)
    val noSections = object : ExternalResource() {
        override fun before() { FakeBackend.withoutSections = true }
        override fun after() { FakeBackend.withoutSections = false }
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private val robot = ScreenshotRobot(compose)

    @Test
    fun feedWithoutSections() = with(robot) {
        waitForText("No sections yet")
        shot("20-feed-no-sections")
    }
}

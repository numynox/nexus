package io.github.numynox.nexus.noctua.screenshots

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import io.github.numynox.nexus.core.testing.ScreenshotRobot
import io.github.numynox.nexus.noctua.MainActivity
import io.github.numynox.nexus.noctua.NoctuaApplication
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A refresh that finds something new starts the list over at its top — also
 * from "all caught up", where the end panel is all the list holds, and also
 * from its Refresh button rather than a pull. It used to stay on the end
 * panel there, with the new articles out of sight above it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ScreenshotApplication::class, qualifiers = "w412dp-h915dp-xxhdpi")
class RefreshScreenshot {
    @get:Rule(order = 0)
    val news = object : ExternalResource() {
        override fun before() {
            FakeBackend.breakingNews = false
            // Each test starts with nothing seen, whatever ran before it.
            runBlocking { ApplicationProvider.getApplicationContext<NoctuaApplication>().container.preferences.clearSeen() }
        }
        override fun after() {
            FakeBackend.breakingNews = false
            // This test sees everything; the next one should not start there.
            runBlocking { ApplicationProvider.getApplicationContext<NoctuaApplication>().container.preferences.clearSeen() }
        }
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private val robot = ScreenshotRobot(compose)

    @Test
    fun refreshFromAllCaughtUpGoesToTheNewArticles() = with(robot) {
        waitForText("EU agrees on the first binding rules for general-purpose AI models")
        // Scroll everything past the top, so the refresh hides it all.
        compose.onAllNodes(hasScrollToKeyAction() and hasAnyDescendant(hasText("Today"))).onFirst().performScrollToKey("end")
        settle()
        tap("Refresh")
        waitForText("Nothing new")

        FakeBackend.breakingNews = true
        tap("Refresh")
        waitForText(FakeBackend.BREAKING)
        compose.onAllNodes(hasText(FakeBackend.BREAKING)).onFirst().assertIsDisplayed()
        shot("22-feed-refreshed-from-caught-up")
    }

    @Test
    fun aSearchFromTheEndShowsItsMatches() = with(robot) {
        waitForText("EU agrees on the first binding rules for general-purpose AI models")
        compose.onAllNodes(hasScrollToKeyAction() and hasAnyDescendant(hasText("Today"))).onFirst().performScrollToKey("end")
        settle()
        // Searching from down here narrows the list above: it must show the
        // match, not stay on the end panel with the match out of sight.
        tapIcon("Search")
        compose.onNode(hasSetTextAction()).performTextInput("Linux")
        settle()
        compose.onAllNodes(hasText("Linux 7.2 released with a new scheduler for hybrid CPUs")).onFirst().assertIsDisplayed()
        shot("23-feed-search-from-the-end")
    }
}

package io.github.numynox.nexus.noctua.screenshots

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import io.github.numynox.nexus.core.testing.ScreenshotRobot
import io.github.numynox.nexus.noctua.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the real app — MainActivity, its view models and repository
 * — against [FakeBackend], walked screen by screen:
 *
 *     ./gradlew :noctua:recordRoborazziDebug
 *
 * writes PNGs to android/noctua/build/outputs/roborazzi/app; CI uploads them
 * as the `noctua-screenshots` artifact. Phone-sized (Pixel 8: 412 × 915 dp).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ScreenshotApplication::class, qualifiers = "w412dp-h915dp-xxhdpi")
class AppScreenshots {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val robot = ScreenshotRobot(compose)

    @Test
    fun walkThroughTheApp() = with(robot) {
        waitForText("EU agrees on the first binding rules for general-purpose AI models")
        shot("01-feed")
        tap("2 more on this story")
        shot("02-feed-same-story")

        compose.onAllNodes(hasText("Linux 7.2 released with a new scheduler for hybrid CPUs")).onFirst()
            .performTouchInput { longClick() }
        settle()
        shot("03-article-menu")
        tap("Star")

        tap("Tech")
        waitForText("6 new")
        shot("04-feed-section")
        tapIcon("More")
        shot("05-feed-menu")
        tap("Mark all as read")
        shot("06-mark-all-read")
        tap("Cancel")
        tapIcon("More")
        tap("Compact list")
        awaitPreferences()
        shot("07-feed-compact")
        tapIcon("More")
        tap("Cards with images")
        awaitPreferences()
        tap("All")

        tap("Library")
        waitForText("A new proof settles a 90-year-old question about prime gaps")
        shot("08-library-starred")
        tap("Read")
        waitForText("Deutsche Bahn reports punctuality up for the third month running")
        shot("09-library-read")

        tap("Statistics")
        waitForText("STREAK")
        shot("10-statistics")
        scrollTo("When you read")
        shot("11-statistics-feeds")
        scrollTo("When articles arrive")
        shot("12-statistics-heatmaps")

        tap("Settings")
        waitForText("Sections")
        shot("13-settings")
        tapIcon("Edit Tech")
        waitForText("Catalogue")
        shot("14-section-editor")
        tapIcon("Rename")
        shot("15-section-sheet")
        tap("Cancel")
        back()

        scrollTo("Wallpaper colours")
        compose.onAllNodes(hasText("Dark") and hasClickAction()).onFirst().performClick()
        awaitPreferences()
        shot("16-settings-dark")
        tap("Feed")
        waitForText("EU agrees on the first binding rules for general-purpose AI models")
        shot("17-feed-dark")
        scrollTo("How birds navigate by sensing Earth's magnetic field")
        shot("17b-feed-scrolled-dark")
        tap("Statistics")
        waitForText("STREAK")
        shot("18-statistics-dark")

        tap("Settings")
        scrollTo("Sign out")
        tap("Sign out")
        waitForText("Sign in")
        shot("19-sign-in-dark")
    }
}

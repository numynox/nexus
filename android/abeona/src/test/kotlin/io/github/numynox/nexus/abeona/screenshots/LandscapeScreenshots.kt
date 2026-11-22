package io.github.numynox.nexus.abeona.screenshots

import android.Manifest
import android.app.Application
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.numynox.nexus.abeona.MainActivity
import io.github.numynox.nexus.core.testing.ScreenshotRobot
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Tray-table mode: a phone on its side (891 × 411 dp). The trip screen in
 * two panes, the map on the right the whole height, live and off the day;
 * the ticket with its code on the left and the rides on the right.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ScreenshotApplication::class, qualifiers = "w891dp-h411dp-land-xxhdpi")
class LandscapeScreenshots {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val robot = ScreenshotRobot(compose)

    @Before
    fun allowNotifications() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test
    fun trayTable() = with(robot) {
        waitForText("Visit grandma in Bamberg")
        tap("Visit grandma in Bamberg")
        waitFor(hasContentDescription("More"))
        waitForText("to Wien Hbf")
        shot("71a-trip-landscape-live")
        tapIcon("Ticket")
        waitFor(hasContentDescription("Ticket page"))
        shot("71c-ticket-landscape")
        back()
        back()
        scrollTo("Hamburg conference")
        tap("Hamburg conference")
        waitFor(hasContentDescription("More"))
        waitForText("3 stops")
        shot("71b-trip-landscape-timeline")
    }
}

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
 * After the ride, against [FakeBackend]'s past trips: the claims bar under
 * the year in review, the claims list, the Compensation card as eligible,
 * without a price and under the minimum, and a recorded missed connection.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ScreenshotApplication::class, qualifiers = "w412dp-h915dp-xxhdpi")
class AfterRideScreenshots {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val robot = ScreenshotRobot(compose)

    @Before
    fun allowNotifications() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test
    fun afterTheRide() = with(robot) {
        waitForText("Visit grandma in Bamberg")
        tap("Review")
        waitForText("Compensation")
        shot("60-review-claims-bar")
        tap("Compensation")
        waitForText("To claim")
        shot("61-claims-list")

        tap("Visit grandma in Bamberg")
        waitFor(hasContentDescription("More"))
        waitForText("Your claim")
        scrollTo("Your claim")
        shot("62-compensation-eligible")
        back()

        waitForText("Cologne meeting")
        tap("Cologne meeting")
        waitForText("Compensation due: add the fare")
        scrollTo("Compensation due: add the fare")
        shot("63-compensation-unknown-price")
        back()

        waitForText("Nürnberg Hbf → Bamberg")
        tap("Nürnberg Hbf → Bamberg")
        waitForText("Your claim")
        scrollTo("Your claim")
        shot("64-compensation-below-minimum")
        back()

        waitForText("Back from grandma")
        scrollTo("Back from grandma")
        tap("Back from grandma")
        waitForText("Taken instead")
        scrollTo("Taken instead")
        shot("65-what-happened")
        scrollTo("Clear")
        tap("Change")
        waitForText("Which train did you miss?")
        tap("Find the connections from there")
        waitFor(androidx.compose.ui.test.hasText("IC 2063", substring = true))
        shot("66-record-missed-connection")
    }
}

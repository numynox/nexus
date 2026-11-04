package io.github.numynox.nexus.abeona.screenshots

import android.Manifest
import android.app.Application
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import io.github.numynox.nexus.abeona.MainActivity
import io.github.numynox.nexus.abeona.data.OfflinePack
import io.github.numynox.nexus.core.testing.ScreenshotRobot
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the real app against [FakeBackend], walked screen by screen:
 *
 *     ./gradlew :abeona:recordRoborazziDebug
 *
 * writes PNGs to android/abeona/build/outputs/roborazzi/app; CI uploads them
 * as the `abeona-screenshots` artifact. Phone-sized (Pixel 8: 412 × 915 dp).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ScreenshotApplication::class, qualifiers = "w412dp-h915dp-xxhdpi")
class AppScreenshots {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val robot = ScreenshotRobot(compose)

    @Before
    fun allowNotifications() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun type(text: String) {
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput(text)
        robot.settle()
    }

    @Test
    fun walkThroughTheApp() = with(robot) {
        waitForText("Visit grandma in Bamberg")
        waitForText("Train binding lifted")
        shot("01-trips")
        scrollTo("${LocalDate.now().year} on rails")
        shot("02-trips-upcoming-history")
        scrollTo("Visit grandma in Bamberg")

        tap("Visit grandma in Bamberg")
        waitForText("Open DB Navigator")
        waitForText("Live · updated", substring = true)
        shot("03-trip-detail")
        scrollTo("Know where you are on the trip")
        shot("03b-trip-location-prompt")
        scrollTo("Start live companion")
        tap("Alternatives")
        waitForText("Needs another ticket")
        shot("03c-trip-alternatives")
        dismiss()
        scrollTo("Visit grandma in Bamberg")
        scrollTo("Your journey")
        shot("04-trip-map")
        scrollTo("RE 10")
        shot("05-trip-timeline")
        tap("2 stops")
        shot("06-trip-stopovers")
        scrollTo("BahnCard discount")
        shot("06b-trip-travellers")

        scrollTo("Open DB Navigator")
        tap("Ticket")
        waitFor(hasContentDescription("Ticket page"))
        shot("07-ticket-wallet")
        back()
        back()

        tap("Search")
        waitForText("Search connections")
        shot("08-search")
        tap("Choose a station")
        type("Frank")
        waitForText("Frankfurt(Main)Süd")
        shot("09-station-sheet")
        tap("Frankfurt(Main)Hbf")
        tap("Choose a station")
        type("Mün")
        waitForText("München Ost")
        tap("München Hbf")
        tap("Search connections")
        waitForText("ICE 599")
        shot("10-search-results")
        scrollTo("Later")
        shot("10b-search-results-later")
        scrollTo("ICE 599")
        tap("ICE 599")
        waitForText("Train binding")
        shot("11-save-sheet")
        scrollIntoView("Who travels")
        shot("11b-save-sheet-travellers")
        compose.onAllNodes(hasText("Save trip") and hasClickAction()).onFirst().performClick()
        waitForText("Open DB Navigator")
        shot("12-saved-trip")
        back()

        tap("Review")
        waitForText("38")
        shot("13-review-trips")
        tapIcon("Story 2")
        shot("14-review-distance")
        tapIcon("Story 3")
        shot("15-review-time")
        tapIcon("Story 4")
        shot("16-review-delays")
        tapIcon("Story 5")
        shot("17-review-favourites")
        tapIcon("Story 6")
        shot("18-review-products")
        tapIcon("Story 7")
        shot("19-review-months")

        tap("Settings")
        waitForText("Time to the station")
        shot("20-settings")
        scrollTo("Use location during a trip")
        shot("20b-settings-on-the-day")
        scrollTo("Mila")
        shot("21-settings-travellers")
        tapIcon("Edit Mila")
        waitForText("Edit traveller")
        shot("22-traveller-sheet")
        tap("Cancel")
        scrollTo("Remind me before a card renews")
        shot("21b-settings-bahncards")
        tap("Show")
        waitFor(hasContentDescription("BahnCard"))
        shot("21c-bahncard")
        back()
        waitForText("Remind me before a card renews")
        tapIcon("Edit BahnCard 50 · 2nd class")
        waitForText("Edit BahnCard")
        shot("21d-bahncard-sheet")
        tap("Cancel")
        scrollTo("Data sources")
        shot("23-settings-about")

        scrollTo("Wallpaper colours")
        compose.onAllNodes(hasText("Dark") and hasClickAction()).onFirst().performClick()
        awaitPreferences()
        tap("Trips")
        waitForText("Visit grandma in Bamberg")
        shot("24-trips-dark")
        tap("Visit grandma in Bamberg")
        waitForText("Open DB Navigator")
        shot("25-trip-detail-dark")
        scrollTo("RE 10")
        shot("26-trip-timeline-dark")
    }

    private fun ScreenshotRobot.waitForText(text: String, substring: Boolean) = waitFor(hasText(text, substring = substring))
}

/** No trips yet: the illustrated empty state. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ScreenshotApplication::class, qualifiers = "w412dp-h915dp-xxhdpi")
class EmptyScreenshots {
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() { FakeBackend.noTrips = true }
        override fun after() { FakeBackend.noTrips = false }
    }).around(compose)

    @Test
    fun emptyTrips() = with(ScreenshotRobot(compose)) {
        waitForText("No trips planned")
        shot("30-trips-empty")
    }
}

/** No network: the trips and the ticket from the offline pack, saying how old they are. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ScreenshotApplication::class, qualifiers = "w412dp-h915dp-xxhdpi")
class OfflineScreenshots {
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            val context = ApplicationProvider.getApplicationContext<Application>()
            val pack = OfflinePack(context)
            val savedAt = Instant.now().minus(47, ChronoUnit.MINUTES)
            runBlocking {
                pack.write(FakeBackend.shownTrips, savedAt)
                pack.saveTicket(FakeBackend.shownTrips.first(), FakeBackend.ticketPng)
            }
            FakeBackend.offline = true
        }
        override fun after() { FakeBackend.offline = false }
    }).around(compose)

    @Test
    fun offlinePack() = with(ScreenshotRobot(compose)) {
        waitForText("Visit grandma in Bamberg")
        shot("31-trips-offline")
        tap("Visit grandma in Bamberg")
        waitForText("Open DB Navigator")
        shot("32-trip-offline")
        scrollTo("Open DB Navigator")
        tap("Ticket")
        waitFor(hasContentDescription("Ticket page"))
        shot("33-ticket-offline")
    }
}

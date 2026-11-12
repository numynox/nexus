package io.github.numynox.nexus.abeona.screenshots

import android.Manifest
import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.numynox.nexus.abeona.MainActivity
import io.github.numynox.nexus.abeona.data.Journey
import io.github.numynox.nexus.abeona.domain.AppClock
import io.github.numynox.nexus.abeona.domain.intake.IntakeJson
import io.github.numynox.nexus.abeona.domain.intake.resourceText
import io.github.numynox.nexus.core.testing.ScreenshotRobot
import java.io.ByteArrayInputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.TimeZone
import kotlinx.serialization.Serializable
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Intake, from the real (anonymised) samples in app/abeona/src/lib/intake/
 * fixtures and the timetable answers of intake.fixtures.json's "matching"
 * cases: a DB Navigator share found in the timetable, the ticket PDF shared
 * from another app with its return-booking price to confirm, and a share the
 * timetable does not know.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ScreenshotApplication::class, qualifiers = "w412dp-h915dp-xxhdpi")
class IntakeScreenshots {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val robot = ScreenshotRobot(compose)
    private val zone = TimeZone.getDefault()

    @Serializable private data class Case(val name: String, val stations: Map<String, String>, val journeys: Map<String, List<Journey>>)
    @Serializable private data class Fixtures(val matching: List<Case>)

    private val cases = IntakeJson.decodeFromString<Fixtures>(resourceText("intake.fixtures.json")).matching

    private fun timetable(prefix: String) = cases.first { it.name.startsWith(prefix) }.let { it.stations to it.journeys }

    @Before
    fun setUp() {
        // The samples are dated November 2025: look at them from the week before, as upcoming trips.
        AppClock.clock = Clock.fixed(Instant.parse("2025-11-10T09:00:00Z"), ZoneOffset.UTC)
        // DB prints Berlin time; show it as a phone in Germany would.
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @After
    fun tearDown() {
        FakeBackend.intakeTimetable = null
        AppClock.clock = Clock.systemUTC()
        TimeZone.setDefault(zone)
    }

    private fun share(intent: Intent) {
        compose.runOnUiThread { compose.activity.receive(intent) }
        robot.settle()
    }

    private fun shareText(text: String) = share(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text))

    @Test
    fun shareFoundInTheTimetable() = with(robot) {
        FakeBackend.intakeTimetable = timetable("outward: Transitous changes")
        waitForText("Visit grandma in Bamberg")
        shareText(resourceText("db-navigator-share-4-legs.txt"))
        waitForText("Read from DB Navigator")
        shot("80-intake-share-matched")
    }

    @Test
    fun ticketPdfWithBookingPrice() = with(robot) {
        FakeBackend.intakeTimetable = timetable("return: the whole journey")
        waitForText("Visit grandma in Bamberg")
        tap("Search")
        waitForText("Add from a ticket or share")
        tap("Add from a ticket or share")
        waitForText("Share text from DB Navigator")
        shot("81-intake-sheet")

        val uri = Uri.parse("content://io.github.numynox.nexus.abeona.test/super-sparpreis-europa.pdf")
        val bytes = checkNotNull(javaClass.getResource("/synthetic-ticket.pdf")).readBytes()
        shadowOf(ApplicationProvider.getApplicationContext<Application>().contentResolver).registerInputStream(uri, ByteArrayInputStream(bytes))
        share(Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri))
        // "Read from your ticket · Return journey": the sample is the return half of a booking.
        waitFor(hasText("Read from your ticket", substring = true))
        shot("82-intake-ticket")
        scrollIntoView("Price (optional)")
        shot("83-intake-ticket-price")
    }

    @Test
    fun shareNotInTheTimetable() = with(robot) {
        FakeBackend.intakeTimetable = timetable("outward: no station is known")
        waitForText("Visit grandma in Bamberg")
        shareText(resourceText("db-navigator-share-4-legs.txt"))
        waitFor(hasText("Not found in the timetable", substring = true))
        shot("85-intake-no-match")
    }
}

package io.github.numynox.nexus.abeona.screenshots

import android.app.Notification
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.numynox.nexus.abeona.domain.Position
import io.github.numynox.nexus.abeona.domain.TravelSignals
import io.github.numynox.nexus.abeona.live.LiveCompanion
import io.github.numynox.nexus.abeona.live.OnboardStatus
import io.github.numynox.nexus.abeona.live.companionStatus
import io.github.numynox.nexus.core.testing.shotWidget
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Android 16's Live Update on the trip to grandma's: the journey as the
 * notification's progress bar (ProgressStyle), with fewer lines.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ScreenshotApplication::class, qualifiers = "w412dp-h915dp-xxhdpi", sdk = [36])
class LiveUpdateScreenshots {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val t0 = Instant.now().truncatedTo(ChronoUnit.HOURS)
    private fun at(m: Long) = t0.plus(m, ChronoUnit.MINUTES)
    private val trip = grandmaTrip(t0).let { t -> t.copy(ticketPath = "h/t/ticket.pdf", legs = listOf(t.legs[0].copy(coach = "24", seats = "66"), t.legs[1])) }

    private fun ongoing(minutes: Long, signals: TravelSignals = TravelSignals(), onboard: OnboardStatus? = null): Notification {
        LiveCompanion.createChannels(context)
        val now = at(minutes)
        return LiveCompanion.ongoing(context, trip.id, companionStatus(trip, now, 15, signals, onboard), now.minusSeconds(40), now, ticket = true)
    }

    private fun shot(name: String, minutes: Long, signals: TravelSignals = TravelSignals(), onboard: OnboardStatus? = null) =
        shotWidget(notificationViews(context, ongoing(minutes, signals, onboard)), 380, 200, name)

    @Test
    fun theJourneyIsTheBar() {
        val notification = ongoing(46)
        assertEquals(Notification.ProgressStyle::class.java.name, notification.extras.getString(Notification.EXTRA_TEMPLATE))
        assertTrue("promotion requested", notification.extras.getBoolean("android.requestPromotedOngoing"))
        assertEquals(listOf("Ticket", "Stop"), notification.actions.map { it.title.toString() })
    }

    @Test fun atTheStation() = shot("44i-live-update-at-the-station", 2, TravelSignals(position = Position(50.1068, 8.6630, 0.0, 10.0)))

    @Test fun onBoard() = shot(
        "44j-live-update-on-board", 46, TravelSignals(onboardLegIndex = 0),
        OnboardStatus("ICE Portal", "ICE", "25", 247.0, 49.94, 9.50, "Würzburg Hbf", at(75), "4", at(46)),
    )

    @Test fun missed() = shot("44k-live-update-missed", 86, TravelSignals(position = Position(49.8018, 9.9355, 0.0, 10.0)))
}

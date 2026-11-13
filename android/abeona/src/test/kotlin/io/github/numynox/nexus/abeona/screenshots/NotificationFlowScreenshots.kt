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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The live companion's ongoing notification through the trip to grandma's,
 * one shot per step, as the system lays it out expanded.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ScreenshotApplication::class, qualifiers = "w412dp-h915dp-xxhdpi")
class NotificationFlowScreenshots {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val t0 = Instant.now().truncatedTo(ChronoUnit.HOURS)
    private fun at(m: Long) = t0.plus(m, ChronoUnit.MINUTES)
    private val trip = grandmaTrip(t0)

    private fun ongoing(name: String, minutes: Long, signals: TravelSignals = TravelSignals(), onboard: OnboardStatus? = null) {
        LiveCompanion.createChannels(context)
        val now = at(minutes)
        val status = companionStatus(trip, now, 15, signals, onboard)
        shot(LiveCompanion.ongoing(context, trip.id, status, now.minusSeconds(40), now), name)
    }

    @Test fun beforeLeaving() = ongoing("44a-notification-leave-in", -25)

    @Test fun atTheStation() = ongoing("44b-notification-platform", 2, TravelSignals(position = Position(50.1068, 8.6630, 0.0, 10.0)))

    @Test fun onBoard() = ongoing("44c-notification-next-stop", 20)

    @Test fun onBoardWifi() = ongoing(
        "44d-notification-train-wifi", 46, TravelSignals(onboardLegIndex = 0),
        OnboardStatus("ICE Portal", "ICE", "25", 247.0, 49.94, 9.50, "Würzburg Hbf", at(75), "4", at(46)),
    )

    @Test fun changing() = ongoing("44e-notification-changing", 77)

    @Test fun lastRide() = ongoing("44f-notification-last-ride", 125)

    @Test fun missed() = ongoing("44g-notification-missed", 86, TravelSignals(position = Position(49.8018, 9.9355, 0.0, 10.0)))

    /** When Android refuses the start anyway: one tap on "Follow live". */
    @Test fun followLive() {
        LiveCompanion.createChannels(context)
        shot(LiveCompanion.reminder(context, trip, at(-28)), "44h-notification-follow-live")
    }

    private fun shot(notification: Notification, name: String) {
        val views = Notification.Builder.recoverBuilder(context, notification).createBigContentView()
            ?: Notification.Builder.recoverBuilder(context, notification).createContentView()
        val root = context.resources.getIdentifier("status_bar_latest_event_content", "id", "android")
        if (root != 0) views.setInt(root, "setBackgroundColor", android.graphics.Color.WHITE)
        shotWidget(views, 380, 190, name)
    }
}

package io.github.numynox.nexus.abeona.screenshots

import android.app.Notification
import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.provideContent
import androidx.test.core.app.ApplicationProvider
import io.github.numynox.nexus.abeona.AbeonaApplication
import io.github.numynox.nexus.abeona.domain.TravelSignals
import io.github.numynox.nexus.abeona.live.LiveCompanion
import io.github.numynox.nexus.abeona.live.OnboardStatus
import io.github.numynox.nexus.abeona.reminders.LeaveReminder
import io.github.numynox.nexus.abeona.live.companionAlerts
import io.github.numynox.nexus.abeona.live.companionStatus
import io.github.numynox.nexus.abeona.reminders.BahnCardReminders
import io.github.numynox.nexus.abeona.reminders.ClaimReminders
import io.github.numynox.nexus.abeona.widget.NextTripContent
import io.github.numynox.nexus.abeona.widget.NextTripState
import io.github.numynox.nexus.abeona.widget.NextTripWidget
import io.github.numynox.nexus.core.testing.shotWidget
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The home screen widget, loading from the fake backend, and the live companion's notifications. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ScreenshotApplication::class, qualifiers = "w412dp-h915dp-xxhdpi")
class WidgetScreenshot {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private class Showing(private val state: NextTripState) : GlanceAppWidget() {
        override val sizeMode: SizeMode = SizeMode.Exact
        override suspend fun provideGlance(context: Context, id: GlanceId) = provideContent { NextTripContent(state) }
    }

    private fun shot(widget: GlanceAppWidget, width: Int, height: Int, name: String) = runBlocking {
        shotWidget(widget.compose(context, size = DpSize(width.dp, height.dp)), width, height, name)
    }

    @Test
    fun nextTrip() {
        shot(NextTripWidget(), 250, 130, "40-widget-3x2")
        shot(NextTripWidget(), 320, 180, "41-widget-4x2")
    }

    @Test
    @Config(qualifiers = "+night")
    fun nextTripAtNight() = shot(NextTripWidget(), 250, 130, "42-widget-3x2-dark")

    @Test
    fun noTrips() = shot(Showing(NextTripState.NoTrips), 250, 130, "43-widget-empty")

    /** The ongoing notification and a heads-up, as the system would lay them out. */
    @Test
    fun companionNotifications() {
        val container = (context.applicationContext as AbeonaApplication).container
        val trip = runBlocking { container.trips.trip(FakeBackend.NEXT).value!! }
        val now = trip.legs[0].from.currentAt!!.plusSeconds(25 * 60)
        LiveCompanion.createChannels(context)
        val ongoing = LiveCompanion.ongoing(context, trip.id, companionStatus(trip, now), now)
        shotNotification(ongoing, "44-notification-ongoing")
        val before = trip.copy(legs = trip.legs.mapIndexed { i, leg -> if (i == 1) leg.copy(from = leg.from.copy(platform = null)) else leg.copy(to = leg.to.copy(current = leg.to.planned)) })
        val alert = companionAlerts(before.copy(legs = before.legs.map { it.copy(to = it.to.copy(current = it.to.planned)) }), trip, Instant.now()).first()
        shotNotification(LiveCompanion.alert(context, trip.id, alert), "45-notification-alert")
    }

    /** Time to leave, for the trip to grandma's, built from the fake backend's trip. */
    @Test
    fun leaveReminder() {
        val trip = runBlocking { (context.applicationContext as AbeonaApplication).container.trips.trip(FakeBackend.NEXT).value!! }
        LeaveReminder.createChannel(context)
        val (title, text) = LeaveReminder.message(trip, 15)
        shotNotification(LeaveReminder.build(context, trip, title, text), "47-notification-leave")
    }

    /** On board, with the ICE Portal's speed in the ongoing notification. */
    @Test
    fun companionOnBoard() {
        val container = (context.applicationContext as AbeonaApplication).container
        val trip = runBlocking { container.trips.trip(FakeBackend.NEXT).value!! }
        val now = trip.legs[0].from.currentAt!!.plusSeconds(25 * 60)
        val onboard = OnboardStatus("ICE Portal", "ICE", "25", 247.0, null, null, "Aschaffenburg Hbf", null, null, now)
        LiveCompanion.createChannels(context)
        val status = companionStatus(trip, now, 15, TravelSignals(onboardLegIndex = 0), onboard)
        shotNotification(LiveCompanion.ongoing(context, trip.id, status, now), "48-notification-on-board")
    }

    /** The BahnCard reminder, built from what abeona_bahncard_deadlines returns. */
    @Test
    fun bahnCardReminder() {
        val deadlines = runBlocking { (context.applicationContext as AbeonaApplication).container.repository.bahnCardDeadlines() }
        val (title, lines) = BahnCardReminders.message(deadlines.map { it.copy(daysLeft = 7) })!!
        shotNotification(BahnCardReminders.build(context, title, lines), "46-notification-bahncard")
    }

    /** The compensation reminder, built from what abeona_claims_overview returns. */
    @Test
    fun claimReminder() {
        val overview = runBlocking { (context.applicationContext as AbeonaApplication).container.repository.claimsOverview(java.time.LocalDate.now().year) }
        val (title, lines) = ClaimReminders.message(overview.toClaim.map { it.copy(daysLeft = 7) })!!
        shotNotification(ClaimReminders.build(context, title, lines), "49-notification-claim")
    }

    private fun shotNotification(notification: Notification, name: String) {
        val views = Notification.Builder.recoverBuilder(context, notification).createBigContentView()
            ?: Notification.Builder.recoverBuilder(context, notification).createContentView()
        // The system draws the card behind a notification; templates have none of their own.
        val root = context.resources.getIdentifier("status_bar_latest_event_content", "id", "android")
        if (root != 0) views.setInt(root, "setBackgroundColor", android.graphics.Color.WHITE)
        shotWidget(views, 380, 170, name)
    }
}

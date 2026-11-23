package io.github.numynox.nexus.abeona.live

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.numynox.nexus.abeona.screenshots.ScreenshotApplication
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The ongoing notification where it is needed most: on the lock screen, the
 * always-on display and the status bar, and as a Live Update on Android 16.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = ScreenshotApplication::class)
class OngoingNotificationTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun ongoing(): Notification {
        LiveCompanion.createChannels(context)
        val now = Instant.now()
        val status = CompanionStatus("Pl. 1 · ICE 600 in 19 min", "to Hamburg-Altona · departs 10:24 (+5)", false, "Pl. 1")
        return LiveCompanion.ongoing(context, "t", status, now, now)
    }

    @Test
    fun theChannelAlertsSilentlyAndShowsOnTheLockScreen() {
        LiveCompanion.createChannels(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel(LiveCompanion.CHANNEL_LIVE)
        // LOW would be a "silent" notification: hidden on the lock screen and AOD by default.
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
        assertNull(channel.sound)
        assertEquals(false, channel.shouldVibrate())
        assertEquals(Notification.VISIBILITY_PUBLIC, channel.lockscreenVisibility)
        assertNull("the old silent channel is gone", manager.getNotificationChannel("live"))
    }

    /** What to act on alerts with a heads-up; updates do not, and things getting better make no sound. */
    @Test
    fun alertsGoToActNowOrUpdates() {
        LiveCompanion.createChannels(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, manager.getNotificationChannel(LiveCompanion.CHANNEL_ALERTS).importance)
        assertEquals("Act now", manager.getNotificationChannel(LiveCompanion.CHANNEL_ALERTS).name)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, manager.getNotificationChannel(LiveCompanion.CHANNEL_UPDATES).importance)
        assertEquals("Updates", manager.getNotificationChannel(LiveCompanion.CHANNEL_UPDATES).name)
        val act = LiveCompanion.alert(context, "t", CompanionAlert("getoff-0", "Get off at Würzburg Hbf in 5 min", "Pl. 4", subject = "getoff-0"))
        assertEquals(LiveCompanion.CHANNEL_ALERTS, act.channelId)
        val quiet = LiveCompanion.alert(context, "t", CompanionAlert("works-1", "Change at Würzburg Hbf works again", "6 min", "change-1", AlertChannel.Updates, silent = true))
        assertEquals(LiveCompanion.CHANNEL_UPDATES, quiet.channelId)
    }

    /** The ticket one tap away, the alternatives when something broke, and Stop: on every Android version. */
    @Test
    fun theActions() {
        LiveCompanion.createChannels(context)
        val now = Instant.now()
        val broken = CompanionStatus("Looks like you missed the RE 10", "It left …", false, "Missed", broken = true)
        assertEquals(listOf("Ticket", "Alternatives", "Stop"), LiveCompanion.ongoing(context, "t", broken, now, now, ticket = true).actions.map { it.title.toString() })
        assertEquals(listOf("Stop"), ongoing().actions.map { it.title.toString() })
    }

    /**
     * Android 16's Live Update: the journey as one segment per ride with the
     * change between them, and the text under the title in two lines. The
     * header says only how fresh, when it is not live: a promoted Live
     * Update does not show it.
     */
    @Test
    @Config(sdk = [36])
    fun theLiveUpdateSaysItAllUnderTheTitle() {
        LiveCompanion.createChannels(context)
        val now = Instant.now()
        val bar = JourneyBar(listOf(BarSegment(100, BarTone.Ride), BarSegment(100, BarTone.Ride)), listOf(BarPoint(100, BarTone.Watch)), 40)
        val status = CompanionStatus(
            "ICE 25 arrives in 29 min · Pl. 4 · 07:15 (+7)", "Change at Würzburg Hbf", false, "29 min",
            info = listOf("Change at Würzburg Hbf → RE 10 · Pl. 5 · 07:21 · 6 min · tight", "Next: Aschaffenburg Hbf 06:39", "247 km/h"),
            bar = bar,
        )
        val live = LiveCompanion.ongoing(context, "t", status, now, now)
        assertEquals(
            "Change at Würzburg Hbf → RE 10 · Pl. 5 · 07:21 · 6 min · tight\nNext: Aschaffenburg Hbf 06:39 · 247 km/h",
            live.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
        assertNull("live: nothing in the header", live.extras.getCharSequence(Notification.EXTRA_SUB_TEXT))
        val style = Notification.Builder.recoverBuilder(context, live).style as Notification.ProgressStyle
        assertEquals(listOf(100, 100), style.progressSegments.map { it.length })
        assertEquals(listOf(100), style.progressPoints.map { it.position })
        assertEquals(40, style.progress)
        assertTrue("the bar fills up to the train", style.isStyledByProgress)
        // Refreshes not getting through: the header says since when.
        val stale = LiveCompanion.ongoing(context, "t", status, now.minusSeconds(10 * 60), now)
        assertTrue(stale.extras.getCharSequence(Notification.EXTRA_SUB_TEXT).toString().startsWith("Offline "))
        // Android 15 keeps its lines.
        assertEquals("Change at Würzburg Hbf", LiveCompanion.ongoing(context, "t", status, now, now, sdk = 35).extras.getCharSequence(Notification.EXTRA_TEXT).toString())
    }

    /**
     * What Notification.hasPromotableCharacteristics() asks on Android 17
     * (read from its framework jar): promotion requested, ongoing, a title, a
     * promotable style (standard, BigText, Call, Progress), not a group
     * summary, no custom views, and not colorized. The first Android 16
     * release asked for colorized instead, so the platform method is not run
     * here: Robolectric's SDK 36 has the old rule.
     */
    @Test
    fun theNotificationIsPublicAndCanBeALiveUpdate() {
        val notification = ongoing()
        val extras = notification.extras
        assertEquals(Notification.VISIBILITY_PUBLIC, notification.visibility)
        assertTrue("promotion requested", extras.getBoolean("android.requestPromotedOngoing"))
        assertTrue("ongoing", notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue("a title", !extras.getCharSequence(Notification.EXTRA_TITLE).isNullOrBlank())
        assertEquals(Notification.BigTextStyle::class.java.name, extras.getString(Notification.EXTRA_TEMPLATE))
        assertNull("no group: setSilent would put it into one", notification.group)
        assertNull("no custom views", notification.contentView)
        assertEquals("not colorized", false, extras.getBoolean(Notification.EXTRA_COLORIZED))
    }
}

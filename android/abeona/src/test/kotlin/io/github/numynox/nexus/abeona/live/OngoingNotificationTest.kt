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
        val status = CompanionStatus("Pl. 1 · ICE 600 in 19 min", "to Hamburg-Altona · departs 10:24 (+5)", false, "Pl. 1", countdownTo = now.plusSeconds(19 * 60))
        return LiveCompanion.ongoing(context, "t", status, now, "Timetable", now)
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

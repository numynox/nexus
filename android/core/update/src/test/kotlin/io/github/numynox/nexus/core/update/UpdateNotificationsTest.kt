package io.github.numynox.nexus.core.update

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import org.robolectric.RuntimeEnvironment
import io.github.numynox.nexus.core.data.AvailableUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class UpdateNotificationsTest {
    private val context: Application = RuntimeEnvironment.getApplication()
    private val update = AvailableUpdate(
        versionName = "0.0.58",
        versionCode = 58,
        pageUrl = "https://github.com/numynox/nexus/releases/tag/annona-android-v0.0.58",
        apkUrl = "https://github.com/numynox/nexus/releases/download/annona-android-v0.0.58/annona-0.0.58.apk",
    )

    @Test
    fun theTapIsABroadcastToTheReceiverCarryingTheApk() {
        val tap = shadowOf(UpdateNotifications.available(context, update)!!.contentIntent)
        assertTrue(tap.isBroadcastIntent)
        val intent = tap.savedIntent
        assertEquals(UpdateReceiver::class.java.name, intent.component?.className)
        assertEquals(UpdateReceiver.ACTION_DOWNLOAD, intent.action)
        assertEquals(update.apkUrl, intent.getStringExtra(SelfUpdate.KEY_APK_URL))
        assertEquals(58L, intent.getLongExtra(SelfUpdate.KEY_VERSION_CODE, 0))
    }

    @Test
    fun aReleaseWithoutApkIsNotNotified() {
        assertNull(UpdateNotifications.available(context, update.copy(apkUrl = null)))
    }

    @Test
    fun notifiesEachReleaseOnce() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(UpdateNotifications.availableOnce(context, update))
        assertFalse(UpdateNotifications.availableOnce(context, update))
        assertTrue(UpdateNotifications.availableOnce(context, update.copy(versionCode = 59)))
    }

    @Test
    fun aNotificationThatCouldNotBePostedIsNotRemembered() {
        val manager = shadowOf(context.getSystemService(NotificationManager::class.java))
        manager.setNotificationsEnabled(false)
        assertFalse(UpdateNotifications.availableOnce(context, update))
        manager.setNotificationsEnabled(true)
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(UpdateNotifications.availableOnce(context, update))
    }
}

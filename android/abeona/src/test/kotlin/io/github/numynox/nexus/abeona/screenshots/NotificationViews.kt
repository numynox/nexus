package io.github.numynox.nexus.abeona.screenshots

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Icon
import android.widget.RemoteViews

/**
 * What the shade draws for [notification]: its expanded view, else its
 * collapsed one, with a white card behind it as the system draws.
 *
 * The small icon goes in as a bitmap. Given a resource, the template's icon
 * view (LocalImageResolver) first tries to decode it with ImageDecoder, and
 * our icon is a vector, so that fails and falls back. Under Robolectric's
 * native graphics that failed decode now and then crashes the test JVM
 * (SIGSEGV in libc, ImageDecoder.nCreate from an asset stream). A bitmap
 * icon is drawn as it is, with no decoder involved.
 */
internal fun notificationViews(context: Context, notification: Notification): RemoteViews {
    val icon = notification.smallIcon?.loadDrawable(context)?.let { drawable ->
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(Canvas(bitmap))
        Icon.createWithBitmap(bitmap)
    }
    fun builder() = Notification.Builder.recoverBuilder(context, notification).also { b -> icon?.let(b::setSmallIcon) }
    val views = builder().createBigContentView() ?: builder().createContentView()
    val root = context.resources.getIdentifier("status_bar_latest_event_content", "id", "android")
    if (root != 0) views.setInt(root, "setBackgroundColor", android.graphics.Color.WHITE)
    return views
}

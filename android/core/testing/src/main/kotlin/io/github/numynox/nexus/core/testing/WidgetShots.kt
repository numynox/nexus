package io.github.numynox.nexus.core.testing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage

/**
 * Photographs a home screen widget: its RemoteViews inflated the way a
 * launcher would, at [widthDp]×[heightDp], on a plain wallpaper so the
 * widget's own background and corners show. Lands beside the app's other
 * screenshots in `build/outputs/roborazzi/app/<name>.png`.
 */
fun shotWidget(views: RemoteViews, widthDp: Int, heightDp: Int, name: String) {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val density = context.resources.displayMetrics.density
    val margin = (16 * density).toInt()
    val width = (widthDp * density).toInt()
    val height = (heightDp * density).toInt()
    val wallpaper = FrameLayout(context).apply {
        setBackgroundColor(Color.rgb(0x4A, 0x5B, 0x6E))
        setPadding(margin, margin, margin, margin)
    }
    wallpaper.addView(views.apply(context, wallpaper), FrameLayout.LayoutParams(width, height))
    wallpaper.measure(
        View.MeasureSpec.makeMeasureSpec(width + 2 * margin, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(height + 2 * margin, View.MeasureSpec.EXACTLY),
    )
    wallpaper.layout(0, 0, wallpaper.measuredWidth, wallpaper.measuredHeight)
    // Drawn by hand: the view has no window, which Roborazzi's view capture needs.
    val image = Bitmap.createBitmap(wallpaper.measuredWidth, wallpaper.measuredHeight, Bitmap.Config.ARGB_8888)
    wallpaper.draw(Canvas(image))
    image.captureRoboImage("build/outputs/roborazzi/app/$name.png")
}

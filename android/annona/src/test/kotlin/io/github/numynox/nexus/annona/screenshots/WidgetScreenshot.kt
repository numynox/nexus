package io.github.numynox.nexus.annona.screenshots

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.provideContent
import androidx.test.core.app.ApplicationProvider
import io.github.numynox.nexus.annona.widget.UseSoonContent
import io.github.numynox.nexus.annona.widget.UseSoonState
import io.github.numynox.nexus.annona.widget.UseSoonWidget
import io.github.numynox.nexus.core.testing.shotWidget
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The home screen widget at the sizes it is placed at, loading from the fake
 * backend's pantry — expired, due this week and good for months.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ScreenshotApplication::class, qualifiers = "w412dp-h915dp-xxhdpi")
class WidgetScreenshot {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** Draws [state] the way the real widget would, without loading anything. */
    private class Showing(private val state: UseSoonState) : GlanceAppWidget() {
        override val sizeMode: SizeMode = SizeMode.Exact
        override suspend fun provideGlance(context: Context, id: GlanceId) = provideContent { UseSoonContent(state) }
    }

    private fun shot(widget: GlanceAppWidget, width: Int, height: Int, name: String) = runBlocking {
        shotWidget(widget.compose(context, size = DpSize(width.dp, height.dp)), width, height, name)
    }

    @Test
    fun useSoon() {
        shot(UseSoonWidget(), 250, 130, "30-widget-3x2")
        shot(UseSoonWidget(), 320, 220, "31-widget-4x3")
    }

    @Test
    @Config(qualifiers = "+night")
    fun useSoonAtNight() = shot(UseSoonWidget(), 250, 130, "32-widget-3x2-dark")

    @Test
    fun nothingDated() = shot(Showing(UseSoonState.Ready(emptyList())), 250, 130, "33-widget-empty")
}

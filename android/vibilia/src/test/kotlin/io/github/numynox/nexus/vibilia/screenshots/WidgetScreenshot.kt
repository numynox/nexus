package io.github.numynox.nexus.vibilia.screenshots

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.provideContent
import androidx.test.core.app.ApplicationProvider
import io.github.numynox.nexus.core.testing.shotWidget
import io.github.numynox.nexus.vibilia.widget.FuelSnapshot
import io.github.numynox.nexus.vibilia.widget.FuelWidget
import io.github.numynox.nexus.vibilia.widget.FuelWidgetContent
import io.github.numynox.nexus.vibilia.widget.FuelWidgetState
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The home screen widget at the sizes it is placed at: the real one loading
 * from the fake backend, and the states that backend does not reach.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ScreenshotApplication::class, qualifiers = "w412dp-h915dp-xxhdpi")
class WidgetScreenshot {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** Draws [state] the way the real widget would, without loading anything. */
    private class Showing(private val state: FuelWidgetState) : GlanceAppWidget() {
        override val sizeMode: SizeMode = SizeMode.Exact
        override suspend fun provideGlance(context: Context, id: GlanceId) = provideContent { FuelWidgetContent(state) }
    }

    private fun shot(widget: GlanceAppWidget, width: Int, height: Int, name: String) = runBlocking {
        shotWidget(widget.compose(context, size = DpSize(width.dp, height.dp)), width, height, name)
    }

    private val low = FuelSnapshot(
        carName = "Golf",
        fillPercent = 14,
        litersLeft = 7.0,
        rangeKm = 102.0,
        low = true,
        fuelType = "E10",
        station = "JET Hanau",
        price = 1.739,
        loadedAt = Instant.now().toEpochMilli(),
    )

    @Test
    fun tankAndPrice() {
        shot(FuelWidget(), 320, 90, "30-widget-4x1")
        shot(FuelWidget(), 250, 130, "31-widget-3x2")
    }

    @Test
    fun lowTank() = shot(Showing(FuelWidgetState.Ready(low)), 250, 130, "32-widget-low")

    @Test
    @Config(qualifiers = "+night")
    fun lowTankAtNightOffline() {
        // The last load, three hours old: the price says so.
        val old = low.copy(loadedAt = Instant.now().minusSeconds(3 * 3600).toEpochMilli())
        shot(Showing(FuelWidgetState.Ready(old)), 320, 90, "33-widget-low-dark-old")
    }

    @Test
    fun signedOut() = shot(Showing(FuelWidgetState.SignedOut), 320, 90, "34-widget-signed-out")
}

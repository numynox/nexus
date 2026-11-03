package io.github.numynox.nexus.abeona.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.numynox.nexus.abeona.data.Journey
import io.github.numynox.nexus.abeona.data.Leg
import io.github.numynox.nexus.abeona.data.LegMode
import io.github.numynox.nexus.abeona.data.StopTime
import io.github.numynox.nexus.abeona.data.Traveller
import io.github.numynox.nexus.abeona.ui.AbeonaPalette
import io.github.numynox.nexus.abeona.ui.search.SaveTripSheet
import io.github.numynox.nexus.core.ui.theme.NexusTheme
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Saving a journey found for a past date: the sheet asks how late it arrived. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ScreenshotApplication::class, qualifiers = "w412dp-h915dp-xxhdpi")
class HistoryScreenshots {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun savePastTrip() {
        val t0 = Instant.now().minus(9, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS)
        fun iso(m: Long) = t0.plus(m, ChronoUnit.MINUTES).toString()
        val journey = Journey(
            refreshToken = "past",
            legs = listOf(
                Leg(
                    mode = LegMode.Train, product = "ICE", lineName = "ICE 25",
                    from = StopTime("ffm", "Frankfurt(Main)Hbf", 50.1071, 8.6636, iso(0), plannedPlatform = "8"),
                    to = StopTime("wue", "Würzburg Hbf", 49.8018, 9.9355, iso(68), plannedPlatform = "4"),
                ),
                Leg(
                    mode = LegMode.Train, product = "RE", lineName = "RE 10",
                    from = StopTime("wue", "Würzburg Hbf", 49.8018, 9.9355, iso(80), plannedPlatform = "6"),
                    to = StopTime("bam", "Bamberg", 49.9007, 10.8994, iso(141)),
                ),
            ),
        )
        compose.setContent {
            NexusTheme(AbeonaPalette) {
                SaveTripSheet(
                    journey = journey,
                    travellers = listOf(Traveller("tr1", "h", "Jannis", "u1")),
                    me = "u1",
                    onSave = {},
                    onDismiss = {},
                )
            }
        }
        compose.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/app/47-save-past-trip.png")
    }
}

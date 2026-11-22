package io.github.numynox.nexus.abeona.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.numynox.nexus.abeona.data.TripSummary
import io.github.numynox.nexus.abeona.ui.AbeonaPalette
import io.github.numynox.nexus.abeona.ui.trip.PostcardCard
import io.github.numynox.nexus.abeona.ui.trip.PostcardImage
import io.github.numynox.nexus.core.ui.theme.NexusTheme
import io.github.numynox.nexus.core.ui.theme.ThemeMode
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The arrival postcard on yesterday's trip to grandma's: the card on the trip
 * screen, light and dark and at the largest text, and the picture it shares.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ScreenshotApplication::class, qualifiers = "w412dp-h420dp-xxhdpi")
class PostcardScreenshots {
    @get:Rule
    val compose = createComposeRule()

    private val trip = grandmaTrip(Instant.now().truncatedTo(ChronoUnit.HOURS).minus(1, ChronoUnit.DAYS))
    private val summary = TripSummary(
        rides = 2, km = 220.0, minutesOnTrain = 149.0, delayMinutes = 22.0, topSpeedKmh = 287,
        co2TrainKg = 7.7, co2CarKg = 40.5, co2SavedKg = 32.8,
    )

    private fun card(name: String, mode: ThemeMode = ThemeMode.Light, fontScale: Float = 1f, figures: TripSummary = summary) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                NexusTheme(AbeonaPalette, themeMode = mode) {
                    Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
                        PostcardCard(trip, figures)
                    }
                }
            }
        }
        // The picture to share is made while the card shows: "Share" works once it is ready.
        // Robolectric draws only when asked: a first capture draws it.
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/app/$name.png")
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasContentDescription("Share postcard") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/app/$name.png")
    }

    @Test fun postcard() = card("67a-postcard")

    @Test fun postcardDark() = card("67b-postcard-dark", ThemeMode.Dark)

    /** On time, no top speed known: four figures, the ending in ok. */
    @Test fun postcardOnTime() = card("67d-postcard-on-time", figures = summary.copy(delayMinutes = 2.0, topSpeedKmh = null))

    @Test
    @Config(qualifiers = "w412dp-h700dp-xxhdpi")
    fun postcardLargestText() = card("67e-postcard-font-200", fontScale = 2f)

    /** What "Share" sends: 1080 × 1350 (360 × 450 at three pixels a dp). */
    @Test
    @Config(qualifiers = "w360dp-h450dp-xxhdpi")
    fun sharedPicture() {
        compose.setContent {
            NexusTheme(AbeonaPalette, themeMode = ThemeMode.Light) { PostcardImage(trip, summary) }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/app/67c-postcard-shared.png")
    }
}

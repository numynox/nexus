package io.github.numynox.nexus.abeona.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.numynox.nexus.abeona.data.FareType
import io.github.numynox.nexus.abeona.data.Leg
import io.github.numynox.nexus.abeona.data.LegMode
import io.github.numynox.nexus.abeona.data.StopTime
import io.github.numynox.nexus.abeona.data.Stopover
import io.github.numynox.nexus.abeona.data.Trip
import io.github.numynox.nexus.abeona.domain.Position
import io.github.numynox.nexus.abeona.domain.TravelSignals
import io.github.numynox.nexus.abeona.domain.alternativesReason
import io.github.numynox.nexus.abeona.domain.travelDay
import io.github.numynox.nexus.abeona.live.OnboardStatus
import io.github.numynox.nexus.abeona.ui.AbeonaPalette
import io.github.numynox.nexus.abeona.ui.common.TravelDayHero
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
 * The travel-day hero in each of its states, on the trip to grandma's: the
 * clock moved through the day, and the phone's signals added where a state
 * needs them (the position for "missed", the ICE Portal on board).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ScreenshotApplication::class, qualifiers = "w412dp-h915dp-xxhdpi")
class TravelDayScreenshots {
    @get:Rule
    val compose = createComposeRule()

    private val t0 = Instant.now().truncatedTo(ChronoUnit.HOURS)
    private fun at(m: Long) = t0.plus(m, ChronoUnit.MINUTES)
    private fun iso(m: Long) = at(m).toString()

    private val frankfurt = StopTime("ffm", "Frankfurt(Main)Hbf", 50.1071, 8.6636)
    private val wuerzburg = StopTime("wue", "Würzburg Hbf", 49.8018, 9.9355)
    private val bamberg = StopTime("bam", "Bamberg", 49.9007, 10.8994)

    private fun trip(firstCancelled: Boolean = false) = Trip(
        "t", "h", "Visit grandma in Bamberg", "planned", frankfurt.name, bamberg.name, iso(0), iso(153), FareType.Saver, true,
        legs = listOf(
            Leg(
                mode = LegMode.Train, product = "ICE", lineName = "ICE 25", headsign = "Wien Hbf",
                from = frankfurt.copy(planned = iso(0), current = iso(7), plannedPlatform = "8"),
                to = wuerzburg.copy(planned = iso(68), current = iso(75), plannedPlatform = "4"),
                stopovers = listOf(Stopover("asch", "Aschaffenburg Hbf", 49.9805, 9.1436, iso(28), iso(35))),
                polyline = listOf(listOf(8.6636, 50.1071), listOf(9.1436, 49.9805), listOf(9.9355, 49.8018)),
                realtime = true, cancelled = firstCancelled,
            ),
            Leg(
                mode = LegMode.Train, product = "RE", lineName = "RE 10", headsign = "Bamberg",
                from = wuerzburg.copy(planned = iso(81), current = iso(81), plannedPlatform = "6", platform = "5"),
                to = bamberg.copy(planned = iso(153), current = iso(175), plannedPlatform = "3"),
                polyline = listOf(listOf(9.9355, 49.8018), listOf(10.8994, 49.9007)),
                realtime = true,
            ),
        ),
    )

    private fun shot(
        name: String,
        caption: String,
        nowMinutes: Long,
        signals: TravelSignals = TravelSignals(),
        onboard: OnboardStatus? = null,
        trip: Trip = trip(),
        mode: ThemeMode = ThemeMode.Light,
    ) {
        val now = at(nowMinutes)
        val day = travelDay(trip.legs, now, 15, signals)
        val reason = alternativesReason(trip.legs, day)
        compose.setContent {
            NexusTheme(AbeonaPalette, themeMode = mode) {
                Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
                    Text(caption, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                    TravelDayHero(trip, day, 15, Modifier.fillMaxWidth(), onboard = onboard, reason = reason, onAlternatives = {})
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/app/$name.png")
    }

    @Test fun beforeLeaving() = shot("50-hero-before-leaving", "Before leaving · 40 min before the ICE", -33)

    @Test fun atTheStation() = shot(
        "51-hero-at-the-station", "At the station · by the position", 1,
        signals = TravelSignals(position = Position(50.1068, 8.6630, 0.0)),
    )

    @Test fun onBoard() = shot("52-hero-on-board", "On board · the change ahead is tight", 40)

    @Test fun changing() = shot("53-hero-changing", "Changing at Würzburg", 77)

    @Test fun arrived() = shot("54-hero-arrived", "Arrived, 22 min late", 180)

    @Test fun missed() = shot(
        "55-hero-missed", "Still at Würzburg after the RE 10 left · by the position", 86,
        signals = TravelSignals(position = Position(49.8018, 9.9355, 0.0)),
    )

    @Test fun cancelled() = shot("56-hero-cancelled", "The ICE is cancelled", -5, trip = trip(firstCancelled = true))

    @Test fun icePortal() = shot(
        "57-hero-ice-portal", "On board · from the ICE Portal", 40,
        signals = TravelSignals(onboardLegIndex = 0),
        onboard = OnboardStatus("ICE Portal", "ICE", "25", 247.0, 49.9, 9.4, "Würzburg Hbf", at(75), "4", at(40)),
    )

    @Test fun onBoardDark() = shot("58-hero-on-board-dark", "On board · dark", 40, mode = ThemeMode.Dark)
}

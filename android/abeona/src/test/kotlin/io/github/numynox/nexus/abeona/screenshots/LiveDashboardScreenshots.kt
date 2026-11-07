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
import io.github.numynox.nexus.abeona.live.Fix
import io.github.numynox.nexus.abeona.live.OnboardStatus
import io.github.numynox.nexus.abeona.live.evidence
import io.github.numynox.nexus.abeona.ui.AbeonaPalette
import io.github.numynox.nexus.abeona.ui.live.LiveDashboard
import io.github.numynox.nexus.abeona.ui.live.liveView
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
 * The live dashboard through a travel day, on the trip to grandma's with
 * its stops: the clock moved through the day, and the phone's signals
 * added where a state needs them. The map is the schematic one here
 * (MapLibre does not run under Robolectric).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ScreenshotApplication::class, qualifiers = "w412dp-h1500dp-xhdpi")
class LiveDashboardScreenshots {
    @get:Rule
    val compose = createComposeRule()

    private val t0 = Instant.now().truncatedTo(ChronoUnit.HOURS)
    private fun at(m: Long) = t0.plus(m, ChronoUnit.MINUTES)
    private fun iso(m: Long) = at(m).toString()

    private val frankfurt = StopTime("ffm", "Frankfurt(Main)Hbf", 50.1071, 8.6636)
    private val wuerzburg = StopTime("wue", "Würzburg Hbf", 49.8018, 9.9355)
    private val bamberg = StopTime("bam", "Bamberg", 49.9007, 10.8994)

    private fun trip() = Trip(
        "t", "h", "Visit grandma in Bamberg", "planned", frankfurt.name, bamberg.name, iso(0), iso(153), FareType.Saver, true,
        refreshedAt = iso(39),
        legs = listOf(
            Leg(
                mode = LegMode.Train, product = "ICE", lineName = "ICE 25", headsign = "Wien Hbf",
                from = frankfurt.copy(planned = iso(0), current = iso(7), plannedPlatform = "8"),
                to = wuerzburg.copy(planned = iso(68), current = iso(75), plannedPlatform = "4"),
                stopovers = listOf(
                    Stopover("off", "Frankfurt-Süd", 50.0995, 8.6860, iso(5), iso(12), iso(6), iso(13)),
                    Stopover("han", "Hanau Hbf", 50.1213, 8.9298, iso(17), iso(24), iso(19), iso(26)),
                    Stopover("asch", "Aschaffenburg Hbf", 49.9805, 9.1436, iso(30), iso(37), iso(32), iso(39)),
                ),
                polyline = listOf(
                    listOf(8.6636, 50.1071), listOf(8.6860, 50.0995), listOf(8.9298, 50.1213), listOf(9.1436, 49.9805),
                    listOf(9.5000, 49.9400), listOf(9.9355, 49.8018),
                ),
                realtime = true,
            ),
            Leg(
                mode = LegMode.Train, product = "RE", lineName = "RE 10", headsign = "Bamberg",
                from = wuerzburg.copy(planned = iso(81), current = iso(81), plannedPlatform = "6", platform = "5"),
                to = bamberg.copy(planned = iso(153), current = iso(175), plannedPlatform = "3"),
                stopovers = listOf(
                    Stopover("kit", "Kitzingen", 49.7370, 10.1630, iso(96), iso(100), iso(97), iso(101)),
                    Stopover("sch", "Schweinfurt Hbf", 50.0350, 10.2120, iso(118), iso(128), iso(119), iso(129)),
                    Stopover("hass", "Haßfurt", 50.0330, 10.5130, iso(133), iso(147), iso(134), iso(148)),
                ),
                polyline = listOf(listOf(9.9355, 49.8018), listOf(10.1630, 49.7370), listOf(10.2120, 50.0350), listOf(10.5130, 50.0330), listOf(10.8994, 49.9007)),
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
        fix: Fix? = null,
        locationOn: Boolean = false,
        companion: Boolean = true,
        mode: ThemeMode = ThemeMode.Light,
    ) {
        val trip = trip()
        val now = at(nowMinutes)
        val day = travelDay(trip.legs, now, 15, signals)
        val view = liveView(
            trip, day, now, 15,
            evidence(trip, day, now, onboard, fix, locationOn, companion),
            onboard, signals.position, alternativesReason(trip.legs, day),
        )
        compose.setContent {
            NexusTheme(AbeonaPalette, themeMode = mode) {
                Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
                    Text(caption, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                    LiveDashboard(view, Modifier.fillMaxWidth(), onAlternatives = {})
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/app/$name.png")
    }

    @Test fun beforeLeaving() = shot("59a-live-before-leaving", "Before leaving · by the timetable", -25)

    @Test fun atTheStation() = shot(
        "59b-live-at-the-station", "At the station · by the position", 2,
        signals = TravelSignals(position = Position(50.1068, 8.6630, 0.0)),
        fix = Fix(Position(50.1068, 8.6630, 0.0), at(2)), locationOn = true,
    )

    @Test fun onBoardByTheTimetable() = shot("59c-live-on-board", "On board · by the timetable, location off", 20)

    @Test fun onBoardByLocation() = shot(
        "59d-live-on-board-location", "On board · by the position, on the line at 182 km/h", 46,
        signals = TravelSignals(position = Position(49.958, 9.33, 182.0)),
        fix = Fix(Position(49.958, 9.33, 182.0), at(46)), locationOn = true,
    )

    @Test fun onBoardIcePortal() = shot(
        "59e-live-ice-portal", "On board · from the ICE Portal", 46,
        signals = TravelSignals(onboardLegIndex = 0),
        onboard = OnboardStatus("ICE Portal", "ICE", "25", 247.0, 49.94, 9.50, "Würzburg Hbf", at(75), "4", at(46)),
    )

    @Test fun changing() = shot("59f-live-changing", "Changing at Würzburg", 77)

    @Test fun lastRide() = shot("59g-live-last-ride", "The last ride, standing at Schweinfurt", 125)

    @Test fun missed() = shot(
        "59h-live-missed", "Still at Würzburg after the RE 10 left · by the position", 86,
        signals = TravelSignals(position = Position(49.8018, 9.9355, 0.0)),
        fix = Fix(Position(49.8018, 9.9355, 0.0), at(86)), locationOn = true,
    )

    @Test fun arrived() = shot("59i-live-arrived", "Arrived, 22 min late", 180)

    @Test fun onBoardDark() = shot("59j-live-on-board-dark", "On board · dark", 46, mode = ThemeMode.Dark)
}

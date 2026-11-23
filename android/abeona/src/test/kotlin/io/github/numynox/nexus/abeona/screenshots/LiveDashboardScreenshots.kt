package io.github.numynox.nexus.abeona.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.numynox.nexus.abeona.data.Approach
import io.github.numynox.nexus.abeona.data.FareType
import io.github.numynox.nexus.abeona.data.Leg
import io.github.numynox.nexus.abeona.data.LegMode
import io.github.numynox.nexus.abeona.data.StopTime
import io.github.numynox.nexus.abeona.data.Stopover
import io.github.numynox.nexus.abeona.data.Trip
import io.github.numynox.nexus.abeona.domain.Position
import io.github.numynox.nexus.abeona.domain.TravelSignals
import io.github.numynox.nexus.abeona.domain.alternativesReason
import io.github.numynox.nexus.abeona.domain.finalArrivalDelay
import io.github.numynox.nexus.abeona.domain.followed
import io.github.numynox.nexus.abeona.domain.followsReplacement
import io.github.numynox.nexus.abeona.domain.hasImpossibleChange
import io.github.numynox.nexus.abeona.domain.missedShortConnection
import io.github.numynox.nexus.abeona.domain.trainBindingLifted
import io.github.numynox.nexus.abeona.domain.travelDay
import io.github.numynox.nexus.abeona.live.ExitSide
import io.github.numynox.nexus.abeona.live.Fix
import io.github.numynox.nexus.abeona.live.OnboardStatus
import io.github.numynox.nexus.abeona.live.evidence
import io.github.numynox.nexus.abeona.ui.AbeonaPalette
import io.github.numynox.nexus.abeona.ui.live.HowAbeonaKnows
import io.github.numynox.nexus.abeona.ui.common.TripFormat
import io.github.numynox.nexus.abeona.ui.live.LiveDashboard
import io.github.numynox.nexus.abeona.ui.live.LiveStatus
import io.github.numynox.nexus.abeona.ui.live.LiveStatusKind
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


    private fun trip() = grandmaTrip(t0)

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
        of: Trip? = null,
        /** Content descriptions to tap before the picture, in turn: folds on the line. */
        tap: List<String> = emptyList(),
        /** While waiting for the train: where it is on its way here. */
        approach: Approach? = null,
        /** The text size the phone asks for: 2 is Android's largest. */
        fontScale: Float = 1f,
    ) {
        // As the trip screen does it: the binding on the booked trip, the card on the trip as travelled.
        val booked = of ?: trip()
        val trip = booked.followed
        val now = at(nowMinutes)
        val day = travelDay(trip.legs, now, 15, signals)
        val view = liveView(
            trip, day, now, 15,
            evidence(trip, day, now, onboard, fix, locationOn, companion),
            onboard, signals.position, alternativesReason(trip.legs, day),
        )
        val dropped = booked.legs.any { !it.isWalk && it.dropped }
        val lifted = trainBindingLifted(
            booked.fareType, booked.trainBinding, finalArrivalDelay(booked.legs),
            hasImpossibleChange(booked.legs) || missedShortConnection(trip.legs, day) || dropped,
        )
        val status = LiveStatus(LiveStatusKind.Live, "Live", TripFormat.time(now.minus(2, ChronoUnit.MINUTES)))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                NexusTheme(AbeonaPalette, themeMode = mode) {
                    Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
                        Text(caption, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                        LiveDashboard(
                            view, Modifier.fillMaxWidth(), onAlternatives = {}, onUseLocation = {}, status = status, approach = approach, lifted = lifted,
                            onBackToPlan = if (booked.followsReplacement) ({}) else null,
                            showMap = fontScale == 1f,
                        )
                    }
                }
            }
        }
        tap.forEach { compose.onNodeWithContentDescription(it).performClick() }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/app/$name.png")
    }

    /** What the source button opens, drawn as the sheet shows it. */
    private fun sheet(name: String, nowMinutes: Long, signals: TravelSignals = TravelSignals(), onboard: OnboardStatus? = null, fix: Fix? = null, locationOn: Boolean = false) {
        val trip = trip()
        val now = at(nowMinutes)
        val day = travelDay(trip.legs, now, 15, signals)
        val evidence = evidence(trip, day, now, onboard, fix, locationOn, true)
        compose.setContent {
            NexusTheme(AbeonaPalette, themeMode = ThemeMode.Light) {
                Column(Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow).padding(top = 24.dp)) {
                    HowAbeonaKnows(evidence, day, onUseLocation = {}, modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp))
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/app/$name.png")
    }

    @Test fun howAbeonaKnowsWifi() = sheet(
        "59k-how-abeona-knows-wifi", 46,
        signals = TravelSignals(onboardLegIndex = 0),
        onboard = OnboardStatus("ICE Portal", "ICE", "25", 247.0, 49.94, 9.50, "Würzburg Hbf", at(75), "4", at(46)),
    )

    @Test fun howAbeonaKnowsTimetable() = sheet("59l-how-abeona-knows-timetable", 20)

    @Test fun beforeLeaving() = shot("59a-live-before-leaving", "Before leaving · by the timetable", -25)

    @Test fun atTheStation() = shot(
        "59b-live-at-the-station", "At the station · by the position", 2,
        signals = TravelSignals(position = Position(50.1068, 8.6630, 0.0, 10.0)),
        fix = Fix(Position(50.1068, 8.6630, 0.0, 10.0), at(2)), locationOn = true,
        approach = Approach("Hanau Hbf", "left", 1),
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

    @Test fun changing() = shot("59f-live-changing", "Changing at Würzburg", 77, approach = Approach("Karlstadt(Main)", "at", 2))

    @Test fun lastRide() = shot("59g-live-last-ride", "The last ride, standing at Schweinfurt", 128)

    /** Standing at the stop before the last: the list still names where to get off. */
    @Test fun standingBeforeTheLastStop() = shot("59m-live-standing-before-last", "Standing at Haßfurt, Bamberg next", 147)

    /** The RE 10 leaves Würzburg before the late ICE is in: no time at all, impossible. */
    @Test fun impossibleChange() {
        val base = grandmaTrip(t0)
        val re = base.legs[1]
        val trip = base.copy(legs = listOf(base.legs[0], re.copy(from = re.from.copy(current = iso(74)))))
        shot("59n-live-impossible-change", "On board · the RE 10 leaves before the ICE is in", 46, of = trip)
    }

    @Test fun missed() = shot(
        "59h-live-missed", "Still at Würzburg after the RE 10 left · by the position", 86,
        signals = TravelSignals(position = Position(49.8018, 9.9355, 0.0, 10.0)),
        fix = Fix(Position(49.8018, 9.9355, 0.0, 10.0), at(86)), locationOn = true,
    )

    @Test fun arrived() = shot("59i-live-arrived", "Arrived, 22 min late", 180)

    @Test fun onBoardDark() = shot("59j-live-on-board-dark", "On board · dark", 46, mode = ThemeMode.Dark)

    /** The ICE is cancelled while waiting for it: the step to board turns red, the rest is dimmed. */
    @Test fun cancelled() {
        val base = grandmaTrip(t0)
        val trip = base.copy(legs = listOf(base.legs[0].copy(cancelled = true), base.legs[1]))
        shot("59o-live-cancelled", "At the station · the ICE is cancelled", -5, of = trip)
    }

    /** The RE 10 fell out of the timetable while on the ICE: its row says so, in its place on the line. */
    @Test fun notRunning() {
        val base = grandmaTrip(t0)
        val trip = base.copy(legs = listOf(base.legs[0], base.legs[1].copy(cancelled = true, droppedAt = iso(-600))))
        shot("59p-live-not-running", "On board · the RE 10 is no longer in the timetable", 46, of = trip)
    }

    /** The RE 10 leaves three minutes after the late ICE is in, from another platform: at risk. */
    @Test fun changeAtRisk() {
        val base = grandmaTrip(t0)
        val re = base.legs[1]
        val trip = base.copy(legs = listOf(base.legs[0], re.copy(from = re.from.copy(current = iso(78)))))
        shot("59q-live-change-at-risk", "On board · the change is at risk", 46, of = trip)
    }

    @Test fun changingDark() = shot("59r-live-changing-dark", "Changing at Würzburg · dark", 77, mode = ThemeMode.Dark)

    @Test fun missedDark() = shot(
        "59s-live-missed-dark", "Still at Würzburg after the RE 10 left · dark", 86,
        signals = TravelSignals(position = Position(49.8018, 9.9355, 0.0, 10.0)),
        fix = Fix(Position(49.8018, 9.9355, 0.0, 10.0), at(86)), locationOn = true, mode = ThemeMode.Dark,
    )

    /** Seats reserved on both rides: where to sit under the Board step, and on the next train's row. */
    private fun seated(): Trip {
        val base = grandmaTrip(t0)
        return base.copy(legs = listOf(base.legs[0].copy(coach = "24", seats = "66"), base.legs[1].copy(coach = "7", seats = "45-48")))
    }

    @Test fun boardWithSeat() {
        shot(
            "59z1-live-board-seat", "At the station · coach and seat reserved", 2, of = seated(),
            approach = Approach("Hanau Hbf", "left", 1),
        )
        // The step, its countdown and its seat are one sentence to a screen reader.
        val time = TripFormat.time(at(7))
        compose.onNodeWithContentDescription(
            "Board ICE 25 to Wien Hbf at Frankfurt(Main)Hbf, platform 8, $time, 7 minutes late, in 5 minutes. Coach 24, seat 66.",
        ).assertExists()
        compose.onNodeWithContentDescription("RE 10 to Bamberg, ${TripFormat.time(at(81))}, platform 5. Coach 7, seats 45–48.").assertExists()
    }

    /** From the ICE Portal: why it is late, under the train; from Railnet-style data, the side to get out on. */
    @Test fun getOffWithExitSide() = shot(
        "59z2-live-get-off-exit", "On board · why it is late, and the doors on the left at Würzburg", 46, of = seated(),
        signals = TravelSignals(onboardLegIndex = 0),
        onboard = OnboardStatus(
            "ICE Portal", "ICE", "25", 247.0, 49.94, 9.50, "Würzburg Hbf", at(75), "4", at(46),
            delayReason = "Verspätung eines vorausfahrenden Zuges",
            exitSides = mapOf("Würzburg Hbf" to ExitSide.Left),
        ),
    )

    /** The ICE's stops unfolded under the band: those it has reached done, as the line under the train says. */
    @Test fun bandStopsOpen() {
        shot("59z5-live-band-stops-open", "On board, standing at Hanau · the ICE's stops unfolded", 25, tap = listOf("Show 3 stops of ICE 25"))
        // The fold says what it does, and to which train; each stop is one sentence.
        compose.onNodeWithContentDescription("Hide 3 stops of ICE 25").assertExists()
        compose.onNodeWithContentDescription("Passed Hanau Hbf, ${TripFormat.time(at(24))}, 7 minutes late").assertExists()
        compose.onNodeWithContentDescription("Aschaffenburg Hbf, ${TripFormat.time(at(37))}, 7 minutes late").assertExists()
    }

    @Test fun bandStopsOpenDark() = shot(
        "59z6-live-band-stops-open-dark", "On board · the ICE's stops unfolded · dark", 25, mode = ThemeMode.Dark, tap = listOf("Show 3 stops of ICE 25"),
    )

    /** The next train's stops unfolded under its row, on its stretch of the line. */
    @Test fun nextTrainStopsOpen() = shot(
        "59z7-live-next-train-stops-open", "On board · the RE 10's stops unfolded", 46,
        signals = TravelSignals(onboardLegIndex = 0),
        onboard = OnboardStatus("ICE Portal", "ICE", "25", 247.0, 49.94, 9.50, "Würzburg Hbf", at(75), "4", at(46)),
        tap = listOf("Show 3 stops of RE 10"),
    )

    /** A later ride unfolded, then its stops. */
    @Test fun laterStopsOpen() = shot(
        "59z8-live-later-stops-open", "Before leaving · a later ride's stops unfolded", -20, of = louvainTrip(t0),
        tap = listOf("Show the later rides", "Show 2 stops of ICE 16"),
    )

    /** Android's largest text: nothing is cut off, the rows grow instead. */
    @Test
    @Config(qualifiers = "w412dp-h2600dp-xhdpi")
    fun largestText() = shot("59z3-live-font-200", "At the station · text at 200 %", 2, of = seated(), fontScale = 2f)

    @Test
    @Config(qualifiers = "w412dp-h2600dp-xhdpi")
    fun largestTextOnBoard() = shot(
        "59z4-live-font-200-on-board", "On board · text at 200 %", 46, of = seated(), fontScale = 2f,
        signals = TravelSignals(onboardLegIndex = 0),
        onboard = OnboardStatus("ICE Portal", "ICE", "25", 247.0, 49.94, 9.50, "Würzburg Hbf", at(75), "4", at(46), exitSides = mapOf("Würzburg Hbf" to ExitSide.Left)),
    )

    /** Four rides: the third and fourth fold into "Later". */
    @Test fun longBeforeLeaving() = shot("59u-live-long-before-leaving", "Before leaving · four rides", -20, of = louvainTrip(t0))

    @Test fun longBeforeLeavingDark() = shot("59y-live-long-before-leaving-dark", "Before leaving · four rides · dark", -20, of = louvainTrip(t0), mode = ThemeMode.Dark)

    @Test fun longLaterOpen() = shot("59x-live-later-open", "Before leaving · the later rides unfolded", -20, of = louvainTrip(t0), tap = listOf("Show the later rides"))

    private fun portal(now: Long) = OnboardStatus("ICE Portal", "ICE", "16", 197.0, 50.80, 6.40, "Aachen Hbf", at(356), "2", at(now))

    /** On the third ride, from its Wi-Fi: two rides done, the stop passed and the next under the train. */
    @Test fun longOnBoard() = shot(
        "59v-live-long-on-board", "On board the ICE 16 · from the ICE Portal", 330, of = louvainTrip(t0),
        signals = TravelSignals(onboardLegIndex = 2), onboard = portal(330),
    )

    @Test fun longDoneOpen() = shot(
        "59w-live-done-open", "On board the ICE 16 · what is done, unfolded", 330, of = louvainTrip(t0),
        signals = TravelSignals(onboardLegIndex = 2), onboard = portal(330), tap = listOf("Show what is done"),
    )

    /** The IC 535 replaced by an IC 3 from the alternatives: "re-planned", and the way back to the plan. */
    @Test fun rePlanned() {
        val base = louvainTrip(t0)
        val liege = base.legs[3].from
        val leuven = base.legs[3].to
        val ic3 = Leg(
            mode = LegMode.Train, product = "IC", lineName = "IC 3", headsign = "Knokke",
            from = liege.copy(planned = iso(392), current = iso(392), plannedPlatform = "6"),
            to = leuven.copy(planned = iso(423), current = iso(423), plannedPlatform = "2"),
            polyline = listOf(listOf(5.5667, 50.6245), listOf(4.7155, 50.8810)),
            realtime = true,
        )
        shot(
            "59t-live-re-planned", "On board · another connection followed from Liège", 330,
            of = base.copy(missedLegIdx = 3, replacementLegs = listOf(ic3)),
            signals = TravelSignals(onboardLegIndex = 2), onboard = portal(330),
        )
    }
}

/**
 * Erlangen to Leuven in four rides, starting at [t0]: an RE, two ICEs (the
 * second 4 min late into Liège) and an IC. Long enough for the done row and
 * the later rides to fold.
 */
internal fun louvainTrip(t0: Instant): Trip {
    fun iso(m: Long) = t0.plus(m, ChronoUnit.MINUTES).toString()
    val erlangen = StopTime("erl", "Erlangen", 49.5958, 11.0019)
    val nuernberg = StopTime("nbg", "Nürnberg Hbf", 49.4456, 11.0827)
    val airport = StopTime("fra", "Frankfurt(M) Flughafen Fernbf", 50.0532, 8.5703)
    val liege = StopTime("lg", "Liège-Guillemins", 50.6245, 5.5667)
    val leuven = StopTime("lv", "Leuven", 50.8810, 4.7155)
    return Trip(
        "l", "h", null, "planned", erlangen.name, leuven.name, iso(0), iso(427), FareType.Saver, true,
        refreshedAt = iso(-2),
        legs = listOf(
            Leg(
                mode = LegMode.Train, product = "RE", lineName = "RE 19", headsign = "Nürnberg Hbf",
                from = erlangen.copy(planned = iso(0), current = iso(0), plannedPlatform = "4"),
                to = nuernberg.copy(planned = iso(19), current = iso(21), plannedPlatform = "4"),
                stopovers = listOf(Stopover("fue", "Fürth(Bay)Hbf", 49.4699, 10.9908, iso(12), iso(13), iso(13), iso(14))),
                polyline = listOf(listOf(11.0019, 49.5958), listOf(10.9908, 49.4699), listOf(11.0827, 49.4456)),
                realtime = true,
            ),
            Leg(
                mode = LegMode.Train, product = "ICE", lineName = "ICE 728", headsign = "Frankfurt(M) Flughafen",
                from = nuernberg.copy(planned = iso(44), current = iso(44), plannedPlatform = "7"),
                to = airport.copy(planned = iso(186), current = iso(186), plannedPlatform = "5"),
                stopovers = listOf(Stopover("wue", "Würzburg Hbf", 49.8018, 9.9355, iso(100), iso(100), iso(102), iso(102))),
                polyline = listOf(listOf(11.0827, 49.4456), listOf(9.9355, 49.8018), listOf(8.5703, 50.0532)),
                realtime = true,
            ),
            Leg(
                mode = LegMode.Train, product = "ICE", lineName = "ICE 16", headsign = "Bruxelles-Midi",
                from = airport.copy(planned = iso(263), current = iso(263), plannedPlatform = "6"),
                to = liege.copy(planned = iso(378), current = iso(382), plannedPlatform = "3"),
                stopovers = listOf(
                    Stopover("koe", "Köln Hbf", 50.9430, 6.9589, iso(316), iso(320), iso(319), iso(323)),
                    Stopover("ac", "Aachen Hbf", 50.7678, 6.0911, iso(352), iso(356), iso(355), iso(359)),
                ),
                polyline = listOf(listOf(8.5703, 50.0532), listOf(6.9589, 50.9430), listOf(6.0911, 50.7678), listOf(5.5667, 50.6245)),
                realtime = true,
            ),
            Leg(
                mode = LegMode.Train, product = "IC", lineName = "IC 535", headsign = "Oostende",
                from = liege.copy(planned = iso(397), current = iso(397), plannedPlatform = "3"),
                to = leuven.copy(planned = iso(427), current = iso(427), plannedPlatform = "1"),
                polyline = listOf(listOf(5.5667, 50.6245), listOf(4.7155, 50.8810)),
                realtime = true,
            ),
        ),
    )
}

/**
 * The trip to grandma's with every stop, starting at [t0]: an ICE 7 min late
 * to Würzburg, a tight change, and an RE 10 that ends 22 min late. Shared by
 * the dashboard's and the notifications' screenshots.
 */
internal fun grandmaTrip(t0: Instant): Trip {
    fun iso(m: Long) = t0.plus(m, ChronoUnit.MINUTES).toString()
    val frankfurt = StopTime("ffm", "Frankfurt(Main)Hbf", 50.1071, 8.6636)
    val wuerzburg = StopTime("wue", "Würzburg Hbf", 49.8018, 9.9355)
    val bamberg = StopTime("bam", "Bamberg", 49.9007, 10.8994)
    return Trip(
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
}

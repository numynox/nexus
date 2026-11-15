package io.github.numynox.nexus.abeona.ui.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.LinearScale
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.numynox.nexus.abeona.data.Approach
import io.github.numynox.nexus.abeona.data.Leg
import io.github.numynox.nexus.abeona.data.LegMode
import io.github.numynox.nexus.abeona.data.StopTime
import io.github.numynox.nexus.abeona.data.Trip
import io.github.numynox.nexus.abeona.data.toInstantOrNull
import io.github.numynox.nexus.abeona.domain.AlternativesReason
import io.github.numynox.nexus.abeona.domain.Position
import io.github.numynox.nexus.abeona.domain.RideProgress
import io.github.numynox.nexus.abeona.domain.Transfer
import io.github.numynox.nexus.abeona.domain.TravelDay
import io.github.numynox.nexus.abeona.domain.TravelDayKind
import io.github.numynox.nexus.abeona.domain.TravelDaySource
import io.github.numynox.nexus.abeona.domain.changes
import io.github.numynox.nexus.abeona.domain.finalArrivalDelay
import io.github.numynox.nexus.abeona.domain.rideProgress
import io.github.numynox.nexus.abeona.domain.trainBindingLifted
import io.github.numynox.nexus.abeona.domain.transfers
import io.github.numynox.nexus.abeona.domain.worstConfidence
import io.github.numynox.nexus.abeona.live.Evidence
import io.github.numynox.nexus.abeona.live.EvidenceKind
import io.github.numynox.nexus.abeona.live.EvidenceState
import io.github.numynox.nexus.abeona.live.OnboardStatus
import io.github.numynox.nexus.abeona.ui.AbeonaType
import io.github.numynox.nexus.abeona.ui.bad
import io.github.numynox.nexus.abeona.ui.brand
import io.github.numynox.nexus.abeona.ui.brandSoft
import io.github.numynox.nexus.abeona.ui.common.ConfidenceChip
import io.github.numynox.nexus.abeona.ui.common.Fact
import io.github.numynox.nexus.abeona.ui.common.LineChip
import io.github.numynox.nexus.abeona.ui.common.MarkIcon
import io.github.numynox.nexus.abeona.ui.common.PlatformBox
import io.github.numynox.nexus.abeona.ui.common.ShiftedTime
import io.github.numynox.nexus.abeona.ui.common.StatusChip
import io.github.numynox.nexus.abeona.ui.common.StatusTone
import io.github.numynox.nexus.abeona.ui.common.StopRow
import io.github.numynox.nexus.abeona.ui.common.TripFormat
import io.github.numynox.nexus.abeona.ui.hairline
import io.github.numynox.nexus.abeona.ui.muted
import io.github.numynox.nexus.abeona.ui.burgundy
import io.github.numynox.nexus.abeona.ui.scene.Scene
import io.github.numynox.nexus.abeona.ui.scene.SceneModel
import io.github.numynox.nexus.abeona.ui.scene.SceneSize
import io.github.numynox.nexus.abeona.ui.scene.SceneState
import io.github.numynox.nexus.abeona.ui.scene.SceneStop
import io.github.numynox.nexus.abeona.ui.scene.TrainKind
import io.github.numynox.nexus.abeona.ui.trip.MapMarker
import io.github.numynox.nexus.abeona.ui.trip.TripMap
import io.github.numynox.nexus.abeona.ui.well
import java.time.Instant
import kotlin.math.roundToInt

/** Everything the dashboard shows, worked out once from the trip, the clock and the signals. */
data class LiveView(
    val trip: Trip,
    val day: TravelDay,
    val now: Instant,
    val stationMinutes: Int,
    /** The ride the day is about: on board, or the one to catch. */
    val leg: Leg?,
    /** That ride's stops and where on it the train is. */
    val progress: RideProgress?,
    /** The next change from here: after the ride on board, or the one being made. */
    val change: Transfer?,
    val evidence: List<Evidence>,
    val marker: MapMarker?,
    val onboard: OnboardStatus?,
    val reason: AlternativesReason?,
)

/**
 * The live view of a travel day, built from the trip and what this phone
 * knows. The marker is the strongest position there is: the train's Wi-Fi,
 * then the phone's fix, then where the timetable puts the train.
 */
fun liveView(
    trip: Trip,
    day: TravelDay,
    now: Instant,
    stationMinutes: Int,
    evidence: List<Evidence>,
    onboard: OnboardStatus?,
    fix: Position?,
    reason: AlternativesReason?,
): LiveView {
    val leg = day.legIndex?.let { trip.legs.getOrNull(it) }
    val progress = leg?.let { rideProgress(it, now) }
    val change = day.legIndex?.let { index ->
        transfers(trip.legs).firstOrNull { if (day.kind == TravelDayKind.OnBoard) it.arriving == index else it.departing == index }
    }
    val portal = onboard?.takeIf { day.source == TravelDaySource.Onboard }
    val marker = when {
        portal?.lat != null && portal.lon != null -> MapMarker(portal.lat, portal.lon, EvidenceKind.Wifi)
        fix != null -> MapMarker(fix.lat, fix.lon, EvidenceKind.Location)
        day.kind == TravelDayKind.OnBoard -> progress?.estimate?.let { MapMarker(it.lat, it.lon, EvidenceKind.Timetable) }
        else -> null
    }
    return LiveView(trip, day, now, stationMinutes, leg, progress, change, evidence, marker, onboard, reason)
}

/**
 * The live companion's dashboard (glossary: Live companion, travel day): one
 * live card, then the map with the traveller on it. Used as a whole by the
 * screenshot tests; the trip screen puts the card and the map in its list.
 */
@Composable
fun LiveDashboard(
    view: LiveView,
    modifier: Modifier = Modifier,
    onAlternatives: (() -> Unit)? = null,
    showMap: Boolean = true,
    onUseLocation: (() -> Unit)? = null,
    status: LiveStatus? = null,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LiveCard(view, status = status, onAlternatives = onAlternatives, onUseLocation = onUseLocation)
        if (showMap) LiveMap(view)
    }
}

/** The map under the live card, with the traveller on it. Nothing once arrived. */
@Composable
fun LiveMap(view: LiveView, modifier: Modifier = Modifier) {
    if (view.day.kind == TravelDayKind.Arrived) return
    TripMap(
        view.trip.legs,
        modifier.fillMaxWidth().height(240.dp),
        marker = view.marker,
        focus = view.leg?.takeIf { view.day.kind == TravelDayKind.OnBoard },
    )
}

/** What kind of freshness the card's last line states: its icon. */
enum class LiveStatusKind { Live, Timetable, Final, Offline, Unavailable }

internal fun liveStatusIcon(kind: LiveStatusKind): ImageVector = when (kind) {
    LiveStatusKind.Live -> Icons.Rounded.Sync
    LiveStatusKind.Timetable -> Icons.Rounded.Schedule
    LiveStatusKind.Final -> Icons.Rounded.CheckCircle
    LiveStatusKind.Offline, LiveStatusKind.Unavailable -> Icons.Rounded.CloudOff
}

/**
 * How fresh the live values are, for the card's last line: an icon and a
 * time. [label] is what the icon stands for ("Live · updated"), read out
 * before [text] ("17:44"); [text] alone where there is no time to show.
 */
data class LiveStatus(val kind: LiveStatusKind, val label: String, val text: String)

// ---------------------------------------------------------------- the card

private fun eyebrow(view: LiveView): String {
    val day = view.day
    val leg = view.leg
    return when {
        day.missedChange -> "Looks like you missed it"
        day.cancelled -> "Change of plan"
        else -> when (day.kind) {
            TravelDayKind.BeforeLeaving -> "Time to leave"
            TravelDayKind.AtStation -> if (day.source == TravelDaySource.Location) "You're at the station" else "Head to the platform"
            // "to" and the train's own destination, as the departure board says it.
            TravelDayKind.OnBoard -> "On board" + (leg?.let { " · ${it.label}" + (it.headsign?.let { h -> " to $h" } ?: "") } ?: "")
            TravelDayKind.Changing -> "Change trains" + (leg?.let { " · ${it.from.name}" } ?: "")
            TravelDayKind.Arrived -> "Arrived"
        }
    }
}

/** "Frankfurt(Main)Hbf" → "Frankfurt": a station's town, for matching the train's own name of a stop. */
private fun short(name: String): String = name.substringBefore('(').substringBefore(" Hbf").substringBefore(" Hauptbahnhof").trim()

private fun stateIcon(day: TravelDay): ImageVector = when {
    day.missedChange || day.cancelled -> Icons.Rounded.ErrorOutline
    else -> when (day.kind) {
        TravelDayKind.BeforeLeaving -> Icons.Rounded.MeetingRoom
        TravelDayKind.AtStation -> Icons.Rounded.Place
        TravelDayKind.OnBoard -> Icons.Rounded.Train
        TravelDayKind.Changing -> Icons.Rounded.SwapHoriz
        TravelDayKind.Arrived -> Icons.Rounded.Flag
    }
}

internal fun evidenceIcon(kind: EvidenceKind): ImageVector = when (kind) {
    EvidenceKind.Wifi -> Icons.Rounded.Wifi
    EvidenceKind.Location -> Icons.Rounded.MyLocation
    EvidenceKind.Timetable -> Icons.Rounded.Schedule
}

private fun sourceKind(source: TravelDaySource) = when (source) {
    TravelDaySource.Onboard -> EvidenceKind.Wifi
    TravelDaySource.Location -> EvidenceKind.Location
    TravelDaySource.Time -> EvidenceKind.Timetable
}

private fun sourceShort(source: TravelDaySource) = when (source) {
    TravelDaySource.Onboard -> "Train Wi-Fi"
    TravelDaySource.Location -> "Location"
    TravelDaySource.Time -> "Timetable"
}

/** The big number and the line under it: "23" "min", "16 h" "54 min" (never "16:54", which reads as a clock), "now". */
internal fun countdown(minutes: Int?): Pair<String, String>? {
    val m = minutes ?: return null
    return when {
        m <= 0 -> "now" to ""
        m < 60 -> "$m" to "min"
        m % 60 == 0 -> "${m / 60} h" to ""
        else -> "${m / 60} h" to "${m % 60} min"
    }
}

/** What the card leads with: a name, the time that goes with it, what to do there, and the countdown. */
private data class Lead(
    val headline: String,
    /** The stop whose time (with its delay) is the one to know; null when there is none. */
    val stop: StopTime? = null,
    val icon: ImageVector? = null,
    val iconDescription: String? = null,
    /** The platform beside the time: on board, where I get off. */
    val platformAt: StopTime? = null,
    /** The platform as the headline itself: waiting for the train. */
    val platformHead: StopTime? = null,
    /** The ride to catch, as a chip with its direction. */
    val line: Leg? = null,
    val note: String? = null,
    val minutes: Int? = null,
    /** What broke, in a word: the headline and its time are struck through. */
    val broken: String? = null,
    val lifted: Boolean = false,
)

/** "Train left Aschaffenburg Hbf · 3 stops away": read out; on the card the stops are an icon and a number. */
internal fun approachText(a: Approach): String =
    "${if (a.state == "at") "Train is at" else "Train left"} ${a.stop} · ${a.stopsAway} ${if (a.stopsAway == 1) "stop" else "stops"} away"

/**
 * While waiting for the train to catch (at the station or changing): where it
 * is on its way here, asked every minute through [fetch]. Null otherwise, and
 * whenever it is not known.
 */
@Composable
fun rememberApproach(leg: Leg?, day: TravelDay?, fetch: suspend (Leg) -> Approach?): Approach? {
    val waiting = leg?.takeIf {
        day != null && !day.missedChange && !day.cancelled && !it.cancelled && it.tripRef != null &&
            (day.kind == TravelDayKind.AtStation || day.kind == TravelDayKind.Changing)
    }
    val key = waiting?.let { "${it.tripRef}|${it.from.id ?: it.from.name}" }
    var approach by remember(key) { mutableStateOf<Approach?>(null) }
    val current by rememberUpdatedState(waiting)
    val load by rememberUpdatedState(fetch)
    LaunchedEffect(key) {
        if (key == null) return@LaunchedEffect
        while (true) {
            current?.let { target ->
                approach = try {
                    load(target)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            }
            kotlinx.coroutines.delay(60_000)
        }
    }
    return approach
}

private fun lead(view: LiveView): Lead {
    val day = view.day
    val trip = view.trip
    val leg = view.leg
    if (day.kind == TravelDayKind.Arrived || leg == null) {
        val last = trip.legs.lastOrNull { !it.isWalk }
        return Lead(trip.destinationName, stop = last?.to, icon = Icons.Rounded.Flag, iconDescription = "Arrived")
    }
    val train = leg.label + (leg.headsign?.let { " to $it" } ?: "")
    if (day.missedChange && day.kind != TravelDayKind.OnBoard) {
        return Lead(train, stop = leg.from, note = "Your ticket may allow another train.", broken = "Missed")
    }
    if (leg.cancelled) {
        // The broken step struck through, with the train it was; whether the ticket now allows others.
        return Lead(
            train,
            stop = leg.from,
            broken = if (leg.dropped) "Not in the timetable" else "Cancelled",
            lifted = trainBindingLifted(trip.fareType, trip.trainBinding, finalArrivalDelay(trip.legs), connectionBroken = true),
        )
    }
    return when (day.kind) {
        // The time to go is the headline; the train to catch is the first row under the hairline.
        TravelDayKind.BeforeLeaving -> Lead(
            "Leave ${TripFormat.time(day.leaveAt)}",
            note = "${view.stationMinutes} min to ${leg.from.name}",
            minutes = day.minutesToLeave,
        )
        TravelDayKind.AtStation, TravelDayKind.Changing -> Lead(
            if (leg.from.shownPlatform != null) "Platform" else leg.label,
            stop = leg.from,
            platformHead = leg.from.takeIf { it.shownPlatform != null },
            line = leg,
            minutes = day.minutesToDeparture,
        )
        else -> Lead(
            leg.to.name,
            stop = leg.to,
            icon = if (view.change != null) Icons.Rounded.SwapHoriz else Icons.Rounded.Flag,
            iconDescription = if (view.change != null) "Change" else "Get off",
            platformAt = leg.to,
            minutes = day.minutesToArrival,
        )
    }
}

/** The train a ride is drawn as in the scene. */
internal fun sceneTrain(leg: Leg?): TrainKind =
    leg?.let { SceneModel.trainKind(it.product, it.label, it.mode == LegMode.Bus) } ?: TrainKind.Ice

/**
 * The live card (abeona-design.md §8): everything live on the trip screen in
 * one place, most urgent first. The scene strip, whose track is the ride's
 * rail; what happens now with its time, delay and countdown; then the steps
 * still ahead as rows in the row grid; last how fresh it all is, and the
 * train's speed when its Wi-Fi says. The source pill opens how Abeona knows.
 * A broken step turns the card bad soft.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
fun LiveCard(
    view: LiveView,
    modifier: Modifier = Modifier,
    status: LiveStatus? = null,
    onAlternatives: (() -> Unit)? = null,
    onUseLocation: (() -> Unit)? = null,
    /** What else is live about the trip (the binding lifted, a connection taken instead), inside the card. */
    notices: (@Composable () -> Unit)? = null,
    /**
     * The Trips screen's next-trip card on the travel day: the same strip,
     * state line, headline and time line, then one row to the destination
     * instead of the steps ahead; no source, no notices, no status.
     */
    summary: Boolean = false,
    /** While waiting for the train: where it is on its way here (rememberApproach). */
    approach: Approach? = null,
    onClick: (() -> Unit)? = null,
) {
    var showSource by rememberSaveable { mutableStateOf(false) }
    if (showSource) {
        ModalBottomSheet(onDismissRequest = { showSource = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            HowAbeonaKnows(view.evidence, view.day, onUseLocation, Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp))
        }
    }
    val day = view.day
    val leg = view.leg
    val trip = view.trip
    val warn = day.missedChange || day.cancelled
    val arrived = day.kind == TravelDayKind.Arrived
    val onBoard = day.kind == TravelDayKind.OnBoard
    val progress = view.progress
    val lastRide = trip.legs.lastOrNull { !it.isWalk }
    val showRide = leg != null && progress != null && !leg.cancelled && !(day.missedChange && !onBoard) &&
        (onBoard || day.kind == TravelDayKind.AtStation || day.kind == TravelDayKind.Changing)
    val tone = if (warn) bad.soft else brandSoft
    val accent = if (warn) bad.soft.content else brand
    // Where the sky is reckoned: the phone or the train, else the stop the step is about.
    val skyAt = view.marker?.let { it.lat to it.lon }
        ?: (if (onBoard) leg?.to else leg?.from ?: lastRide?.to)?.let { stop -> stop.lat?.let { lat -> stop.lon?.let { lat to it } } }
    val speed = view.onboard?.speedKmh?.takeIf { it >= 1 && onBoard }

    val content: @Composable () -> Unit = {
        Column {
            // The strip is the ride's rail: a dot per stop, the train where the ride is.
            Scene(
                state = when {
                    arrived -> SceneState.Arrived
                    warn -> SceneState.Stopped
                    onBoard -> SceneState.Riding
                    else -> SceneState.Waiting
                },
                size = if (arrived) SceneSize.Header else SceneSize.Strip,
                train = sceneTrain(leg ?: lastRide),
                stops = if (showRide) progress!!.stops.map { SceneStop(it.fraction, it.name) } else null,
                fraction = if (onBoard && progress != null) progress.fraction else 0.0,
                speedKmh = speed,
                lat = skyAt?.first,
                lon = skyAt?.second,
            )
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // What state, and how Abeona knows.
                Row(Modifier.heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val disc = if (warn) bad.fill else burgundy
                    Surface(color = disc.container, contentColor = disc.content, shape = CircleShape, modifier = Modifier.size(24.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(stateIcon(day), contentDescription = null, modifier = Modifier.size(14.dp)) }
                    }
                    Text(eyebrow(view).uppercase(), style = AbeonaType.label, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (!arrived && !summary) SourcePill(day.source) { showSource = true }
                }

                // The one thing to know now.
                val lead = lead(view)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                lead.headline,
                                style = AbeonaType.headline.copy(textDecoration = if (lead.broken != null) TextDecoration.LineThrough else null),
                                color = if (lead.broken != null) muted else MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            lead.platformHead?.let { PlatformBox(it.plannedPlatform, it.platform, lead = true) }
                        }
                        if (lead.stop != null || lead.line != null || lead.broken != null) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                itemVerticalAlignment = Alignment.CenterVertically,
                            ) {
                                lead.stop?.let { ShiftedTime(it.plannedAt, it.expectedAt, AbeonaType.time, cancelled = lead.broken != null) }
                                lead.icon?.let { Icon(it, contentDescription = lead.iconDescription, tint = muted, modifier = Modifier.size(20.dp)) }
                                lead.platformAt?.let { PlatformBox(it.plannedPlatform, it.platform, lead = true) }
                                lead.line?.let { line ->
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        LineChip(line)
                                        line.headsign?.let { Text("to $it", style = AbeonaType.meta, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                    }
                                }
                                lead.broken?.let { StatusChip(StatusTone.Bad, if (it == "Missed") Icons.Rounded.ErrorOutline else Icons.Rounded.Block, label = it) }
                                if (lead.lifted) StatusChip(StatusTone.Watch, Icons.Rounded.SwapHoriz, label = "Train binding lifted")
                            }
                        }
                        lead.note?.let { note ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (day.kind == TravelDayKind.BeforeLeaving && lead.broken == null) {
                                    Icon(Icons.AutoMirrored.Rounded.DirectionsWalk, contentDescription = null, tint = muted, modifier = Modifier.size(16.dp))
                                }
                                Text(note, style = AbeonaType.meta, color = muted)
                            }
                        }
                        // Where the train to board is on its way here.
                        if (approach != null && !warn && lead.line != null) {
                            Row(
                                Modifier.clearAndSetSemantics { contentDescription = approachText(approach) },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(Icons.Rounded.Train, contentDescription = null, tint = muted, modifier = Modifier.size(16.dp))
                                Text(
                                    "${if (approach.state == "at") "at" else "left"} ${approach.stop}",
                                    style = AbeonaType.meta, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                                )
                                Icon(Icons.Rounded.LinearScale, contentDescription = null, tint = muted, modifier = Modifier.size(16.dp))
                                Text("${approach.stopsAway}", style = AbeonaType.meta, color = muted)
                            }
                        }
                    }
                    countdown(lead.minutes)?.let { (number, unit) ->
                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.clearAndSetSemantics { contentDescription = "$number $unit".trim() }) {
                            Text(number, style = AbeonaType.numeral, color = accent, maxLines = 1)
                            if (unit.isNotEmpty()) Text(unit, style = AbeonaType.meta, color = muted, maxLines = 1)
                        }
                    }
                }

                val ahead = leg != null && !warn && !arrived
                if (summary) {
                    // The Trips card's one row below the train: the arrival, the changes, how they look.
                    if (ahead && lastRide != null && !(onBoard && leg === lastRide)) {
                        Steps {
                            val count = changes(trip.legs)
                            val worst = worstConfidence(trip.legs)
                            StopRow(
                                // With a title of its own, the card above does not name the destination.
                                name = if (trip.title.isNullOrBlank()) "" else trip.destinationName,
                                planned = lastRide.to.plannedAt, expected = lastRide.to.expectedAt, quiet = true,
                                mark = { MarkIcon(Icons.Rounded.Flag, "Destination") },
                                trailing = {
                                    Fact(Icons.Rounded.SwapHoriz, "Changes", "$count")
                                    if (worst != null) ConfidenceChip(worst)
                                },
                            )
                        }
                    }
                } else if (ahead) {
                    StepsAhead(view, showRide)
                }

                if (day.missedChange && onBoard) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = bad.soft.content, modifier = Modifier.size(16.dp))
                        Text("You are still on this train after your connection left.", style = AbeonaType.meta, color = bad.soft.content)
                    }
                }
                // Filled only when something broke; a change merely at risk gets a quiet button, so
                // nothing looks wrong that is not (and none on the Trips card).
                val broken = view.reason != null && view.reason != AlternativesReason.AtRisk
                if (view.reason != null && onAlternatives != null && (broken || !summary)) {
                    if (broken) {
                        Button(onClick = onAlternatives, colors = ButtonDefaults.buttonColors(containerColor = bad.fill.container, contentColor = bad.fill.content)) {
                            Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("See alternatives", style = AbeonaType.body.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(start = 8.dp))
                        }
                    } else {
                        OutlinedButton(onClick = onAlternatives) {
                            Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Alternatives", style = AbeonaType.body.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }

                if (!summary) {
                    notices?.invoke()

                    // How fresh, and the train's speed when its Wi-Fi says: small, last.
                    if (status != null || speed != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            status?.let {
                                Fact(liveStatusIcon(it.kind), it.label, it.text)
                            }
                            Spacer(Modifier.weight(1f))
                            speed?.let { Fact(Icons.Rounded.Speed, "Speed", "${it.roundToInt()} km/h") }
                        }
                    }
                }
            }
        }
    }
    val shape = RoundedCornerShape(24.dp)
    if (onClick != null) {
        Surface(onClick = onClick, color = tone.container, contentColor = MaterialTheme.colorScheme.onSurface, shape = shape, modifier = modifier.fillMaxWidth(), content = content)
    } else {
        Surface(color = tone.container, contentColor = MaterialTheme.colorScheme.onSurface, shape = shape, modifier = modifier.fillMaxWidth(), content = content)
    }
}

/** What placed the traveller, as an icon to tap: it opens how Abeona knows. */
@Composable
private fun SourcePill(source: TravelDaySource, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.7f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = CircleShape,
        modifier = Modifier.semantics { contentDescription = "How Abeona knows: ${sourceShort(source)}" },
    ) {
        Row(Modifier.height(28.dp).padding(start = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Icon(evidenceIcon(sourceKind(source)), contentDescription = null, modifier = Modifier.size(16.dp))
            Icon(Icons.Rounded.ExpandMore, contentDescription = null, modifier = Modifier.size(14.dp), tint = muted)
        }
    }
}

/** The rows under the hairline: rows in the row grid, in travel order, without a gap between them. */
@Composable
private fun Steps(content: @Composable () -> Unit) {
    Column {
        HorizontalDivider(color = hairline)
        Spacer(Modifier.height(4.dp))
        content()
    }
}

/** A train as a row: its departure, line, "to" its own destination, and platform. */
@Composable
private fun TrainRow(leg: Leg) {
    StopRow(
        name = "",
        planned = leg.from.plannedAt, expected = leg.from.expectedAt,
        platformPlanned = leg.from.plannedPlatform, platformCurrent = leg.from.platform,
        mark = { MarkIcon(Icons.Rounded.Train, "Train") },
        content = {
            LineChip(leg)
            leg.headsign?.let { Text("to $it", style = AbeonaType.body, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        },
    )
}

/** A change as a row: its margin, then the walk or the platform left behind; the platform to reach is the row's own. */
@Composable
private fun ChangeRow(change: Transfer, legs: List<Leg>) {
    val from = legs[change.arriving].to
    val to = legs[change.departing].from
    val walk = change.walkMinutes > 0
    StopRow(
        name = "",
        platformPlanned = if (walk) null else to.plannedPlatform, platformCurrent = if (walk) null else to.platform,
        mark = { MarkIcon(Icons.Rounded.SwapHoriz, "Change") },
        content = { ConfidenceChip(change.confidence, minutes = change.availableMinutes) },
        trailing = {
            if (walk) {
                Fact(Icons.AutoMirrored.Rounded.DirectionsWalk, "Walk", "${change.walkMinutes} min")
            } else if (from.shownPlatform != null && to.shownPlatform != null) {
                PlatformBox(from.plannedPlatform, from.platform)
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "to", tint = muted, modifier = Modifier.size(16.dp))
            }
        },
    )
}

@Composable
private fun RowScope.StopsFact(description: String, count: Int) = Fact(Icons.Rounded.LinearScale, description, "$count")

/**
 * The steps still ahead, in travel order, each with its time and platform:
 * before leaving the train to catch; changing, the change being made; on
 * board the stop coming up; then where this ride ends (unless it is the
 * headline), the change after it and its margin, the next train, and where
 * the trip ends.
 */
@Composable
private fun StepsAhead(view: LiveView, showRide: Boolean) {
    val day = view.day
    val trip = view.trip
    val leg = view.leg ?: return
    val progress = view.progress
    val onBoard = day.kind == TravelDayKind.OnBoard
    val lastRide = trip.legs.lastOrNull { !it.isWalk }
    val then = transfers(trip.legs).firstOrNull { it.arriving == day.legIndex }
    Steps {
        if (day.kind == TravelDayKind.BeforeLeaving) TrainRow(leg)
        if (day.kind == TravelDayKind.Changing && view.change != null) ChangeRow(view.change, trip.legs)
        if (onBoard && progress != null) NextStop(leg, progress, view.onboard)
        if (then != null) {
            val arriving = trip.legs[then.arriving]
            if (!onBoard) {
                StopRow(
                    name = arriving.to.name,
                    planned = arriving.to.plannedAt, expected = arriving.to.expectedAt,
                    platformPlanned = arriving.to.plannedPlatform, platformCurrent = arriving.to.platform,
                    mark = { MarkIcon(Icons.AutoMirrored.Rounded.Logout, "Get off") },
                    trailing = { if (showRide && progress != null && arriving === leg) StopsFact("Stops", progress.stops.size - 1) },
                )
            }
            ChangeRow(then, trip.legs)
            TrainRow(trip.legs[then.departing])
        }
        // Where the trip ends (the headline, on the last ride).
        if (lastRide != null && !(onBoard && leg === lastRide)) {
            StopRow(
                name = trip.destinationName,
                planned = lastRide.to.plannedAt, expected = lastRide.to.expectedAt,
                quiet = then != null,
                mark = { MarkIcon(Icons.Rounded.Flag, "Destination") },
                trailing = { if (then == null && !onBoard && showRide && progress != null) StopsFact("Stops", progress.stops.size - 1) },
            )
        }
    }
}

/**
 * The stop coming up, on board: its name and platform (the train's Wi-Fi
 * knows it), when, and the stops left. Standing at a stopover, that stop and
 * when the train leaves it. Not when the only stop left is the one the card
 * leads with.
 */
@Composable
private fun NextStop(leg: Leg, progress: RideProgress, onboard: OnboardStatus?) {
    val stops = progress.stops
    val last = stops.lastIndex
    val next = progress.next ?: return
    val current = progress.current
    val standing = current != null && current in 1 until last
    if (!standing && next == last) return
    val stop = if (standing) stops[current!!] else stops[next]
    val planned = leg.stopovers.firstOrNull { it.name == stop.name && (it.id == null || it.id == stop.id) }
        ?.let { (if (standing) it.plannedDeparture ?: it.plannedArrival else it.plannedArrival ?: it.plannedDeparture).toInstantOrNull() }
    // The train's Wi-Fi names the platform of its next stop; only when that is the stop shown.
    val track = onboard?.let { o ->
        o.nextTrack?.takeIf { !standing && o.nextStop?.let { n -> n.startsWith(short(stop.name)) || stop.name.startsWith(n) } != false }
    }
    val at = if (standing) stop.departure else stop.arrival
    StopRow(
        name = stop.name,
        planned = planned ?: at, expected = at,
        platformPlanned = track, platformCurrent = track,
        mark = {
            if (standing) MarkIcon(Icons.Rounded.Place, "Now at, leaves") else MarkIcon(Icons.AutoMirrored.Rounded.ArrowForward, "Next stop")
        },
        trailing = { StopsFact("Stops left", progress.stopsLeft) },
    )
}

// ---------------------------------------------------------------- how Abeona knows

/**
 * What the source pill opens: each signal with its icon, the one that
 * decided lit, the others saying what they see, and "Use" for the location.
 * Public so the screenshot test can draw it without the sheet.
 */
@Composable
fun HowAbeonaKnows(evidence: List<Evidence>, day: TravelDay, onUseLocation: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            when (day.kind) {
                TravelDayKind.OnBoard -> "How Abeona knows your train"
                TravelDayKind.AtStation, TravelDayKind.Changing -> "How Abeona knows where you are"
                else -> "How Abeona knows"
            },
            style = AbeonaType.headline,
        )
        Text(
            "The strongest signal there is decides: the train's Wi-Fi, then your location, then the timetable with the live times.",
            style = AbeonaType.meta,
            color = muted,
        )
        evidence.forEach { e ->
            val decides = e.state == EvidenceState.Decides
            val off = e.state == EvidenceState.Off || e.state == EvidenceState.Silent
            val disc = when {
                decides -> burgundy
                off -> well.copy(content = muted)
                else -> brandSoft.copy(content = brand)
            }
            Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(color = disc.container, contentColor = disc.content, shape = CircleShape, modifier = Modifier.size(36.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(evidenceIcon(e.kind), contentDescription = null, modifier = Modifier.size(20.dp)) }
                }
                Column(Modifier.weight(1f)) {
                    Text(e.title, style = if (decides) AbeonaType.title else AbeonaType.body)
                    Text(e.detail, style = AbeonaType.meta, color = muted)
                }
                if (e.kind == EvidenceKind.Location && e.state == EvidenceState.Off && onUseLocation != null) {
                    FilledTonalButton(onClick = onUseLocation, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) { Text("Use") }
                }
                if (decides) Text("DECIDES", style = AbeonaType.label, color = brand)
            }
        }
    }
}

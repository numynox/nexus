package io.github.numynox.nexus.abeona.ui.live

import androidx.compose.material.icons.rounded.LinearScale
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.CloudOff
import io.github.numynox.nexus.abeona.ui.common.ShiftedTime
import io.github.numynox.nexus.abeona.data.toInstantOrNull
import io.github.numynox.nexus.abeona.data.StopTime
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.numynox.nexus.abeona.data.Leg
import io.github.numynox.nexus.abeona.data.Trip
import io.github.numynox.nexus.abeona.domain.AlternativesReason
import io.github.numynox.nexus.abeona.domain.RideProgress
import io.github.numynox.nexus.abeona.domain.Transfer
import io.github.numynox.nexus.abeona.domain.TravelDay
import io.github.numynox.nexus.abeona.domain.TravelDayKind
import io.github.numynox.nexus.abeona.domain.TravelDaySource
import io.github.numynox.nexus.abeona.domain.arrivalDelay
import io.github.numynox.nexus.abeona.domain.departureDelay
import io.github.numynox.nexus.abeona.domain.platformChanged
import io.github.numynox.nexus.abeona.domain.rideProgress
import io.github.numynox.nexus.abeona.domain.transfers
import io.github.numynox.nexus.abeona.live.Evidence
import io.github.numynox.nexus.abeona.live.EvidenceKind
import io.github.numynox.nexus.abeona.live.EvidenceState
import io.github.numynox.nexus.abeona.live.OnboardStatus
import io.github.numynox.nexus.abeona.ui.attention
import io.github.numynox.nexus.abeona.ui.common.ConfidenceChip
import io.github.numynox.nexus.abeona.ui.common.LineChip
import io.github.numynox.nexus.abeona.ui.common.PlatformBox
import io.github.numynox.nexus.abeona.ui.common.TripFormat
import io.github.numynox.nexus.abeona.ui.trip.MapMarker
import io.github.numynox.nexus.abeona.ui.trip.TripMap
import io.github.numynox.nexus.core.ui.theme.tabular
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
    fix: io.github.numynox.nexus.abeona.domain.Position?,
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

/** How fresh the live values are, for the card's last line: "Live · updated 17:44", "Offline · updated …". */
data class LiveStatus(val text: String, val offline: Boolean = false)

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
            TravelDayKind.OnBoard -> "On board" + (leg?.let { " · ${it.label}" + (it.headsign?.let { h -> " → ${short(h)}" } ?: "") } ?: "")
            TravelDayKind.Changing -> "Change trains"
            TravelDayKind.Arrived -> "Arrived"
        }
    }
}

/** "Frankfurt(Main)Hbf" → "Frankfurt": a station's town, for tight labels. */
private fun short(name: String): String = name.substringBefore('(').substringBefore(" Hbf").substringBefore(" Hauptbahnhof").trim()

private fun stateIcon(day: TravelDay): ImageVector = when {
    day.missedChange || day.cancelled -> Icons.Rounded.WarningAmber
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
    /** The platform there, in a box beside [icon]. */
    val platform: StopTime? = null,
    val platformAtArrival: Boolean = false,
    /** The ride to catch, as a chip, instead of a platform. */
    val line: Leg? = null,
    val note: String? = null,
    val minutes: Int? = null,
    val cancelled: Boolean = false,
)

private fun lead(view: LiveView): Lead {
    val day = view.day
    val trip = view.trip
    val leg = view.leg
    if (day.kind == TravelDayKind.Arrived || leg == null) {
        val last = trip.legs.lastOrNull { !it.isWalk }
        return Lead(trip.destinationName, stop = last?.to, icon = Icons.Rounded.Flag)
    }
    if (day.missedChange && day.kind != TravelDayKind.OnBoard) {
        return Lead("${leg.label} has left", stop = leg.from, note = "Your ticket may allow another train.")
    }
    if (leg.cancelled) {
        return Lead(if (leg.dropped) "${leg.label} is not in the timetable" else "${leg.label} is cancelled", stop = leg.from, cancelled = true)
    }
    return when (day.kind) {
        TravelDayKind.BeforeLeaving -> Lead(
            // The station is the trip's start, in the title above; the time to go fits one line.
            "Leave at ${TripFormat.time(day.leaveAt)}",
            stop = leg.from,
            line = leg,
            note = "${view.stationMinutes} min to the station",
            minutes = day.minutesToLeave,
        )
        TravelDayKind.AtStation, TravelDayKind.Changing -> Lead(
            leg.from.shownPlatform?.let { "Platform $it" } ?: leg.label,
            stop = leg.from,
            line = leg,
            minutes = day.minutesToDeparture,
        )
        else -> Lead(
            leg.to.name,
            stop = leg.to,
            icon = if (view.change != null) Icons.Rounded.SwapHoriz else Icons.Rounded.Flag,
            platform = leg.to,
            platformAtArrival = true,
            minutes = day.minutesToArrival,
        )
    }
}

/**
 * The live card: everything live on the trip screen in one place, most
 * urgent first. What happens now with its time, delay and countdown; the ride
 * on a rail with every stop; the next stop; the change after this ride and
 * where the trip ends; then how fresh it all is, and the train's speed when
 * its Wi-Fi says. The source pill opens how Abeona knows.
 */
@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
fun LiveCard(
    view: LiveView,
    modifier: Modifier = Modifier,
    status: LiveStatus? = null,
    onAlternatives: (() -> Unit)? = null,
    onUseLocation: (() -> Unit)? = null,
    /** What else is live about the trip (the binding lifted, a connection taken instead), inside the card. */
    notices: (@Composable () -> Unit)? = null,
) {
    var showSource by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    if (showSource) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showSource = false },
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            HowAbeonaKnows(view.evidence, view.day, onUseLocation, Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp))
        }
    }
    val day = view.day
    val leg = view.leg
    val trip = view.trip
    val warn = day.missedChange || day.cancelled
    val scheme = MaterialTheme.colorScheme
    val accent = if (warn) attention.content else scheme.primary
    val background = if (warn) {
        Brush.linearGradient(listOf(attention.container.copy(alpha = 0.55f), attention.container.copy(alpha = 0.25f)))
    } else {
        Brush.linearGradient(listOf(scheme.primaryContainer, scheme.primaryContainer.copy(alpha = 0.45f)))
    }
    Surface(color = Color.Transparent, contentColor = scheme.onSurface, shape = RoundedCornerShape(28.dp), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.background(background).padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // What state, and how Abeona knows.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    color = if (warn) attention.container else scheme.primary,
                    contentColor = if (warn) attention.content else scheme.onPrimary,
                    shape = CircleShape,
                ) {
                    Box(Modifier.padding(7.dp)) { Icon(stateIcon(day), contentDescription = null, modifier = Modifier.size(20.dp)) }
                }
                Text(
                    eyebrow(view).uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp),
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (day.kind != TravelDayKind.Arrived) SourcePill(day.source) { showSource = true }
            }

            // The one thing to know now.
            val lead = lead(view)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        lead.headline,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        lead.stop?.let {
                            ShiftedTime(
                                it.plannedAt, it.expectedAt,
                                MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                                cancelled = lead.cancelled,
                            )
                        }
                        lead.icon?.let { Icon(it, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) }
                        lead.platform?.let {
                            if (lead.platformAtArrival) PlatformBox(it.plannedPlatform, it.platform, platformChanged(it.plannedPlatform, it.platform))
                        }
                        lead.line?.let { line ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                LineChip(line)
                                line.headsign?.let { Text("→ ${short(it)}", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 1) }
                            }
                        }
                    }
                    lead.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant) }
                }
                countdown(lead.minutes)?.let { (number, unit) ->
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "$number $unit" }) {
                        Text(
                            number,
                            style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp).tabular(),
                            color = accent,
                            maxLines = 1,
                        )
                        if (unit.isNotEmpty()) Text(unit, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }

            // The change being made: its margin and the way between platforms.
            if (day.kind == TravelDayKind.Changing && view.change != null && !warn) {
                val change = view.change
                val arriving = trip.legs[change.arriving]
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ConfidenceChip(change.confidence, minutes = change.availableMinutes)
                    if (change.walkMinutes > 0) {
                        Icon(Icons.AutoMirrored.Rounded.DirectionsWalk, contentDescription = "Walk", tint = scheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        Text("${change.walkMinutes} min", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    } else {
                        arriving.to.shownPlatform?.let { from ->
                            Text("Pl. $from" + (leg?.from?.shownPlatform?.let { " → $it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // The ride on a rail, and the stop coming up.
            val progress = view.progress
            val onBoard = day.kind == TravelDayKind.OnBoard
            val showRide = leg != null && progress != null && !leg.cancelled && !(day.missedChange && !onBoard) &&
                (onBoard || day.kind == TravelDayKind.AtStation || day.kind == TravelDayKind.Changing)
            if (showRide) {
                RideRail(leg!!, progress!!, onBoard)
                if (onBoard) NextStop(leg, progress, view.onboard)
            }

            // After this ride: the next train, and where the trip ends.
            val next = leg?.takeIf { !warn && day.kind != TravelDayKind.Arrived }?.let { _ ->
                transfers(trip.legs).firstOrNull { it.arriving == day.legIndex }
            }
            if (next != null) ThenBox(trip, next, showStation = !onBoard)

            if (day.missedChange && onBoard) {
                Text("You are still on this train after your connection left.", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
            }
            if (view.reason != null && onAlternatives != null) {
                Button(
                    onClick = onAlternatives,
                    colors = ButtonDefaults.buttonColors(containerColor = attention.container, contentColor = attention.content),
                ) {
                    Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("See alternatives", modifier = Modifier.padding(start = 8.dp))
                }
            }

            notices?.invoke()

            // How fresh, and the train's speed when its Wi-Fi says: small, last.
            val speed = view.onboard?.speedKmh?.takeIf { it >= 1 && onBoard }
            if (status != null || speed != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (status != null) {
                        Icon(
                            if (status.offline) Icons.Rounded.CloudOff else Icons.Rounded.Sync,
                            contentDescription = null,
                            tint = if (status.offline) scheme.onSurfaceVariant else scheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(status.text, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    speed?.let {
                        Icon(Icons.Rounded.Speed, contentDescription = "Speed", tint = scheme.outline, modifier = Modifier.size(14.dp))
                        Text("${it.roundToInt()} km/h", style = MaterialTheme.typography.labelMedium.tabular(), color = scheme.outline)
                    }
                }
            }
        }
    }
}

/** What placed the traveller, as a button: a tap opens how Abeona knows. */
@Composable
private fun SourcePill(source: TravelDaySource, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = CircleShape,
        modifier = Modifier.semantics { contentDescription = "How Abeona knows: ${sourceShort(source)}" },
    ) {
        Row(Modifier.padding(start = 9.dp, end = 5.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(evidenceIcon(sourceKind(source)), contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            Text(sourceShort(source), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
            Icon(Icons.Rounded.ExpandMore, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A tinted box inside the card. */
@Composable
private fun Inset(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

/**
 * The ride on a rail: every stop, the train on it. Under it, where the ride
 * starts with its departure, and at its end how many stops are left (on
 * board, where the card already names the end) or the end and its arrival.
 */
@Composable
private fun RideRail(leg: Leg, progress: RideProgress, onBoard: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val label = MaterialTheme.typography.labelMedium
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        StopRail(progress, onBoard)
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(short(leg.from.name), style = label, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                ShiftedTime(leg.from.plannedAt, leg.from.expectedAt, label, block = false, color = scheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                val count = if (onBoard) progress.stopsLeft else progress.stops.size - 1
                if (!onBoard) {
                    Text(short(leg.to.name), style = label, color = scheme.onSurfaceVariant, maxLines = 1)
                    ShiftedTime(leg.to.plannedAt, leg.to.expectedAt, label, block = false, color = scheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "$count stops" + if (onBoard) " left" else "" }) {
                    Icon(Icons.Rounded.LinearScale, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                    Text("$count", style = label.tabular(), color = scheme.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * The stop coming up, on board: its name and platform (the train's Wi-Fi
 * knows it), and when, with the minutes to it. Standing at a stopover, that
 * stop and when the train leaves it. Not when the only stop left is the one
 * the card leads with.
 */
@Composable
private fun NextStop(leg: Leg, progress: RideProgress, onboard: OnboardStatus?) {
    val scheme = MaterialTheme.colorScheme
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
    Inset {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                if (standing) Icons.Rounded.Place else Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = if (standing) "Now at" else "Next stop",
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Text(
                stop.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            track?.let { PlatformBox(it, it, changed = false) }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                val at = if (standing) stop.departure else stop.arrival
                ShiftedTime(planned ?: at, at, MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), block = false)
                Text(
                    if (standing) "leaves" else TripFormat.relative((progress.minutesToNext ?: 0).toLong()),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * After this ride: the change (where, when not on the train yet), its margin,
 * the train to catch with its platform and time, and where the trip ends.
 */
@Composable
private fun ThenBox(trip: Trip, change: Transfer, showStation: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val departing = trip.legs[change.departing]
    val last = trip.legs.lastOrNull { !it.isWalk }
    Inset {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                if (change.walkMinutes > 0) Icons.AutoMirrored.Rounded.DirectionsWalk else Icons.Rounded.SwapHoriz,
                contentDescription = "Then",
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Text(
                if (showStation) departing.from.name else "",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            ConfidenceChip(change.confidence, minutes = change.availableMinutes)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LineChip(departing)
            PlatformBox(departing.from.plannedPlatform, departing.from.platform, platformChanged(departing.from.plannedPlatform, departing.from.platform))
            Spacer(Modifier.weight(1f))
            ShiftedTime(departing.from.plannedAt, departing.from.expectedAt, MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), block = false)
        }
        if (last != null && last !== departing) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Flag, contentDescription = "Destination", tint = scheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Text(trip.destinationName, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                ShiftedTime(last.to.plannedAt, last.to.expectedAt, MaterialTheme.typography.bodyMedium, block = false)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Flag, contentDescription = "Destination", tint = scheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Text(departing.to.name, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                ShiftedTime(departing.to.plannedAt, departing.to.expectedAt, MaterialTheme.typography.bodyMedium, block = false)
            }
        }
    }
}

/** Every stop on a line, the part behind filled, the train where it is now. */
@Composable
private fun StopRail(progress: RideProgress, onBoard: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val done = scheme.primary
    val ahead = scheme.outlineVariant
    val surface = scheme.primaryContainer
    val fraction = if (onBoard) progress.fraction.toFloat() else 0f
    BoxWithConstraints(Modifier.fillMaxWidth().height(36.dp)) {
        val width = maxWidth
        val inset = 10.dp
        Canvas(Modifier.fillMaxWidth().height(36.dp)) {
            val y = size.height / 2
            val left = inset.toPx()
            val right = size.width - inset.toPx()
            val x = { f: Float -> left + (right - left) * f }
            drawLine(ahead, Offset(left, y), Offset(right, y), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
            if (fraction > 0f) drawLine(done, Offset(left, y), Offset(x(fraction), y), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
            progress.stops.forEachIndexed { i, stop ->
                val c = Offset(x(stop.fraction.toFloat()), y)
                val end = i == 0 || i == progress.stops.lastIndex
                val r = (if (end) 7.dp else 5.dp).toPx()
                val passed = onBoard && stop.fraction <= fraction
                drawCircle(surface, radius = r + 2.dp.toPx(), center = c)
                if (passed) {
                    drawCircle(done, radius = r, center = c)
                } else {
                    drawCircle(surface, radius = r, center = c)
                    drawCircle(if (end) done else scheme.outline, radius = r, center = c, style = Stroke(2.dp.toPx()))
                }
            }
        }
        if (onBoard) {
            val markerSize = 26.dp
            val offset: Dp = inset + (width - inset * 2) * fraction - markerSize / 2
            Surface(
                color = done,
                contentColor = scheme.onPrimary,
                shape = CircleShape,
                shadowElevation = 2.dp,
                modifier = Modifier.offset(x = offset, y = 5.dp).size(markerSize),
            ) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Train, contentDescription = "The train", modifier = Modifier.size(16.dp)) }
            }
        }
    }
}

// ---------------------------------------------------------------- how Abeona knows

/**
 * What the source pill opens: each signal with its icon, the one that
 * decided lit, the others saying what they see, and "Use" for the location.
 * Public so the screenshot test can draw it without the sheet.
 */
@Composable
fun HowAbeonaKnows(evidence: List<Evidence>, day: TravelDay, onUseLocation: (() -> Unit)?, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            when (day.kind) {
                TravelDayKind.OnBoard -> "How Abeona knows your train"
                TravelDayKind.AtStation, TravelDayKind.Changing -> "How Abeona knows where you are"
                else -> "How Abeona knows"
            },
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        )
        Text(
            "The strongest signal there is decides: the train's Wi-Fi, then your location, then the timetable with the live times.",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )
        evidence.forEach { e ->
            val decides = e.state == EvidenceState.Decides
            val off = e.state == EvidenceState.Off || e.state == EvidenceState.Silent
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    color = when {
                        decides -> scheme.primary
                        off -> scheme.surfaceContainerHighest
                        else -> scheme.secondaryContainer
                    },
                    contentColor = when {
                        decides -> scheme.onPrimary
                        off -> scheme.onSurfaceVariant.copy(alpha = 0.7f)
                        else -> scheme.onSecondaryContainer
                    },
                    shape = CircleShape,
                ) {
                    Box(Modifier.padding(8.dp)) { Icon(evidenceIcon(e.kind), contentDescription = null, modifier = Modifier.size(18.dp)) }
                }
                Column(Modifier.weight(1f)) {
                    Text(e.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = if (decides) FontWeight.SemiBold else FontWeight.Medium))
                    Text(e.detail, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                if (e.kind == EvidenceKind.Location && e.state == EvidenceState.Off && onUseLocation != null) {
                    androidx.compose.material3.FilledTonalButton(
                        onClick = onUseLocation,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    ) { Text("Use") }
                }
                if (decides) {
                    Surface(color = scheme.primaryContainer, contentColor = scheme.onPrimaryContainer, shape = CircleShape) {
                        Text("Decides", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
            }
        }
    }
}

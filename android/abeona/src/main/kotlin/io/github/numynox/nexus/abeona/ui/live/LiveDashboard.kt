package io.github.numynox.nexus.abeona.ui.live

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
import io.github.numynox.nexus.abeona.ui.common.DelayBadge
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
 * The live companion's dashboard (glossary: Live companion, travel day):
 * the one thing to do now with its countdown, then — as far as they matter
 * in this state — where on the ride the train is (next stop, stops left),
 * the map with the traveller on it, the next change and the whole journey.
 * How Abeona knows which train the traveller is on sits behind the first
 * card's source pill: one tap when in doubt, out of the way otherwise.
 */
@Composable
fun LiveDashboard(
    view: LiveView,
    modifier: Modifier = Modifier,
    onAlternatives: (() -> Unit)? = null,
    showMap: Boolean = true,
    onUseLocation: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LiveNow(view, onAlternatives = onAlternatives, onUseLocation = onUseLocation)
        LiveDetails(view, showMap = showMap)
    }
}

/**
 * The dashboard's first card: the one thing to do now, and its countdown.
 * Its source pill ("Train Wi-Fi", "Location", "Timetable") opens how Abeona
 * knows, with the way to turn the location on ([onUseLocation]).
 */
@Composable
fun LiveNow(view: LiveView, modifier: Modifier = Modifier, onAlternatives: (() -> Unit)? = null, onUseLocation: (() -> Unit)? = null) {
    Box(modifier) { NowCard(view, onAlternatives, onUseLocation) }
}

/**
 * The rest of the dashboard, under the first card (the trip screen puts the
 * ticket and DB Navigator between the two): the ride, the map, the change
 * and the journey. Nothing once arrived.
 */
@Composable
fun LiveDetails(view: LiveView, modifier: Modifier = Modifier, showMap: Boolean = true) {
    val day = view.day
    if (day.kind == TravelDayKind.Arrived) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val showRide = view.leg != null && view.progress != null && !view.leg.cancelled && !(day.missedChange && day.kind != TravelDayKind.OnBoard) &&
            (day.kind == TravelDayKind.OnBoard || day.kind == TravelDayKind.AtStation || day.kind == TravelDayKind.Changing)
        if (showRide) RideCard(view.leg, view.progress, onBoard = day.kind == TravelDayKind.OnBoard, onboard = view.onboard)
        if (showMap) {
            TripMap(
                view.trip.legs,
                Modifier.fillMaxWidth().height(240.dp),
                marker = view.marker,
                focus = view.leg?.takeIf { day.kind == TravelDayKind.OnBoard },
            )
        }
        if (day.kind == TravelDayKind.OnBoard && view.change != null) ChangeCard(view.trip, view.change, view.now)
        JourneyStrip(view.trip, day)
    }
}

// ---------------------------------------------------------------- now

private fun eyebrow(view: LiveView): String {
    val day = view.day
    val leg = view.leg
    return when {
        day.missedChange -> "Looks like you missed it"
        day.cancelled -> "Change of plan"
        else -> when (day.kind) {
            TravelDayKind.BeforeLeaving -> "Time to leave"
            TravelDayKind.AtStation -> if (day.source == TravelDaySource.Location) "You're at the station" else "Head to the platform"
            TravelDayKind.OnBoard -> "On board" + (leg?.let { " · ${it.label}" } ?: "")
            TravelDayKind.Changing -> "Change trains"
            TravelDayKind.Arrived -> "Arrived"
        }
    }
}

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

/** The big number and its unit: "23" "min", "1:05" "h", "now". */
private fun countdown(minutes: Int?): Pair<String, String>? {
    val m = minutes ?: return null
    return when {
        m <= 0 -> "now" to ""
        m < 60 -> "$m" to "min"
        else -> "${m / 60}:${"%02d".format(m % 60)}" to "h"
    }
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
private fun NowCard(view: LiveView, onAlternatives: (() -> Unit)?, onUseLocation: (() -> Unit)?) {
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
    val warn = day.missedChange || day.cancelled
    val scheme = MaterialTheme.colorScheme
    val accent = if (warn) attention.content else scheme.primary
    val background = if (warn) {
        Brush.linearGradient(listOf(attention.container.copy(alpha = 0.55f), attention.container.copy(alpha = 0.25f)))
    } else {
        Brush.linearGradient(listOf(scheme.primaryContainer, scheme.primaryContainer.copy(alpha = 0.45f)))
    }
    Surface(color = Color.Transparent, contentColor = scheme.onSurface, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.background(background).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

            val (headline, detail, delay, minutes, unit) = nowLines(view)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        headline,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold).tabular(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(detail, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f, fill = false))
                        DelayBadge(delay)
                    }
                }
                countdown(minutes)?.let { (number, label) ->
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.semantics { contentDescription = "$number $label $unit" }) {
                        Text(
                            number,
                            style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp).tabular(),
                            color = accent,
                            maxLines = 1,
                        )
                        Text(
                            listOf(label, unit).filter { it.isNotEmpty() }.joinToString(" "),
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }

            if (day.kind == TravelDayKind.Changing && view.change != null && !warn) {
                val change = view.change
                val arriving = view.trip.legs[change.arriving]
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ConfidenceChip(change.confidence, minutes = change.availableMinutes)
                    Text(
                        when {
                            change.walkMinutes > 0 -> "Walk ${change.walkMinutes} min"
                            arriving.to.shownPlatform != null -> "From Pl. ${arriving.to.shownPlatform}" + (leg?.from?.shownPlatform?.let { " → Pl. $it" } ?: "")
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            if (day.missedChange && day.kind == TravelDayKind.OnBoard) {
                Text(
                    "You are still on this train after your connection left.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                )
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
        }
    }
}

private data class NowLines(val headline: String, val detail: String, val delay: Int, val minutes: Int?, val unit: String)

private fun nowLines(view: LiveView): NowLines {
    val day = view.day
    val trip = view.trip
    val leg = view.leg
    if (day.kind == TravelDayKind.Arrived || leg == null) {
        val last = trip.legs.lastOrNull { !it.isWalk }
        return NowLines(trip.destinationName, "Arrived at ${TripFormat.time(trip.expectedArrival)}", last?.let(::arrivalDelay) ?: 0, null, "")
    }
    if (day.missedChange && day.kind != TravelDayKind.OnBoard) {
        return NowLines("${leg.label} has left", "It left ${leg.from.name} at ${TripFormat.time(leg.from.expectedAt)}. Your ticket may allow another train.", 0, null, "")
    }
    if (leg.cancelled) {
        return NowLines("${leg.label} is cancelled", "It was to leave ${leg.from.name} at ${TripFormat.time(leg.from.plannedAt)}.", 0, null, "")
    }
    val platform = leg.from.shownPlatform
    return when (day.kind) {
        TravelDayKind.BeforeLeaving -> NowLines(
            "Leave for ${leg.from.name}",
            listOfNotNull(leg.label, platform?.let { "Pl. $it" }, TripFormat.time(leg.from.expectedAt)).joinToString(" · ") +
                " · ${view.stationMinutes} min to the station",
            departureDelay(leg),
            day.minutesToLeave,
            "to leave",
        )
        TravelDayKind.AtStation, TravelDayKind.Changing -> NowLines(
            platform?.let { "Platform $it" } ?: leg.label,
            "${leg.label}${leg.headsign?.let { " to $it" } ?: ""} · ${TripFormat.time(leg.from.expectedAt)}",
            departureDelay(leg),
            day.minutesToDeparture,
            "to departure",
        )
        else -> NowLines(
            leg.to.name,
            listOfNotNull(
                if (view.change != null) "Change here" else if (leg.to.name == trip.destinationName) "Your stop" else "Get off here",
                TripFormat.time(leg.to.expectedAt),
                leg.to.shownPlatform?.let { "Pl. $it" },
            ).joinToString(" · "),
            arrivalDelay(leg),
            day.minutesToArrival,
            "to arrival",
        )
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

// ---------------------------------------------------------------- the ride

@Composable
private fun DashCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * The ride on board (or the one to catch): its next stop, how many are left
 * until getting off, a rail with every stop and the train on it, and the
 * stops still ahead.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RideCard(leg: Leg, progress: RideProgress, onBoard: Boolean, onboard: OnboardStatus?) {
    val scheme = MaterialTheme.colorScheme
    val stops = progress.stops
    val last = stops.lastIndex
    DashCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LineChip(leg, large = true)
            Text(
                leg.headsign?.let { "to $it" } ?: "to ${leg.to.name}",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                when {
                    progress.stopsLeft == 0 -> "Arrived"
                    !onBoard -> "${stops.size - 1} stops"
                    progress.stopsLeft == 1 -> "1 stop left"
                    else -> "${progress.stopsLeft} stops left"
                },
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold).tabular(),
                color = scheme.primary,
            )
        }

        val next = progress.next
        val current = progress.current
        if (onBoard && next != null) {
            val standing = current != null && current in 1 until last
            val stop = if (standing) stops[current] else stops[next]
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Label(if (standing) "Now at" else if (next == last) "Next stop · get off" else "Next stop")
                    Text(stop.name, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        TripFormat.time(if (standing) stop.departure else stop.arrival),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold).tabular(),
                    )
                    Text(
                        if (standing) "leaves" else TripFormat.relative((progress.minutesToNext ?: 0).toLong()),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }

        StopRail(progress, onBoard)

        // The stops still ahead, up to the one to get off at, that the line
        // above does not name: standing at a stop it names that stop, so the
        // list starts at the next one; moving, it names the next one.
        val standingHere = onBoard && current != null && current in 1 until last
        val from = when {
            next == null -> last + 1
            !onBoard || standingHere -> next
            else -> next + 1
        }
        val ahead = (from..last).toList()
        if (ahead.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val shown = if (ahead.size > 5) ahead.take(3) + ahead.last() else ahead
                shown.forEachIndexed { k, i ->
                    if (k == 3 && ahead.size > 5) {
                        Text("· · ·  ${ahead.size - 4} more", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, modifier = Modifier.padding(start = 54.dp))
                    }
                    val stop = stops[i]
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(TripFormat.time(stop.arrival), style = MaterialTheme.typography.bodyMedium.tabular(), color = scheme.onSurfaceVariant, modifier = Modifier.width(42.dp))
                        Box(Modifier.size(if (i == last) 10.dp else 7.dp).background(if (i == last) scheme.primary else scheme.outline, CircleShape))
                        Text(
                            stop.name,
                            style = if (i == last) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold) else MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        if (onboard != null && (onboard.speedKmh != null || onboard.nextStop != null)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                onboard.speedKmh?.takeIf { it >= 1 }?.let { Fact(Icons.Rounded.Speed, "${it.roundToInt()} km/h") }
                onboard.nextTrack?.let { Fact(Icons.Rounded.Place, "Next stop Pl. $it") }
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
    val surface = scheme.surfaceContainerLow
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

@Composable
private fun Fact(icon: ImageVector, text: String) {
    Surface(color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface, shape = CircleShape) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Text(text, style = MaterialTheme.typography.labelLarge.tabular())
        }
    }
}

// ---------------------------------------------------------------- the change

/**
 * The next change, while still on the train before it: where, from which
 * platform to which, the minutes to make it, and the train to catch.
 */
@Composable
private fun ChangeCard(trip: Trip, change: Transfer, now: Instant) {
    val scheme = MaterialTheme.colorScheme
    val arriving = trip.legs[change.arriving]
    val departing = trip.legs[change.departing]
    val leavesIn = departing.from.expectedAt?.let { Math.ceil(java.time.Duration.between(now, it).toMillis() / 60_000.0).toLong() }
    DashCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.SwapHoriz, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                Label("Your change")
                Text(departing.from.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            ConfidenceChip(change.confidence, minutes = change.availableMinutes)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ChangeEnd("Arrive", arriving.to.expectedAt, arrivalDelay(arriving), arriving.to.plannedPlatform, arriving.to.platform, platformChanged(arriving.to.plannedPlatform, arriving.to.platform), Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    if (change.walkMinutes > 0) Icons.AutoMirrored.Rounded.DirectionsWalk else Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                )
                Text(
                    if (change.walkMinutes > 0) "${change.walkMinutes} min" else "${change.requiredMinutes} min",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            ChangeEnd("Depart", departing.from.expectedAt, departureDelay(departing), departing.from.plannedPlatform, departing.from.platform, platformChanged(departing.from.plannedPlatform, departing.from.platform), Modifier.weight(1f), alignEnd = true)
        }
        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.6f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LineChip(departing)
            Text(
                listOfNotNull(departing.headsign?.let { "to $it" }, leavesIn?.let { "leaves ${TripFormat.relative(it)}" }).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val later = transfers(trip.legs).count { it.arriving > change.arriving }
        if (later > 0) {
            Text(
                if (later == 1) "Then one more change" else "Then $later more changes",
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChangeEnd(
    label: String,
    at: Instant?,
    delay: Int,
    plannedPlatform: String?,
    platform: String?,
    changed: Boolean,
    modifier: Modifier,
    alignEnd: Boolean = false,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp, if (alignEnd) Alignment.End else Alignment.Start)) {
        if (alignEnd) PlatformBox(plannedPlatform, platform, changed)
        Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
            Label(label)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(TripFormat.time(at), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold).tabular())
                DelayBadge(delay)
            }
        }
        if (!alignEnd) PlatformBox(plannedPlatform, platform, changed)
    }
}

// ---------------------------------------------------------------- the journey

/** Every ride of the trip in a row: behind, now, ahead; then the destination and when. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JourneyStrip(trip: Trip, day: TravelDay) {
    val scheme = MaterialTheme.colorScheme
    val rides = trip.legs.indices.filter { !trip.legs[it].isWalk }
    val currentIndex = day.legIndex
    val position = rides.indexOf(currentIndex).takeIf { it >= 0 }
    val last = trip.legs.lastOrNull { !it.isWalk }
    DashCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label(
                when {
                    rides.size == 1 -> "Your journey · direct"
                    position != null -> "Your journey · ride ${position + 1} of ${rides.size}"
                    else -> "Your journey"
                },
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${trip.destinationName.substringBefore('(').trim()} ${TripFormat.time(trip.expectedArrival)}",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold).tabular(),
                maxLines = 1,
            )
            last?.let { DelayBadge(arrivalDelay(it), Modifier.padding(start = 6.dp)) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            rides.forEachIndexed { k, i ->
                val leg = trip.legs[i]
                val done = currentIndex != null && i < currentIndex || day.kind == TravelDayKind.Arrived
                val now = i == currentIndex && day.kind == TravelDayKind.OnBoard
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (done) Icon(Icons.Rounded.CheckCircle, contentDescription = "Done", tint = scheme.primary, modifier = Modifier.size(16.dp))
                    Box(Modifier.background(if (now) scheme.primary.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(10.dp)).padding(if (now) 3.dp else 0.dp)) {
                        LineChip(leg)
                    }
                }
                if (k < rides.lastIndex) {
                    Text(trip.legs[i].to.name.substringBefore('(').trim(), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, maxLines = 1)
                }
            }
            Icon(Icons.Rounded.Flag, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
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

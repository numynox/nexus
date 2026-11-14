package io.github.numynox.nexus.abeona.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.numynox.nexus.abeona.data.Leg
import io.github.numynox.nexus.abeona.data.Trip
import io.github.numynox.nexus.abeona.domain.AlternativesReason
import io.github.numynox.nexus.abeona.domain.TravelDay
import io.github.numynox.nexus.abeona.domain.TravelDayKind
import io.github.numynox.nexus.abeona.domain.TravelDaySource
import io.github.numynox.nexus.abeona.domain.arrivalDelay
import io.github.numynox.nexus.abeona.domain.departureDelay
import io.github.numynox.nexus.abeona.domain.rideProgress
import io.github.numynox.nexus.abeona.live.nextStopLine
import io.github.numynox.nexus.abeona.domain.transfers
import io.github.numynox.nexus.abeona.live.OnboardStatus
import io.github.numynox.nexus.abeona.ui.attention
import io.github.numynox.nexus.core.ui.theme.tabular
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

/** True from a day before the first departure until the trip has arrived: when the travel day shows. */
fun isLiveDay(trip: Trip, now: Instant): Boolean =
    !trip.departureAt.isAfter(now.plus(Duration.ofHours(24))) &&
        trip.expectedArrival.plus(Duration.ofMinutes(15)).isAfter(now) &&
        trip.legs.any { !it.isWalk }

private fun inMinutes(minutes: Int?): String = TripFormat.relative((minutes ?: 0).toLong())

/** The small line above the headline: what state this is. The same words as the live card's (LiveDashboard eyebrow). */
private fun eyebrow(day: TravelDay): String = when {
    day.missedChange -> "Looks like you missed it"
    day.cancelled -> "Change of plan"
    else -> when (day.kind) {
        TravelDayKind.BeforeLeaving -> "Time to leave"
        TravelDayKind.AtStation -> if (day.source == TravelDaySource.Location) "You're at the station" else "Head to the platform"
        TravelDayKind.OnBoard -> "On board"
        TravelDayKind.Changing -> "Change trains"
        TravelDayKind.Arrived -> "Arrived"
    }
}

private fun icon(day: TravelDay): ImageVector = when {
    day.missedChange || day.cancelled -> Icons.Rounded.WarningAmber
    else -> when (day.kind) {
        TravelDayKind.BeforeLeaving -> Icons.Rounded.MeetingRoom
        TravelDayKind.AtStation -> Icons.Rounded.Place
        TravelDayKind.OnBoard -> Icons.Rounded.Train
        TravelDayKind.Changing -> Icons.Rounded.SwapHoriz
        TravelDayKind.Arrived -> Icons.Rounded.Flag
    }
}

/**
 * The one hero card of a travel day (glossary: Travel day): what to do now,
 * in one big line, and the facts behind it. Mint and calm while things go to
 * plan; amber when a train is cancelled or missed, with the way to
 * alternatives.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TravelDayHero(
    trip: Trip,
    day: TravelDay,
    stationMinutes: Int,
    modifier: Modifier = Modifier,
    onboard: OnboardStatus? = null,
    reason: AlternativesReason? = null,
    onAlternatives: (() -> Unit)? = null,
    now: Instant = Instant.now(),
) {
    val warn = day.missedChange || day.cancelled
    val container = if (warn) attention.container.copy(alpha = 0.32f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    val accent = if (warn) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary
    val legs = trip.legs
    val leg = day.legIndex?.let { legs[it] }
    val change = day.legIndex?.let { index ->
        transfers(legs).firstOrNull { if (day.kind == TravelDayKind.OnBoard) it.arriving == index else it.departing == index }
    }
    // A translucent container has no matching content colour: say it.
    Surface(color = container, contentColor = MaterialTheme.colorScheme.onSurface, shape = RoundedCornerShape(24.dp), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    color = if (warn) attention.container else MaterialTheme.colorScheme.primary,
                    contentColor = if (warn) attention.content else MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape,
                ) {
                    Box(Modifier.padding(7.dp)) { Icon(icon(day), contentDescription = null, modifier = Modifier.size(20.dp)) }
                }
                Text(
                    eyebrow(day).uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = MaterialTheme.typography.labelMedium.letterSpacing * 1.6f),
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when {
                day.kind == TravelDayKind.Arrived -> Arrived(trip)
                leg == null -> Unit
                day.kind == TravelDayKind.BeforeLeaving -> {
                    // As the live card says it; the train, its time and platform are on the card below.
                    Headline("Leave at ${TripFormat.time(day.leaveAt)}")
                    val soon = (day.minutesToLeave ?: Int.MAX_VALUE) <= 180
                    Detail((if (soon) "${inMinutes(day.minutesToLeave).replaceFirstChar { it.uppercase() }} · " else "") + "$stationMinutes min to the station")
                }
                day.missedChange && day.kind != TravelDayKind.OnBoard -> {
                    Headline("${leg.label} has left")
                    Detail("It left ${leg.from.name} at ${TripFormat.time(leg.from.expectedAt)}. Your ticket may allow another train.")
                }
                leg.cancelled -> {
                    Headline(if (leg.dropped) "${leg.label} is not in the timetable" else "${leg.label} is cancelled")
                    Detail("It was to leave ${leg.from.name} at ${TripFormat.time(leg.from.plannedAt)}.")
                }
                // At the station of the trip's first train: the card below has its platform, time and line.
                day.kind == TravelDayKind.AtStation && legs.firstOrNull { !it.isWalk } == leg -> {
                    Headline("Leaves ${inMinutes(day.minutesToDeparture)}")
                    leg.headsign?.let { Detail("to $it") }
                }
                day.kind == TravelDayKind.AtStation || day.kind == TravelDayKind.Changing -> {
                    Headline(leg.from.shownPlatform?.let { "Platform $it" } ?: leg.label)
                    DetailWithTime(
                        "${leg.label}${leg.headsign?.let { " to $it" } ?: ""} leaves " +
                            (if (day.kind == TravelDayKind.Changing) "${leg.from.name} " else "") +
                            "${inMinutes(day.minutesToDeparture)},",
                        leg.from,
                    )
                    if (day.kind == TravelDayKind.Changing && change != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                            ConfidenceChip(change.confidence, minutes = change.availableMinutes)
                            val from = legs[change.arriving]
                            Text(
                                // As the live card: the walk, or the platforms from and to.
                                if (change.walkMinutes > 0) "Walk ${change.walkMinutes} min" else "Pl. ${from.to.shownPlatform ?: "–"}" + (leg.from.shownPlatform?.let { " → $it" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                else -> OnBoard(trip, day, now, leg, change?.let { legs[it.departing] }, change?.availableMinutes, change?.walkMinutes ?: 0, change?.confidence)
            }
            if (onboard != null && day.source == TravelDaySource.Onboard) OnboardStrip(onboard)
            if (reason != null && onAlternatives != null) {
                Button(
                    onClick = onAlternatives,
                    colors = ButtonDefaults.buttonColors(containerColor = attention.container, contentColor = attention.content),
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("See alternatives", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun Headline(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold).tabular(),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun Detail(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** [before], the time with its delay inline (ShiftedTime), then [after]. */
@Composable
private fun DetailWithTime(before: String, stop: io.github.numynox.nexus.abeona.data.StopTime?, after: String = "") {
    val style = MaterialTheme.typography.bodyMedium
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (before.isNotEmpty()) Text(before, style = style, color = color, modifier = Modifier.weight(1f, fill = false))
        ShiftedTime(stop?.plannedAt, stop?.expectedAt, style, block = false, color = color)
        if (after.isNotEmpty()) Text(after, style = style, color = color, maxLines = 1)
    }
}

@Composable
private fun OnBoard(
    trip: Trip,
    day: TravelDay,
    now: Instant,
    leg: Leg,
    next: Leg?,
    available: Int?,
    walk: Int,
    confidence: io.github.numynox.nexus.abeona.domain.Confidence?,
) {
    Headline("${leg.label}${leg.headsign?.let { " to $it" } ?: ""}")
    rideProgress(leg, now)
        ?.let(::nextStopLine)
        ?.let { Text(it, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)) }
    DetailWithTime(
        "${if (next != null) "Get off at" else "Arrives at"} ${leg.to.name} ${inMinutes(day.minutesToArrival)},",
        leg.to,
        leg.to.shownPlatform?.let { "· Pl. $it" } ?: "",
    )
    if (next != null && available != null && confidence != null) {
        Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), contentColor = MaterialTheme.colorScheme.onSurface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    if (walk > 0) Icons.AutoMirrored.Rounded.DirectionsWalk else Icons.Rounded.SwapHoriz,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Then ${next.label} at", style = MaterialTheme.typography.bodyMedium)
                    ShiftedTime(next.from.plannedAt, next.from.expectedAt, MaterialTheme.typography.bodyMedium, block = false)
                    next.from.shownPlatform?.let { Text("· Pl. $it", style = MaterialTheme.typography.bodyMedium, maxLines = 1) }
                }
                ConfidenceChip(confidence, minutes = available)
            }
        }
    }
    if (day.missedChange) {
        Text(
            "You are still on this train after your connection left.",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun Arrived(trip: Trip) {
    val last = trip.legs.lastOrNull { !it.isWalk }
    Headline(trip.destinationName)
    DetailWithTime("At", last?.to)
}

/** Speed and next stop from the train's Wi-Fi: what the seat-back screen says. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnboardStrip(status: OnboardStatus) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(top = 6.dp),
    ) {
        status.speedKmh?.let { Fact(Icons.Rounded.Speed, "${it.roundToInt()} km/h") }
        status.nextStop?.let { stop ->
            Fact(
                Icons.Rounded.Place,
                "Next: $stop" + (status.nextStopAt?.let { " ${TripFormat.time(it)}" } ?: "") + (status.nextTrack?.let { " · Pl. $it" } ?: ""),
            )
        }
        Fact(Icons.Rounded.Wifi, status.portal)
    }
}

@Composable
private fun Fact(icon: ImageVector, text: String) {
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f), contentColor = MaterialTheme.colorScheme.onSurface, shape = CircleShape) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Text(text, style = MaterialTheme.typography.labelLarge.tabular())
        }
    }
}

/**
 * The current time, moving every half minute, so countdowns on screen move
 * with it. The wait runs on wall-clock time, off the composition's frame
 * clock, so a test that advances that clock is never kept busy by it.
 */
@Composable
fun rememberTicker(every: Duration = Duration.ofSeconds(30)): androidx.compose.runtime.State<Instant> =
    androidx.compose.runtime.produceState(Instant.now(), every) {
        while (true) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { kotlinx.coroutines.delay(every.toMillis()) }
            value = Instant.now()
        }
    }

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

/** The small line above the headline: what state this is, and how sure. */
private fun eyebrow(day: TravelDay): String = when {
    day.source == TravelDaySource.Onboard -> "On board · from the train's Wi-Fi"
    day.missedChange -> "Looks like you missed it"
    day.cancelled -> "Change of plan"
    else -> when (day.kind) {
        TravelDayKind.BeforeLeaving -> "Time to leave"
        TravelDayKind.AtStation -> if (day.source == TravelDaySource.Location) "Looks like you're at the station" else "Head to the platform"
        TravelDayKind.OnBoard -> if (day.source == TravelDaySource.Location) "Looks like you're on board" else "On board"
        TravelDayKind.Changing -> if (day.source == TravelDaySource.Location) "Looks like you're changing trains" else "Change trains"
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
                    Headline(
                        if ((day.minutesToLeave ?: 0) <= 180) "Leave ${inMinutes(day.minutesToLeave)}" else "Leave at ${TripFormat.time(day.leaveAt)}",
                    )
                    Detail(
                        "${leg.label} leaves ${leg.from.name} at ${TripFormat.time(leg.from.expectedAt)}" +
                            (leg.from.shownPlatform?.let { " from Pl. $it" } ?: "") + ". $stationMinutes min to the station.",
                    )
                }
                day.missedChange && day.kind != TravelDayKind.OnBoard -> {
                    Headline("${leg.label} has left")
                    Detail("It left ${leg.from.name} at ${TripFormat.time(leg.from.expectedAt)}. Your ticket may allow another train.")
                }
                leg.cancelled -> {
                    Headline("${leg.label} is cancelled")
                    Detail("It was to leave ${leg.from.name} at ${TripFormat.time(leg.from.plannedAt)}.")
                }
                day.kind == TravelDayKind.AtStation || day.kind == TravelDayKind.Changing -> {
                    Headline(leg.from.shownPlatform?.let { "Platform $it" } ?: leg.label)
                    DetailWithDelay(
                        "${leg.label}${leg.headsign?.let { " to $it" } ?: ""} leaves " +
                            (if (day.kind == TravelDayKind.Changing) "${leg.from.name} " else "") +
                            "${inMinutes(day.minutesToDeparture)}, ${TripFormat.time(leg.from.expectedAt)}",
                        departureDelay(leg),
                    )
                    if (day.kind == TravelDayKind.Changing && change != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                            ConfidenceChip(change.confidence, minutes = change.availableMinutes)
                            val from = legs[change.arriving]
                            Text(
                                if (change.walkMinutes > 0) "Walk ${change.walkMinutes} min" else "Arriving at Pl. ${from.to.shownPlatform ?: "–"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                else -> OnBoard(trip, day, leg, change?.let { legs[it.departing] }, change?.availableMinutes, change?.walkMinutes ?: 0, change?.confidence)
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

@Composable
private fun DetailWithDelay(text: String, delay: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f, fill = false))
        DelayBadge(delay)
    }
}

@Composable
private fun OnBoard(
    trip: Trip,
    day: TravelDay,
    leg: Leg,
    next: Leg?,
    available: Int?,
    walk: Int,
    confidence: io.github.numynox.nexus.abeona.domain.Confidence?,
) {
    Headline("${leg.label}${leg.headsign?.let { " to $it" } ?: ""}")
    DetailWithDelay(
        "${if (next != null) "Get off at" else "Arrives at"} ${leg.to.name} ${inMinutes(day.minutesToArrival)}, ${TripFormat.time(leg.to.expectedAt)}" +
            (leg.to.shownPlatform?.let { " · Pl. $it" } ?: ""),
        arrivalDelay(leg),
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
                Text(
                    "Then ${next.label} at ${TripFormat.time(next.from.expectedAt)}" + (next.from.shownPlatform?.let { ", Pl. $it" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
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
    DetailWithDelay("At ${TripFormat.time(trip.expectedArrival)}", last?.let(::arrivalDelay) ?: 0)
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

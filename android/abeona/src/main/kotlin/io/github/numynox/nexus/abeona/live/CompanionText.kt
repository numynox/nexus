package io.github.numynox.nexus.abeona.live

import io.github.numynox.nexus.abeona.data.AlertSettings
import io.github.numynox.nexus.abeona.data.FareType
import io.github.numynox.nexus.abeona.data.Leg
import io.github.numynox.nexus.abeona.data.Trip
import io.github.numynox.nexus.abeona.domain.Confidence
import io.github.numynox.nexus.abeona.domain.TravelDay
import io.github.numynox.nexus.abeona.domain.TravelDayKind
import io.github.numynox.nexus.abeona.domain.TravelDayRules
import io.github.numynox.nexus.abeona.domain.RideProgress
import io.github.numynox.nexus.abeona.domain.TravelSignals
import io.github.numynox.nexus.abeona.domain.arrivalDelay
import io.github.numynox.nexus.abeona.domain.departureDelay
import io.github.numynox.nexus.abeona.domain.finalArrivalDelay
import io.github.numynox.nexus.abeona.domain.hasImpossibleChange
import io.github.numynox.nexus.abeona.domain.normalisePlatform
import io.github.numynox.nexus.abeona.domain.rideProgress
import io.github.numynox.nexus.abeona.domain.trainBindingLifted
import io.github.numynox.nexus.abeona.domain.transfers
import io.github.numynox.nexus.abeona.domain.intake.seatText
import io.github.numynox.nexus.abeona.domain.travelDay
import io.github.numynox.nexus.abeona.ui.common.TripFormat
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

/**
 * What the ongoing notification says. [title] is the one thing to know now
 * ("ICE 728 arrives in 1 h 25 min · Pl. 7 · 10:19"), to the minute; [text] the line under
 * it, which is all a collapsed notification shows, so it carries the time and
 * the delay of that next thing; [lines] everything, for the expanded one on
 * Android 15 and older, most urgent first. [chip] is the few characters
 * Android 16+ shows in the status bar for a Live Update ("12 min", "Pl. 7");
 * null when there is nothing to count down. [progress] is how far along the
 * ride on board is.
 *
 * Android 16+ draws the journey as a bar instead ([bar]), so its lines are
 * fewer: [info] is what the bar cannot say, most important first (the
 * change, the seat before boarding, the next stop, the speed), and
 * [compact] makes the text under the title of it. [broken] is a ride missed
 * or cancelled, or a change that cannot be made: the notification offers
 * the alternatives.
 */
data class CompanionStatus(
    val title: String,
    val text: String,
    val finished: Boolean,
    val chip: String? = null,
    val lines: List<String> = listOf(text),
    val progress: RideProgress? = null,
    val info: List<String> = listOf(text),
    val bar: JourneyBar? = null,
    val broken: Boolean = false,
) {
    /** The Live Update's text under the title, and in its header how fresh it is when that is worth saying. */
    fun compact(freshness: String?): CompactText = compactText(info, freshness)
}

/** A Live Update's text: [text] under the title, [subText] in the header. */
data class CompactText(val text: String, val subText: String?)

/**
 * The first of [info] under the title, the rest on a second line joined by
 * " · " ("Next: Würzburg Hbf 07:15 · 247 km/h"). The header is for
 * [freshness] only: a promoted Live Update does not show it, so nothing the
 * traveller needs may stand there.
 */
fun compactText(info: List<String>, freshness: String?): CompactText {
    val lines = info.filter { it.isNotBlank() }
    val rest = lines.drop(1).joinToString(" · ")
    return CompactText(lines.firstOrNull().orEmpty() + if (rest.isEmpty()) "" else "\n$rest", freshness)
}

/** What a piece of the journey bar is: a ride (the brand), or a change in its confidence's status colour. */
enum class BarTone { Ride, Ok, Watch, Bad }

/** A stretch of the bar, [length] long: a ride is [JourneyBar.RIDE]. */
data class BarSegment(val length: Int, val tone: BarTone)

/** A mark on the bar: a change, on the boundary between the rides before and after it. */
data class BarPoint(val position: Int, val tone: BarTone)

/**
 * The whole journey as Android 16's progress bar: one segment per ride, all
 * as long, so a three-hour ICE and a quarter of an hour on the S-Bahn are
 * both readable; the changes as points on the boundaries between them; and
 * [progress] where the trip is. [walking]: the traveller is on foot (a
 * change, or the way to the first train), so the tracker is a walker on the
 * point rather than the train.
 */
data class JourneyBar(val segments: List<BarSegment>, val points: List<BarPoint>, val progress: Int, val walking: Boolean = false) {
    val max: Int get() = segments.sumOf { it.length }

    companion object {
        /** A ride's length on the bar. */
        const val RIDE = 100
    }
}

internal fun barTone(confidence: Confidence): BarTone = when (confidence) {
    Confidence.SAFE -> BarTone.Ok
    Confidence.TIGHT -> BarTone.Watch
    Confidence.AT_RISK, Confidence.IMPOSSIBLE -> BarTone.Bad
}

/**
 * The journey bar for [legs] (the journey followed) at [now]: each ride a
 * segment of [JourneyBar.RIDE] in the brand, the changes points on the
 * boundaries in their confidence's colour, at most [maxPoints] (the next
 * ones ahead first, then the ones passed, nearest first; in the order they
 * come on the bar). Where the trip is: 0 before the first departure; on a
 * ride, its start and the share of its minutes gone by (expected times); at
 * a change, on its boundary; arrived, the end. On a ride or at a change as
 * the travel [day] says it when there is one, so a train missed leaves the
 * traveller at the change; else by the clock. On foot (changing, or before
 * leaving) it is [JourneyBar.walking]. Rides beyond [maxSegments]
 * (Android draws no more) make one segment, still with their boundaries.
 * Null without a ride or a time to start from.
 */
fun journeyBar(legs: List<Leg>, now: Instant, day: TravelDay? = null, maxSegments: Int = 15, maxPoints: Int = 4): JourneyBar? {
    val rides = legs.indices.filter { !legs[it].isWalk }
    if (rides.isEmpty() || legs[rides.first()].from.expectedAt == null) return null
    val ride = JourneyBar.RIDE
    val end = rides.size * ride

    /** How far along ride [n] the clock is: short of its end, so the train stays on it until it is off. */
    fun along(n: Int): Int {
        val from = legs[rides[n]].from.expectedAt ?: return 0
        val to = legs[rides[n]].to.expectedAt ?: return 0
        val span = Duration.between(from, to).toMinutes().takeIf { it > 0 } ?: return 0
        return (Duration.between(from, now).toMinutes() * ride / span).toInt().coerceIn(0, ride - 1)
    }
    var walking = false
    val progress = if (day != null) {
        val n = day.legIndex?.let(rides::indexOf)?.takeIf { it >= 0 }
        walking = day.kind == TravelDayKind.Changing || day.kind == TravelDayKind.BeforeLeaving
        when {
            day.kind == TravelDayKind.Arrived || n == null -> end
            day.kind == TravelDayKind.OnBoard -> n * ride + along(n)
            else -> n * ride
        }
    } else {
        // The first ride not yet over: before it leaves the trip stands on its start, on it at the share gone by.
        rides.indices.firstNotNullOfOrNull { n ->
            val from = legs[rides[n]].from.expectedAt
            val to = legs[rides[n]].to.expectedAt
            when {
                from != null && now.isBefore(from) -> {
                    // Between two rides: changing, on foot.
                    walking = n > 0
                    n * ride
                }
                to != null && now.isBefore(to) -> n * ride + along(n)
                else -> null
            }
        } ?: end
    }

    val changes = transfers(legs)
    val boundaries = (1 until rides.size).map { n ->
        BarPoint(n * ride, barTone(changes.firstOrNull { it.departing == rides[n] }?.confidence ?: Confidence.SAFE))
    }
    // A change being made stands where the trip is: still ahead until its train has left.
    val (ahead, passed) = boundaries.partition { it.position >= progress }
    val points = (ahead.take(maxPoints) + passed.reversed().take((maxPoints - ahead.size).coerceAtLeast(0))).sortedBy { it.position }
    val segments = if (rides.size > maxSegments) listOf(BarSegment(end, BarTone.Ride)) else rides.map { BarSegment(ride, BarTone.Ride) }
    return JourneyBar(segments, points, progress, walking)
}

/** "4 min", "1 h 05": short enough for the status bar chip. */
internal fun chipMinutes(minutes: Int?): String? {
    val m = minutes?.coerceAtLeast(0) ?: return null
    return if (m < 60) "$m min" else "${m / 60} h ${"%02d".format(m % 60)}"
}

/**
 * Which channel an alert goes to. [Act]: something to do now (a platform
 * change, a change at risk or impossible, a cancellation, a missed train,
 * getting off, the binding lifted), with sound. [Updates]: worth knowing
 * (delays, back on track, compensation thresholds).
 */
enum class AlertChannel { Act, Updates }

/**
 * Something worth a heads-up. [key] is the news itself: each is posted once.
 * [subject] is what it is about ("ride-1-platform", "change-1", "binding"):
 * the notification's id, so a newer alert about the same thing replaces the
 * older one. [silent] news needs no sound (things got better).
 */
data class CompanionAlert(
    val key: String,
    val title: String,
    val text: String,
    val subject: String = key,
    val channel: AlertChannel = AlertChannel.Act,
    val silent: Boolean = false,
)

private fun withDelay(text: String, delay: Int) = if (delay > 0) "$text (+$delay)" else text

/**
 * "Next: Aschaffenburg Hbf 10:35", "Now at Aschaffenburg Hbf · then Würzburg
 * Hbf", "Next stop: Würzburg Hbf": the ride's next stop, from the timetable
 * and the live times (domain/RideProgress.kt). How many are left is
 * [stopsLeft], on the notification's last line.
 */
fun nextStopLine(progress: RideProgress): String? {
    val last = progress.stops.lastIndex
    val current = progress.current
    val next = progress.next ?: return null
    return when {
        current != null && current in 1 until last ->
            "Now at ${progress.stops[current].name}" + if (next == last) " · then ${progress.stops[next].name}" else ""
        next == last -> "Next stop: ${progress.stops[next].name}"
        else -> "Next: ${progress.stops[next].name} ${TripFormat.time(progress.stops[next].arrival)}"
    }
}

/** "3 stops left", "last stop". */
fun stopsLeft(progress: RideProgress): String = if (progress.stopsLeft == 1) "last stop" else "${progress.stopsLeft} stops left"

/**
 * The notification for the travel day (domain/TravelDay.kt).
 * Before leaving: when to leave, then the first ride. At the station or
 * changing: the platform and the train, with the change's margin. On board:
 * the title is the train, when it arrives where the next thing happens, the
 * platform there and the time ("ICE 728 arrives in 1 h 25 min · Pl. 7 ·
 * 10:19"); then where to change ("Change at Würzburg Hbf") and the next
 * train (platform, time, delay, minutes to change,
 * and how the change looks unless it is safe), the next stop, and last how
 * many stops are left and the train's speed when its Wi-Fi says. A missed
 * change leads with "Looks like".
 * Finished once the last ride has arrived.
 */
fun companionStatus(
    trip: Trip,
    now: Instant,
    stationMinutes: Int = TravelDayRules.DEFAULT_STATION_MINUTES,
    signals: TravelSignals = TravelSignals(),
    onboard: OnboardStatus? = null,
): CompanionStatus {
    val day = travelDay(trip.legs, now, stationMinutes, signals)
    val index = day.legIndex
    val bar = journeyBar(trip.legs, now, day)
    if (day.kind == TravelDayKind.Arrived || index == null) {
        val last = trip.legs.lastOrNull { !it.isWalk }
        val text = withDelay("${trip.displayTitle} · ${TripFormat.time(trip.expectedArrival)}", last?.let(::arrivalDelay) ?: 0)
        return CompanionStatus("Arrived at ${trip.destinationName}", text, finished = true, bar = bar)
    }
    val leg = trip.legs[index]
    val platform = TripFormat.platform(leg.from.shownPlatform)
    val broken = brokenAhead(trip, now, day.missedChange)
    if (day.missedChange && day.kind != TravelDayKind.OnBoard) {
        return CompanionStatus(
            "Looks like you missed the ${leg.label}",
            "It left ${leg.from.name} at ${TripFormat.time(leg.from.expectedAt)} · see alternatives in Abeona",
            finished = false,
            chip = "Missed",
            bar = bar,
            broken = true,
        )
    }
    if (leg.cancelled) {
        return CompanionStatus(
            "${leg.label} is cancelled",
            "It was to leave ${leg.from.name} at ${TripFormat.time(leg.from.plannedAt)} · see alternatives in Abeona",
            finished = false,
            chip = "Cancelled",
            bar = bar,
            broken = true,
        )
    }
    val toward = leg.headsign ?: leg.to.name
    return when (day.kind) {
        TravelDayKind.BeforeLeaving -> {
            val ride = withDelay(
                listOfNotNull(leg.label, platform, TripFormat.time(leg.from.expectedAt)).joinToString(" · "),
                departureDelay(leg),
            ) + " from ${leg.from.name}"
            CompanionStatus(
                "Leave ${TripFormat.relative((day.minutesToLeave ?: 0).toLong())}",
                ride,
                finished = false,
                chip = chipMinutes(day.minutesToLeave),
                lines = listOf(ride, "$stationMinutes min to the station"),
                info = listOf(ride),
                bar = bar,
                broken = broken,
            )
        }
        TravelDayKind.AtStation, TravelDayKind.Changing -> {
            val title = listOfNotNull(platform, "${leg.label} ${TripFormat.relative((day.minutesToDeparture ?: 0).toLong())}").joinToString(" · ")
            val text = withDelay("to $toward · departs ${TripFormat.time(leg.from.expectedAt)}", departureDelay(leg))
            val lines = mutableListOf(text)
            val info = mutableListOf(text)
            if (day.kind == TravelDayKind.Changing) {
                transfers(trip.legs).firstOrNull { it.departing == index }?.let { change ->
                    val from = trip.legs[change.arriving].to.shownPlatform
                    val way = if (change.walkMinutes > 0) "walk ${change.walkMinutes} min" else from?.let { "from Pl. $it" }
                    lines += listOfNotNull("${change.availableMinutes} min to change · ${change.confidence.label}", way).joinToString(" · ")
                    // The bar's colour says how the change looks; the words only when it is not safe.
                    info += listOfNotNull(
                        "${change.availableMinutes} min to change" + if (change.confidence == Confidence.SAFE) "" else " · ${change.confidence.label.lowercase()}",
                        way,
                    ).joinToString(" · ")
                }
            }
            // Where to sit, while it still matters: before boarding.
            seatText(leg.coach, leg.seats)?.let { info += it }
            CompanionStatus(
                title,
                text,
                finished = false,
                chip = platform ?: chipMinutes(day.minutesToDeparture),
                lines = lines,
                info = info,
                bar = bar,
                broken = broken,
            )
        }
        else -> {
            val change = transfers(trip.legs).firstOrNull { it.arriving == index }
            val progress = rideProgress(leg, now)
            // The train, when it arrives where the next thing happens, the platform there and the time.
            val title = if (day.missedChange) {
                "Looks like you missed your change"
            } else {
                listOfNotNull(
                    "${leg.label} arrives ${TripFormat.relative((day.minutesToArrival ?: 0).toLong())}",
                    TripFormat.platform(leg.to.shownPlatform),
                    withDelay(TripFormat.time(leg.to.expectedAt), arrivalDelay(leg)),
                ).joinToString(" · ")
            }
            val lines = mutableListOf<String>()
            val info = mutableListOf<String>()
            // The last ride: the title names no place, so the destination leads the lines.
            if (change == null && !day.missedChange) {
                lines += leg.to.name
                info += leg.to.name
            }
            if (change != null) {
                val next = trip.legs[change.departing]
                val nextTrain = listOfNotNull(
                    TripFormat.platform(next.from.shownPlatform),
                    withDelay(TripFormat.time(next.from.expectedAt), departureDelay(next)),
                )
                // Where to change: first under the title, which has no room for a long station name.
                lines += "Change at ${leg.to.name}"
                lines += (listOf("Then ${next.label}") + nextTrain + listOf(
                    // Safe goes without saying; tight, at risk or impossible does not.
                    "${change.availableMinutes} min change" + if (change.confidence == Confidence.SAFE) "" else " (${change.confidence.label})",
                )).joinToString(" · ")
                info += (listOf("Change at ${leg.to.name} → ${next.label}") + nextTrain + listOf("${change.availableMinutes} min") +
                    listOfNotNull(change.confidence.takeIf { it != Confidence.SAFE }?.label?.lowercase())).joinToString(" · ")
            }
            // The stop coming up; not "Next stop: <where the title arrives>".
            val upcoming = progress?.takeIf { it.next != it.stops.lastIndex || (it.current ?: -1) in 1 until it.stops.lastIndex }
            upcoming?.let(::nextStopLine)?.let {
                lines += it
                info += it
            }
            val speed = onboard?.speedKmh?.takeIf { it >= 1 }?.let { "${it.roundToInt()} km/h" }
            // Last, small: how many stops are left and the train's speed (the freshness follows on this line).
            // The bar shows the stops left on Android 16, so its lines end with the speed.
            listOfNotNull(progress?.let(::stopsLeft), speed).takeIf { it.isNotEmpty() }?.let { lines += it.joinToString(" · ") }
            speed?.let { info += it }
            CompanionStatus(
                title,
                lines.firstOrNull() ?: leg.to.name,
                finished = false,
                chip = if (day.missedChange) "Missed" else chipMinutes(day.minutesToArrival),
                lines = lines.ifEmpty { listOf(leg.to.name) },
                progress = progress,
                info = info.ifEmpty { listOf(leg.to.name) },
                bar = bar,
                broken = broken,
            )
        }
    }
}

/** Something on the way ahead broke: the change looks missed, a ride still to come is cancelled, or a change cannot be made. */
private fun brokenAhead(trip: Trip, now: Instant, missed: Boolean): Boolean {
    val legs = trip.legs
    return missed ||
        legs.any { !it.isWalk && it.cancelled && it.to.expectedAt?.isAfter(now) != false } ||
        transfers(legs).any { it.confidence == Confidence.IMPOSSIBLE && legs[it.departing].from.expectedAt?.isAfter(now) != false }
}

/** The delay that matters for a ride now: its departure until it has left, then its arrival. */
private fun liveDelay(leg: Leg, now: Instant): Int =
    if (leg.from.expectedAt?.isAfter(now) == true) departureDelay(leg) else arrivalDelay(leg)

/**
 * The destination's delays worth an update, highest first, and what each is
 * worth: DB pays 25 % of the fare from 60 minutes and 50 % from 120
 * (abeona_compensation_rules; Android has no copy of the table).
 */
private val DestinationThresholds = listOf(120 to 50, 60 to 25)

/** A ride that is late by less than this is on time again, after a delay alert. */
private const val ON_TIME_MINUTES = 3

/**
 * What changed between two refreshes that someone on the platform needs to
 * know now, as far as [settings] asks for it. Only news: a platform that was
 * already changed is not repeated. [alerted] are the keys already posted
 * for this trip: "back on track" follows only an alert that something was
 * wrong. A ride's delay alerts once, when it reaches the threshold; after
 * that only its consequence at the destination: an expected delay of 60 or
 * 120 minutes, worth compensation.
 */
fun companionAlerts(
    before: Trip,
    after: Trip,
    now: Instant,
    settings: AlertSettings = AlertSettings(),
    alerted: Set<String> = emptySet(),
): List<CompanionAlert> {
    val alerts = mutableListOf<CompanionAlert>()
    after.legs.forEachIndexed { index, leg ->
        val old = before.legs.getOrNull(index) ?: return@forEachIndexed
        if (leg.isWalk) return@forEachIndexed
        if (settings.cancellation && leg.cancelled && !old.cancelled) {
            alerts += CompanionAlert(
                "cancel-$index",
                "${leg.label} is cancelled",
                "It was to leave ${leg.from.name} at ${TripFormat.time(leg.from.plannedAt)}. Abeona shows alternatives on the trip.",
                subject = "ride-$index-cancel",
            )
        }
        val departed = leg.from.expectedAt?.isBefore(now) ?: false
        val oldPlatform = normalisePlatform(old.from.shownPlatform)
        val newPlatform = normalisePlatform(leg.from.shownPlatform)
        if (settings.platformChange && !departed && newPlatform != null && oldPlatform != null && newPlatform != oldPlatform) {
            alerts += CompanionAlert(
                "platform-$index-$newPlatform",
                "Platform change: ${leg.label} now from Pl. ${leg.from.shownPlatform}",
                "At ${leg.from.name}, ${TripFormat.time(leg.from.expectedAt)} (was Pl. ${old.from.shownPlatform}).",
                subject = "ride-$index-platform",
            )
        }
    }
    if (settings.connectionAtRisk) {
        val oldChanges = transfers(before.legs).associateBy { it.departing }
        transfers(after.legs).forEach { change ->
            val was = oldChanges[change.departing]?.confidence
            val next = after.legs[change.departing]
            val station = next.from.name
            val subject = "change-${change.departing}"
            if (change.confidence == Confidence.IMPOSSIBLE && was != Confidence.IMPOSSIBLE) {
                val arriving = after.legs[change.arriving]
                alerts += CompanionAlert(
                    "impossible-${change.departing}",
                    "Your change at $station is impossible",
                    "${next.label} leaves at ${TripFormat.time(next.from.expectedAt)}, before the ${arriving.label} is in (${TripFormat.time(arriving.to.expectedAt)}). " +
                        "Abeona shows other ways to ${after.destinationName}.",
                    subject = subject,
                )
            } else if (change.confidence == Confidence.AT_RISK && was != Confidence.AT_RISK && was != Confidence.IMPOSSIBLE) {
                alerts += CompanionAlert(
                    "risk-${change.departing}",
                    "Your change at $station is at risk",
                    "${change.availableMinutes} min to change, about ${change.requiredMinutes} needed. ${next.label} leaves ${TripFormat.time(next.from.expectedAt)}.",
                    subject = subject,
                )
            } else if ((change.confidence == Confidence.SAFE || change.confidence == Confidence.TIGHT) &&
                (was == Confidence.AT_RISK || was == Confidence.IMPOSSIBLE) &&
                ("risk-${change.departing}" in alerted || "impossible-${change.departing}" in alerted)
            ) {
                // Back on track: the alert that it would not work is replaced, quietly.
                alerts += CompanionAlert(
                    "works-${change.departing}",
                    "Change at $station works again",
                    listOfNotNull("${change.availableMinutes} min", next.label + (next.from.shownPlatform?.let { " from Pl. $it" } ?: "")).joinToString(" · "),
                    subject = subject,
                    channel = AlertChannel.Updates,
                    silent = true,
                )
            }
        }
    }
    val delay = finalArrivalDelay(after.legs)
    if (trainBindingLifted(after.fareType, after.trainBinding, delay, hasImpossibleChange(after.legs)) &&
        !trainBindingLifted(before.fareType, before.trainBinding, finalArrivalDelay(before.legs), hasImpossibleChange(before.legs))
    ) {
        // As the banner says it: the words have legal weight.
        alerts += CompanionAlert(
            "binding",
            "You may take another train",
            if (delay >= 20) "Expected +$delay min at ${after.destinationName}: train binding lifted." else "A change cannot be made: train binding lifted.",
        )
    }
    val step = settings.delayMinutes
    if (step > 0) {
        // The ride that matters now: the first one that has not arrived.
        val index = after.legs.indices.firstOrNull { !after.legs[it].isWalk && after.legs[it].to.expectedAt?.isAfter(now) == true }
        val leg = index?.let { after.legs[it] }
        val old = index?.let { before.legs.getOrNull(it) }
        if (leg != null && old != null && !leg.cancelled) {
            val late = liveDelay(leg, now)
            val was = liveDelay(old, now).coerceAtLeast(0)
            val leaving = leg.from.expectedAt?.isAfter(now) == true
            if (late >= step && was < step) {
                alerts += CompanionAlert(
                    "delay-$index",
                    "${leg.label} is now +$late",
                    if (leaving) {
                        "Leaves ${leg.from.name} at ${TripFormat.time(leg.from.expectedAt)} instead of ${TripFormat.time(leg.from.plannedAt)}."
                    } else {
                        "Arrives at ${leg.to.name} at ${TripFormat.time(leg.to.expectedAt)} instead of ${TripFormat.time(leg.to.plannedAt)}."
                    },
                    subject = "ride-$index-delay",
                    channel = AlertChannel.Updates,
                )
            } else if (late < ON_TIME_MINUTES && was >= ON_TIME_MINUTES && "delay-$index" in alerted) {
                alerts += CompanionAlert(
                    "ontime-$index",
                    "${leg.label} is on time again",
                    if (leaving) "Leaves ${leg.from.name} at ${TripFormat.time(leg.from.plannedAt)}" else "Arrives at ${leg.to.name} at ${TripFormat.time(leg.to.plannedAt)}",
                    subject = "ride-$index-delay",
                    channel = AlertChannel.Updates,
                    silent = true,
                )
            }
        }
        // Then what the delays add up to at the destination, once per threshold: the highest one reached now.
        val was = finalArrivalDelay(before.legs)
        DestinationThresholds.firstOrNull { (minutes, _) -> delay >= minutes && was < minutes }?.let { (minutes, percent) ->
            val flat = after.fareType == FareType.Deutschlandticket || after.fareType == FareType.BahnCard100
            alerts += CompanionAlert(
                "destination-$minutes",
                "Expected +$delay min at ${after.destinationName}",
                if (flat) "Compensation: DB's flat rate" else "Compensation: $percent % of the fare",
                subject = "destination-delay",
                channel = AlertChannel.Updates,
            )
        }
    }
    return alerts
}

/** A heads-up when a signal says the traveller missed a change (or the first train): once per ride. */
fun missedAlert(trip: Trip, now: Instant, stationMinutes: Int, signals: TravelSignals, settings: AlertSettings): CompanionAlert? {
    if (!settings.connectionAtRisk) return null
    val day = travelDay(trip.legs, now, stationMinutes, signals)
    if (!day.missedChange) return null
    val missed = when (day.kind) {
        TravelDayKind.OnBoard -> trip.legs.indices.firstOrNull { it > (day.legIndex ?: return null) && !trip.legs[it].isWalk }
        else -> day.legIndex
    } ?: return null
    val leg = trip.legs[missed]
    return CompanionAlert(
        "missed-$missed",
        "Looks like you missed the ${leg.label}",
        "It left ${leg.from.name} at ${TripFormat.time(leg.from.expectedAt)}. Abeona shows alternatives your ticket covers.",
        subject = "missed",
    )
}

/** How long before the arrival the get-off reminder comes. */
private val GET_OFF_BEFORE: Duration = Duration.ofMinutes(5)

/**
 * Time to get off: once per ride, when the traveller is on board it and its
 * expected arrival is [GET_OFF_BEFORE] away or less, and not yet passed.
 * Worked out from the stored times on every tick, so it comes without a
 * connection too. "Get off at Würzburg Hbf in 5 min", then the platform
 * there, the next train and its platform at a change, and the side the doors
 * open on when the train's Wi-Fi says (phase 5); a platform not known is left out.
 */
fun getOffAlert(
    trip: Trip,
    now: Instant,
    stationMinutes: Int,
    signals: TravelSignals,
    settings: AlertSettings,
    onboard: OnboardStatus? = null,
): CompanionAlert? {
    if (!settings.getOffReminder) return null
    val day = travelDay(trip.legs, now, stationMinutes, signals)
    if (day.kind != TravelDayKind.OnBoard || day.missedChange) return null
    val index = day.legIndex ?: return null
    val leg = trip.legs[index]
    if (leg.cancelled) return null
    val left = Duration.between(now, leg.to.expectedAt ?: return null)
    if (left.isNegative || left.isZero || left > GET_OFF_BEFORE) return null
    val minutes = Math.ceil(left.toMillis() / 60_000.0).toLong()
    val next = transfers(trip.legs).firstOrNull { it.arriving == index }?.let { trip.legs[it.departing] }
    return CompanionAlert(
        "getoff-$index",
        "Get off at ${leg.to.name} ${TripFormat.relative(minutes)}",
        listOfNotNull(
            TripFormat.platform(leg.to.shownPlatform),
            next?.let { n -> "then ${n.label}" + (n.from.shownPlatform?.let { " from Pl. $it" } ?: "") },
            exitSideAt(onboard, leg.to.name)?.let { "exit ${it.word}" },
        ).joinToString(" · "),
        subject = "getoff-$index",
    )
}

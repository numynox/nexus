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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DoorSliding
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.MyLocation
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.numynox.nexus.abeona.data.Approach
import io.github.numynox.nexus.abeona.data.Leg
import io.github.numynox.nexus.abeona.data.LegMode
import io.github.numynox.nexus.abeona.data.StopTime
import io.github.numynox.nexus.abeona.data.Trip
import io.github.numynox.nexus.abeona.domain.AlternativesReason
import io.github.numynox.nexus.abeona.domain.Confidence
import io.github.numynox.nexus.abeona.domain.Position
import io.github.numynox.nexus.abeona.domain.RideProgress
import io.github.numynox.nexus.abeona.domain.Transfer
import io.github.numynox.nexus.abeona.domain.TravelDay
import io.github.numynox.nexus.abeona.domain.TravelDayKind
import io.github.numynox.nexus.abeona.domain.TravelDaySource
import io.github.numynox.nexus.abeona.domain.arrivalDelay
import io.github.numynox.nexus.abeona.domain.delayMinutes
import io.github.numynox.nexus.abeona.domain.finalArrivalDelay
import io.github.numynox.nexus.abeona.domain.intake.seatText
import io.github.numynox.nexus.abeona.domain.followsReplacement
import io.github.numynox.nexus.abeona.domain.platformChanged
import io.github.numynox.nexus.abeona.domain.rideProgress
import io.github.numynox.nexus.abeona.domain.trainBindingLifted
import io.github.numynox.nexus.abeona.domain.transfers
import io.github.numynox.nexus.abeona.live.Evidence
import io.github.numynox.nexus.abeona.live.EvidenceKind
import io.github.numynox.nexus.abeona.live.EvidenceState
import io.github.numynox.nexus.abeona.live.ExitSide
import io.github.numynox.nexus.abeona.live.OnboardStatus
import io.github.numynox.nexus.abeona.live.exitSideAt
import io.github.numynox.nexus.abeona.ui.AbeonaType
import io.github.numynox.nexus.abeona.ui.bad
import io.github.numynox.nexus.abeona.ui.brand
import io.github.numynox.nexus.abeona.ui.brandSoft
import io.github.numynox.nexus.abeona.ui.burgundy
import io.github.numynox.nexus.abeona.ui.common.ConfidenceChip
import io.github.numynox.nexus.abeona.ui.common.Fact
import io.github.numynox.nexus.abeona.ui.common.LineChip
import io.github.numynox.nexus.abeona.ui.common.MarkIcon
import io.github.numynox.nexus.abeona.ui.common.PlatformBox
import io.github.numynox.nexus.abeona.ui.common.Rail
import io.github.numynox.nexus.abeona.ui.common.RailDot
import io.github.numynox.nexus.abeona.ui.common.RailLine
import io.github.numynox.nexus.abeona.ui.common.RowGrid
import io.github.numynox.nexus.abeona.ui.common.SeatFact
import io.github.numynox.nexus.abeona.ui.common.ShiftedTime
import io.github.numynox.nexus.abeona.ui.common.StatusChip
import io.github.numynox.nexus.abeona.ui.common.StatusTone
import io.github.numynox.nexus.abeona.ui.common.StopRow
import io.github.numynox.nexus.abeona.ui.common.TripFormat
import io.github.numynox.nexus.abeona.ui.hairline
import io.github.numynox.nexus.abeona.ui.muted
import io.github.numynox.nexus.abeona.ui.scene.Scene
import io.github.numynox.nexus.abeona.ui.scene.SceneModel
import io.github.numynox.nexus.abeona.ui.scene.SceneSize
import io.github.numynox.nexus.abeona.ui.scene.SceneState
import io.github.numynox.nexus.abeona.ui.scene.SceneStop
import io.github.numynox.nexus.abeona.ui.scene.TrainKind
import io.github.numynox.nexus.abeona.ui.trip.MapMarker
import io.github.numynox.nexus.abeona.ui.trip.TripMap
import io.github.numynox.nexus.abeona.ui.watch
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
    approach: Approach? = null,
    lifted: Boolean = false,
    onBackToPlan: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LiveCard(
            view,
            status = status,
            onAlternatives = onAlternatives,
            onUseLocation = onUseLocation,
            approach = approach,
            lifted = lifted,
            onBackToPlan = onBackToPlan,
        )
        if (showMap) LiveMap(view)
    }
}

/**
 * The map under the live card, with the traveller on it. Nothing once
 * arrived. [fill]: the whole space given (the trip screen's right pane on a
 * wide screen), else 240 high.
 */
@Composable
fun LiveMap(view: LiveView, modifier: Modifier = Modifier, fill: Boolean = false) {
    if (view.day.kind == TravelDayKind.Arrived) return
    TripMap(
        view.trip.legs,
        if (fill) modifier.fillMaxSize() else modifier.fillMaxWidth().height(240.dp),
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
 * How fresh the live values are, for the card's last line: an icon, then
 * [label] and [text] as one line ("Live 17:44", "Offline 17:44"); [label]
 * may be empty where [text] says it all ("Timetable only").
 */
data class LiveStatus(val kind: LiveStatusKind, val label: String, val text: String) {
    val line: String get() = listOf(label, text).filter { it.isNotBlank() }.joinToString(" ")
}

// ---------------------------------------------------------------- the card

/** "Frankfurt(Main)Hbf" → "Frankfurt": a station's town, for matching the train's own name of a stop. */
private fun short(name: String): String = name.substringBefore('(').substringBefore(" Hbf").substringBefore(" Hauptbahnhof").trim()

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

/** "Train left Aschaffenburg Hbf · 3 stops away": where the train to board is on its way here. */
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

/** The train a ride is drawn as in the scene. */
internal fun sceneTrain(leg: Leg?): TrainKind =
    leg?.let { SceneModel.trainKind(it.product, it.label, it.mode == LegMode.Bus) } ?: TrainKind.Ice

/** The word above the step to do next, or above a step that broke, with its icon from the vocabulary. */
private enum class Verb(val text: String, val icon: ImageVector, val broken: Boolean = false) {
    Leave("Leave", Icons.Rounded.MeetingRoom),
    Board("Board", Icons.Rounded.Train),
    GetOff("Get off", Icons.AutoMirrored.Rounded.Logout),
    Arrive("Arrive", Icons.Rounded.Flag),
    Arrived("Arrived", Icons.Rounded.Flag),
    Missed("Missed", Icons.Rounded.ErrorOutline, broken = true),
    Cancelled("Cancelled", Icons.Rounded.Block, broken = true),
    NotRunning("Not running", Icons.Rounded.Block, broken = true),
}

/** The time and the station of the step to do next: larger than the other rows. */
private val StepTimeStyle = AbeonaType.headline.copy(fontSize = 19.sp, lineHeight = 24.sp)
private val StepNameStyle = AbeonaType.headline.copy(fontSize = 22.sp, lineHeight = 28.sp)

/**
 * One row of the journey line, drawn by [draw] with its piece of the rail.
 * [above] null: the line from the row before runs on into it; [below] null:
 * it runs on through it. [dim]: after a step that broke. Rows of one
 * [group] (a step: its verb, its row, its countdown, its seat) are one node
 * for a screen reader, which reads [speech] for all of them.
 */
private class LineRow(
    val key: String,
    val dot: RailDot,
    val above: RailLine?,
    val below: RailLine?,
    val dim: Boolean,
    val group: String? = null,
    val speech: String? = null,
    val draw: @Composable (Rail) -> Unit,
)

/** "in 12 minutes", "now": a countdown as a screen reader says it. */
private fun spokenIn(minutes: Int?): String? = minutes?.let { m ->
    when {
        m <= 0 -> "now"
        m < 60 -> "in $m ${if (m == 1) "minute" else "minutes"}"
        else -> "in ${m / 60} ${if (m / 60 == 1) "hour" else "hours"} ${m % 60} minutes"
    }
}

/** "14:14, 7 minutes late": a time as the row shows it, said. */
private fun spokenTime(planned: Instant?, expected: Instant?): String? {
    val shown = expected ?: planned ?: return null
    val delay = delayMinutes(planned, expected)
    return if (delay > 0) "${TripFormat.time(shown)}, $delay minutes late" else TripFormat.time(shown)
}

private fun spokenPlatform(stop: StopTime): String? = stop.shownPlatform?.let { "platform $it" }

/** "Coach 24, seat 66": the seat as the row shows it, said. */
private fun spokenSeat(leg: Leg): String? = seatText(leg.coach, leg.seats)?.replace(" · S", ", s")

/** "ICE 25 to Wien Hbf". */
private fun spokenTrain(leg: Leg): String = leg.label + (leg.headsign?.let { " to $it" } ?: "")

/** Parts of a row, said as one sentence: commas between, a full stop at the end, then [after] (more sentences). */
private fun sentence(vararg parts: String?, after: List<String?> = emptyList()): String =
    (listOf(parts.filterNotNull().filter { it.isNotBlank() }.joinToString(", ")) + after.filterNotNull())
        .filter { it.isNotBlank() }
        .joinToString(" ") { if (it.endsWith(".")) it else "$it." }

/**
 * The live card (abeona-design.md §8): the journey drawn as one line in the
 * row grid. Done rides fold into one row; the ride on board, or the one to
 * board, is the band (the scene strip on its stretch of line, the train and
 * where it is); the step to do next is the one large row, with its verb, its
 * time, station, countdown and platform; then the rest of the way down to
 * the destination, always last; then how fresh it all is and, as an icon,
 * how Abeona knows (it opens the sheet). A step that broke turns red, with
 * the way to the alternatives under it, and dims what follows.
 *
 * [summary] is the compact form on the Trips screen: the band without its
 * second line, the step to do next, and one row with the change, the next
 * train and the arrival; nothing else.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun LiveCard(
    view: LiveView,
    modifier: Modifier = Modifier,
    status: LiveStatus? = null,
    onAlternatives: (() -> Unit)? = null,
    onUseLocation: (() -> Unit)? = null,
    summary: Boolean = false,
    /** While waiting for the train: where it is on its way here (rememberApproach). */
    approach: Approach? = null,
    /** The ticket's train binding is lifted: "Any train". */
    lifted: Boolean = false,
    /** A connection from the alternatives is followed: the way back to the plan, under the destination. */
    onBackToPlan: (() -> Unit)? = null,
    /** A ride no longer in the timetable: the search from where it would have left (the trip's own). */
    onDroppedAlternatives: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    var showSource by rememberSaveable { mutableStateOf(false) }
    if (showSource) {
        ModalBottomSheet(onDismissRequest = { showSource = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            HowAbeonaKnows(view.evidence, view.day, onUseLocation, Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp))
        }
    }
    val arrived = view.day.kind == TravelDayKind.Arrived || view.leg == null
    val lastRide = view.trip.legs.lastOrNull { !it.isWalk }
    val hole = brandSoft.container
    val rows = journeyRows(view, summary, approach, lifted, onAlternatives, onDroppedAlternatives, onBackToPlan)

    val content: @Composable () -> Unit = {
        Column {
            if (arrived) {
                val at = lastRide?.to
                Scene(SceneState.Arrived, size = SceneSize.Header, train = sceneTrain(lastRide), lat = at?.lat, lon = at?.lon)
            }
            JourneyLine(rows, hole, Modifier.padding(start = 16.dp, end = 16.dp, top = if (arrived) 0.dp else 12.dp, bottom = if (summary) 12.dp else 4.dp))
            if (!summary) Footer(status, view.day.source) { showSource = true }
        }
    }
    val shape = RoundedCornerShape(24.dp)
    if (onClick != null) {
        Surface(onClick = onClick, color = brandSoft.container, contentColor = MaterialTheme.colorScheme.onSurface, shape = shape, modifier = modifier.fillMaxWidth(), content = content)
    } else {
        Surface(color = brandSoft.container, contentColor = MaterialTheme.colorScheme.onSurface, shape = shape, modifier = modifier.fillMaxWidth(), content = content)
    }
}

/**
 * The rows on one continuous line: each row's rail starts where the one
 * before ended. The rows of a group are one sentence to a screen reader.
 */
@Composable
private fun JourneyLine(rows: List<LineRow>, hole: Color, modifier: Modifier = Modifier) {
    // Each row's rail first: a group draws several rows at once.
    val rails = mutableListOf<Rail>()
    var line = RailLine.None
    rows.forEach { row ->
        val above = row.above ?: line
        val below = row.below ?: above
        line = below
        rails += Rail(above, below, row.dot, hole)
    }
    @Composable
    fun draw(i: Int) {
        val row = rows[i]
        key(row.key) {
            Box(if (row.dim) Modifier.alpha(0.45f) else Modifier) { row.draw(rails[i]) }
        }
    }
    Column(modifier.fillMaxWidth()) {
        var i = 0
        while (i < rows.size) {
            val group = rows[i].group
            if (group == null) {
                draw(i)
                i++
                continue
            }
            var end = i
            while (end + 1 < rows.size && rows[end + 1].group == group) end++
            val speech = (i..end).firstNotNullOfOrNull { rows[it].speech }
            key("group-$group") {
                Column(Modifier.fillMaxWidth().clearAndSetSemantics { speech?.let { contentDescription = it } }) {
                    for (k in i..end) draw(k)
                }
            }
            i = end + 1
        }
    }
}

/**
 * The journey as rows on the line, top to bottom (abeona-design.md §8): what
 * is done, the step to do next and the band, the rest of the way, the
 * destination. Exactly one large row, one countdown.
 */
@OptIn(ExperimentalLayoutApi::class)
private fun journeyRows(
    view: LiveView,
    compact: Boolean,
    approach: Approach?,
    lifted: Boolean,
    onAlternatives: (() -> Unit)?,
    onDroppedAlternatives: (() -> Unit)?,
    onBackToPlan: (() -> Unit)?,
): List<LineRow> {
    val trip = view.trip
    val legs = trip.legs
    val day = view.day
    val rides = legs.indices.filter { !legs[it].isWalk }
    val last = rides.lastOrNull() ?: return emptyList()
    val changes = transfers(legs)
    fun changeBefore(k: Int) = changes.firstOrNull { it.departing == k }
    fun changeAfter(k: Int) = changes.firstOrNull { it.arriving == k }

    val rows = mutableListOf<LineRow>()
    var dim = false
    var alternativesShown = false
    var anyTrainShown = false
    // The step being added, and how a screen reader says it: its rows are one sentence.
    var group: String? = null
    var speech: String? = null
    fun add(key: String, dot: RailDot = RailDot.None, above: RailLine? = null, below: RailLine? = null, dimmed: Boolean = dim, draw: @Composable (Rail) -> Unit) {
        rows += LineRow(key, dot, above, below, dimmed, group, speech, draw)
    }
    /** The rows [block] adds read as [says]. Nothing that can be pressed belongs in one: it would be hidden. */
    fun spoken(name: String, says: String, block: () -> Unit) {
        group = name
        speech = says
        block()
        group = null
        speech = null
    }

    // The binding is lifted by the ticket's rules (the caller's), or by a ride that does not run.
    fun liftedFor(k: Int?): Boolean = lifted ||
        (k != null && legs[k].cancelled && trainBindingLifted(trip.fareType, trip.trainBinding, finalArrivalDelay(legs), connectionBroken = true))
    // A ride no longer in the timetable has its own search when the trip offers it; anything else the live one.
    fun alternativesFor(k: Int?): (() -> Unit)? = when {
        k != null && legs[k].dropped && onDroppedAlternatives != null -> onDroppedAlternatives
        view.reason != null -> onAlternatives
        else -> null
    }

    /** The verb above a step, on the line. */
    fun verb(key: String, verb: Verb) = add("$key-verb") { rail -> VerbRow(verb, rail) }

    /** The line under a step: its countdown, the walk to the station, what to do about it. */
    fun under(key: String, dimmed: Boolean = dim, content: @Composable RowScope.() -> Unit) =
        add("$key-under", dimmed = dimmed) { rail -> StopRow(name = "", rail = rail, minHeight = 24.dp, content = content) }

    fun countdown(minutes: Int?): String? = minutes?.let { TripFormat.relative(it.toLong()) }

    /** A step that broke: red, the train and its time struck, the way to the alternatives and whether any train will do. */
    fun brokenStep(k: Int, verb: Verb, large: Boolean, below: RailLine) {
        val ride = legs[k]
        val action = alternativesFor(k)
        val anyTrain = liftedFor(k)
        spoken("broken-$k", sentence("${verb.text}: ${spokenTrain(ride)} from ${ride.from.name}", TripFormat.time(ride.from.plannedAt))) {
        verb("broken-$k", verb)
        add("broken-$k", RailDot.Broken, below = below) { rail ->
            StopRow(
                name = "",
                planned = ride.from.plannedAt, expected = ride.from.expectedAt, cancelled = true,
                rail = rail,
                timeStyle = if (large) StepTimeStyle else null,
                minHeight = if (large) 44.dp else 40.dp,
                content = {
                    LineChip(ride, large = large)
                    ride.headsign?.let {
                        Text("to $it", style = if (large) AbeonaType.title else AbeonaType.body, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    }
                },
            )
        }
        // The large one says where: the station it was to leave from.
        if (large) under("broken-$k-at") { Text(ride.from.name, style = AbeonaType.body, color = muted, maxLines = RowGrid.lines, overflow = TextOverflow.Ellipsis) }
        }
        val button = action?.takeIf { !alternativesShown }
        if (button != null || anyTrain) {
            under("broken-$k") { Actions(button, filled = true, anyTrain = anyTrain) }
            if (button != null) alternativesShown = true
            if (anyTrain) anyTrainShown = true
        }
        dim = true
    }

    /** A change on the line: how it looks, and the way between the platforms; what to do when it will not work. */
    fun change(key: String, change: Transfer, below: RailLine = RailLine.Dashed) {
        val trouble = change.confidence == Confidence.AT_RISK || change.confidence == Confidence.IMPOSSIBLE
        val impossible = change.confidence == Confidence.IMPOSSIBLE
        val action = if (trouble && !alternativesShown && !compact) alternativesFor(null) else null
        val anyTrain = impossible && lifted && !anyTrainShown
        if (action != null) alternativesShown = true
        if (anyTrain) anyTrainShown = true
        val row = { add(key, above = null, below = below) { rail -> ChangeRow(change, legs, rail, action, impossible, anyTrain) } }
        if (action == null && !anyTrain) spoken(key, spokenChange(change, legs), row) else row()
        if (impossible) dim = true
    }

    /** Where a ride starts or ends, as a stop on the line; [quiet] once it is behind (and its platform no longer matters). */
    fun stop(key: String, at: StopTime, dot: RailDot, below: RailLine, quiet: Boolean = false) =
        spoken(key, sentence(at.name, spokenTime(at.plannedAt, at.expectedAt), spokenPlatform(at).takeIf { !quiet })) {
            add(key, dot, below = below) { rail ->
                StopRow(
                    name = at.name, planned = at.plannedAt, expected = at.expectedAt,
                    platformPlanned = at.plannedPlatform.takeIf { !quiet }, platformCurrent = at.platform.takeIf { !quiet },
                    quiet = quiet, rail = rail,
                )
            }
        }

    fun destination(dimmed: Boolean = dim) {
        val end = legs[last].to
        val anyTrain = lifted && !anyTrainShown && !compact
        if (anyTrain) anyTrainShown = true
        val says = sentence(
            "Destination ${trip.destinationName}", spokenTime(end.plannedAt, end.expectedAt), spokenPlatform(end),
            after = listOf(if (anyTrain) "Train binding lifted: any train" else null),
        )
        spoken("destination", says) { add("destination", below = RailLine.None, dimmed = dimmed) { rail ->
            StopRow(
                name = trip.destinationName, planned = end.plannedAt, expected = end.expectedAt,
                platformPlanned = end.plannedPlatform, platformCurrent = end.platform,
                rail = rail,
                mark = { DestinationMark(rail.hole) },
                trailing = { if (anyTrain) AnyTrainChip() },
            )
        } }
    }

    /** Where the ride ends: the destination, or the stop to get off at before a change. */
    fun end(k: Int) = if (k == last) destination() else stop("end-$k", legs[k].to, RailDot.Stop, RailLine.Dashed)

    val index = day.legIndex
    if (day.kind == TravelDayKind.Arrived || index == null) {
        if (!compact) add("done", RailDot.Done, above = RailLine.None, below = RailLine.Done) { rail -> DoneRow(rides.map(legs::get), trip.followsReplacement, rail) }
        val end = legs[last].to
        spoken("arrived", sentence("Arrived at ${trip.destinationName}", spokenPlatform(end), spokenTime(end.plannedAt, end.expectedAt))) {
            verb("arrived", Verb.Arrived)
            // The line ends at the flag: nothing is left to do.
            add("arrived", RailDot.None, below = RailLine.None) { rail ->
                StopRow(
                    name = "", planned = end.plannedAt, expected = end.expectedAt, rail = rail, timeStyle = StepTimeStyle, minHeight = 44.dp,
                    mark = { DestinationMark(rail.hole) },
                    content = { StepName(trip.destinationName) },
                    trailing = { PlatformBox(end.plannedPlatform, end.platform, lead = true) },
                )
            }
        }
        return rows
    }

    val ride = legs[index]
    val onBoard = day.kind == TravelDayKind.OnBoard
    val missedHere = day.missedChange && !onBoard
    val brokenHere = missedHere || ride.cancelled
    val done = rides.filter { it < index }
    if (done.isNotEmpty() && !compact) {
        add("done", RailDot.Done, above = RailLine.None, below = RailLine.Done) { rail -> DoneRow(done.map(legs::get), trip.followsReplacement, rail) }
    }

    // The step to do before the band's ride, or the one that broke in its place.
    if (brokenHere) {
        brokenStep(index, if (missedHere) Verb.Missed else if (ride.dropped) Verb.NotRunning else Verb.Cancelled, large = true, below = RailLine.Faint)
        dim = false
    } else {
        when (day.kind) {
            TravelDayKind.BeforeLeaving -> {
                val says = sentence(
                    "Leave at ${TripFormat.time(day.leaveAt)}", spokenIn(day.minutesToLeave),
                    after = listOf("Walk to the station: ${view.stationMinutes} minutes"),
                )
                spoken("leave", says) {
                    verb("leave", Verb.Leave)
                    add("leave", RailDot.Active, below = RailLine.Dashed) { rail ->
                        StopRow(
                            name = "", planned = day.leaveAt, expected = day.leaveAt, rail = rail, timeStyle = StepTimeStyle, minHeight = 44.dp,
                            content = { StepName(countdown(day.minutesToLeave) ?: ride.from.name, color = if (day.minutesToLeave != null) brand else Color.Unspecified) },
                        )
                    }
                    under("leave") { Fact(Icons.AutoMirrored.Rounded.DirectionsWalk, "Walk to the station", "${view.stationMinutes} min") }
                }
                if (!compact) stop("board", ride.from, RailDot.Stop, RailLine.Brand)
            }
            TravelDayKind.AtStation, TravelDayKind.Changing -> {
                if (day.kind == TravelDayKind.Changing && !compact) changeBefore(index)?.let { change("change-now", it) }
                val says = sentence(
                    "Board ${spokenTrain(ride)} at ${ride.from.name}", spokenPlatform(ride.from),
                    spokenTime(ride.from.plannedAt, ride.from.expectedAt), spokenIn(day.minutesToDeparture),
                    after = listOf(spokenSeat(ride).takeIf { !compact }),
                )
                spoken("board", says) {
                    verb("board", Verb.Board)
                    add("board", RailDot.Active, below = RailLine.Brand) { rail ->
                        StopRow(
                            name = "", planned = ride.from.plannedAt, expected = ride.from.expectedAt, rail = rail, timeStyle = StepTimeStyle, minHeight = 44.dp,
                            content = { StepName(ride.from.name) },
                            trailing = { PlatformBox(ride.from.plannedPlatform, ride.from.platform, lead = true) },
                        )
                    }
                    countdown(day.minutesToDeparture)?.let { text -> under("board") { Countdown(text) } }
                    // Where to sit, under the countdown: not on the compact card.
                    if (!compact && seatText(ride.coach, ride.seats) != null) under("board-seat") { SeatFact(ride.coach, ride.seats) }
                }
            }
            else -> if (!compact) stop("boarded", ride.from, RailDot.Done, RailLine.Brand, quiet = true)
        }
    }

    // The band: the ride on board, or the one to board, on its stretch of line.
    val progress = view.progress
    val line = if (brokenHere) RailLine.Faint else RailLine.Brand
    add("band", above = line, below = line, dimmed = false) { rail ->
        BandRow(
            view, ride, rail,
            state = when {
                brokenHere -> SceneState.Stopped
                onBoard -> SceneState.Riding
                else -> SceneState.Waiting
            },
            // Before leaving the rail stays empty: the ride is not the question yet.
            stops = progress?.takeIf { !brokenHere && day.kind != TravelDayKind.BeforeLeaving }?.stops?.map { SceneStop(it.fraction, it.name) },
            caption = !brokenHere,
            second = when {
                compact || brokenHere -> null
                onBoard && progress != null -> { -> PassingStops(progress) }
                approach != null -> { -> Text(approachText(approach), style = AbeonaType.meta, color = muted, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                else -> null
            },
        )
    }
    if (brokenHere) dim = true

    // Where the band's ride ends: on board the step to do next.
    if (onBoard && !brokenHere) {
        val end = ride.to
        val arrive = index == last
        // The train's Wi-Fi knows the platform of its next stop: where to get off, when the timetable does not.
        val track = view.onboard?.nextTrack?.takeIf {
            end.shownPlatform == null && progress?.next == progress?.stops?.lastIndex &&
                view.onboard.nextStop?.let { n -> n.startsWith(short(end.name)) || end.name.startsWith(n) } != false
        }
        val anyTrain = arrive && lifted
        if (anyTrain) anyTrainShown = true
        val text = countdown(day.minutesToArrival)
        // The train's Wi-Fi knows the side the doors open on: under the countdown.
        val exit = if (compact) null else exitSideAt(view.onboard, end.name)
        val step = if (arrive) Verb.Arrive else Verb.GetOff
        val says = sentence(
            "${step.text} at ${if (arrive) trip.destinationName else end.name}", (end.shownPlatform ?: track)?.let { "platform $it" },
            spokenTime(end.plannedAt, end.expectedAt), spokenIn(day.minutesToArrival),
            after = listOf(exit?.let { "Exit ${it.word}" }, if (anyTrain) "Train binding lifted: any train" else null),
        )
        spoken("get-off", says) {
            verb("get-off", step)
            add("get-off", RailDot.Active, below = if (arrive) RailLine.None else RailLine.Dashed) { rail ->
                StopRow(
                    name = "", planned = end.plannedAt, expected = end.expectedAt, rail = rail, timeStyle = StepTimeStyle, minHeight = 44.dp,
                    content = { StepName(if (arrive) trip.destinationName else end.name) },
                    trailing = { PlatformBox(end.plannedPlatform ?: track, end.platform ?: track, lead = true) },
                )
            }
            if (text != null || anyTrain) {
                under("get-off") {
                    text?.let { Countdown(it) }
                    if (anyTrain) AnyTrainChip()
                }
            }
            exit?.let { side -> under("get-off-exit") { ExitFact(side) } }
        }
    } else if (!compact) {
        end(index)
    }

    val after = rides.filter { it > index }
    if (compact) {
        // One row: the change, the next train, and when the trip ends; not when the step above is the arrival.
        if (!(onBoard && index == last && !brokenHere)) {
            val next = after.firstOrNull()
            // After a ride that broke, how the change would have looked says nothing.
            val change = changeAfter(index)?.takeIf { !brokenHere }
            // The binding lifted is this row's "Any train", as on the web: no banner under the card.
            val anyTrain = lifted && !anyTrainShown
            if (anyTrain) anyTrainShown = true
            add("summary", above = if (change != null) RailLine.Dashed else null, below = RailLine.None) { rail ->
                StopRow(name = "", rail = rail, content = {
                    // Wraps as on the web: "Any train" goes to a second line rather than off the card.
                    FlowRow(
                        Modifier.weight(1f).padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        change?.let { ConfidenceChip(it.confidence, minutes = it.availableMinutes, word = true) }
                        next?.let { LineChip(legs[it]) }
                        Fact(Icons.Rounded.Flag, "Destination") {
                            ShiftedTime(legs[last].to.plannedAt, legs[last].to.expectedAt, AbeonaType.meta, block = false, color = muted)
                        }
                        if (anyTrain) AnyTrainChip()
                    }
                })
            }
        }
        // Something ahead broke: the way to the alternatives (on the Trips card, the trip).
        val broken = view.reason != null && view.reason != AlternativesReason.AtRisk
        if (broken && !alternativesShown && onAlternatives != null) {
            add("alternatives", above = RailLine.None, below = RailLine.None, dimmed = false) { rail ->
                StopRow(name = "", rail = rail, content = { Actions(onAlternatives, filled = true, anyTrain = false) })
            }
        }
        return rows
    }
    if (after.isEmpty()) {
        // The band's ride ends at the destination: the row above is the last.
        onBackToPlan?.let { back -> add("back", above = RailLine.None, below = RailLine.None, dimmed = false) { rail -> BackToPlanRow(back, rail) } }
        return rows
    }

    // The rest of the way: the next train in full, and anything up to a ride or change that will not work; the others folded.
    val missedNext = onBoard && day.missedChange
    val trouble = if (brokenHere) {
        null
    } else if (missedNext) {
        after.first()
    } else {
        after.firstOrNull { k ->
            legs[k].cancelled || changeBefore(k)?.confidence.let { it == Confidence.AT_RISK || it == Confidence.IMPOSSIBLE }
        }
    }
    val until = maxOf(after.first(), trouble ?: after.first())
    val shown = after.filter { it <= until }
    shown.forEachIndexed { i, k ->
        val broken = (missedNext && k == after.first()) || legs[k].cancelled
        // A change to or from a ride that broke has no margin worth showing: the broken step says it.
        val behindBroken = brokenHere && k == after.first() || legs[changeBefore(k)?.arriving ?: k].cancelled
        if (!broken && !behindBroken) changeBefore(k)?.let { change("change-$k", it) }
        if (broken && !brokenHere) {
            brokenStep(k, if (missedNext && k == after.first()) Verb.Missed else if (legs[k].dropped) Verb.NotRunning else Verb.Cancelled, large = false, below = RailLine.Faint)
        } else {
            val train = legs[k]
            spoken("train-$k", sentence(spokenTrain(train), spokenTime(train.from.plannedAt, train.from.expectedAt), spokenPlatform(train.from), after = listOf(spokenSeat(train)))) {
                add("train-$k", RailDot.Stop, below = RailLine.Faint) { rail -> TrainRow(train, rail) }
            }
        }
        if (i < shown.lastIndex) stop("end-$k", legs[k].to, RailDot.Stop, RailLine.Dashed)
    }
    val later = after.filter { it > until }
    if (later.isNotEmpty()) {
        add("later") { rail -> LaterRow(legs, until, later, changes, rail) }
    }
    destination()
    onBackToPlan?.let { back -> add("back", above = RailLine.None, below = RailLine.None, dimmed = false) { rail -> BackToPlanRow(back, rail) } }
    return rows
}

// ---------------------------------------------------------------- the rows

/** The verb above a step, in the label style: brand for the step to do, bad for one that broke. */
@Composable
private fun VerbRow(verb: Verb, rail: Rail) {
    val colour = if (verb.broken) bad.soft.content else brand
    StopRow(name = "", rail = rail, minHeight = 22.dp, content = {
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(verb.icon, contentDescription = null, tint = colour, modifier = Modifier.size(14.dp))
            Text(verb.text.uppercase(), style = AbeonaType.label, color = colour, maxLines = 1)
        }
    })
}

/** The step's station, or before leaving its countdown: the largest words on the card. */
@Composable
private fun RowScope.StepName(text: String, color: Color = Color.Unspecified) {
    Text(text, style = StepNameStyle, color = color, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
}

/** "in 12 min", "now": the one countdown, under the step it counts down to. */
@Composable
private fun Countdown(text: String) {
    Text(text, style = AbeonaType.body.copy(fontWeight = FontWeight.Bold), color = brand, maxLines = 1)
}

/** The flag where the line ends, on a disc of the card's colour so the line stops at it. */
@Composable
private fun DestinationMark(hole: Color) {
    Surface(color = hole, shape = CircleShape, modifier = Modifier.size(24.dp)) {
        Box(contentAlignment = Alignment.Center) { MarkIcon(Icons.Rounded.Flag, "Destination", tint = MaterialTheme.colorScheme.onSurface) }
    }
}

/** The ticket is valid on other trains for this journey: the train binding is lifted (glossary). */
@Composable
private fun AnyTrainChip() {
    StatusChip(StatusTone.Watch, Icons.Rounded.SwapHoriz, label = "Any train", description = "Train binding lifted: any train")
}

/** What to do about a step that broke: the alternatives (filled when it broke, outlined when only at risk), and whether any train will do. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RowScope.Actions(onAlternatives: (() -> Unit)?, filled: Boolean, anyTrain: Boolean) {
    FlowRow(
        Modifier.weight(1f).padding(top = 4.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        onAlternatives?.let { action ->
            val label: @Composable RowScope.() -> Unit = {
                Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Alternatives", style = AbeonaType.body.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(start = 8.dp))
            }
            if (filled) {
                Button(onClick = action, colors = ButtonDefaults.buttonColors(containerColor = bad.fill.container, contentColor = bad.fill.content), content = label)
            } else {
                OutlinedButton(onClick = action, content = label)
            }
        }
        if (anyTrain) AnyTrainChip()
    }
}

/**
 * What is done, folded into one muted row: the first departure, its station,
 * how many rides, and whether the trip was re-planned on the way. A tap
 * unfolds the rides done, muted, on the line.
 */
@Composable
private fun DoneRow(rides: List<Leg>, replanned: Boolean, rail: Rail) {
    var open by rememberSaveable { mutableStateOf(false) }
    val first = rides.first()
    Column {
        StopRow(
            name = "",
            planned = first.from.plannedAt, expected = first.from.expectedAt, quiet = true,
            rail = rail,
            onClick = { open = !open },
            content = {
                Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                    Text(first.from.name, style = AbeonaType.body, color = muted, maxLines = RowGrid.lines, overflow = TextOverflow.Ellipsis)
                    Text(
                        (if (rides.size == 1) "1 ride done" else "${rides.size} rides done") + if (replanned) " · re-planned" else "",
                        style = AbeonaType.meta, color = muted, maxLines = 1,
                    )
                }
            },
            trailing = {
                Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = if (open) "Hide what is done" else "Show what is done", tint = muted, modifier = Modifier.size(20.dp))
            },
        )
        if (open) {
            val through = Rail(RailLine.Done, RailLine.Done, RailDot.None, rail.hole)
            val at = Rail(RailLine.Done, RailLine.Done, RailDot.Done, rail.hole)
            rides.forEachIndexed { i, ride ->
                if (i > 0) {
                    StopRow(name = ride.from.name, planned = ride.from.plannedAt, expected = ride.from.expectedAt, quiet = true, rail = at)
                }
                StopRow(name = "", rail = through, minHeight = 32.dp, content = {
                    LineChip(ride, Modifier.alpha(0.7f))
                    ride.headsign?.let { Text("to $it", style = AbeonaType.meta, color = muted, maxLines = RowGrid.lines, overflow = TextOverflow.Ellipsis) }
                })
                StopRow(name = ride.to.name, planned = ride.to.plannedAt, expected = ride.to.expectedAt, quiet = true, rail = at)
            }
        }
    }
}

/**
 * The band: the scene strip on the ride's stretch of line (its rail the
 * ride's stops, the train where it is), under it the train with "to" its
 * own destination and, from its Wi-Fi, the speed; then [second]: on board
 * the stop just passed and the next, waiting where the train is.
 */
@Composable
private fun BandRow(
    view: LiveView,
    ride: Leg,
    rail: Rail,
    state: SceneState,
    stops: List<SceneStop>?,
    caption: Boolean,
    second: (@Composable () -> Unit)?,
) {
    val onBoard = view.day.kind == TravelDayKind.OnBoard
    val speed = view.onboard?.speedKmh?.takeIf { it >= 1 && onBoard }
    // Where the sky is reckoned: the phone or the train, else the stop the step is about.
    val skyAt = view.marker?.let { it.lat to it.lon }
        ?: (if (onBoard) ride.to else ride.from).let { stop -> stop.lat?.let { lat -> stop.lon?.let { lat to it } } }
    StopRow(name = "", rail = rail, content = {
        Column(Modifier.weight(1f).padding(top = 6.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Scene(
                state = state,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)),
                size = SceneSize.Strip,
                train = sceneTrain(ride),
                stops = stops,
                fraction = if (onBoard) view.progress?.fraction ?: 0.0 else 0.0,
                speedKmh = speed,
                lat = skyAt?.first,
                lon = skyAt?.second,
            )
            if (caption) {
                Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LineChip(ride)
                    Text(
                        ride.headsign?.let { "to $it" }.orEmpty(),
                        style = AbeonaType.body.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = RowGrid.lines, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    speed?.let {
                        val kmh = "${it.roundToInt()} km/h"
                        Row(
                            Modifier.clearAndSetSemantics { contentDescription = "Speed $kmh" },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(Icons.Rounded.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(kmh, style = AbeonaType.body.copy(fontWeight = FontWeight.Bold), maxLines = 1)
                        }
                    }
                }
                // Why it is late, from the train's Wi-Fi: only while it is late enough to matter.
                view.onboard?.delayReason?.takeIf { onBoard && arrivalDelay(ride) >= 5 }?.let { reason ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Rounded.Info, contentDescription = null, tint = muted, modifier = Modifier.size(16.dp))
                        Text(reason, style = AbeonaType.meta, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            second?.invoke()
        }
    })
}

/**
 * On board, under the train: the stop it last reached on the left, the next
 * on the right ("Next Würzburg Hbf 11:15"), left out when the next is where
 * the traveller gets off (the step below says it). Not the ride's first stop:
 * the row above has it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PassingStops(progress: RideProgress) {
    val stops = progress.stops
    val lastIndex = stops.lastIndex
    val current = progress.current?.takeIf { it in 1 until lastIndex }
    val reached = current ?: stops.indexOfLast { it.passed }.takeIf { it in 1 until lastIndex }
    val next = progress.next?.takeIf { it < lastIndex }
    if (reached == null && next == null) return
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        if (reached != null) {
            val stop = stops[reached]
            val time = TripFormat.time(if (reached == current) stop.arrival else stop.departure)
            Row(
                Modifier.clearAndSetSemantics { contentDescription = "Passed ${stop.name} $time" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = muted, modifier = Modifier.size(14.dp))
                Text("${stop.name} $time", style = AbeonaType.meta, color = muted, maxLines = RowGrid.lines)
            }
        } else {
            Spacer(Modifier.width(0.dp))
        }
        next?.let { Text("Next ${stops[it].name} ${TripFormat.time(stops[it].arrival)}", style = AbeonaType.meta, color = muted, maxLines = RowGrid.lines) }
    }
}

/**
 * A train ahead on the line: its departure, line, "to" its own destination,
 * and platform; under them the seat, when the ticket has one.
 */
@Composable
private fun TrainRow(leg: Leg, rail: Rail) {
    val seated = seatText(leg.coach, leg.seats) != null
    StopRow(
        name = "",
        planned = leg.from.plannedAt, expected = leg.from.expectedAt,
        platformPlanned = leg.from.plannedPlatform, platformCurrent = leg.from.platform,
        rail = rail,
        content = {
            if (seated) {
                Column(Modifier.weight(1f).padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LineChip(leg)
                        leg.headsign?.let { Text("to $it", style = AbeonaType.body, maxLines = RowGrid.lines, overflow = TextOverflow.Ellipsis) }
                    }
                    SeatFact(leg.coach, leg.seats)
                }
            } else {
                LineChip(leg)
                leg.headsign?.let { Text("to $it", style = AbeonaType.body, maxLines = RowGrid.lines, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)) }
            }
        },
    )
}

/** The side the doors open on at the stop to get off at: "Exit left", from the train's Wi-Fi. */
@Composable
private fun ExitFact(side: ExitSide) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(Icons.Rounded.DoorSliding, contentDescription = null, tint = muted, modifier = Modifier.size(16.dp))
        Text("Exit ${side.word}", style = AbeonaType.meta, color = muted, maxLines = 1)
    }
}

/** "Change, 6 minutes, tight, platform 4 to platform 5": a change row as a screen reader says it. */
private fun spokenChange(change: Transfer, legs: List<Leg>): String {
    val from = legs[change.arriving].to.shownPlatform
    val to = legs[change.departing].from.shownPlatform
    return sentence(
        "Change: ${change.availableMinutes} minutes",
        change.confidence.takeIf { it != Confidence.SAFE }?.label?.lowercase(),
        when {
            change.walkMinutes > 0 -> "walk ${change.walkMinutes} minutes"
            from != null && to != null -> "platform $from to platform $to"
            else -> null
        },
    )
}

/**
 * A change on the line: how it looks ("15 min", "6 min · tight"), then the
 * platforms ("4 → 5", the new one in watch when it changed) or the walk.
 * When it will not work, [onAlternatives] under it, filled when it is
 * [impossible], outlined when it is only at risk.
 */
@Composable
private fun ChangeRow(change: Transfer, legs: List<Leg>, rail: Rail, onAlternatives: (() -> Unit)?, impossible: Boolean, anyTrain: Boolean) {
    val from = legs[change.arriving].to
    val to = legs[change.departing].from
    StopRow(name = "", rail = rail, minHeight = 36.dp, content = {
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ConfidenceChip(change.confidence, minutes = change.availableMinutes, word = true)
                val a = from.shownPlatform
                val b = to.shownPlatform
                when {
                    change.walkMinutes > 0 -> Fact(Icons.AutoMirrored.Rounded.DirectionsWalk, "Walk", "${change.walkMinutes} min")
                    a != null && b != null -> {
                        val moved = platformChanged(to.plannedPlatform, to.platform)
                        val late = watch.soft.content
                        Text(
                            buildAnnotatedString {
                                append(a)
                                withStyle(SpanStyle(color = muted)) { append(" → ") }
                                if (moved) withStyle(SpanStyle(color = late, fontWeight = FontWeight.Bold)) { append(b) } else append(b)
                            },
                            style = AbeonaType.body.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            modifier = Modifier.clearAndSetSemantics {
                                contentDescription = "Platform $a to platform $b" + if (moved) ", changed from ${to.plannedPlatform}" else ""
                            },
                        )
                    }
                }
            }
            if (onAlternatives != null || anyTrain) {
                Row { Actions(onAlternatives, filled = impossible, anyTrain = anyTrain) }
            }
        }
    })
}

/**
 * The rides beyond the next train, folded: "Later", their lines, how many
 * changes and the worst of them unless all are safe. A tap unfolds them on
 * the line: where each ride before ends, the change, the train.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LaterRow(legs: List<Leg>, from: Int, later: List<Int>, changes: List<Transfer>, rail: Rail) {
    var open by rememberSaveable { mutableStateOf(false) }
    val theirs = changes.filter { it.departing in later }
    val worst = theirs.maxByOrNull { it.confidence }?.takeIf { it.confidence != Confidence.SAFE }
    Column {
        StopRow(
            name = "",
            rail = rail,
            onClick = { open = !open },
            content = {
                FlowRow(
                    Modifier.weight(1f).padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Later", style = AbeonaType.body, color = muted)
                    later.forEach { LineChip(legs[it]) }
                    Text(if (later.size == 1) "1 change" else "${later.size} changes", style = AbeonaType.meta, color = muted)
                    worst?.let { ConfidenceChip(it.confidence, minutes = it.availableMinutes, word = true) }
                }
            },
            trailing = {
                Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = if (open) "Fold the later rides" else "Show the later rides", tint = muted, modifier = Modifier.size(20.dp))
            },
        )
        if (open) {
            var before = from
            later.forEach { k ->
                val end = legs[before].to
                StopRow(
                    name = end.name, planned = end.plannedAt, expected = end.expectedAt,
                    platformPlanned = end.plannedPlatform, platformCurrent = end.platform,
                    rail = Rail(RailLine.Faint, RailLine.Dashed, RailDot.Stop, rail.hole),
                )
                changes.firstOrNull { it.departing == k }?.let {
                    ChangeRow(it, legs, Rail(RailLine.Dashed, RailLine.Dashed, RailDot.None, rail.hole), onAlternatives = null, impossible = false, anyTrain = false)
                }
                TrainRow(legs[k], Rail(RailLine.Dashed, RailLine.Faint, RailDot.Stop, rail.hole))
                before = k
            }
        }
    }
}

/** A connection from the alternatives is followed: the way back to the plan, under the destination. */
@Composable
private fun BackToPlanRow(onClick: () -> Unit, rail: Rail) {
    StopRow(name = "", rail = rail, minHeight = 36.dp, content = {
        TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)) { Text("Back to the plan") }
    })
}

/** The card's last line: how fresh, and, as an icon, how Abeona knows (it opens the sheet). */
@Composable
private fun Footer(status: LiveStatus?, source: TravelDaySource, onSource: () -> Unit) {
    HorizontalDivider(color = hairline, modifier = Modifier.padding(horizontal = 16.dp))
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (status != null) {
            val colour = if (status.kind == LiveStatusKind.Offline) watch.soft.content else muted
            Icon(liveStatusIcon(status.kind), contentDescription = null, tint = colour, modifier = Modifier.size(16.dp))
            Text(status.line, style = AbeonaType.meta, color = colour, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        } else {
            Spacer(Modifier.weight(1f))
        }
        IconButton(onClick = onSource) {
            Icon(evidenceIcon(sourceKind(source)), contentDescription = "How Abeona knows: ${sourceShort(source)}", tint = muted, modifier = Modifier.size(20.dp))
        }
    }
}

// ---------------------------------------------------------------- how Abeona knows

/**
 * What the source button opens: each signal with its icon, the one that
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

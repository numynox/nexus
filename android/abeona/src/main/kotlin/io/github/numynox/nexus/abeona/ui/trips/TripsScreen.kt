package io.github.numynox.nexus.abeona.ui.trips

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.numynox.nexus.abeona.data.Statistics
import io.github.numynox.nexus.abeona.data.Trip
import io.github.numynox.nexus.abeona.domain.TravelDay
import io.github.numynox.nexus.abeona.domain.TravelDayRules
import io.github.numynox.nexus.abeona.domain.alternativesOrigin
import io.github.numynox.nexus.abeona.domain.alternativesReason
import io.github.numynox.nexus.abeona.domain.changes
import io.github.numynox.nexus.abeona.domain.finalArrivalDelay
import io.github.numynox.nexus.abeona.domain.followed
import io.github.numynox.nexus.abeona.domain.trainBindingLifted
import io.github.numynox.nexus.abeona.domain.travelDay
import io.github.numynox.nexus.abeona.domain.worstConfidence
import io.github.numynox.nexus.abeona.live.LiveSignals
import io.github.numynox.nexus.abeona.live.OnboardStatus
import io.github.numynox.nexus.abeona.ui.AbeonaType
import io.github.numynox.nexus.abeona.ui.AppViewModel
import io.github.numynox.nexus.abeona.ui.Navigator
import io.github.numynox.nexus.abeona.ui.brand
import io.github.numynox.nexus.abeona.ui.common.BindingBanner
import io.github.numynox.nexus.abeona.ui.common.ConfidenceChip
import io.github.numynox.nexus.abeona.ui.common.DeadlineBanner
import io.github.numynox.nexus.abeona.ui.common.EmptyState
import io.github.numynox.nexus.abeona.ui.common.Fact
import io.github.numynox.nexus.abeona.ui.common.FactLine
import io.github.numynox.nexus.abeona.ui.common.LegChips
import io.github.numynox.nexus.abeona.ui.common.OfflinePill
import io.github.numynox.nexus.abeona.ui.common.PlatformBox
import io.github.numynox.nexus.abeona.ui.common.PrimaryButton
import io.github.numynox.nexus.abeona.ui.common.RowGrid
import io.github.numynox.nexus.abeona.ui.common.SectionHeader
import io.github.numynox.nexus.abeona.ui.common.ShiftedTime
import io.github.numynox.nexus.abeona.ui.common.StatusChip
import io.github.numynox.nexus.abeona.ui.common.StatusTone
import io.github.numynox.nexus.abeona.ui.common.SurfaceCard
import io.github.numynox.nexus.abeona.ui.common.TripFormat
import io.github.numynox.nexus.abeona.ui.common.containerViewModel
import io.github.numynox.nexus.abeona.ui.common.isLiveDay
import io.github.numynox.nexus.abeona.ui.common.rememberTicker
import io.github.numynox.nexus.abeona.ui.hairline
import io.github.numynox.nexus.abeona.ui.live.LiveCard
import io.github.numynox.nexus.abeona.ui.live.liveView
import io.github.numynox.nexus.abeona.ui.live.sceneTrain
import io.github.numynox.nexus.abeona.ui.muted
import io.github.numynox.nexus.abeona.ui.scene.Scene
import io.github.numynox.nexus.abeona.ui.scene.SceneSize
import io.github.numynox.nexus.abeona.ui.scene.SceneState
import io.github.numynox.nexus.core.ui.Loadable
import io.github.numynox.nexus.core.ui.NexusFormat
import io.github.numynox.nexus.core.ui.components.ErrorState
import io.github.numynox.nexus.core.ui.components.PlaceholderList
import io.github.numynox.nexus.core.ui.components.UpdateBanner
import io.github.numynox.nexus.core.ui.components.rememberPullRefresh
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * Home (abeona-design.md §9, Trips): what needs doing, then the next trip (on
 * its travel day the live card in its compact form), what comes after as
 * dense rows, the year so far, and the way to the past trips.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripsScreen(app: AppViewModel, navigator: Navigator) {
    val application = LocalContext.current.applicationContext as android.app.Application
    val viewModel = containerViewModel { TripsViewModel(it, application) }
    val trips by viewModel.trips.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val deadlines by viewModel.deadlines.collectAsStateWithLifecycle()
    val version by app.dataVersion.collectAsStateWithLifecycle()
    val settings by app.settings.collectAsStateWithLifecycle()
    val onboard by LiveSignals.onboard.collectAsStateWithLifecycle()
    val fix by LiveSignals.fix.collectAsStateWithLifecycle()
    val now by rememberTicker()
    val pull = rememberPullRefresh(viewModel::load)

    LaunchedEffect(version) { viewModel.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trips") },
                actions = { (trips as? Loadable.Ready)?.value?.offlineSince?.let { OfflinePill(it, Modifier.padding(end = 16.dp)) } },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = pull.refreshing,
            onRefresh = pull.onRefresh,
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
        ) {
            when (val state = trips) {
                Loadable.Loading -> PlaceholderList(count = 3, modifier = Modifier.padding(horizontal = 16.dp), lines = 3)
                is Loadable.Failed -> ErrorState(state.message, onRetry = { viewModel.load() })
                is Loadable.Ready -> {
                    val upcoming = state.value.trips.filter { it.expectedArrival.isAfter(now) }
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        item(key = "update") { UpdateBanner(app.updates) }
                        if (deadlines.isNotEmpty()) {
                            item(key = "deadlines") { DeadlineBanner(deadlines, onClick = navigator::settings) }
                        }
                        if (upcoming.isEmpty()) {
                            item(key = "empty") {
                                EmptyState("No trips planned", Modifier.padding(vertical = 8.dp)) {
                                    PrimaryButton("Plan a trip", onClick = navigator::search, icon = Icons.Rounded.Route)
                                }
                            }
                        } else {
                            item(key = "next") {
                                // As travelled: a connection taken from the alternatives replaces the plan's rides.
                                val next = upcoming.first().followed
                                val day = if (isLiveDay(next, now)) travelDay(next.legs, now, settings.stationMinutes, LiveSignals.forTrip(next, now, onboard, fix)) else null
                                NextTripCard(
                                    next, now, onClick = { navigator.trip(next.id) },
                                    day = day, stationMinutes = settings.stationMinutes, onboard = LiveSignals.onboardFor(next, now, onboard),
                                )
                            }
                            if (upcoming.size > 1) {
                                item(key = "upcoming-head") {
                                    SectionHeader("Upcoming") { Fact(Icons.Rounded.Train, "More planned", "${upcoming.size - 1}") }
                                }
                                item(key = "upcoming") { TripRows(upcoming.drop(1), onClick = { navigator.trip(it.id) }) }
                            }
                        }
                        item(key = "history") { HistoryCard(history, onClick = navigator::review) }
                        item(key = "past") {
                            Row(
                                Modifier.fillMaxWidth().clickable(onClick = navigator::pastTrips).heightIn(min = 48.dp).padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(Icons.Rounded.History, contentDescription = null, tint = muted, modifier = Modifier.size(20.dp))
                                Text("Past trips", style = AbeonaType.body, modifier = Modifier.weight(1f))
                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = muted, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The trip's day, its title, and under a title of its own the route. */
@Composable
private fun DayAndTitle(trip: Trip, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(TripFormat.longDay(trip.departureAt), style = AbeonaType.meta, color = brand)
        Text(trip.displayTitle, style = AbeonaType.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (!trip.title.isNullOrBlank()) {
            Text("${trip.originName} → ${trip.destinationName}", style = AbeonaType.meta, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * The next trip. On its travel day ([day]): its day and title as plain text,
 * then the live card in its compact form, so no card sits in a card.
 * Otherwise one card: the scene with the train parked (the signal at red
 * when a ride fell out of the timetable), day and title, the departure with
 * its platform, and one line of facts.
 */
@Composable
fun NextTripCard(
    trip: Trip,
    now: Instant,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    day: TravelDay? = null,
    stationMinutes: Int = TravelDayRules.DEFAULT_STATION_MINUTES,
    onboard: OnboardStatus? = null,
) {
    val first = trip.legs.firstOrNull { !it.isWalk }
    val last = trip.legs.lastOrNull { !it.isWalk }
    val arrivalDelay = finalArrivalDelay(trip.legs)
    val lifted = trainBindingLifted(trip.fareType, trip.trainBinding, arrivalDelay)
    if (day != null) {
        val origin = alternativesOrigin(trip.legs, day, now)
        val reason = alternativesReason(trip.legs, day).takeIf { origin != null }
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DayAndTitle(trip, Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 4.dp))
            LiveCard(
                liveView(trip, day, now, stationMinutes, emptyList(), onboard, null, reason),
                onAlternatives = onClick,
                summary = true,
                lifted = lifted,
                onClick = onClick,
            )
        }
        return
    }
    val dropped = trip.legs.any { it.dropped }
    SurfaceCard(modifier, onClick = onClick, padding = PaddingValues(0.dp)) {
        Scene(
            // A ride that fell out of the timetable and nothing followed instead: the signal is at red.
            state = if (dropped && trip.missedLegIdx == null) SceneState.Stopped else SceneState.Parked,
            size = SceneSize.Header,
            train = sceneTrain(first),
            lat = first?.from?.lat,
            lon = first?.from?.lon,
        )
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DayAndTitle(trip)
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShiftedTime(first?.from?.plannedAt ?: trip.departureAt, first?.from?.expectedAt, AbeonaType.time, cancelled = first?.cancelled == true)
                Spacer(Modifier.weight(1f))
                first?.let { PlatformBox(it.from.plannedPlatform, it.from.platform, lead = true) }
            }
            FactLine {
                Fact(Icons.Rounded.Flag, "Arrives") {
                    ShiftedTime(last?.to?.plannedAt ?: trip.expectedArrival, trip.expectedArrival, AbeonaType.meta, block = false, color = muted)
                }
                Fact(Icons.Rounded.Schedule, "Duration", TripFormat.shortDuration(Duration.between(trip.departureAt, trip.arrivalAt).toMinutes()))
                Fact(Icons.Rounded.SwapHoriz, "Changes", "${changes(trip.legs)}")
                worstConfidence(trip.legs)?.let { ConfidenceChip(it) }
                LegChips(trip.legs)
                if (trip.ticketPath != null) Icon(Icons.Rounded.ConfirmationNumber, contentDescription = "Ticket attached", tint = muted, modifier = Modifier.size(16.dp))
            }
            if (dropped) TimetableChip(trip)
            if (lifted) BindingBanner(arrivalDelay)
        }
    }
}

/** A ride no longer in the timetable: adjusted once a connection is followed instead. */
@Composable
private fun TimetableChip(trip: Trip) {
    if (trip.missedLegIdx != null) {
        StatusChip(StatusTone.Watch, Icons.Rounded.Schedule, label = "Adjusted for a timetable change")
    } else {
        StatusChip(StatusTone.Bad, Icons.Rounded.Block, label = "Timetable changed")
    }
}

/** Trips as dense rows in one card, a hairline between them. */
@Composable
internal fun TripRows(trips: List<Trip>, onClick: (Trip) -> Unit, modifier: Modifier = Modifier, past: Boolean = false) {
    SurfaceCard(modifier, padding = PaddingValues(vertical = 4.dp)) {
        trips.forEachIndexed { index, trip ->
            if (index > 0) HorizontalDivider(color = hairline, modifier = Modifier.padding(horizontal = 16.dp))
            TripRow(trip, onClick = { onClick(trip) }, past = past)
        }
    }
}

/**
 * A later trip, or in Past trips ([past]) an earlier one, on one row: its day
 * over its departure where the grid has the time and the mark, then the title
 * with the arrival and the lines under it, the ticket at the right. A past
 * trip ends with how late it arrived.
 */
@Composable
internal fun TripRow(trip: Trip, onClick: () -> Unit, modifier: Modifier = Modifier, past: Boolean = false) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowGrid.Gap),
    ) {
        Column(Modifier.width(RowGrid.Time + RowGrid.Gap + RowGrid.Mark)) {
            Text(TripFormat.day(trip.departureAt), style = AbeonaType.meta, color = if (past) muted else brand, maxLines = 1)
            Text(TripFormat.time(trip.departureAt), style = AbeonaType.title, maxLines = 1)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(trip.displayTitle, style = AbeonaType.body.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // The departure is in the column beside: here only the arrival.
                Fact(Icons.Rounded.Flag, "Arrives", TripFormat.time(trip.arrivalAt))
                LegChips(trip.legs)
            }
            if (!past && trip.legs.any { it.dropped }) TimetableChip(trip)
        }
        if (trip.ticketPath != null) {
            Icon(Icons.Rounded.ConfirmationNumber, contentDescription = "Ticket attached", tint = muted, modifier = Modifier.size(20.dp))
        }
        if (past) {
            val delay = finalArrivalDelay(trip.followed.legs)
            if (delay > 0) {
                StatusChip(StatusTone.Watch, Icons.Rounded.Timelapse, label = "+$delay", description = "$delay min late")
            } else {
                StatusChip(StatusTone.Ok, Icons.Rounded.CheckCircle, description = "On time")
            }
        }
    }
}

/** The year so far as four figures in one row; the way into the year in review. */
@Composable
private fun HistoryCard(stats: Statistics?, onClick: () -> Unit) {
    SurfaceCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${LocalDate.now().year} on rails", style = AbeonaType.title, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Year in review", tint = muted, modifier = Modifier.size(20.dp))
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Figure(stats?.trips?.toString() ?: "–", "trips")
            Figure(stats?.let { NexusFormat.number(it.km) } ?: "–", "km")
            Figure(stats?.let { NexusFormat.number(it.delayMinutes) } ?: "–", "min late")
            Figure(stats?.punctualShare?.let { NexusFormat.percent(it) } ?: "–", "punctual")
        }
    }
}

@Composable
private fun Figure(value: String, label: String) {
    Column {
        Text(value, style = AbeonaType.headline, maxLines = 1)
        Text(label, style = AbeonaType.meta, color = muted, maxLines = 1)
    }
}

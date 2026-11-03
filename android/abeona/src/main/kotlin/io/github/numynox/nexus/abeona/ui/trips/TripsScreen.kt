package io.github.numynox.nexus.abeona.ui.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.numynox.nexus.abeona.R
import io.github.numynox.nexus.abeona.data.Statistics
import io.github.numynox.nexus.abeona.data.Trip
import io.github.numynox.nexus.abeona.domain.changes
import io.github.numynox.nexus.abeona.domain.departureDelay
import io.github.numynox.nexus.abeona.domain.finalArrivalDelay
import io.github.numynox.nexus.abeona.domain.platformChanged
import io.github.numynox.nexus.abeona.domain.trainBindingLifted
import io.github.numynox.nexus.abeona.domain.worstConfidence
import io.github.numynox.nexus.abeona.ui.AppViewModel
import io.github.numynox.nexus.abeona.ui.Navigator
import io.github.numynox.nexus.abeona.ui.common.BindingLiftedBanner
import io.github.numynox.nexus.abeona.ui.common.ConfidenceChip
import io.github.numynox.nexus.abeona.ui.common.DeadlineBanner
import io.github.numynox.nexus.abeona.ui.common.DelayBadge
import io.github.numynox.nexus.abeona.ui.common.Illustration
import io.github.numynox.nexus.abeona.ui.common.LegChips
import io.github.numynox.nexus.abeona.ui.common.OfflinePill
import io.github.numynox.nexus.abeona.ui.common.PlatformBox
import io.github.numynox.nexus.abeona.ui.common.StatusPill
import io.github.numynox.nexus.abeona.ui.common.TripFormat
import io.github.numynox.nexus.abeona.ui.common.confidenceSummary
import io.github.numynox.nexus.abeona.ui.common.TravelDayHero
import io.github.numynox.nexus.abeona.ui.common.containerViewModel
import io.github.numynox.nexus.abeona.ui.common.isLiveDay
import io.github.numynox.nexus.abeona.ui.common.rememberTicker
import io.github.numynox.nexus.abeona.domain.TravelDay
import io.github.numynox.nexus.abeona.domain.TravelDayRules
import io.github.numynox.nexus.abeona.domain.travelDay
import io.github.numynox.nexus.abeona.live.LiveSignals
import io.github.numynox.nexus.abeona.live.OnboardStatus
import io.github.numynox.nexus.core.ui.Loadable
import io.github.numynox.nexus.core.ui.NexusFormat
import io.github.numynox.nexus.core.ui.components.ErrorState
import io.github.numynox.nexus.core.ui.components.NexusCard
import io.github.numynox.nexus.core.ui.components.PlaceholderList
import io.github.numynox.nexus.core.ui.components.SectionHeader
import io.github.numynox.nexus.core.ui.components.UpdateBanner
import io.github.numynox.nexus.core.ui.components.rememberPullRefresh
import io.github.numynox.nexus.core.ui.theme.tabular
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** Home: the next trip as one big card, then what comes after, then the year so far. */
@Composable
fun TripsScreen(app: AppViewModel, navigator: Navigator) {
    val application = LocalContext.current.applicationContext as android.app.Application
    val viewModel = containerViewModel { TripsViewModel(it, application) }
    val trips by viewModel.trips.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val deadlines by viewModel.deadlines.collectAsStateWithLifecycle()
    val version by app.dataVersion.collectAsStateWithLifecycle()
    val update by app.update.collectAsStateWithLifecycle()
    val settings by app.settings.collectAsStateWithLifecycle()
    val onboard by LiveSignals.onboard.collectAsStateWithLifecycle()
    val fix by LiveSignals.fix.collectAsStateWithLifecycle()
    val now by rememberTicker()
    val pull = rememberPullRefresh(viewModel::load)
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    LaunchedEffect(version) { viewModel.load() }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text("Trips")
                        (trips as? Loadable.Ready)?.value?.offlineSince?.let { OfflinePill(it, Modifier.padding(top = 4.dp)) }
                    }
                },
                scrollBehavior = scroll,
            )
        },
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
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
                        item(key = "update") { UpdateBanner(update) }
                        if (deadlines.isNotEmpty()) {
                            item(key = "deadlines") { DeadlineBanner(deadlines, onClick = navigator::settings) }
                        }
                        if (upcoming.isEmpty()) {
                            item(key = "empty") { EmptyTrips(onPlan = navigator::search) }
                        } else {
                            item(key = "next") {
                                val next = upcoming.first()
                                val day = if (isLiveDay(next, now)) travelDay(next.legs, now, settings.stationMinutes, LiveSignals.forTrip(next, now, onboard, fix)) else null
                                NextTripCard(
                                    next, now, onClick = { navigator.trip(next.id) },
                                    day = day, stationMinutes = settings.stationMinutes, onboard = LiveSignals.onboardFor(next, now, onboard),
                                )
                            }
                            if (upcoming.size > 1) {
                                item(key = "upcoming-head") { SectionHeader("Upcoming", supporting = "${upcoming.size - 1} more planned") }
                                items(upcoming.drop(1), key = { it.id }) { trip ->
                                    TripRow(trip, onClick = { navigator.trip(trip.id) }, modifier = Modifier.animateItem())
                                }
                            }
                        }
                        item(key = "history") { HistoryCard(history, onClick = navigator::review) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyTrips(onPlan: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Illustration(R.drawable.illus_empty_trips, height = 220.dp)
        Spacer(Modifier.height(24.dp))
        Text("No trips planned", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            "Search a connection and save it: Abeona keeps an eye on delays, platforms and your changes on the day.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onPlan) {
            Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Plan a trip", modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** The one hero of the screen: when to be where, and whether anything has changed. */
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
    val delay = first?.let(::departureDelay) ?: 0
    val arrivalDelay = finalArrivalDelay(trip.legs)
    val changes = changes(trip.legs)
    val worst = worstConfidence(trip.legs)
    val minutesToGo = Duration.between(now, trip.expectedDeparture).toMinutes()
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Box {
            Illustration(R.drawable.illus_train_hero, height = 132.dp, corner = 0.dp)
            StatusPill(
                if (minutesToGo in 0..720) "Departs ${TripFormat.relative(minutesToGo)}" else TripFormat.day(trip.departureAt),
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
            )
        }
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text(
                    TripFormat.longDay(trip.departureAt),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    trip.title?.takeIf { it.isNotBlank() } ?: "${trip.originName} → ${trip.destinationName}",
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!trip.title.isNullOrBlank()) {
                    Text(
                        "${trip.originName} → ${trip.destinationName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            day?.let { TravelDayHero(trip, it, stationMinutes, onboard = onboard) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Departure", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            TripFormat.time(first?.from?.plannedAt ?: trip.departureAt),
                            style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.SemiBold).tabular(),
                        )
                        DelayBadge(delay, large = true)
                    }
                    Text(
                        "Arrives ${TripFormat.time(trip.expectedArrival)}" + if (arrivalDelay > 0) " (+$arrivalDelay)" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                first?.let {
                    PlatformBox(
                        planned = it.from.plannedPlatform,
                        current = it.from.platform,
                        changed = platformChanged(it.from.plannedPlatform, it.from.platform),
                        large = true,
                    )
                }
            }
            LegChips(trip.legs)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    confidenceSummary(changes, worst),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (worst != null) ConfidenceChip(worst)
                if (trip.ticketPath != null) {
                    Icon(
                        Icons.Rounded.ConfirmationNumber,
                        contentDescription = "Ticket attached",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            if (trainBindingLifted(trip.fareType, trip.trainBinding, arrivalDelay)) {
                BindingLiftedBanner(arrivalDelay)
            }
        }
    }
}

/** A later trip: its day, route and times on one calm row. */
@Composable
private fun TripRow(trip: Trip, onClick: () -> Unit, modifier: Modifier = Modifier) {
    NexusCard(modifier = modifier.fillMaxWidth(), onClick = onClick, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(76.dp)) {
                Text(TripFormat.day(trip.departureAt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                Text(TripFormat.time(trip.departureAt), style = MaterialTheme.typography.titleLarge.tabular())
            }
            Column(Modifier.weight(1f)) {
                Text(trip.displayTitle, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${TripFormat.time(trip.departureAt)} – ${TripFormat.time(trip.arrivalAt)} · " +
                        TripFormat.duration(Duration.between(trip.departureAt, trip.arrivalAt).toMinutes()),
                    style = MaterialTheme.typography.bodySmall.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LegChips(trip.legs, Modifier.padding(top = 6.dp))
            }
            if (trip.ticketPath != null) {
                Icon(Icons.Rounded.ConfirmationNumber, contentDescription = "Ticket attached", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** The year so far, in one line of figures; the way into the year in review. */
@Composable
private fun HistoryCard(stats: Statistics?, onClick: () -> Unit) {
    NexusCard(onClick = onClick, containerColor = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(
                "${LocalDate.now().year} on rails",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f).padding(start = 10.dp),
            )
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Year in review", tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
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
        Text(value, style = MaterialTheme.typography.titleLarge.tabular(), color = MaterialTheme.colorScheme.onSecondaryContainer)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f))
    }
}

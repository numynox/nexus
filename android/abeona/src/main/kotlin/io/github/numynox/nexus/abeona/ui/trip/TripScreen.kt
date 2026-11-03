package io.github.numynox.nexus.abeona.ui.trip

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.numynox.nexus.abeona.data.FareType
import io.github.numynox.nexus.abeona.data.Leg
import io.github.numynox.nexus.abeona.data.StopTime
import io.github.numynox.nexus.abeona.data.Trip
import io.github.numynox.nexus.abeona.data.toInstantOrNull
import io.github.numynox.nexus.abeona.domain.Transfer
import io.github.numynox.nexus.abeona.domain.arrivalDelay
import io.github.numynox.nexus.abeona.domain.delayMinutes
import io.github.numynox.nexus.abeona.domain.departureDelay
import io.github.numynox.nexus.abeona.domain.finalArrivalDelay
import io.github.numynox.nexus.abeona.domain.platformChanged
import io.github.numynox.nexus.abeona.domain.trainBindingLifted
import io.github.numynox.nexus.abeona.domain.transfers
import io.github.numynox.nexus.abeona.live.LiveCompanion
import io.github.numynox.nexus.abeona.ui.AppViewModel
import io.github.numynox.nexus.abeona.ui.Navigator
import io.github.numynox.nexus.abeona.ui.danger
import io.github.numynox.nexus.abeona.ui.common.BindingLiftedBanner
import io.github.numynox.nexus.abeona.ui.common.ConfidenceChip
import io.github.numynox.nexus.abeona.ui.common.DbNavigator
import io.github.numynox.nexus.abeona.ui.common.DelayBadge
import io.github.numynox.nexus.abeona.ui.common.LineChip
import io.github.numynox.nexus.abeona.ui.common.OfflinePill
import io.github.numynox.nexus.abeona.ui.common.PlatformBox
import io.github.numynox.nexus.abeona.ui.common.StatusPill
import io.github.numynox.nexus.abeona.ui.common.TimeWithDelay
import io.github.numynox.nexus.abeona.ui.common.PickedFile
import io.github.numynox.nexus.abeona.ui.common.TripFormat
import io.github.numynox.nexus.abeona.ui.common.TripWarningsBanner
import io.github.numynox.nexus.abeona.ui.common.containerViewModel
import io.github.numynox.nexus.core.ui.Loadable
import io.github.numynox.nexus.core.ui.components.ConnectedChoiceRow
import io.github.numynox.nexus.core.ui.components.ErrorState
import io.github.numynox.nexus.core.ui.components.NexusCard
import io.github.numynox.nexus.core.ui.components.PlaceholderList
import io.github.numynox.nexus.core.ui.components.SectionHeader
import io.github.numynox.nexus.core.ui.components.rememberPullRefresh
import io.github.numynox.nexus.core.ui.theme.tabular
import java.time.Duration
import kotlinx.coroutines.launch

@Composable
fun TripScreen(app: AppViewModel, tripId: String, navigator: Navigator) {
    val context = LocalContext.current
    val application = context.applicationContext as android.app.Application
    val viewModel = containerViewModel(key = tripId) { TripViewModel(it, application, tripId) }
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val people by viewModel.people.collectAsStateWithLifecycle()
    val companion by LiveCompanion.running.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val pull = rememberPullRefresh(viewModel::refresh)
    var confirmDelete by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(tripId) { viewModel.load() }

    // Two-minute refreshes only while the screen is in front.
    DisposableEffect(lifecycle, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.setVisible(true)
                Lifecycle.Event.ON_PAUSE -> viewModel.setVisible(false)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            viewModel.setVisible(false)
        }
    }

    val pickTicket = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.attachTicket(uri, onError = { scope.launch { snackbar.showSnackbar(it) } }) {
                app.changed("Ticket added")
                navigator.ticket(tripId)
            }
        }
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // The companion runs either way; without the permission its notification is just hidden.
        LiveCompanion.start(context, tripId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = navigator.back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) { Icon(Icons.Rounded.Refresh, contentDescription = "Refresh") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete trip") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = pull.refreshing,
            onRefresh = pull.onRefresh,
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
        ) {
            when (val state = detail) {
                Loadable.Loading -> PlaceholderList(count = 3, modifier = Modifier.padding(horizontal = 16.dp), lines = 3)
                is Loadable.Failed -> ErrorState(state.message, onRetry = { viewModel.load() })
                is Loadable.Ready -> TripContent(
                    detail = state.value,
                    people = people,
                    onToggleTraveller = { id -> viewModel.toggleTraveller(id) { scope.launch { snackbar.showSnackbar(it) } } },
                    onDiscount = { value -> viewModel.setBahncardDiscount(value) { scope.launch { snackbar.showSnackbar(it) } } },
                    companionOn = companion == tripId,
                    onDbNavigator = { DbNavigator.open(context) },
                    onTicket = {
                        if (state.value.trip.ticketPath != null) navigator.ticket(tripId)
                        else pickTicket.launch(arrayOf("application/pdf", "image/png", "image/jpeg"))
                    },
                    onCompanion = {
                        if (companion == tripId) {
                            LiveCompanion.stop(context)
                        } else if (Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            LiveCompanion.start(context, tripId)
                        }
                    },
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this trip?") },
            text = { Text("It goes for everyone in your household, with its ticket.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(onError = { scope.launch { snackbar.showSnackbar(it) } }) {
                        app.changed("Trip deleted")
                        navigator.back()
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
}

@Composable
private fun TripContent(
    detail: TripDetail,
    people: TripPeople?,
    onToggleTraveller: (String) -> Unit,
    onDiscount: (Int?) -> Unit,
    companionOn: Boolean,
    onDbNavigator: () -> Unit,
    onTicket: () -> Unit,
    onCompanion: () -> Unit,
) {
    val trip = detail.trip
    val changes = remember(trip) { transfers(trip.legs) }
    val arrivalDelay = finalArrivalDelay(trip.legs)
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "header") { TripHeader(detail) }
        if (trainBindingLifted(trip.fareType, trip.trainBinding, arrivalDelay)) {
            item(key = "binding") { BindingLiftedBanner(arrivalDelay) }
        }
        if (!people?.warnings.isNullOrEmpty() && trip.expectedArrival.isAfter(java.time.Instant.now())) {
            item(key = "warnings") { TripWarningsBanner(people.warnings) }
        }
        item(key = "actions") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onDbNavigator, modifier = Modifier.weight(1f)) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Open DB Navigator", modifier = Modifier.padding(start = 8.dp), maxLines = 1)
                    }
                    FilledTonalButton(onClick = onTicket) {
                        Icon(Icons.Rounded.ConfirmationNumber, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(if (trip.ticketPath != null) "Ticket" else "Add ticket", modifier = Modifier.padding(start = 8.dp))
                    }
                }
                OutlinedButton(onClick = onCompanion, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        if (companionOn) Icons.Rounded.NotificationsOff else Icons.Rounded.NotificationsActive,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        if (companionOn) "Stop live companion" else "Start live companion",
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
        item(key = "map") {
            TripMap(trip.legs, Modifier.fillMaxWidth().height(220.dp).padding(top = 4.dp))
        }
        item(key = "timeline-head") { SectionHeader("Your journey", supporting = timelineSummary(trip)) }
        trip.legs.forEachIndexed { index, leg ->
            if (leg.isWalk) {
                item(key = "walk-$index") { WalkRow(leg) }
            } else {
                changes.firstOrNull { it.departing == index }?.let { change ->
                    item(key = "change-$index") { TransferRow(change, trip.legs) }
                }
                item(key = "leg-$index") { LegCard(leg) }
            }
        }
        people?.let { item(key = "travellers") { TravellersCard(trip, it, onToggleTraveller, onDiscount) } }
    }
}

/** Who rides on this trip, and the BahnCard discount their fare was bought with. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TravellersCard(trip: Trip, people: TripPeople, onToggle: (String) -> Unit, onDiscount: (Int?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        SectionHeader("Travellers", supporting = "Who rides, and the BahnCard their fare counts on")
        NexusCard(Modifier.fillMaxWidth()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                people.travellers.forEach { traveller ->
                    val on = traveller.id in people.onTrip
                    FilterChip(
                        selected = on,
                        onClick = { onToggle(traveller.id) },
                        label = { Text(traveller.name) },
                        leadingIcon = if (on) {
                            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else {
                            null
                        },
                    )
                }
            }
            Text("BahnCard discount", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
            when (trip.fareType) {
                FareType.BahnCard100 -> Text("Every traveller needs a BahnCard 100.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FareType.Deutschlandticket -> Text("None with a Deutschlandticket.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> ConnectedChoiceRow(
                    options = BahnCardDiscounts,
                    selected = trip.bahncardDiscount,
                    onSelect = onDiscount,
                    label = { it?.let { kind -> "BC $kind" } ?: "None" },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** The discounts a fare can be bought with; a BahnCard 100 is a fare type of its own. */
internal val BahnCardDiscounts: List<Int?> = listOf(null, 25, 50)

private fun timelineSummary(trip: Trip): String {
    val rides = trip.legs.count { !it.isWalk }
    val minutes = Duration.between(trip.departureAt, trip.arrivalAt).toMinutes()
    return listOf(
        TripFormat.duration(minutes),
        if (rides <= 1) "direct" else "${rides - 1} ${if (rides == 2) "change" else "changes"}",
        "${trip.fareType.label}, ${if (trip.travelClass == 1) "1st" else "2nd"} class" +
            (trip.bahncardDiscount?.let { ", BahnCard $it" } ?: ""),
    ).joinToString(" · ")
}

@Composable
private fun TripHeader(detail: TripDetail) {
    val trip = detail.trip
    val first = trip.legs.firstOrNull { !it.isWalk }
    val last = trip.legs.lastOrNull { !it.isWalk }
    Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(TripFormat.longDay(trip.departureAt), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(trip.displayTitle, style = MaterialTheme.typography.headlineMedium)
        if (!trip.title.isNullOrBlank()) {
            Text("${trip.originName} → ${trip.destinationName}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                HeaderTime(first?.from?.plannedAt, first?.let(::departureDelay) ?: 0)
                Text(trip.originName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("→", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                HeaderTime(last?.to?.plannedAt, last?.let(::arrivalDelay) ?: 0)
                Text(trip.destinationName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                detail.offlineSince != null -> OfflinePill(detail.offlineSince)
                detail.refreshError != null -> StatusPill("Live data unavailable", offline = true)
                trip.refreshedAt != null -> StatusPill("Live · updated ${TripFormat.time(trip.refreshedAt.toInstantOrNull())}")
            }
            if (trip.legs.none { it.realtime } && trip.legs.any { !it.isWalk }) StatusPill("Timetable only")
            trip.priceCents?.let { StatusPill(TripFormat.price(it)) }
        }
    }
}

/** A big board time with its delay as a badge; the expected time itself is in the timeline below. */
@Composable
private fun HeaderTime(planned: java.time.Instant?, delay: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(TripFormat.time(planned), style = MaterialTheme.typography.displaySmall.tabular())
        DelayBadge(delay)
    }
}

/** One ride: the line, where it is headed, and its two stops with times and platforms. */
@Composable
private fun LegCard(leg: Leg) {
    var expanded by rememberSaveable(leg.tripRef, leg.from.planned) { mutableStateOf(false) }
    val line = MaterialTheme.colorScheme.primary
    val hole = MaterialTheme.colorScheme.surfaceContainerLow
    NexusCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LineChip(leg, large = true)
            Column(Modifier.weight(1f)) {
                leg.headsign?.let { Text("to $it", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                leg.operator?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
            }
            if (leg.cancelled) {
                val colors = danger
                Surface(color = colors.container, contentColor = colors.content, shape = RoundedCornerShape(8.dp)) {
                    Text("Cancelled", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
            } else if (!leg.realtime) {
                StatusPill("Timetable only")
            }
        }
        Row(Modifier.padding(top = 14.dp).height(IntrinsicSize.Min)) {
            Box(
                Modifier.width(20.dp).fillMaxHeight().drawBehind {
                    val x = size.width / 2
                    val top = 14.dp.toPx()
                    val bottom = size.height - 14.dp.toPx()
                    drawLine(line, Offset(x, top), Offset(x, bottom), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
                    drawCircle(line, radius = 7.dp.toPx(), center = Offset(x, top))
                    drawCircle(hole, radius = 3.dp.toPx(), center = Offset(x, top))
                    drawCircle(line, radius = 7.dp.toPx(), center = Offset(x, bottom))
                    drawCircle(hole, radius = 3.dp.toPx(), center = Offset(x, bottom))
                },
            )
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                StopRow(leg.from, departureDelay(leg), leg.cancelled)
                if (leg.stopovers.isNotEmpty()) {
                    Row(
                        Modifier.clickable { expanded = !expanded }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${leg.stopovers.size} ${if (leg.stopovers.size == 1) "stop" else "stops"}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    AnimatedVisibility(expanded) {
                        Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            leg.stopovers.forEach { stop ->
                                val planned = (stop.plannedArrival ?: stop.plannedDeparture).toInstantOrNull()
                                val current = (stop.arrival ?: stop.departure).toInstantOrNull()
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        TripFormat.time(planned),
                                        style = MaterialTheme.typography.bodySmall.tabular(),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.width(44.dp),
                                    )
                                    Text(
                                        stop.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(textDecoration = if (stop.cancelled) TextDecoration.LineThrough else null),
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    DelayBadge(delayMinutes(planned, current))
                                }
                            }
                        }
                    }
                } else {
                    Box(Modifier.height(16.dp))
                }
                StopRow(leg.to, arrivalDelay(leg), leg.cancelled)
            }
        }
    }
}

@Composable
private fun StopRow(stop: StopTime, delay: Int, cancelled: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            TimeWithDelay(stop.plannedAt, stop.currentAt, delay, MaterialTheme.typography.titleLarge, cancelled = cancelled)
            Text(stop.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        PlatformBox(stop.plannedPlatform, stop.platform, platformChanged(stop.plannedPlatform, stop.platform))
    }
}

/** The change between two rides: how long there is, how long it takes, and what that means. */
@Composable
private fun TransferRow(change: Transfer, legs: List<Leg>) {
    val arriving = legs[change.arriving]
    val departing = legs[change.departing]
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.SwapHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(
                    "${change.availableMinutes} min to change" + if (change.changesStation) "" else " at ${departing.from.name}",
                    style = MaterialTheme.typography.titleSmall,
                )
                val platforms = listOfNotNull(arriving.to.shownPlatform, departing.from.shownPlatform)
                Text(
                    when {
                        change.changesStation && change.walkMinutes > 0 -> "Walk ${change.walkMinutes} min to ${departing.from.name}"
                        change.changesStation -> "Change to ${departing.from.name}"
                        platforms.size == 2 && platforms[0] == platforms[1] -> "Same platform ${platforms[0]}"
                        platforms.size == 2 -> "Pl. ${platforms[0]} → Pl. ${platforms[1]}"
                        else -> "Needs about ${change.requiredMinutes} min"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ConfidenceChip(change.confidence)
        }
    }
}

@Composable
private fun WalkRow(leg: Leg) {
    val start = leg.from.plannedAt
    val end = leg.to.plannedAt
    val minutes = if (start != null && end != null) Duration.between(start, end).toMinutes() else null
    val dash = MaterialTheme.colorScheme.outline
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 28.dp).height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(4.dp).fillMaxHeight().drawBehind {
                drawLine(dash, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), strokeWidth = 3.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx())), cap = StrokeCap.Round)
            },
        )
        Icon(Icons.AutoMirrored.Rounded.DirectionsWalk, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp))
        Text(
            "Walk ${minutes?.let { "$it min " } ?: ""}to ${leg.to.name}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

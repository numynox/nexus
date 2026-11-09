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
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
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
import io.github.numynox.nexus.abeona.domain.AlternativesReason
import io.github.numynox.nexus.abeona.domain.TravelDay
import io.github.numynox.nexus.abeona.domain.TravelDayKind
import io.github.numynox.nexus.abeona.domain.alternativesOrigin
import io.github.numynox.nexus.abeona.domain.alternativesReason
import io.github.numynox.nexus.abeona.domain.travelDay
import io.github.numynox.nexus.abeona.domain.missedShortConnection
import io.github.numynox.nexus.abeona.domain.hasImpossibleChange
import io.github.numynox.nexus.abeona.domain.followsReplacement
import io.github.numynox.nexus.abeona.domain.followed
import io.github.numynox.nexus.abeona.live.Background
import io.github.numynox.nexus.abeona.live.CompanionOffer
import io.github.numynox.nexus.abeona.live.companionOffer
import io.github.numynox.nexus.abeona.live.LiveCompanion
import io.github.numynox.nexus.abeona.live.evidence
import io.github.numynox.nexus.abeona.ui.live.LiveDetails
import io.github.numynox.nexus.abeona.ui.live.LiveNow
import io.github.numynox.nexus.abeona.ui.live.LiveView
import io.github.numynox.nexus.abeona.ui.live.liveView
import io.github.numynox.nexus.abeona.live.LiveSignals
import io.github.numynox.nexus.abeona.live.OnboardStatus
import io.github.numynox.nexus.abeona.live.TripLocation
import io.github.numynox.nexus.abeona.ui.common.TravelDayHero
import io.github.numynox.nexus.abeona.ui.common.TravellerPicker
import io.github.numynox.nexus.abeona.ui.common.isLiveDay
import io.github.numynox.nexus.abeona.ui.common.rememberTicker
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
    val stoppedByHand by LiveCompanion.stoppedByHand.collectAsStateWithLifecycle()
    val settings by app.settings.collectAsStateWithLifecycle()
    val currentSettings by androidx.compose.runtime.rememberUpdatedState(settings)
    val alternatives by viewModel.alternatives.collectAsStateWithLifecycle()
    val afterRide by viewModel.afterRide.collectAsStateWithLifecycle()
    val replacements by viewModel.replacements.collectAsStateWithLifecycle()
    val shareLinks by viewModel.shareLinks.collectAsStateWithLifecycle()
    val shareBusy by viewModel.shareBusy.collectAsStateWithLifecycle()
    var recording by rememberSaveable { mutableStateOf(false) }
    val onboard by LiveSignals.onboard.collectAsStateWithLifecycle()
    val fix by LiveSignals.fix.collectAsStateWithLifecycle()
    // Every 15 s: the dashboard's countdowns and the train on its line move with it.
    val now by rememberTicker(Duration.ofSeconds(15))
    var showAlternatives by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val pull = rememberPullRefresh(viewModel::refresh)
    val showError: (String) -> Unit = { message -> scope.launch { snackbar.showSnackbar(message) } }
    var confirmDelete by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(tripId) { viewModel.load() }


    // Two-minute refreshes only while the screen is in front.
    DisposableEffect(lifecycle, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.setVisible(true)
                    // Started by the alarm, the companion could not have the position: take it now.
                    LiveCompanion.takeLocationIfAllowed(context, currentSettings.tripLocation)
                }
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
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) {
            app.setTripLocation(true)
            // A running companion picks the position up when it starts again.
            if (companion == tripId) LiveCompanion.start(context, tripId)
        } else {
            app.dismissLocationPrompt()
        }
    }

    // Opening a trip that is under way is enough to have it followed: the
    // companion starts by itself (the app is in front, so Android allows it),
    // unless it was stopped for this trip by hand.
    val ready = (detail as? Loadable.Ready)?.value?.trip
    val due = ready != null && settings.liveCompanion && Background.isDue(ready, settings.stationMinutes, now)
    LaunchedEffect(due, companion) {
        if (!due || companion != null || LiveCompanion.stoppedByHand.value == tripId) return@LaunchedEffect
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            // Asks once for the notification; the companion starts either way.
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            runCatching { LiveCompanion.start(context, tripId) }
        }
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
                    // The live part follows the trains actually taken: a connection
                    // chosen from the alternatives replaces the rest of the plan.
                    live = state.value.trip.followed.let { trip ->
                        if (!isLiveDay(trip, now)) {
                            null
                        } else {
                            val signals = LiveSignals.forTrip(trip, now, onboard, fix)
                            val day = travelDay(trip.legs, now, settings.stationMinutes, signals)
                            // Nothing to search once the only stop left is the destination.
                            val searchable = alternativesOrigin(trip.legs, day, now) != null
                            val reason = alternativesReason(trip.legs, day).takeIf { searchable }
                            val following = companion == tripId
                            val locationOn = settings.tripLocation && TripLocation(context).hasPermission()
                            LiveDay(
                                missedShort = missedShortConnection(trip.legs, day),
                                view = liveView(
                                    trip, day, now, settings.stationMinutes,
                                    evidence(trip, day, now, onboard, fix, locationOn, following),
                                    LiveSignals.onboardFor(trip, now, onboard),
                                    signals.position,
                                    reason,
                                ),
                                searchable = searchable,
                            )
                        }
                    },
                    askLocation = !settings.tripLocation && !settings.locationAsked,
                    onAllowLocation = { locationPermission.launch(TripLocation.PERMISSIONS) },
                    onDismissLocation = app::dismissLocationPrompt,
                    onUseLocation = {
                        when {
                            !TripLocation(context).hasPermission() -> locationPermission.launch(TripLocation.PERMISSIONS)
                            !settings.tripLocation -> {
                                app.setTripLocation(true)
                                if (companion == tripId) LiveCompanion.start(context, tripId)
                            }
                            // The setting and permission are there: the companion is what follows the position.
                            companion != tripId -> runCatching { LiveCompanion.start(context, tripId) }
                        }
                    },
                    onAlternatives = {
                        showAlternatives = true
                        if (alternatives?.result == null && alternatives?.loading != true) viewModel.findAlternatives()
                    },
                    people = people,
                    afterRide = afterRide?.let { state ->
                        AfterRideActions(
                            state = state,
                            onStatus = { viewModel.setClaimStatus(it, showError) },
                            onPrice = { viewModel.setPrice(it, showError) },
                            onForceMajeure = { viewModel.setForceMajeure(it, showError) },
                            onRecord = { recording = true },
                            onClear = { viewModel.recordDisruption(null, null, showError) },
                        )
                    },
                    share = shareLinks?.let { links ->
                        ShareActions(
                            links = links,
                            busy = shareBusy,
                            onCreate = { kind ->
                                viewModel.createShareLink(kind, showError) { link -> shareLinkIntent(context, state.value.trip, link) }
                            },
                            onShare = { link -> shareLinkIntent(context, state.value.trip, link) },
                            onRevoke = { link -> viewModel.revokeShareLink(link, showError) },
                        )
                    },
                    onToggleTraveller = { id -> viewModel.toggleTraveller(id) { scope.launch { snackbar.showSnackbar(it) } } },
                    onDiscount = { id, value -> viewModel.setDiscount(id, value) { scope.launch { snackbar.showSnackbar(it) } } },
                    companion = companionOffer(
                        state.value.trip, now, settings.liveCompanion, settings.stationMinutes,
                        running = companion == tripId, stoppedByHand = stoppedByHand == tripId,
                    ),
                    liveUpdatesOff = Build.VERSION.SDK_INT >= 36 &&
                        !context.getSystemService(android.app.NotificationManager::class.java).canPostPromotedNotifications(),
                    onLiveUpdatesSettings = {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
                                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName),
                            )
                        }
                    },
                    onDbNavigator = { DbNavigator.open(context) },
                    onTicket = {
                        if (state.value.trip.ticketPath != null) navigator.ticket(tripId)
                        else pickTicket.launch(arrayOf("application/pdf", "image/png", "image/jpeg"))
                    },
                    onFollowPlan = { viewModel.followPlan(showError) },
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

    if (showAlternatives) {
        val trip = (detail as? Loadable.Ready)?.value?.trip?.followed
        val reason = trip?.let { t ->
            if (isLiveDay(t, now)) alternativesReason(t.legs, travelDay(t.legs, now, settings.stationMinutes, LiveSignals.forTrip(t, now, onboard, fix))) else null
        }
        AlternativesSheet(
            state = alternatives,
            reason = reason,
            destination = trip?.destinationName.orEmpty(),
            onSearchAgain = { viewModel.findAlternatives() },
            onDismiss = { showAlternatives = false },
            onTake = { alternative ->
                viewModel.takeConnection(alternative, showError) {
                    showAlternatives = false
                    val first = alternative.legs.firstOrNull { !it.isWalk }
                    scope.launch { snackbar.showSnackbar("Following the ${first?.label ?: "new connection"} from now on") }
                }
            },
        )
    }

    if (recording) {
        (detail as? Loadable.Ready)?.value?.trip?.let { trip ->
            RecordDisruptionSheet(
                trip = trip,
                search = replacements,
                onSearch = { viewModel.searchReplacements(it) },
                onSave = { idx, legs ->
                    viewModel.recordDisruption(idx, legs, showError) { recording = false }
                },
                onDismiss = {
                    recording = false
                    viewModel.clearReplacementSearch()
                },
            )
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

/** The after-the-ride cards' state and what they can do. */
private class AfterRideActions(
    val state: AfterRide,
    val onStatus: (String) -> Unit,
    val onPrice: (Int) -> Unit,
    val onForceMajeure: (Boolean) -> Unit,
    val onRecord: () -> Unit,
    val onClear: () -> Unit,
)

/** The travel day as the trip screen shows it, while the trip is live. */
private data class LiveDay(
    val view: LiveView,
    /** The position saw a short change missed: the binding is lifted (missedShortConnection). */
    val missedShort: Boolean = false,
    /** False once the only stop left is the destination: there is nothing to find another way to. */
    val searchable: Boolean,
) {
    val day: TravelDay get() = view.day
}

@Composable
private fun TripContent(
    detail: TripDetail,
    live: LiveDay?,
    askLocation: Boolean,
    onAllowLocation: () -> Unit,
    onDismissLocation: () -> Unit,
    /** "Use" on the Location row behind the source pill: the setting, the permission, the companion. */
    onUseLocation: () -> Unit,
    onAlternatives: () -> Unit,
    people: TripPeople?,
    afterRide: AfterRideActions?,
    share: ShareActions?,
    onToggleTraveller: (String) -> Unit,
    onDiscount: (travellerId: String, discount: Int?) -> Unit,
    companion: CompanionOffer,
    /** Android 16+ with Live Updates turned off for Abeona: no status bar chip, not pinned on the lock screen. */
    liveUpdatesOff: Boolean,
    onLiveUpdatesSettings: () -> Unit,
    onDbNavigator: () -> Unit,
    onTicket: () -> Unit,
    onCompanion: () -> Unit,
    /** Forget the connection taken instead, and follow the plan again. */
    onFollowPlan: () -> Unit,
) {
    val trip = detail.trip
    // The journey as travelled: the plan, or the connection taken instead.
    val journey = remember(trip) { trip.followed }
    val changes = remember(journey) { transfers(journey.legs) }
    // The ticket's binding is about the booked journey (as on the server), not a connection taken instead.
    val arrivalDelay = finalArrivalDelay(trip.legs)
    val connectionBroken = hasImpossibleChange(trip.legs) || live?.missedShort == true
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "header") { TripHeader(detail) }
        live?.let { l ->
            item(key = "travel-day") { LiveNow(l.view, onAlternatives = onAlternatives, onUseLocation = onUseLocation) }
        }
        val over = trip.expectedArrival.plus(Duration.ofHours(1)).isBefore(java.time.Instant.now())
        if (!over && trainBindingLifted(trip.fareType, trip.trainBinding, arrivalDelay, connectionBroken)) {
            item(key = "binding") {
                BindingLiftedBanner(
                    arrivalDelay,
                    because = when {
                        arrivalDelay >= 20 -> null
                        live?.missedShort == true -> "You missed a change that had less than 5 minutes."
                        else -> "A change on your trip cannot be made."
                    },
                )
            }
        }
        if (!over && trip.followsReplacement) {
            item(key = "following") { FollowingCard(trip, onFollowPlan) }
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
                // The companion's button only when pressing it does something
                // the app would not do by itself: stop it, or start it on the day.
                val companionButton = companion is CompanionOffer.Running || companion is CompanionOffer.Manual
                val alternativesButton = !over && live != null && live.searchable
                if (companionButton || alternativesButton) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (companionButton) {
                        val on = companion is CompanionOffer.Running
                        OutlinedButton(onClick = onCompanion, modifier = Modifier.weight(1f)) {
                            Icon(
                                if (on) Icons.Rounded.NotificationsOff else Icons.Rounded.NotificationsActive,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                if (on) "Stop live companion" else "Start live companion",
                                modifier = Modifier.padding(start = 8.dp),
                                maxLines = 1,
                            )
                        }
                    }
                    if (alternativesButton) {
                        OutlinedButton(onClick = onAlternatives) {
                            Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Alternatives", modifier = Modifier.padding(start = 8.dp), maxLines = 1)
                        }
                    }
                }
                if (companion is CompanionOffer.Automatic) CompanionSmallPrint(companion)
                if (liveUpdatesOff && (companion is CompanionOffer.Running || companion is CompanionOffer.Automatic)) {
                    LiveUpdatesOff(onLiveUpdatesSettings)
                }
            }
        }
        live?.let { l ->
            // The ticket and DB Navigator stay one tap from the top; the
            // rest of the dashboard follows them.
            item(key = "live-details") { LiveDetails(l.view) }
            if (askLocation && l.day.kind != TravelDayKind.Arrived) {
                item(key = "location") { LocationPrompt(onAllow = onAllowLocation, onDismiss = onDismissLocation) }
            }
        }
        afterRide?.let { a ->
            item(key = "compensation") { CompensationCard(trip, a.state, a.onStatus, a.onPrice, a.onForceMajeure) }
            item(key = "what-happened") { WhatHappenedCard(trip, a.state.busy, a.onRecord, a.onClear) }
        }
        // While the trip is live the dashboard has the map, with the traveller on it.
        if (live == null || live.day.kind == TravelDayKind.Arrived) {
            item(key = "map") {
                TripMap(journey.legs, Modifier.fillMaxWidth().height(220.dp).padding(top = 4.dp))
            }
        }
        item(key = "timeline-head") { SectionHeader("Your journey", supporting = timelineSummary(trip)) }
        journey.legs.forEachIndexed { index, leg ->
            if (leg.isWalk) {
                item(key = "walk-$index") { WalkRow(leg) }
            } else {
                changes.firstOrNull { it.departing == index }?.let { change ->
                    item(key = "change-$index") { TransferRow(change, journey.legs) }
                }
                item(key = "leg-$index") { LegCard(leg) }
            }
        }
        people?.let { item(key = "travellers") { TravellersCard(trip, it, onToggleTraveller, onDiscount) } }
        share?.let { item(key = "share") { ShareCard(it) } }
    }
}

/**
 * Asks once, on a live trip, whether the companion may use the position:
 * what it is for, and that it stays on the phone. The permission prompt
 * follows only after "Use location".
 */
@Composable
private fun LocationPrompt(onAllow: () -> Unit, onDismiss: () -> Unit) {
    NexusCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.secondaryContainer) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.MyLocation, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(top = 2.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Know where you are on the trip", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(
                    "With your location, the live companion can tell whether you are at the station, on board, or have missed a change. " +
                        "Only while it runs, and it never leaves this phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    Button(onClick = onAllow) { Text("Use location") }
                    TextButton(onClick = onDismiss) { Text("Not now") }
                }
            }
        }
    }
}

/** Who rides on this trip, and the BahnCard discount each one's share was bought with. */
@Composable
private fun TravellersCard(trip: Trip, people: TripPeople, onToggle: (String) -> Unit, onDiscount: (String, Int?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        SectionHeader("Travellers", supporting = "Who rides, and the BahnCard each one's fare counts on")
        NexusCard(Modifier.fillMaxWidth()) {
            val showDiscount = trip.fareType != FareType.BahnCard100 && trip.fareType != FareType.Deutschlandticket
            TravellerPicker(
                travellers = people.travellers,
                selected = people.onTrip,
                showDiscount = showDiscount,
                onToggle = { onToggle(it.id) },
                onDiscount = onDiscount,
            )
            when (trip.fareType) {
                FareType.BahnCard100 -> Text("Every traveller needs a BahnCard 100.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                FareType.Deutschlandticket -> Text("No BahnCard discount with a Deutschlandticket.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                else -> Unit
            }
        }
    }
}

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
                trip.finalizedAt != null -> StatusPill("Final times recorded")
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

/** In place of a "Start" button the app would press by itself anyway: when it will. */
@Composable
private fun CompanionSmallPrint(offer: CompanionOffer.Automatic) {
    val now = java.time.Instant.now()
    val day = TripFormat.day(offer.at)
    val text = when {
        !offer.at.isAfter(now) -> "Abeona is starting to follow this trip live."
        day == "Today" -> "Abeona follows this trip live by itself from ${TripFormat.time(offer.at)}."
        day == "Tomorrow" -> "Abeona follows this trip live by itself from tomorrow, ${TripFormat.time(offer.at)}."
        else -> "Abeona follows this trip live by itself from $day, ${TripFormat.time(offer.at)}."
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
        Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Android 16+: without Live Updates the notification is not pinned on the lock screen nor shown as a chip. */
@Composable
private fun LiveUpdatesOff(onSettings: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 4.dp)) {
        Text(
            "Live Updates are off for Abeona: the companion is not pinned to the lock screen and the status bar.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onSettings) { Text("Turn on") }
    }
}

/** A connection from the alternatives was taken: which, and the way back to the plan. */
@Composable
private fun FollowingCard(trip: Trip, onFollowPlan: () -> Unit) {
    val replacement = trip.replacementLegs.orEmpty()
    val first = replacement.firstOrNull { !it.isWalk } ?: replacement.firstOrNull() ?: return
    NexusCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.secondaryContainer) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Shuffle, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Column(Modifier.weight(1f)) {
                Text("You follow another connection", style = MaterialTheme.typography.titleSmall)
                Text(
                    "From ${first.from.name}: " + replacement.filter { !it.isWalk }.joinToString(" › ") { it.label } +
                        ". The dashboard and the live companion follow these trains.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        TextButton(onClick = onFollowPlan, modifier = Modifier.align(Alignment.End)) { Text("Back to the plan") }
    }
}

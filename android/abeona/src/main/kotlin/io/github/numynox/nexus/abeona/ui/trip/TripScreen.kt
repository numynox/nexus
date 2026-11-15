package io.github.numynox.nexus.abeona.ui.trip

import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.rounded.MoreVert
import io.github.numynox.nexus.abeona.ui.common.ShiftedTime
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.numynox.nexus.abeona.data.Approach
import io.github.numynox.nexus.abeona.data.Leg
import io.github.numynox.nexus.abeona.data.Trip
import io.github.numynox.nexus.abeona.data.toInstantOrNull
import io.github.numynox.nexus.abeona.domain.Transfer
import io.github.numynox.nexus.abeona.domain.finalArrivalDelay
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
import io.github.numynox.nexus.abeona.ui.live.LiveMap
import io.github.numynox.nexus.abeona.ui.live.LiveCard
import io.github.numynox.nexus.abeona.ui.live.rememberApproach
import io.github.numynox.nexus.abeona.ui.live.LiveStatus
import io.github.numynox.nexus.abeona.ui.live.liveView
import io.github.numynox.nexus.abeona.live.LiveSignals
import io.github.numynox.nexus.abeona.live.OnboardStatus
import io.github.numynox.nexus.abeona.live.TripLocation
import io.github.numynox.nexus.abeona.ui.common.isLiveDay
import io.github.numynox.nexus.abeona.ui.common.rememberTicker
import io.github.numynox.nexus.abeona.ui.AppViewModel
import io.github.numynox.nexus.abeona.ui.Navigator
import io.github.numynox.nexus.abeona.ui.common.ConfidenceChip
import io.github.numynox.nexus.abeona.ui.common.DbNavigator
import io.github.numynox.nexus.abeona.ui.common.LineChip
import io.github.numynox.nexus.abeona.ui.common.PickedFile
import io.github.numynox.nexus.abeona.ui.common.TripFormat
import io.github.numynox.nexus.abeona.ui.common.containerViewModel
import io.github.numynox.nexus.core.ui.Loadable
import io.github.numynox.nexus.core.ui.components.ErrorState
import io.github.numynox.nexus.core.ui.components.PlaceholderList
import io.github.numynox.nexus.core.ui.components.rememberPullRefresh
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.ButtonDefaults
import io.github.numynox.nexus.abeona.ui.AbeonaType
import androidx.compose.ui.text.font.FontWeight
import io.github.numynox.nexus.abeona.ui.bad
import io.github.numynox.nexus.abeona.ui.brand
import io.github.numynox.nexus.abeona.ui.card
import io.github.numynox.nexus.abeona.ui.muted
import io.github.numynox.nexus.abeona.ui.common.Banner
import io.github.numynox.nexus.abeona.ui.common.BindingBanner
import io.github.numynox.nexus.abeona.ui.common.Fact
import io.github.numynox.nexus.abeona.ui.common.FactLine
import io.github.numynox.nexus.abeona.ui.common.Rail
import io.github.numynox.nexus.abeona.ui.common.RailDot
import io.github.numynox.nexus.abeona.ui.common.RailLine
import io.github.numynox.nexus.abeona.ui.common.SectionHeader
import io.github.numynox.nexus.abeona.ui.common.StatusChip
import io.github.numynox.nexus.abeona.ui.common.StatusTone
import io.github.numynox.nexus.abeona.ui.common.StopRow
import io.github.numynox.nexus.abeona.ui.common.SurfaceCard
import io.github.numynox.nexus.abeona.ui.common.bahnCardsShort
import io.github.numynox.nexus.abeona.ui.live.LiveStatusKind
import io.github.numynox.nexus.abeona.ui.live.LiveView
import io.github.numynox.nexus.abeona.ui.live.liveStatusIcon
import java.time.Duration
import kotlinx.coroutines.launch

@Composable
fun TripScreen(app: AppViewModel, tripId: String, navigator: Navigator) {
    val context = LocalContext.current
    val application = context.applicationContext as android.app.Application
    val viewModel = containerViewModel(key = tripId) { TripViewModel(it, application, tripId) }
    val detail by viewModel.detail.collectAsStateWithLifecycle()
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
    var editing by rememberSaveable { mutableStateOf(false) }
    var sharing by rememberSaveable { mutableStateOf(false) }
    val onboard by LiveSignals.onboard.collectAsStateWithLifecycle()
    val fix by LiveSignals.fix.collectAsStateWithLifecycle()
    // Every 15 s: the dashboard's countdowns and the train on its line move with it.
    val now by rememberTicker(Duration.ofSeconds(15))
    var showAlternatives by rememberSaveable { mutableStateOf(false) }
    var showDroppedAlternatives by rememberSaveable { mutableStateOf(false) }
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

    // The trip's actions live in the top bar: the ticket one tap away, the rest under ⋮.
    val openTicket: () -> Unit = {
        if (ready?.ticketPath != null) navigator.ticket(tripId)
        else pickTicket.launch(arrayOf("application/pdf", "image/png", "image/jpeg"))
    }
    val toggleCompanion: () -> Unit = {
        if (companion == tripId) {
            LiveCompanion.stop(context)
        } else if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            LiveCompanion.start(context, tripId)
        }
    }
    val openAlternatives: () -> Unit = {
        showAlternatives = true
        if (alternatives?.result == null && alternatives?.loading != true) viewModel.findAlternatives()
    }
    val offer = ready?.let {
        companionOffer(it, now, settings.liveCompanion, settings.stationMinutes, running = companion == tripId, stoppedByHand = stoppedByHand == tripId)
    }
    val over = ready?.expectedArrival?.plus(Duration.ofHours(1))?.isBefore(java.time.Instant.now()) ?: true
    // Nothing to search once the only stop left is the destination.
    val searchable = ready?.followed?.takeIf { isLiveDay(it, now) }?.let { t ->
        alternativesOrigin(t.legs, travelDay(t.legs, now, settings.stationMinutes, LiveSignals.forTrip(t, now, onboard, fix)), now) != null
    } == true
    var menu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                // The route once, here; the trip's own title, when it has one, heads the page.
                title = { ready?.let { Text("${it.originName} → ${it.destinationName}", style = AbeonaType.body.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                navigationIcon = { IconButton(onClick = navigator.back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
                actions = {
                    if (ready != null) {
                        IconButton(onClick = openTicket) {
                            Icon(Icons.Rounded.ConfirmationNumber, contentDescription = if (ready.ticketPath != null) "Ticket" else "Add ticket")
                        }
                    }
                    IconButton(onClick = { viewModel.refresh() }) { Icon(Icons.Rounded.Refresh, contentDescription = "Refresh") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (ready != null) {
                                DropdownMenuItem(
                                    text = { Text("Edit trip") },
                                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                                    onClick = { menu = false; editing = true },
                                )
                                DropdownMenuItem(
                                    text = { Text("Share") },
                                    leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                                    onClick = { menu = false; sharing = true },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Open DB Navigator") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null) },
                                onClick = { menu = false; DbNavigator.open(context) },
                            )
                            if (!over && searchable) {
                                DropdownMenuItem(
                                    text = { Text("Alternatives") },
                                    leadingIcon = { Icon(Icons.Rounded.Shuffle, contentDescription = null) },
                                    onClick = { menu = false; openAlternatives() },
                                )
                            }
                            // Only when pressing it does something the app would not do by
                            // itself: stop the companion, or start it on the day.
                            if (offer is CompanionOffer.Running || offer is CompanionOffer.Manual) {
                                val on = offer is CompanionOffer.Running
                                DropdownMenuItem(
                                    text = { Text(if (on) "Stop live companion" else "Start live companion") },
                                    leadingIcon = { Icon(if (on) Icons.Rounded.NotificationsOff else Icons.Rounded.NotificationsActive, contentDescription = null) },
                                    onClick = { menu = false; toggleCompanion() },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Delete trip", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { menu = false; confirmDelete = true },
                            )
                        }
                    }
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
                    onAlternatives = openAlternatives,
                    onTicket = openTicket,
                    fetchApproach = app.container.repository::approach,
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
                    companion = offer ?: CompanionOffer.Hidden,
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
                    onFollowPlan = { viewModel.followPlan(showError) },
                    onDroppedAlternatives = {
                        showDroppedAlternatives = true
                        viewModel.findDroppedAlternatives()
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

    if (showDroppedAlternatives) {
        AlternativesSheet(
            state = alternatives,
            reason = AlternativesReason.Dropped,
            destination = (detail as? Loadable.Ready)?.value?.trip?.destinationName.orEmpty(),
            onSearchAgain = { viewModel.findDroppedAlternatives() },
            onDismiss = { showDroppedAlternatives = false },
            onTake = { alternative ->
                viewModel.takeInstead(alternative, showError) {
                    showDroppedAlternatives = false
                    val first = alternative.legs.firstOrNull { !it.isWalk }
                    scope.launch { snackbar.showSnackbar("Following the ${first?.label ?: "new connection"} instead") }
                }
            },
        )
    }

    if (editing) {
        (detail as? Loadable.Ready)?.value?.trip?.let { trip ->
            EditTripSheet(
                trip = trip,
                onSave = { edit ->
                    viewModel.editTrip(edit, onError = { message -> editing = false; showError(message) }) {
                        editing = false
                        scope.launch { snackbar.showSnackbar("Trip saved") }
                    }
                },
                onDismiss = { editing = false },
            )
        }
    }

    if (sharing) {
        (detail as? Loadable.Ready)?.value?.trip?.let { trip ->
            ShareSheet(
                actions = shareLinks?.let { links ->
                    ShareActions(
                        links = links,
                        busy = shareBusy,
                        onCreate = { kind ->
                            viewModel.createShareLink(kind, showError) { link -> shareLinkIntent(context, trip, link) }
                        },
                        onShare = { link -> shareLinkIntent(context, trip, link) },
                        onRevoke = { link -> viewModel.revokeShareLink(link, showError) },
                    )
                },
                onDismiss = { sharing = false },
            )
        }
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
    afterRide: AfterRideActions?,
    companion: CompanionOffer,
    /** Android 16+ with Live Updates turned off for Abeona: no status bar chip, not pinned on the lock screen. */
    liveUpdatesOff: Boolean,
    onLiveUpdatesSettings: () -> Unit,
    /** Forget the connection taken instead, and follow the plan again. */
    onFollowPlan: () -> Unit,
    /** A ride no longer in the timetable: find another connection. */
    onDroppedAlternatives: () -> Unit,
    /** The ticket: show it, or pick one to add. */
    onTicket: () -> Unit,
    /** Where the train to catch is on its way to the boarding stop (AbeonaRepository.approach). */
    fetchApproach: suspend (Leg) -> Approach? = { null },
) {
    val trip = detail.trip
    // The journey as travelled: the plan, or the connection taken instead.
    val journey = remember(trip) { trip.followed }
    val changes = remember(journey) { transfers(journey.legs) }
    // The ticket's binding is about the booked journey (as on the server), not a connection taken instead.
    val arrivalDelay = finalArrivalDelay(trip.legs)
    // A ride no longer in the timetable for its day (AbeonaRepository.checkTimetable): cancelled as booked.
    val droppedIdx = trip.legs.indexOfFirst { !it.isWalk && it.dropped }
    val dropped = trip.legs.getOrNull(droppedIdx)
    val connectionBroken = hasImpossibleChange(trip.legs) || live?.missedShort == true || dropped != null
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        val over = trip.expectedArrival.plus(Duration.ofHours(1)).isBefore(java.time.Instant.now())
        val binding = !over && trainBindingLifted(trip.fareType, trip.trainBinding, arrivalDelay, connectionBroken)
        val following = !over && trip.followsReplacement
        // The binding and a connection taken instead: in the live card while live.
        val notices: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // A dropped ride, or a cancelled one in the live card, says the binding is lifted itself.
                if (binding && dropped == null && live?.view?.leg?.cancelled != true) {
                    BindingBanner(
                        arrivalDelay,
                        because = when {
                            arrivalDelay >= 20 -> null
                            live?.missedShort == true -> "You missed a change that had less than 5 minutes"
                            else -> "A change on your trip cannot be made"
                        },
                    )
                }
                if (dropped != null && !following) DroppedBanner(dropped, bindingLifted = binding, onDroppedAlternatives)
                // The same trains at other times, taken by Abeona for the dropped ride: there is no plan to go back to.
                if (following && dropped != null && (trip.missedLegIdx ?: Int.MAX_VALUE) <= droppedIdx) {
                    AdjustedBanner(trip, dropped)
                } else if (following) {
                    FollowingBanner(trip, onFollowPlan)
                }
            }
        }
        val anyNotice = binding || following || (!over && dropped != null)
        // While the trip is live, everything live is in the one card; above it only what does not change.
        item(key = "header") { TripHeader(trip, live = live != null) }
        if (live != null) {
            item(key = "travel-day") {
                LiveCard(
                    live.view,
                    approach = rememberApproach(live.view.leg, live.day, fetchApproach),
                    status = liveStatus(detail),
                    onAlternatives = onAlternatives,
                    onUseLocation = onUseLocation,
                    notices = if (anyNotice) notices else null,
                )
            }
        } else if (anyNotice) {
            item(key = "notices") { notices() }
        }
        live?.let { l ->
            item(key = "live-map") { LiveMap(l.view) }
            if (askLocation && l.day.kind != TravelDayKind.Arrived) {
                item(key = "location") { LocationPrompt(onAllow = onAllowLocation, onDismiss = onDismissLocation) }
            }
        }
        val smallPrint = companion is CompanionOffer.Automatic
        val updatesOff = liveUpdatesOff && (companion is CompanionOffer.Running || companion is CompanionOffer.Automatic)
        if (smallPrint || updatesOff) {
            item(key = "companion") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (companion is CompanionOffer.Automatic) CompanionSmallPrint(companion)
                    if (updatesOff) LiveUpdatesOff(onLiveUpdatesSettings)
                }
            }
        }
        // After the ride the verdict is the answer (abeona-design.md §2, moment 12): before the journey it is about.
        afterRide?.let { a ->
            item(key = "compensation") { CompensationCard(trip, a.state, a.onStatus, a.onPrice, a.onForceMajeure) }
            item(key = "what-happened") { WhatHappenedCard(trip, a.state.busy, a.onRecord, a.onClear) }
        }
        // The header has the duration and the changes when it shows the times; while live the timeline's head does.
        item(key = "timeline-head") {
            SectionHeader("Your journey") { if (live != null) JourneyFacts(trip) }
        }
        item(key = "timeline") {
            // "Timetable only" on a ride only when others are live; otherwise the freshness line says it once.
            Timeline(journey.legs, changes, freshness = if (live == null) freshness(detail) else null)
        }
        item(key = "ticket") { TicketCard(trip, onTicket) }
        // While the trip is live the dashboard has the map, with the traveller on it.
        if (live == null || live.day.kind == TravelDayKind.Arrived) {
            item(key = "map") { TripMap(journey.legs, Modifier.fillMaxWidth().height(220.dp)) }
        }
    }
}

/**
 * Asks once, on a live trip, whether the companion may use the position:
 * what it is for, and that it stays on the phone. The permission prompt
 * follows only after "Use location".
 */
@Composable
private fun LocationPrompt(onAllow: () -> Unit, onDismiss: () -> Unit) {
    Banner(
        StatusTone.Quiet,
        Icons.Rounded.MyLocation,
        "Know where you are on the trip",
        text = "To tell whether you are at the station, on board or have missed a change: only while the live companion runs, and it never leaves this phone.",
    ) {
        OutlinedButton(onClick = onAllow) { Text("Use location") }
        TextButton(onClick = onDismiss) { Text("Not now") }
    }
}

/** How long the journey takes and how many changes it has. */
@Composable
private fun JourneyFacts(trip: Trip) {
    val rides = trip.legs.count { !it.isWalk }
    Fact(Icons.Rounded.Schedule, "Duration", TripFormat.shortDuration(Duration.between(trip.departureAt, trip.arrivalAt).toMinutes()))
    Fact(Icons.Rounded.SwapHoriz, "Changes", "${(rides - 1).coerceAtLeast(0)}")
}

/** The live card's last line: how fresh the live values are, or why there are none. */
private fun liveStatus(detail: TripDetail): LiveStatus? {
    val trip = detail.trip
    val timetableOnly = trip.legs.none { it.realtime } && trip.legs.any { !it.isWalk }
    val refreshed = trip.refreshedAt.toInstantOrNull()
    return when {
        detail.offlineSince != null -> LiveStatus(LiveStatusKind.Offline, "Offline · updated", TripFormat.time(detail.offlineSince))
        detail.refreshError != null -> LiveStatus(LiveStatusKind.Unavailable, "Live data", "unavailable")
        // Not "Live · … · timetable only": one word for where the times come from.
        timetableOnly && refreshed != null -> LiveStatus(LiveStatusKind.Timetable, "Timetable · updated", TripFormat.time(refreshed))
        timetableOnly -> LiveStatus(LiveStatusKind.Timetable, "Times", "Timetable only")
        refreshed != null -> LiveStatus(LiveStatusKind.Live, "Live · updated", TripFormat.time(refreshed))
        else -> null
    }
}

/** Off the travel day, the timeline's last line: how fresh its times are, or that they are final. */
private fun freshness(detail: TripDetail): LiveStatus? =
    if (detail.offlineSince == null && detail.refreshError == null && detail.trip.finalizedAt != null) {
        LiveStatus(LiveStatusKind.Final, "Times", "Final times recorded")
    } else {
        liveStatus(detail)
    }

/**
 * The trip's day and, when it has one, its title (the route is the top
 * bar's). While the trip is live that is all: its times are in the live
 * card. Otherwise the departure and the arrival with their delays, and one
 * line of facts.
 */
@Composable
private fun TripHeader(trip: Trip, live: Boolean) {
    val first = trip.legs.firstOrNull { !it.isWalk }
    val last = trip.legs.lastOrNull { !it.isWalk }
    Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(TripFormat.longDay(trip.departureAt), style = AbeonaType.meta, color = brand)
        if (!trip.title.isNullOrBlank()) Text(trip.displayTitle, style = AbeonaType.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (live) return@Column
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShiftedTime(first?.from?.plannedAt, first?.from?.expectedAt, AbeonaType.time, cancelled = first?.cancelled == true)
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "to", tint = muted, modifier = Modifier.size(20.dp))
            ShiftedTime(last?.to?.plannedAt, last?.to?.expectedAt, AbeonaType.time, cancelled = last?.cancelled == true)
        }
        FactLine { JourneyFacts(trip) }
    }
}

/**
 * What the ticket is: fare and class in words (they carry the ticket's
 * terms), the price, the travellers and their BahnCards, and the way to the
 * ticket itself.
 */
@Composable
private fun TicketCard(trip: Trip, onTicket: () -> Unit) {
    SurfaceCard(padding = PaddingValues(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.ConfirmationNumber, contentDescription = null, tint = muted, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${trip.fareType.label}, ${if (trip.travelClass == 1) "1st" else "2nd"} class", style = AbeonaType.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                FactLine {
                    trip.priceCents?.let { Text(TripFormat.price(it), style = AbeonaType.meta, color = muted) }
                    trip.travellerCount?.let { Fact(Icons.Rounded.Person, "Travellers", "$it") }
                    if (trip.bahncardDiscounts.isNotEmpty()) Fact(Icons.Rounded.CreditCard, "BahnCards", bahnCardsShort(trip.bahncardDiscounts))
                }
            }
            TextButton(onClick = onTicket) { Text(if (trip.ticketPath != null) "Show ticket" else "Add ticket") }
        }
    }
}

/**
 * The journey on one continuous rail, in the row grid: a ride is its first
 * and last stop with its line between them and the stops it passes folded
 * behind "n stops"; a change is one row; a walk at either end one row. The
 * last line says how fresh the times are.
 */
@Composable
private fun Timeline(legs: List<Leg>, changes: List<Transfer>, freshness: LiveStatus?) {
    val anyLive = legs.any { it.realtime }
    SurfaceCard(padding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        val rides = legs.indices.filter { !legs[it].isWalk }
        legs.forEachIndexed { index, leg ->
            val firstRide = index == rides.firstOrNull()
            val lastRide = index == rides.lastOrNull()
            if (leg.isWalk) {
                // A walk between two rides is its change's; only one before the first or after the last is its own row.
                if (rides.isEmpty() || index < rides.first() || index > rides.last()) WalkRow(leg, after = index > (rides.lastOrNull() ?: -1))
            } else {
                changes.firstOrNull { it.departing == index }?.let { ChangeRow(it, legs) }
                RideRows(leg, timetableOnly = !leg.realtime && anyLive, walkBefore = firstRide && index > 0, walkAfter = lastRide && index < legs.lastIndex)
            }
        }
        freshness?.let {
            Fact(
                liveStatusIcon(it.kind),
                it.label,
                it.text,
                Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
        }
    }
}

/** One ride on the rail: where it starts, its line and where it is headed, the stops it passes, where it ends. */
@Composable
private fun RideRows(leg: Leg, timetableOnly: Boolean, walkBefore: Boolean, walkAfter: Boolean) {
    var expanded by rememberSaveable(leg.tripRef, leg.from.planned) { mutableStateOf(false) }
    val line = if (leg.cancelled) RailLine.Faint else RailLine.Solid
    StopRow(
        name = leg.from.name,
        planned = leg.from.plannedAt, expected = leg.from.expectedAt, cancelled = leg.cancelled,
        platformPlanned = leg.from.plannedPlatform, platformCurrent = leg.from.platform,
        strong = true,
        rail = Rail(above = if (walkBefore) RailLine.Dashed else RailLine.None, below = line),
    )
    StopRow(
        name = "",
        rail = Rail(line, line, RailDot.None),
        content = {
            LineChip(leg)
            Text(
                listOfNotNull(leg.headsign?.let { "to $it" }, leg.operator).joinToString(" · "),
                style = AbeonaType.meta, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
            )
        },
        trailing = {
            if (leg.cancelled) {
                StatusChip(StatusTone.Bad, Icons.Rounded.Block, label = if (leg.dropped) "Not in the timetable" else "Cancelled")
            } else if (timetableOnly) {
                StatusChip(StatusTone.Quiet, Icons.Rounded.Schedule, description = "Timetable only")
            }
        },
    )
    if (leg.stopovers.isNotEmpty()) {
        StopRow(
            name = "",
            rail = Rail(line, line, RailDot.None),
            onClick = { expanded = !expanded },
            content = {
                Text("${leg.stopovers.size} ${if (leg.stopovers.size == 1) "stop" else "stops"}", style = AbeonaType.meta, color = brand)
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, tint = brand, modifier = Modifier.size(20.dp))
            },
        )
        AnimatedVisibility(expanded) {
            Column {
                leg.stopovers.forEach { stop ->
                    StopRow(
                        name = stop.name,
                        planned = (stop.plannedArrival ?: stop.plannedDeparture).toInstantOrNull(),
                        expected = (stop.arrival ?: stop.departure).toInstantOrNull(),
                        cancelled = stop.cancelled,
                        quiet = true,
                        rail = Rail(line, line, RailDot.Through),
                        minHeight = 36.dp,
                    )
                }
            }
        }
    }
    StopRow(
        name = leg.to.name,
        planned = leg.to.plannedAt, expected = leg.to.expectedAt, cancelled = leg.cancelled,
        platformPlanned = leg.to.plannedPlatform, platformCurrent = leg.to.platform,
        strong = true,
        rail = Rail(above = line, below = if (walkAfter) RailLine.Dashed else RailLine.None),
    )
}

/**
 * The change between two rides, one row between their stops: how long there
 * is and what that means, and the walk when the next train leaves from
 * somewhere else. The platforms are the two stops' own, above and below.
 */
@Composable
private fun ChangeRow(change: Transfer, legs: List<Leg>) {
    val arriving = legs[change.arriving]
    val departing = legs[change.departing]
    StopRow(
        name = "",
        rail = Rail(RailLine.Dashed, RailLine.Dashed, RailDot.None),
        mark = { RailIcon(Icons.Rounded.SwapHoriz, "Change") },
        content = {
            ConfidenceChip(change.confidence, minutes = change.availableMinutes)
            when {
                change.walkMinutes > 0 -> Fact(Icons.AutoMirrored.Rounded.DirectionsWalk, "Walk", "${change.walkMinutes} min")
                // Without both platforms the time it needs is all there is to say about the way.
                !change.changesStation && (arriving.to.shownPlatform == null || departing.from.shownPlatform == null) ->
                    Text("needs about ${change.requiredMinutes} min", style = AbeonaType.meta, color = muted, maxLines = 1)
            }
        },
    )
}

/** A walk before the first ride or after the last: how long, and where to. */
@Composable
private fun WalkRow(leg: Leg, after: Boolean) {
    val start = leg.from.plannedAt
    val end = leg.to.plannedAt
    val minutes = if (start != null && end != null) Duration.between(start, end).toMinutes() else null
    StopRow(
        name = if (after) leg.to.name else leg.from.name,
        planned = if (after) end else start,
        quiet = true,
        rail = Rail(if (after) RailLine.Dashed else RailLine.None, if (after) RailLine.None else RailLine.Dashed, RailDot.None),
        mark = { RailIcon(Icons.AutoMirrored.Rounded.DirectionsWalk, "Walk") },
        trailing = { minutes?.let { Text("$it min", style = AbeonaType.meta, color = muted) } },
    )
}

/** An icon standing on the rail: on a disc of the card's colour, so the line stops at it. */
@Composable
private fun RailIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String) {
    Surface(color = card, shape = CircleShape, modifier = Modifier.size(24.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = description, tint = muted, modifier = Modifier.size(20.dp)) }
    }
}

/** In place of a "Start" button the app would press by itself anyway: when it will. */
@Composable
private fun CompanionSmallPrint(offer: CompanionOffer.Automatic) {
    val now = java.time.Instant.now()
    val day = TripFormat.day(offer.at)
    val text = when {
        !offer.at.isAfter(now) -> "Live companion starting"
        day == "Today" -> "Live companion starts at ${TripFormat.time(offer.at)}"
        day == "Tomorrow" -> "Live companion starts tomorrow, ${TripFormat.time(offer.at)}"
        else -> "Live companion starts $day, ${TripFormat.time(offer.at)}"
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
        Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = muted, modifier = Modifier.size(16.dp))
        Text(text, style = AbeonaType.meta, color = muted)
    }
}

/** Android 16+: without Live Updates the notification is not pinned on the lock screen nor shown as a chip. */
@Composable
private fun LiveUpdatesOff(onSettings: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 4.dp)) {
        Text(
            "Live Updates are off for Abeona: the companion is not pinned to the lock screen and the status bar.",
            style = AbeonaType.meta,
            color = muted,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onSettings) { Text("Turn on") }
    }
}

/** A ride no longer in the timetable for its day, and nothing followed instead yet: the one thing that broke. */
@Composable
private fun DroppedBanner(dropped: Leg, bindingLifted: Boolean, onFindAlternatives: () -> Unit) {
    Banner(
        StatusTone.Bad,
        Icons.Rounded.Block,
        "${dropped.label} is no longer in the timetable",
        text = if (bindingLifted) "Train binding lifted." else null,
    ) {
        Button(onClick = onFindAlternatives, colors = ButtonDefaults.buttonColors(containerColor = bad.fill.container, contentColor = bad.fill.content)) {
            Text("Find another connection")
        }
    }
}

/** The same trains at other times, taken by Abeona because [dropped] is no longer in the timetable. */
@Composable
private fun AdjustedBanner(trip: Trip, dropped: Leg) {
    val replacement = trip.replacementLegs.orEmpty()
    if (replacement.none { !it.isWalk }) return
    Banner(
        StatusTone.Watch,
        Icons.Rounded.Schedule,
        "${dropped.label} is no longer in the timetable",
        text = "Now: " + replacement.filter { !it.isWalk }.joinToString(" › ") { "${it.label} ${TripFormat.time(it.from.plannedAt)} ${it.from.name}" },
    )
}

/** A connection from the alternatives was taken: which, and the way back to the plan. */
@Composable
private fun FollowingBanner(trip: Trip, onFollowPlan: () -> Unit) {
    val replacement = trip.replacementLegs.orEmpty()
    val first = replacement.firstOrNull { !it.isWalk } ?: replacement.firstOrNull() ?: return
    Banner(
        StatusTone.Quiet,
        Icons.Rounded.Shuffle,
        "You follow another connection",
        text = "From ${first.from.name}: " + replacement.filter { !it.isWalk }.joinToString(" › ") { it.label } +
            ", on the live card and in the live companion.",
    ) {
        OutlinedButton(onClick = onFollowPlan) { Text("Back to the plan") }
    }
}

package io.github.numynox.nexus.abeona.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.TripOrigin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.numynox.nexus.abeona.data.Journey
import io.github.numynox.nexus.abeona.data.ProductFilter
import io.github.numynox.nexus.abeona.data.Station
import io.github.numynox.nexus.abeona.domain.changes
import io.github.numynox.nexus.abeona.domain.departureDelay
import io.github.numynox.nexus.abeona.domain.finalArrivalDelay
import io.github.numynox.nexus.abeona.domain.worstConfidence
import io.github.numynox.nexus.abeona.ui.AppViewModel
import io.github.numynox.nexus.abeona.ui.Navigator
import io.github.numynox.nexus.abeona.ui.common.ShiftedTime
import io.github.numynox.nexus.abeona.ui.common.ConfidenceChip
import io.github.numynox.nexus.abeona.ui.common.LegChips
import io.github.numynox.nexus.abeona.ui.common.TripFormat
import io.github.numynox.nexus.abeona.ui.common.containerViewModel
import io.github.numynox.nexus.abeona.ui.intake.IntakeSheet
import io.github.numynox.nexus.abeona.ui.intake.IntakeState
import io.github.numynox.nexus.abeona.ui.intake.IntakeViewModel
import io.github.numynox.nexus.core.ui.Loadable
import io.github.numynox.nexus.core.ui.components.ConnectedChoiceRow
import io.github.numynox.nexus.core.ui.components.EmptyState
import io.github.numynox.nexus.core.ui.components.ErrorState
import io.github.numynox.nexus.core.ui.components.NexusCard
import io.github.numynox.nexus.core.ui.components.PlaceholderList
import io.github.numynox.nexus.core.ui.theme.tabular
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

private enum class Picking { From, To }

@Composable
fun SearchScreen(app: AppViewModel, navigator: Navigator) {
    val viewModel = containerViewModel { SearchViewModel(it) }
    val application = LocalContext.current.applicationContext as android.app.Application
    val intake = containerViewModel { IntakeViewModel(it, application) }
    val intakeState by intake.state.collectAsStateWithLifecycle()
    var intakeOpen by remember { mutableStateOf(false) }
    // A share from DB Navigator or a PDF from another app (MainActivity → AppViewModel).
    val shared by app.share.collectAsStateWithLifecycle()
    LaunchedEffect(shared) {
        shared?.let {
            app.share.value = null
            intakeOpen = true
            intake.receive(it)
        }
    }
    val form by viewModel.form.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf<Picking?>(null) }
    var saving by remember { mutableStateOf<Journey?>(null) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search") },
                actions = {
                    IconButton(onClick = { intakeOpen = true }) {
                        Icon(Icons.Rounded.ConfirmationNumber, contentDescription = "Add from a ticket or share")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "form") {
                SearchFormCard(
                    form = form,
                    onPickFrom = { picking = Picking.From },
                    onPickTo = { picking = Picking.To },
                    onSwap = viewModel::swap,
                    onPickDate = { pickDate = true },
                    onPickTime = { pickTime = true },
                    onNow = { viewModel.setTime(null) },
                    onArriveBy = viewModel::setArriveBy,
                    onProducts = viewModel::setProducts,
                    onSearch = viewModel::search,
                )
            }
            item(key = "intake") { IntakeEntry(onClick = { intakeOpen = true }) }
            when (val state = results) {
                null -> Unit
                Loadable.Loading -> item(key = "loading") { PlaceholderList(count = 4, lines = 2) }
                is Loadable.Failed -> item(key = "failed") { ErrorState(state.message, onRetry = viewModel::search) }
                is Loadable.Ready -> {
                    if (state.value.journeys.isEmpty()) {
                        item(key = "none") {
                            EmptyState(Icons.Rounded.Search, "No connections", "Try another time, or all trains instead of regional only.")
                        }
                    } else {
                        if (state.value.earlier != null) {
                            item(key = "earlier") {
                                MoreButton("Earlier", Icons.Rounded.ExpandLess, state.value.loadingMore) { viewModel.more(later = false) }
                            }
                        }
                        items(state.value.journeys, key = { j -> j.legs.joinToString { "${it.tripRef}${it.from.planned}" } }) { journey ->
                            JourneyCard(journey, onClick = { saving = journey }, modifier = Modifier.animateItem())
                        }
                        if (state.value.later != null) {
                            item(key = "later") {
                                MoreButton("Later", Icons.Rounded.ExpandMore, state.value.loadingMore) { viewModel.more(later = true) }
                            }
                        }
                    }
                }
            }
        }
    }

    val ready = intakeState as? IntakeState.Ready
    if (intakeOpen && ready == null) {
        IntakeSheet(
            state = intakeState,
            onReadText = intake::readText,
            onReadPdf = intake::readPdf,
            onDismiss = { intakeOpen = false; if (intakeState !is IntakeState.Working) intake.dismiss() },
        )
    }
    // A sheet of its own for each of a ticket's trips, so its fields start from that trip.
    if (ready != null) key(ready.draft) {
        SaveTripSheet(
            journey = ready.draft.match.journey,
            intake = ready.draft,
            more = ready.queued.size,
            onSave = { input ->
                intake.save(
                    ready, input,
                    onError = { message -> scope.launch { snackbar.showSnackbar(message) } },
                ) { id ->
                    intakeOpen = false
                    app.changed("Trip saved")
                    navigator.savedTrip(id)
                }
            },
            onDismiss = { intakeOpen = false; intake.dismiss() },
        )
    }

    picking?.let { which ->
        StationSheet(
            title = if (which == Picking.From) "From" else "To",
            viewModel = viewModel,
            onPick = { station ->
                if (which == Picking.From) viewModel.setFrom(station) else viewModel.setTo(station)
                viewModel.stationQuery.value = ""
                picking = null
            },
            onDismiss = { viewModel.stationQuery.value = ""; picking = null },
        )
    }

    saving?.let { journey ->
        SaveTripSheet(
            journey = journey,
            onSave = { input ->
                viewModel.save(
                    journey, input,
                    onError = { message -> scope.launch { snackbar.showSnackbar(message) } },
                ) { id ->
                    saving = null
                    app.changed("Trip saved")
                    navigator.savedTrip(id)
                }
            },
            onDismiss = { saving = null },
        )
    }

    if (pickDate) {
        val initial = (form.time ?: LocalDateTime.now()).toLocalDate()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        viewModel.setTime(date.atTime((form.time ?: LocalDateTime.now()).toLocalTime()))
                    }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }

    if (pickTime) {
        val initial = (form.time ?: LocalDateTime.now()).toLocalTime()
        val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = {
                TextButton(onClick = {
                    val date = (form.time ?: LocalDateTime.now()).toLocalDate()
                    viewModel.setTime(date.atTime(LocalTime.of(state.hour, state.minute)))
                    pickTime = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Cancel") } },
            text = { TimePicker(state) },
        )
    }
}

@Composable
private fun SearchFormCard(
    form: SearchForm,
    onPickFrom: () -> Unit,
    onPickTo: () -> Unit,
    onSwap: () -> Unit,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
    onNow: () -> Unit,
    onArriveBy: (Boolean) -> Unit,
    onProducts: (ProductFilter) -> Unit,
    onSearch: () -> Unit,
) {
    NexusCard(containerColor = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Box {
            Column {
                StationField("From", form.from, Icons.Rounded.TripOrigin, onPickFrom)
                HorizontalDivider(Modifier.padding(start = 44.dp, end = 56.dp))
                StationField("To", form.to, Icons.Rounded.Place, onPickTo)
            }
            FilledTonalIconButton(onClick = onSwap, modifier = Modifier.align(Alignment.CenterEnd)) {
                Icon(Icons.Rounded.SwapVert, contentDescription = "Swap stations")
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val time = form.time
            PickerPill(
                Icons.Rounded.CalendarMonth,
                time?.toLocalDate()?.let { if (it == LocalDate.now()) "Today" else DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH).format(it) } ?: "Today",
                onPickDate,
            )
            PickerPill(Icons.Rounded.Schedule, time?.let { "%02d:%02d".format(it.hour, it.minute) } ?: "Now", onPickTime)
            if (time != null) {
                IconButton(onClick = onNow) { Icon(Icons.Rounded.Close, contentDescription = "Leave now") }
            }
        }
        ConnectedChoiceRow(
            options = listOf(false, true),
            selected = form.arriveBy,
            onSelect = onArriveBy,
            label = { if (it) "Arrive by" else "Depart at" },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        ConnectedChoiceRow(
            options = ProductFilter.entries,
            selected = form.products,
            onSelect = onProducts,
            label = { if (it == ProductFilter.All) "All trains" else "Regional only" },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Button(onClick = onSearch, enabled = form.complete, modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(52.dp)) {
            Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(20.dp))
            Text("Search connections", modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleSmall)
        }
    }
}

/** The way in to intake, under the search form. */
@Composable
private fun IntakeEntry(onClick: () -> Unit) {
    NexusCard(onClick = onClick, containerColor = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ConfirmationNumber, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text("Add from a ticket or share", style = MaterialTheme.typography.titleSmall)
                Text(
                    "A DB Navigator share or a DB or Trenitalia ticket PDF, read on this phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StationField(label: String, station: Station?, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(end = 56.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 8.dp).size(22.dp))
        Column(Modifier.padding(start = 6.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                station?.name ?: "Choose a station",
                style = MaterialTheme.typography.titleMedium,
                color = if (station == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PickerPill(icon: ImageVector, text: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun MoreButton(label: String, icon: ImageVector, loading: Boolean, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        if (loading) {
            LoadingIndicator(Modifier.size(40.dp))
        } else {
            TextButton(onClick = onClick) {
                Icon(icon, contentDescription = null)
                Text(label, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

/** One connection: when, how long, how many changes, and how they look today. */
@Composable
fun JourneyCard(journey: Journey, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val first = journey.legs.firstOrNull { !it.isWalk } ?: journey.legs.firstOrNull() ?: return
    val last = journey.legs.lastOrNull { !it.isWalk } ?: journey.legs.last()
    val start = journey.legs.first().from.plannedAt
    val end = journey.legs.last().to.plannedAt
    val changes = changes(journey.legs)
    val worst = worstConfidence(journey.legs)
    val cancelled = journey.legs.any { it.cancelled }
    NexusCard(modifier = modifier.fillMaxWidth(), onClick = onClick, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ShiftedTime(first.from.plannedAt, first.from.expectedAt, MaterialTheme.typography.headlineSmall, block = false)
                    Text("→", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ShiftedTime(last.to.plannedAt, last.to.expectedAt, MaterialTheme.typography.headlineSmall, block = false)
                }
                Text(
                    listOfNotNull(
                        if (start != null && end != null) TripFormat.duration(Duration.between(start, end).toMinutes()) else null,
                        if (changes == 0) "direct" else if (changes == 1) "1 change" else "$changes changes",
                        first.from.shownPlatform?.let { "from Pl. $it" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (cancelled) {
                Text("Cancelled", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.error)
            } else if (worst != null) {
                ConfidenceChip(worst)
            }
        }
        LegChips(journey.legs, Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun StationSheet(title: String, viewModel: SearchViewModel, onPick: (Station) -> Unit, onDismiss: () -> Unit) {
    val query by viewModel.stationQuery.collectAsStateWithLifecycle()
    val stations by viewModel.stations.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 24.dp).navigationBarsPadding().imePadding()) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.stationQuery.value = it },
                label = { Text("Station") },
                placeholder = { Text("Frankfurt Hbf") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(360.dp)) {
                when (val state = stations) {
                    null -> Text(
                        "Type at least two letters.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp),
                    )
                    Loadable.Loading -> LoadingIndicator(Modifier.align(Alignment.TopCenter).padding(top = 24.dp))
                    is Loadable.Failed -> Text(state.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
                    is Loadable.Ready -> LazyColumn {
                        if (state.value.isEmpty()) item { Text("No station by that name.", modifier = Modifier.padding(8.dp)) }
                        items(state.value, key = { it.id }) { station ->
                            ListItem(
                                headlineContent = { Text(station.name) },
                                supportingContent = station.country?.let { { Text(it) } },
                                leadingContent = { Icon(Icons.Rounded.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                modifier = Modifier.clickable { onPick(station) },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(1.dp).height(16.dp))
        }
    }
}

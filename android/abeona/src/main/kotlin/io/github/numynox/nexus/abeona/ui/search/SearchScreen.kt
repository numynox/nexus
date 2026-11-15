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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import io.github.numynox.nexus.abeona.domain.worstConfidence
import io.github.numynox.nexus.abeona.ui.AbeonaType
import io.github.numynox.nexus.abeona.ui.AppViewModel
import io.github.numynox.nexus.abeona.ui.Navigator
import io.github.numynox.nexus.abeona.ui.bad
import io.github.numynox.nexus.abeona.ui.brand
import io.github.numynox.nexus.abeona.ui.hairline
import io.github.numynox.nexus.abeona.ui.muted
import io.github.numynox.nexus.abeona.ui.well
import io.github.numynox.nexus.abeona.ui.common.Banner
import io.github.numynox.nexus.abeona.ui.common.ConfidenceChip
import io.github.numynox.nexus.abeona.ui.common.Fact
import io.github.numynox.nexus.abeona.ui.common.LegChips
import io.github.numynox.nexus.abeona.ui.common.PlatformBox
import io.github.numynox.nexus.abeona.ui.common.PrimaryButton
import io.github.numynox.nexus.abeona.ui.common.Segmented
import io.github.numynox.nexus.abeona.ui.common.ShiftedTime
import io.github.numynox.nexus.abeona.ui.common.StatusChip
import io.github.numynox.nexus.abeona.ui.common.StatusTone
import io.github.numynox.nexus.abeona.ui.common.SurfaceCard
import io.github.numynox.nexus.abeona.ui.common.TripFormat
import io.github.numynox.nexus.abeona.ui.common.WellField
import io.github.numynox.nexus.abeona.ui.common.containerViewModel
import io.github.numynox.nexus.abeona.ui.intake.IntakeSheet
import io.github.numynox.nexus.abeona.ui.intake.IntakeState
import io.github.numynox.nexus.abeona.ui.intake.IntakeViewModel
import io.github.numynox.nexus.core.ui.Loadable
import io.github.numynox.nexus.core.ui.components.ErrorState
import io.github.numynox.nexus.core.ui.components.PlaceholderList
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
            // Intake is the quiet row under the form, so the bar holds the title alone.
            TopAppBar(title = { Text("Plan") })
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
                            Banner(StatusTone.Quiet, Icons.Rounded.Search, "No connections", text = "Try another time, or all trains instead of regional only.")
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
    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StationField("From", form.from, Icons.Rounded.Place, onPickFrom)
                StationField("To", form.to, Icons.Rounded.Flag, onPickTo)
            }
            IconButton(onClick = onSwap) {
                Icon(Icons.Rounded.SwapVert, contentDescription = "Swap stations", tint = brand)
            }
        }
        // When: the day and the time on one line; "now" until a time is chosen.
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val time = form.time
            PickerField(
                Icons.Rounded.CalendarMonth,
                time?.toLocalDate()?.let { if (it == LocalDate.now()) "Today" else DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH).format(it) } ?: "Today",
                "Date",
                onPickDate,
                Modifier.weight(1f),
            )
            PickerField(Icons.Rounded.Schedule, time?.let { "%02d:%02d".format(it.hour, it.minute) } ?: "Now", "Time", onPickTime, Modifier.weight(1f))
            if (time != null) {
                IconButton(onClick = onNow) { Icon(Icons.Rounded.Close, contentDescription = "Leave now", tint = muted) }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Segmented(listOf(false, true), form.arriveBy, onArriveBy, { if (it) "Arrive" else "Depart" }, description = "Depart at or arrive by")
            Segmented(ProductFilter.entries, form.products, onProducts, { if (it == ProductFilter.All) "All" else "Regional" }, description = "All trains or regional only")
        }
        PrimaryButton("Search", onSearch, Modifier.fillMaxWidth().padding(top = 16.dp).height(48.dp), icon = Icons.Rounded.Search, enabled = form.complete)
    }
}

/** The way in to intake, under the search form: a quiet row. */
@Composable
private fun IntakeEntry(onClick: () -> Unit) {
    SurfaceCard(onClick = onClick, padding = PaddingValues(horizontal = 16.dp)) {
        Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.ConfirmationNumber, contentDescription = null, tint = muted, modifier = Modifier.size(20.dp))
            Text("Add from a ticket or share", style = AbeonaType.body, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = muted)
        }
    }
}

/** A station in the well; the placeholder ("From", "To") is the label. */
@Composable
private fun StationField(label: String, station: Station?, icon: ImageVector, onClick: () -> Unit) {
    Surface(onClick = onClick, color = well.container, contentColor = well.content, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(icon, contentDescription = if (station == null) null else label, tint = muted, modifier = Modifier.size(20.dp))
            Text(
                station?.name ?: label,
                style = AbeonaType.body.copy(fontWeight = if (station == null) FontWeight.Normal else FontWeight.SemiBold),
                color = if (station == null) muted else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The date or the time, in the well like the stations. */
@Composable
private fun PickerField(icon: ImageVector, text: String, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, color = well.container, contentColor = well.content, shape = RoundedCornerShape(12.dp), modifier = modifier) {
        Row(Modifier.heightIn(min = 44.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = description, tint = muted, modifier = Modifier.size(18.dp))
            Text(text, style = AbeonaType.body, maxLines = 1)
        }
    }
}

@Composable
private fun MoreButton(label: String, icon: ImageVector, loading: Boolean, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
        if (loading) {
            LoadingIndicator(Modifier.size(40.dp))
        } else {
            TextButton(onClick = onClick) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(label, style = AbeonaType.meta.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

/** Width of the duration and changes columns, so cards compare at a glance. */
private val DurationColumn = 64.dp
private val ChangesColumn = 40.dp

/**
 * One connection in fixed columns: departure → arrival with the worst change
 * at the right; then duration, changes, the trains and the first platform.
 * The same lines as [JourneyCard] without the card, for the save sheet's head.
 */
@Composable
internal fun JourneySummary(journey: Journey, modifier: Modifier = Modifier) {
    val first = journey.legs.firstOrNull { !it.isWalk } ?: journey.legs.firstOrNull() ?: return
    val last = journey.legs.lastOrNull { !it.isWalk } ?: journey.legs.last()
    val start = journey.legs.first().from.plannedAt
    val end = journey.legs.last().to.plannedAt
    val changes = changes(journey.legs)
    val worst = worstConfidence(journey.legs)
    val cancelled = journey.legs.any { it.cancelled }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShiftedTime(first.from.plannedAt, first.from.expectedAt, AbeonaType.title, block = false, cancelled = cancelled)
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "to", tint = muted, modifier = Modifier.size(16.dp))
            ShiftedTime(last.to.plannedAt, last.to.expectedAt, AbeonaType.title, block = false, cancelled = cancelled)
            Spacer(Modifier.weight(1f))
            if (cancelled) {
                StatusChip(StatusTone.Bad, Icons.Rounded.Block, label = "Cancelled")
            } else if (worst != null) {
                ConfidenceChip(worst)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(DurationColumn)) {
                if (start != null && end != null) Fact(Icons.Rounded.Schedule, "Duration", TripFormat.shortDuration(Duration.between(start, end).toMinutes()))
            }
            Box(Modifier.width(ChangesColumn)) { Fact(Icons.Rounded.SwapHoriz, "Changes", "$changes") }
            LegChips(journey.legs, Modifier.weight(1f), wrap = true)
            PlatformBox(first.from.plannedPlatform, first.from.platform, Modifier.padding(start = 8.dp))
        }
    }
}

/** One connection as a card; tapping it opens the save sheet. */
@Composable
fun JourneyCard(journey: Journey, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    SurfaceCard(modifier = modifier, onClick = onClick, padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        JourneySummary(journey)
    }
}

@Composable
private fun StationSheet(title: String, viewModel: SearchViewModel, onPick: (Station) -> Unit, onDismiss: () -> Unit) {
    val query by viewModel.stationQuery.collectAsStateWithLifecycle()
    val stations by viewModel.stations.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding().imePadding()) {
            Text(title, style = AbeonaType.headline, modifier = Modifier.padding(horizontal = 4.dp))
            Spacer(Modifier.height(12.dp))
            WellField(
                value = query,
                onValueChange = { viewModel.stationQuery.value = it },
                placeholder = "Station, e.g. Frankfurt Hbf",
                description = "Station",
                icon = Icons.Rounded.Search,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(360.dp)) {
                when (val state = stations) {
                    null -> Text("Type at least two letters.", style = AbeonaType.meta, color = muted, modifier = Modifier.padding(12.dp))
                    Loadable.Loading -> LoadingIndicator(Modifier.align(Alignment.TopCenter).padding(top = 24.dp))
                    is Loadable.Failed -> Text(state.message, style = AbeonaType.body, color = bad.soft.content, modifier = Modifier.padding(12.dp))
                    is Loadable.Ready -> LazyColumn {
                        if (state.value.isEmpty()) item { Text("No station by that name.", style = AbeonaType.body, modifier = Modifier.padding(12.dp)) }
                        items(state.value, key = { it.id }) { station ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onPick(station) }.heightIn(min = 48.dp).padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(Icons.Rounded.Place, contentDescription = null, tint = muted, modifier = Modifier.size(20.dp))
                                Text(station.name, style = AbeonaType.body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                station.country?.let { Text(it, style = AbeonaType.meta, color = muted) }
                            }
                            HorizontalDivider(color = hairline, modifier = Modifier.padding(start = 36.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

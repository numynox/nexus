package io.github.numynox.nexus.vibilia.ui.prices

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.numynox.nexus.core.ui.NexusFormat
import io.github.numynox.nexus.vibilia.BuildConfig
import io.github.numynox.nexus.core.ui.components.ConnectedChoiceRow
import io.github.numynox.nexus.core.ui.components.EmptyState
import io.github.numynox.nexus.core.ui.components.ErrorState
import io.github.numynox.nexus.core.ui.components.LoadingState
import io.github.numynox.nexus.core.ui.components.NexusCard
import io.github.numynox.nexus.core.ui.components.SectionHeader
import io.github.numynox.nexus.core.ui.components.UpdateBanner
import io.github.numynox.nexus.vibilia.data.FuelLevel
import io.github.numynox.nexus.vibilia.data.LastDrive
import io.github.numynox.nexus.vibilia.data.StationPrice
import io.github.numynox.nexus.vibilia.tracking.latestLocalDrive
import io.github.numynox.nexus.vibilia.tracking.localDrivingHoursAfter
import io.github.numynox.nexus.vibilia.ui.AppViewModel
import io.github.numynox.nexus.vibilia.ui.common.FuelGaugeCard
import io.github.numynox.nexus.vibilia.ui.common.FuelLevelPill
import io.github.numynox.nexus.vibilia.ui.common.LastDriveLine
import io.github.numynox.nexus.vibilia.ui.common.LiveDriveBanner
import io.github.numynox.nexus.vibilia.ui.common.Loadable
import io.github.numynox.nexus.vibilia.ui.common.StationCard
import io.github.numynox.nexus.vibilia.ui.common.containerViewModel
import io.github.numynox.nexus.vibilia.ui.common.rememberNow
import io.github.numynox.nexus.vibilia.ui.common.valueOrNull

private enum class PricesTab(val label: String) { History("History"), Nearby("Nearby") }

@Composable
fun PricesScreen(app: AppViewModel) {
    val settings by app.settings.collectAsStateWithLifecycle()
    val cars by app.cars.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(PricesTab.History) }
    val listState = rememberLazyListState()
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    val hasCars = cars.valueOrNull.orEmpty().isNotEmpty()

    // The fuel level is shown on both tabs: as the gauge card in History, as a
    // pill in the top bar on Nearby — as the web shows its badge on both pages.
    val history = containerViewModel { PriceHistoryViewModel(it) }
    val historyState by history.state.collectAsStateWithLifecycle()
    val car by app.selectedCar.collectAsStateWithLifecycle()
    val version by app.dataVersion.collectAsStateWithLifecycle()
    val tracker by app.tracker.collectAsStateWithLifecycle()
    val now = rememberNow(30_000)
    LaunchedEffect(car?.id, version) { history.loadFuelLevel(car, version) }
    val level = historyState.fuelLevel?.let { level ->
        car?.let { level.plusLocalDriving(tracker.localDrivingHoursAfter(it.id, level.lastRefuelAt, now)) } ?: level
    }
    val lastDrive = car?.let { LastDrive.of(historyState.lastDrive, tracker.latestLocalDrive(it.id), historyState.kmPerHour) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Fuel prices")
                        Text(
                            "${settings.fuelType} · " + if (tab == PricesTab.History) "your stations" else "within ${NexusFormat.number(BuildConfig.SEARCH_RADIUS_KM, 0)} km",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    AnimatedVisibility(visible = tab == PricesTab.Nearby && level != null, enter = fadeIn(), exit = fadeOut()) {
                        level?.let { FuelLevelPill(it, Modifier.padding(end = 12.dp)) }
                    }
                },
            )
        },
        floatingActionButton = {
            if (hasCars) {
                ExtendedFloatingActionButton(
                    onClick = { app.newRefuel() },
                    expanded = fabExpanded,
                    // One label whether the text shows or the button has collapsed to its icon.
                    modifier = Modifier.clearAndSetSemantics {
                        contentDescription = "Log a refuel"
                        role = Role.Button
                    },
                    icon = { Icon(Icons.Rounded.LocalGasStation, contentDescription = null) },
                    text = { Text("Refuel") },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // The fuel type is the one chosen in Settings; the subtitle names it.
            ConnectedChoiceRow(
                options = PricesTab.entries,
                selected = tab,
                onSelect = { tab = it },
                label = { it.label },
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            )
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (slideInHorizontally { it / 6 * direction } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it / 6 * direction } + fadeOut())
                },
                label = "prices tab",
            ) { current ->
                when (current) {
                    PricesTab.History -> HistoryTab(app, listState, level, lastDrive)
                    PricesTab.Nearby -> NearbyTab(settings.fuelType)
                }
            }
        }
    }
}

@Composable
private fun HistoryTab(
    app: AppViewModel,
    listState: androidx.compose.foundation.lazy.LazyListState,
    level: FuelLevel?,
    lastDrive: LastDrive?,
) {
    val viewModel = containerViewModel { PriceHistoryViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by app.settings.collectAsStateWithLifecycle()
    val version by app.dataVersion.collectAsStateWithLifecycle()
    val car by app.selectedCar.collectAsStateWithLifecycle()
    val cars by app.cars.collectAsStateWithLifecycle()
    val tracker by app.tracker.collectAsStateWithLifecycle()
    val update by app.update.collectAsStateWithLifecycle()
    val now = rememberNow(30_000)

    LaunchedEffect(settings.fuelType, settings.priceHistoryDays, version) {
        viewModel.load(settings.fuelType, settings.priceHistoryDays, version)
    }
    val openDrive = tracker.open
    val drivingCar = cars.valueOrNull.orEmpty().firstOrNull { it.id == openDrive?.carId }
    val refreshing = state.stations is Loadable.Loading && state.history is Loadable.Loading

    PullToRefreshBox(
        isRefreshing = false,
        onRefresh = { viewModel.load(settings.fuelType, settings.priceHistoryDays, version, force = true) },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "update") { UpdateBanner(update) }
            item(key = "drive") {
                LiveDriveBanner(
                    visible = openDrive != null && drivingCar != null && settings.trackDrives,
                    carName = drivingCar?.name.orEmpty(),
                    startedAt = openDrive?.startedAt,
                )
            }
            if (level != null && car != null) {
                item(key = "gauge") { FuelGaugeCard(level, car!!.name, Modifier.animateItem()) }
            }
            if (lastDrive != null) {
                item(key = "last-drive") { LastDriveLine(lastDrive, Modifier.animateItem()) }
            }
            item(key = "chart") {
                NexusCard(Modifier.animateItem()) {
                    when (val history = state.history) {
                        Loadable.Loading -> LoadingState(Modifier.fillMaxWidth().height(260.dp))
                        is Loadable.Failed -> ErrorState(history.message, onRetry = {
                            viewModel.load(settings.fuelType, settings.priceHistoryDays, version, force = true)
                        })
                        is Loadable.Ready -> {
                            PriceHistoryChart(history.value)
                            PriceHistoryLegend(settings.fuelType)
                        }
                    }
                }
            }
            item(key = "stations-header") { SectionHeader("My stations", supporting = "Cheapest first, after discounts") }
            when (val stations = state.stations) {
                Loadable.Loading -> if (!refreshing) item(key = "stations-loading") { LoadingState() }
                is Loadable.Failed -> item(key = "stations-error") {
                    ErrorState(stations.message, onRetry = {
                        viewModel.load(settings.fuelType, settings.priceHistoryDays, version, force = true)
                    })
                }
                is Loadable.Ready -> {
                    val sorted = stations.value.sortedWith(stationOrder)
                    val lowest = sorted.firstNotNullOfOrNull { it.effectivePrice }
                    if (sorted.isEmpty()) {
                        item(key = "stations-empty") {
                            EmptyState(Icons.Rounded.Storefront, "No stations", "No fuel stations are tracked yet.")
                        }
                    }
                    items(sorted, key = { it.id }) { station ->
                        StationCard(station, lowest, Modifier.animateItem())
                    }
                }
            }
        }
        if (refreshing) LoadingState(Modifier.fillMaxSize())
    }
}

private val stationOrder = compareBy<StationPrice>({ it.effectivePrice == null }, { it.effectivePrice }, { it.displayName.lowercase() })

@Composable
private fun NearbyTab(fuelType: String) {
    val viewModel = containerViewModel { NearbyViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.onPermissionResult()
        viewModel.refresh(fuelType, force = true)
    }

    LaunchedEffect(fuelType, state.permission) {
        if (state.permission) viewModel.refresh(fuelType)
    }

    if (!state.permission) {
        EmptyState(
            icon = Icons.Rounded.LocationOff,
            title = "Where are you?",
            message = "Nearby prices need your location. It is sent only to look up stations around you.",
            action = {
                Button(onClick = {
                    permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }) { Text("Allow location") }
            },
        )
        return
    }

    val stations = state.stations.valueOrNull.orEmpty().sortedWith(stationOrder)
    val lowest = stations.firstNotNullOfOrNull { it.effectivePrice }
    val listState = rememberLazyListState()

    LaunchedEffect(state.selectedId) {
        val index = stations.indexOfFirst { it.id == state.selectedId }
        if (index >= 0) listState.animateScrollToItem(index)
    }

    Column(Modifier.fillMaxSize().padding(top = 12.dp)) {
        Box(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(300.dp)
                .clip(MaterialTheme.shapes.extraLarge),
        ) {
            StationsMap(
                stations = stations,
                userLocation = state.location,
                selectedId = state.selectedId,
                lowestPrice = lowest,
                onSelect = viewModel::select,
                modifier = Modifier.fillMaxSize(),
            )
            if (state.locating || state.stations is Loadable.Loading) {
                LoadingIndicator(Modifier.align(Alignment.Center))
            }
            SmallFloatingActionButton(
                onClick = { viewModel.refresh(fuelType, force = true) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
            ) {
                Icon(if (state.location == null) Icons.Rounded.MyLocation else Icons.Rounded.Refresh, contentDescription = "Refresh")
            }
        }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
        }
        (state.stations as? Loadable.Failed)?.let {
            Text(it.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (stations.isEmpty() && state.stations is Loadable.Ready && state.location != null) {
                item { Text("No stations found nearby.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(stations, key = { it.id }) { station ->
                StationCard(
                    station = station,
                    lowestPrice = lowest,
                    selected = station.id == state.selectedId,
                    expandable = false,
                    onClick = { viewModel.select(station.id) },
                    modifier = Modifier.animateItem(),
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

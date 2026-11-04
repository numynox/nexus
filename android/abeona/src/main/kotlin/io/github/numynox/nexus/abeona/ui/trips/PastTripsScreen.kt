package io.github.numynox.nexus.abeona.ui.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.numynox.nexus.abeona.AppContainer
import io.github.numynox.nexus.abeona.data.Trip
import io.github.numynox.nexus.abeona.ui.AppViewModel
import io.github.numynox.nexus.abeona.ui.Navigator
import io.github.numynox.nexus.abeona.ui.common.containerViewModel
import io.github.numynox.nexus.core.data.describe
import io.github.numynox.nexus.core.ui.components.EmptyState
import io.github.numynox.nexus.core.ui.components.ErrorState
import io.github.numynox.nexus.core.ui.components.LoadingState
import io.github.numynox.nexus.core.ui.components.SectionHeader
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the past trips list holds so far; [more] while older ones may be left. */
data class PastTrips(
    val trips: List<Trip> = emptyList(),
    val loading: Boolean = true,
    val more: Boolean = true,
    val error: String? = null,
)

class PastTripsViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(PastTrips())
    val state: StateFlow<PastTrips> = _state.asStateFlow()

    /** The newest first again, after anything changed. */
    fun reload() {
        _state.value = PastTrips()
        loadMore()
    }

    /** The next [PAGE] trips, older than the oldest shown. */
    fun loadMore() {
        val current = _state.value
        if (current.trips.isNotEmpty() && (!current.more || current.loading)) return
        _state.value = current.copy(loading = true, error = null)
        viewModelScope.launch {
            val before = current.trips.lastOrNull()?.departureAt ?: Instant.now()
            _state.value = runCatching { container.repository.pastTrips(before, PAGE) }
                .fold(
                    onSuccess = { page -> current.copy(trips = current.trips + page, loading = false, more = page.size == PAGE, error = null) },
                    onFailure = { current.copy(loading = false, error = it.describe()) },
                )
        }
    }

    companion object {
        const val PAGE = 30
    }
}

private val MonthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

/**
 * Every trip that has arrived, newest first, by month, each opening its trip
 * page (the ticket, compensation, what happened). Reached from the end of
 * the trips list. The web's /past page is the same.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PastTripsScreen(app: AppViewModel, navigator: Navigator) {
    val viewModel = containerViewModel { PastTripsViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val version by app.dataVersion.collectAsStateWithLifecycle()
    LaunchedEffect(version) { viewModel.reload() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Past trips") },
                navigationIcon = { IconButton(onClick = navigator.back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        val modifier = Modifier.padding(top = padding.calculateTopPadding())
        when {
            state.trips.isEmpty() && state.loading -> LoadingState(modifier.fillMaxSize())
            state.trips.isEmpty() && state.error != null -> ErrorState(state.error!!, onRetry = viewModel::reload, modifier = modifier)
            state.trips.isEmpty() -> EmptyState(
                icon = Icons.Rounded.History,
                title = "No past trips yet",
                message = "Once a saved trip has arrived, it moves here, with its ticket and what it is owed.",
                modifier = modifier,
            )
            else -> {
                val zone = ZoneId.systemDefault()
                val months = state.trips.groupBy { MonthFormat.format(it.departureAt.atZone(zone)) }
                LazyColumn(
                    modifier = modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    months.forEach { (month, trips) ->
                        item(key = "m-$month") { SectionHeader(month, supporting = "${trips.size} ${if (trips.size == 1) "trip" else "trips"}") }
                        items(trips, key = { it.id }) { trip -> TripRow(trip, onClick = { navigator.trip(trip.id) }) }
                    }
                    if (state.more) {
                        item(key = "more") {
                            OutlinedButton(onClick = viewModel::loadMore, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) {
                                Text(if (state.loading) "Loading…" else "Older trips")
                            }
                        }
                    }
                    state.error?.let { item(key = "error") { Text(it) } }
                }
            }
        }
    }
}

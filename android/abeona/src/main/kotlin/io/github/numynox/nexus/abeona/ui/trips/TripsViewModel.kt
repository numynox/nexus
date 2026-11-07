package io.github.numynox.nexus.abeona.ui.trips

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.numynox.nexus.abeona.AppContainer
import io.github.numynox.nexus.abeona.data.BahnCardDeadline
import io.github.numynox.nexus.abeona.data.Statistics
import io.github.numynox.nexus.abeona.data.Trip
import io.github.numynox.nexus.abeona.live.Background
import io.github.numynox.nexus.abeona.widget.NextTripWidget
import io.github.numynox.nexus.core.ui.Loadable
import io.github.numynox.nexus.core.ui.loadable
import io.github.numynox.nexus.core.ui.valueOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TripsState(
    val trips: List<Trip>,
    /** Set when the list came from the offline pack. */
    val offlineSince: Instant?,
)

class TripsViewModel(private val container: AppContainer, private val application: Application) : ViewModel() {
    private val _trips = MutableStateFlow<Loadable<TripsState>>(Loadable.Loading)
    val trips: StateFlow<Loadable<TripsState>> = _trips.asStateFlow()

    /** This year so far, for the history card; null while loading or when it cannot load. */
    private val _history = MutableStateFlow<Statistics?>(null)
    val history: StateFlow<Statistics?> = _history.asStateFlow()

    /** BahnCard subscriptions to cancel soon; empty while loading, offline, or when there are none. */
    private val _deadlines = MutableStateFlow<List<BahnCardDeadline>>(emptyList())
    val deadlines: StateFlow<List<BahnCardDeadline>> = _deadlines.asStateFlow()

    fun load(): Job = viewModelScope.launch {
        if (_trips.value !is Loadable.Ready) _trips.value = Loadable.Loading
        val loaded = loadable { container.trips.upcoming() }
        _trips.value = when (loaded) {
            is Loadable.Ready -> Loadable.Ready(TripsState(loaded.value.value, loaded.value.offlineSince))
            is Loadable.Failed -> loaded
            Loadable.Loading -> Loadable.Loading
        }
        val state = _trips.value.valueOrNull ?: return@launch
        if (state.offlineSince == null) {
            launch { _history.value = runCatching { container.repository.statistics(LocalDate.now().year) }.getOrNull() }
            launch { _deadlines.value = runCatching { container.repository.bahnCardDeadlines() }.getOrElse { _deadlines.value } }
            refreshNextIfSoon(state.trips)
            Background.scheduleAutostart(application, state.trips, foreground = true)
        }
        NextTripWidget.refresh(application)
    }

    /** The hero shows live values: the next trip, when it runs within the next twelve hours. */
    private suspend fun refreshNextIfSoon(trips: List<Trip>) {
        val now = Instant.now()
        val next = trips.firstOrNull { it.expectedArrival.isAfter(now) } ?: return
        if (next.departureAt.isAfter(now.plus(12, ChronoUnit.HOURS))) return
        val zone = ZoneId.systemDefault()
        if (next.departureAt.atZone(zone).toLocalDate() != LocalDate.now(zone) && next.departureAt.isAfter(now)) return
        val fresh = runCatching { container.trips.refresh(next) }.getOrNull() ?: return
        val current = _trips.value.valueOrNull ?: return
        _trips.value = Loadable.Ready(current.copy(trips = current.trips.map { if (it.id == fresh.id) fresh else it }))
    }
}

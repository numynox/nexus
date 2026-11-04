package io.github.numynox.nexus.abeona.ui.trip

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.numynox.nexus.abeona.AppContainer
import io.github.numynox.nexus.abeona.data.AlternativesResponse
import io.github.numynox.nexus.abeona.data.Traveller
import io.github.numynox.nexus.abeona.data.Trip
import io.github.numynox.nexus.abeona.data.TripWarning
import io.github.numynox.nexus.abeona.domain.AlternativesOrigin
import io.github.numynox.nexus.abeona.domain.TravelDay
import io.github.numynox.nexus.abeona.domain.alternativesOrigin
import io.github.numynox.nexus.abeona.domain.alternativesReason
import io.github.numynox.nexus.abeona.domain.travelDay
import io.github.numynox.nexus.abeona.live.LiveSignals
import io.github.numynox.nexus.abeona.ui.common.PickedFile
import io.github.numynox.nexus.abeona.ui.common.isLiveDay
import io.github.numynox.nexus.abeona.widget.NextTripWidget
import io.github.numynox.nexus.core.data.describe
import io.github.numynox.nexus.core.ui.Loadable
import io.github.numynox.nexus.core.ui.valueOrNull
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TripDetail(
    val trip: Trip,
    /** Set when the trip came from the offline pack. */
    val offlineSince: Instant? = null,
    /** The last live refresh that did not get through, to say so quietly. */
    val refreshError: String? = null,
)

/** An alternatives search for the rest of the trip: from where, and what came back. */
data class AlternativesState(
    val origin: AlternativesOrigin,
    val loading: Boolean = true,
    val result: AlternativesResponse? = null,
    val error: String? = null,
)

/** Who travels on the trip, everyone who could, and what is wrong with their BahnCards. */
data class TripPeople(
    val travellers: List<Traveller>,
    val onTrip: Set<String>,
    val warnings: List<TripWarning>,
)

class TripViewModel(
    private val container: AppContainer,
    private val application: Application,
    private val tripId: String,
) : ViewModel() {
    private val store = container.trips

    private val _detail = MutableStateFlow<Loadable<TripDetail>>(Loadable.Loading)
    val detail: StateFlow<Loadable<TripDetail>> = _detail.asStateFlow()

    private val _people = MutableStateFlow<TripPeople?>(null)
    /** Null while loading, offline, or when it could not load: the section then stays away. */
    val people: StateFlow<TripPeople?> = _people.asStateFlow()

    private val _alternatives = MutableStateFlow<AlternativesState?>(null)
    /** Null until a search ran, by itself (something is wrong) or on demand. */
    val alternatives: StateFlow<AlternativesState?> = _alternatives.asStateFlow()

    private var auto: Job? = null
    private var probe: Job? = null
    private var searching: Job? = null
    /** The refresh the last automatic search was for: one search per refresh. */
    private var searchedFor: String? = null

    fun load(): Job = viewModelScope.launch {
        if (_detail.value !is Loadable.Ready) _detail.value = Loadable.Loading
        try {
            val fetched = store.trip(tripId)
            val trip = fetched.value
            if (trip == null) {
                _detail.value = Loadable.Failed("This trip no longer exists.")
                return@launch
            }
            _detail.value = Loadable.Ready(TripDetail(trip, fetched.offlineSince))
            if (fetched.offlineSince == null) launch { loadPeople() }
            if (fetched.offlineSince == null && !isOver(trip)) refreshNow()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            _detail.value = Loadable.Failed(e.describe())
        }
    }

    private suspend fun loadPeople() {
        val repository = container.repository
        _people.value = runCatching {
            coroutineScope {
                val travellers = async { repository.travellers() }
                val onTrip = async { repository.tripTravellerIds(tripId) }
                val warnings = async { repository.tripWarnings(tripId) }
                TripPeople(travellers.await(), onTrip.await().toSet(), warnings.await())
            }
        }.getOrElse { _people.value }
    }

    /** Puts a traveller on the trip or takes them off; the warnings follow. */
    fun toggleTraveller(id: String, onError: (String) -> Unit) {
        val current = _people.value ?: return
        val next = if (id in current.onTrip) current.onTrip - id else current.onTrip + id
        _people.value = current.copy(onTrip = next)
        viewModelScope.launch {
            runCatching { container.repository.setTripTravellers(tripId, next.toList()) }
                .onSuccess { loadPeople() }
                .onFailure { _people.value = current; onError(it.describe()) }
        }
    }

    /** The BahnCard discount of the fare (25, 50 or null); the warnings follow. */
    fun setBahncardDiscount(discount: Int?, onError: (String) -> Unit) {
        val detail = _detail.value.valueOrNull ?: return
        if (detail.trip.bahncardDiscount == discount) return
        _detail.value = Loadable.Ready(detail.copy(trip = detail.trip.copy(bahncardDiscount = discount)))
        viewModelScope.launch {
            runCatching { container.repository.setBahncardDiscount(tripId, discount) }
                .onSuccess { loadPeople() }
                .onFailure {
                    _detail.value.valueOrNull?.let { latest ->
                        _detail.value = Loadable.Ready(latest.copy(trip = latest.trip.copy(bahncardDiscount = detail.trip.bahncardDiscount)))
                    }
                    onError(it.describe())
                }
        }
    }

    /** Live values from the provider; the screen keeps showing what it has meanwhile. */
    fun refresh(): Job = viewModelScope.launch { refreshNow() }

    private suspend fun refreshNow() {
        val current = _detail.value.valueOrNull ?: return
        try {
            val fresh = store.refresh(current.trip)
            _detail.value = Loadable.Ready(TripDetail(fresh))
            NextTripWidget.refresh(application)
            searchIfNeeded(fresh)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            val latest = _detail.value.valueOrNull ?: return
            _detail.value = Loadable.Ready(latest.copy(refreshError = e.describe()))
        }
    }

    /**
     * While the screen is visible and the trip is today, refresh every two
     * minutes, like the departure boards; stops when the screen goes away.
     */
    fun setVisible(visible: Boolean) {
        auto?.cancel()
        probe?.cancel()
        if (!visible) return
        // The train's Wi-Fi, every half minute while the trip is live: cheap,
        // and nothing at all off a train's network.
        probe = viewModelScope.launch {
            while (isActive) {
                val trip = _detail.value.valueOrNull?.trip
                if (trip != null && isLiveDay(trip, Instant.now())) {
                    LiveSignals.setOnboard(runCatching { container.onboard.status() }.getOrNull())
                }
                delay(PROBE_EVERY)
            }
        }
        auto = viewModelScope.launch {
            while (isActive) {
                delay(REFRESH_EVERY)
                val trip = _detail.value.valueOrNull?.trip ?: continue
                if (isToday(trip) && !isOver(trip)) refreshNow()
            }
        }
    }

    /** The travel day of [trip] now, with this phone's settings and signals; null outside its live window. */
    private suspend fun dayOf(trip: Trip, now: Instant = Instant.now()): TravelDay? {
        if (!isLiveDay(trip, now)) return null
        return travelDay(trip.legs, now, container.preferences.current().stationMinutes, LiveSignals.forTrip(trip, now))
    }

    /** By itself only when a change is at risk, missed, or a train cancelled — and once per refresh. */
    private suspend fun searchIfNeeded(trip: Trip) {
        val day = dayOf(trip) ?: return
        if (alternativesReason(trip.legs, day) == null || trip.refreshedAt == searchedFor) return
        searchedFor = trip.refreshedAt
        findAlternatives()
    }

    /** Connections from where the traveller is to the destination, marked against the ticket. */
    fun findAlternatives(): Job = viewModelScope.launch {
        val trip = _detail.value.valueOrNull?.trip ?: return@launch
        val now = Instant.now()
        val day = dayOf(trip, now) ?: return@launch
        val origin = alternativesOrigin(trip.legs, day, now) ?: return@launch
        searching?.cancel()
        _alternatives.value = AlternativesState(origin, loading = true, result = _alternatives.value?.result)
        searching = launch {
            _alternatives.value = try {
                AlternativesState(origin, loading = false, result = container.repository.alternatives(trip, origin))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AlternativesState(origin, loading = false, result = _alternatives.value?.result, error = e.describe())
            }
        }
    }

    fun delete(onError: (String) -> Unit, onDone: () -> Unit) {
        val trip = _detail.value.valueOrNull?.trip ?: return
        viewModelScope.launch {
            runCatching { store.delete(trip) }
                .onSuccess {
                    NextTripWidget.refresh(application)
                    onDone()
                }
                .onFailure { onError(it.describe()) }
        }
    }

    /** Reads the picked document and puts it in the wallet: Storage, the trip row, and this phone. */
    fun attachTicket(uri: Uri, onError: (String) -> Unit, onDone: () -> Unit) {
        val trip = _detail.value.valueOrNull?.trip ?: return
        viewModelScope.launch {
            runCatching {
                val file = PickedFile.read(application, uri, "ticket.pdf")
                store.attachTicket(trip, file.name, file.mimeType, file.bytes)
            }.onSuccess { updated ->
                val latest = _detail.value.valueOrNull ?: return@onSuccess
                _detail.value = Loadable.Ready(latest.copy(trip = updated))
                onDone()
            }.onFailure { onError(it.describe()) }
        }
    }

    private fun isOver(trip: Trip) = trip.expectedArrival.plus(Duration.ofHours(1)).isBefore(Instant.now())

    private fun isToday(trip: Trip): Boolean {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        return trip.departureAt.atZone(zone).toLocalDate() <= today && trip.arrivalAt.atZone(zone).toLocalDate() >= today
    }

    private companion object {
        const val REFRESH_EVERY = 120_000L
        const val PROBE_EVERY = 30_000L
    }
}

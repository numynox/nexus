package io.github.numynox.nexus.abeona.ui.trip

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.numynox.nexus.abeona.AppContainer
import io.github.numynox.nexus.abeona.data.AlternativesResponse
import io.github.numynox.nexus.abeona.data.Claim
import io.github.numynox.nexus.abeona.data.Compensation
import io.github.numynox.nexus.abeona.data.Journey
import io.github.numynox.nexus.abeona.data.Leg
import io.github.numynox.nexus.abeona.data.ShareLink
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

/** After the ride: what the delay is worth, and the household's claim for it. */
data class AfterRide(
    val compensation: Compensation,
    val claim: Claim?,
    val busy: Boolean = false,
)

/** The connections from a missed ride's stop, to pick the one actually taken. */
data class ReplacementSearch(
    val missedIdx: Int,
    val loading: Boolean = true,
    val options: List<Journey>? = null,
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

    private val _afterRide = MutableStateFlow<AfterRide?>(null)
    /** Null before arrival, offline, or while loading. */
    val afterRide: StateFlow<AfterRide?> = _afterRide.asStateFlow()

    private val _replacements = MutableStateFlow<ReplacementSearch?>(null)
    val replacements: StateFlow<ReplacementSearch?> = _replacements.asStateFlow()

    private val _shareLinks = MutableStateFlow<List<ShareLink>?>(null)
    /** The trip's live share links; null until loaded, or offline. */
    val shareLinks: StateFlow<List<ShareLink>?> = _shareLinks.asStateFlow()

    private val _shareBusy = MutableStateFlow<String?>(null)
    /** The kind of link being made or revoked right now. */
    val shareBusy: StateFlow<String?> = _shareBusy.asStateFlow()

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
            if (fetched.offlineSince == null) launch { loadShareLinks() }
            if (fetched.offlineSince == null && hasArrived(trip)) launch { loadAfterRide() }
            // Final trips (frozen by the job, or imported as past trips) never change again.
            if (fetched.offlineSince == null && !isOver(trip) && trip.finalizedAt == null) refreshNow()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            _detail.value = Loadable.Failed(e.describe())
        }
    }

    private suspend fun loadShareLinks() {
        _shareLinks.value = runCatching { container.repository.shareLinks(tripId) }.getOrElse { _shareLinks.value }
    }

    /** Makes a link of [kind] ("status" or "ics") and hands it to [onCreated], to share. */
    fun createShareLink(kind: String, onError: (String) -> Unit, onCreated: (ShareLink) -> Unit) {
        if (_shareBusy.value != null) return
        _shareBusy.value = kind
        viewModelScope.launch {
            runCatching { container.repository.createShareLink(tripId, kind) }
                .onSuccess { link ->
                    _shareLinks.value = (_shareLinks.value.orEmpty().filter { it.kind != kind }) + link
                    onCreated(link)
                }
                .onFailure { onError(it.describe()) }
            _shareBusy.value = null
        }
    }

    fun revokeShareLink(link: ShareLink, onError: (String) -> Unit) {
        if (_shareBusy.value != null) return
        _shareBusy.value = link.kind
        viewModelScope.launch {
            runCatching { container.repository.revokeShareLink(link.token) }
                .onSuccess { _shareLinks.value = _shareLinks.value.orEmpty().filter { it.token != link.token } }
                .onFailure { onError(it.describe()) }
            _shareBusy.value = null
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

    private suspend fun loadAfterRide() {
        val repository = container.repository
        _afterRide.value = runCatching {
            coroutineScope {
                val compensation = async { repository.compensation(tripId) }
                val claim = async { repository.claim(tripId) }
                compensation.await()?.let { AfterRide(it, claim.await()) }
            }
        }.getOrElse { _afterRide.value }
    }

    private fun afterRideAction(onError: (String) -> Unit, block: suspend () -> Unit) {
        _afterRide.value = _afterRide.value?.copy(busy = true)
        viewModelScope.launch {
            runCatching { block() }.onFailure { onError(it.describe()) }
            loadAfterRide()
        }
    }

    fun setClaimStatus(status: String, onError: (String) -> Unit) {
        val current = _afterRide.value ?: return
        if ((current.claim?.status ?: "open") == status) return
        afterRideAction(onError) {
            container.repository.setClaimStatus(tripId, status, current.claim?.amountCents ?: current.compensation.amountCents, current.claim)
        }
    }

    fun setPrice(cents: Int, onError: (String) -> Unit) = afterRideAction(onError) {
        container.repository.setTripPrice(tripId, cents)
        updateTrip { it.copy(priceCents = cents) }
    }

    fun setForceMajeure(value: Boolean, onError: (String) -> Unit) = afterRideAction(onError) {
        container.repository.setForceMajeure(tripId, value)
        updateTrip { it.copy(forceMajeure = value) }
    }

    /** Records (or with nulls clears) the missed ride and what was taken instead. */
    fun recordDisruption(missedIdx: Int?, legs: List<Leg>?, onError: (String) -> Unit, onDone: () -> Unit = {}) = afterRideAction(onError) {
        container.repository.recordDisruption(tripId, missedIdx, legs)
        updateTrip { it.copy(missedLegIdx = missedIdx, replacementLegs = legs?.takeIf { l -> l.isNotEmpty() }) }
        _replacements.value = null
        onDone()
    }

    /** Connections from the missed ride's stop at its planned time. */
    fun searchReplacements(missedIdx: Int): Job = viewModelScope.launch {
        val trip = _detail.value.valueOrNull?.trip ?: return@launch
        val missed = trip.legs.getOrNull(missedIdx) ?: return@launch
        _replacements.value = ReplacementSearch(missedIdx)
        _replacements.value = try {
            ReplacementSearch(missedIdx, loading = false, options = container.repository.connectionsAfter(trip, missed).alternatives.map { Journey(it.provider, it.refreshToken, it.legs) })
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            ReplacementSearch(missedIdx, loading = false, error = e.describe())
        }
    }

    fun clearReplacementSearch() {
        _replacements.value = null
    }

    private fun updateTrip(change: (Trip) -> Trip) {
        val latest = _detail.value.valueOrNull ?: return
        _detail.value = Loadable.Ready(latest.copy(trip = change(latest.trip)))
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

    private fun hasArrived(trip: Trip) = trip.expectedArrival.isBefore(Instant.now())

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

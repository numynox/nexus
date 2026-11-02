package io.github.numynox.nexus.abeona.ui.trip

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.numynox.nexus.abeona.AppContainer
import io.github.numynox.nexus.abeona.data.Trip
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

class TripViewModel(
    private val container: AppContainer,
    private val application: Application,
    private val tripId: String,
) : ViewModel() {
    private val store = container.trips

    private val _detail = MutableStateFlow<Loadable<TripDetail>>(Loadable.Loading)
    val detail: StateFlow<Loadable<TripDetail>> = _detail.asStateFlow()

    private var auto: Job? = null

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
            if (fetched.offlineSince == null && !isOver(trip)) refreshNow()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            _detail.value = Loadable.Failed(e.describe())
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
        if (!visible) return
        auto = viewModelScope.launch {
            while (isActive) {
                delay(REFRESH_EVERY)
                val trip = _detail.value.valueOrNull?.trip ?: continue
                if (isToday(trip) && !isOver(trip)) refreshNow()
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
                val resolver = application.contentResolver
                val (name, bytes) = withContext(Dispatchers.IO) {
                    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    } ?: "ticket.pdf"
                    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("The file could not be read.")
                    name to bytes
                }
                require(bytes.size <= MAX_TICKET_BYTES) { "Tickets can be at most 10 MB." }
                val mime = resolver.getType(uri) ?: if (name.endsWith(".pdf", true)) "application/pdf" else "image/jpeg"
                store.attachTicket(trip, name, mime, bytes)
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
        const val MAX_TICKET_BYTES = 10 * 1024 * 1024
    }
}

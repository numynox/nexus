package io.github.numynox.nexus.abeona.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.numynox.nexus.abeona.AppContainer
import io.github.numynox.nexus.abeona.data.Journey
import io.github.numynox.nexus.abeona.data.ProductFilter
import io.github.numynox.nexus.abeona.data.SaveTripInput
import io.github.numynox.nexus.abeona.data.Station
import io.github.numynox.nexus.abeona.data.BahnCard
import io.github.numynox.nexus.abeona.data.Traveller
import io.github.numynox.nexus.core.data.describe
import io.github.numynox.nexus.core.ui.Loadable
import io.github.numynox.nexus.core.ui.loadable
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchForm(
    val from: Station? = null,
    val to: Station? = null,
    /** Local date and time of the departure (or arrival, with [arriveBy]); null is "now". */
    val time: LocalDateTime? = null,
    val arriveBy: Boolean = false,
    val products: ProductFilter = ProductFilter.All,
) {
    val complete: Boolean get() = from != null && to != null && from.id != to.id
}

data class SearchResults(
    val journeys: List<Journey>,
    val earlier: String?,
    val later: String?,
    val loadingMore: Boolean = false,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(private val container: AppContainer) : ViewModel() {
    private val repository = container.repository

    private val _form = MutableStateFlow(SearchForm())
    val form: StateFlow<SearchForm> = _form.asStateFlow()

    private val _results = MutableStateFlow<Loadable<SearchResults>?>(null)
    val results: StateFlow<Loadable<SearchResults>?> = _results.asStateFlow()

    /** What the station picker has typed; answered after a pause in typing. */
    val stationQuery = MutableStateFlow("")

    val stations: StateFlow<Loadable<List<Station>>?> = stationQuery
        .debounce(300)
        .distinctUntilChanged()
        .flatMapLatest { query ->
            flow {
                if (query.trim().length < 2) {
                    emit(null)
                } else {
                    emit(Loadable.Loading)
                    emit(loadable { repository.stations(query) })
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setFrom(station: Station) = _form.update { it.copy(from = station) }
    fun setTo(station: Station) = _form.update { it.copy(to = station) }
    fun swap() = _form.update { it.copy(from = it.to, to = it.from) }
    fun setTime(time: LocalDateTime?) = _form.update { it.copy(time = time) }
    fun setArriveBy(value: Boolean) = _form.update { it.copy(arriveBy = value) }
    fun setProducts(value: ProductFilter) = _form.update { it.copy(products = value) }

    fun search() {
        val form = _form.value
        val from = form.from ?: return
        val to = form.to ?: return
        _results.value = Loadable.Loading
        viewModelScope.launch {
            val time = form.time?.truncatedTo(ChronoUnit.MINUTES)?.atZone(ZoneId.systemDefault())?.toInstant()
            _results.value = loadable {
                val page = repository.journeys(from, to, time, form.arriveBy, form.products)
                SearchResults(page.journeys, page.previousCursor, page.nextCursor)
            }
        }
    }

    /** Earlier or later connections, from the cursor the last page gave. */
    fun more(later: Boolean) {
        val current = (_results.value as? Loadable.Ready)?.value ?: return
        val cursor = (if (later) current.later else current.earlier) ?: return
        val form = _form.value
        val from = form.from ?: return
        val to = form.to ?: return
        _results.value = Loadable.Ready(current.copy(loadingMore = true))
        viewModelScope.launch {
            val page = runCatching { repository.journeys(from, to, null, form.arriveBy, form.products, cursor) }.getOrNull()
            val latest = (_results.value as? Loadable.Ready)?.value ?: return@launch
            _results.value = Loadable.Ready(
                when {
                    page == null -> latest.copy(loadingMore = false)
                    later -> latest.copy(journeys = latest.journeys + page.journeys, later = page.nextCursor, loadingMore = false)
                    else -> latest.copy(journeys = page.journeys + latest.journeys, earlier = page.previousCursor, loadingMore = false)
                },
            )
        }
    }

    /** The household's travellers, for "Who travels" in the save sheet; null until loaded. */
    private val _travellers = MutableStateFlow<List<Traveller>?>(null)
    val travellers: StateFlow<List<Traveller>?> = _travellers.asStateFlow()
    val userId: String? get() = container.core.auth.currentUserId

    /** The household's BahnCards, so the save sheet can tell who holds which; empty if they cannot be loaded. */
    private val _cards = MutableStateFlow<List<BahnCard>>(emptyList())
    val cards: StateFlow<List<BahnCard>> = _cards.asStateFlow()

    fun loadTravellers() = viewModelScope.launch {
        _cards.value = runCatching { repository.bahnCards() }.getOrDefault(emptyList())
        _travellers.value = runCatching { repository.travellers() }.getOrNull()
    }

    fun save(journey: Journey, input: SaveTripInput, onError: (String) -> Unit, onSaved: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.saveTrip(journey, input) }
                .onSuccess(onSaved)
                .onFailure { onError(it.describe()) }
        }
    }
}

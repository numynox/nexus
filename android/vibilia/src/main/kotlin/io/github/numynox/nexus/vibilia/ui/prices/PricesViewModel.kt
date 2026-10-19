package io.github.numynox.nexus.vibilia.ui.prices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.numynox.nexus.core.data.describe
import io.github.numynox.nexus.vibilia.AppContainer
import io.github.numynox.nexus.vibilia.BuildConfig
import io.github.numynox.nexus.vibilia.data.Car
import io.github.numynox.nexus.vibilia.data.DrivePeriod
import io.github.numynox.nexus.vibilia.data.FuelLevel
import io.github.numynox.nexus.vibilia.data.LastRefuelPrice
import io.github.numynox.nexus.vibilia.data.PriceBucket
import io.github.numynox.nexus.vibilia.data.StationPrice
import io.github.numynox.nexus.vibilia.location.LatLng
import io.github.numynox.nexus.core.ui.Loadable
import io.github.numynox.nexus.core.ui.loadable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PriceHistory(
    val buckets: List<PriceBucket>,
    val days: Int,
    val lastRefuel: LastRefuelPrice?,
)

data class HistoryState(
    val stations: Loadable<List<StationPrice>> = Loadable.Loading,
    val history: Loadable<PriceHistory> = Loadable.Loading,
    val fuelLevel: FuelLevel? = null,
    /** The last merged drive on the server, and the model's km per hour to estimate its distance. */
    val lastDrive: DrivePeriod? = null,
    val kmPerHour: Double? = null,
)

/** Our stations' prices and the price history chart — the web app's Fuel Price page. */
class PriceHistoryViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(HistoryState())
    val state: StateFlow<HistoryState> = _state.asStateFlow()

    private var key: Triple<String, Int, Int>? = null
    private var job: Job? = null

    fun load(fuelType: String, days: Int, version: Int, force: Boolean = false) {
        val next = Triple(fuelType, days, version)
        if (!force && next == key) return
        key = next
        job?.cancel()
        job = viewModelScope.launch {
            if (force) _state.update { it.copy(stations = Loadable.Loading, history = Loadable.Loading) }
            val stations = async { loadable { container.prices.currentPrices(fuelType) } }
            val history = async {
                loadable {
                    val zone = ZoneId.systemDefault()
                    val since = LocalDate.now(zone).minusDays(days.toLong()).atStartOfDay(zone).toInstant()
                    PriceHistory(
                        buckets = container.prices.priceHistory(fuelType, since, BuildConfig.PRICE_BUCKET_MINUTES, zone.id),
                        days = days,
                        lastRefuel = runCatching { container.prices.lastRefuelPrice() }.getOrNull(),
                    )
                }
            }
            _state.update { it.copy(stations = stations.await(), history = history.await()) }
        }
    }

    private var levelKey: Pair<String, Int>? = null

    fun loadFuelLevel(car: Car?, version: Int) {
        if (car == null) {
            _state.update { it.copy(fuelLevel = null, lastDrive = null, kmPerHour = null) }
            return
        }
        val next = car.id to version
        if (next == levelKey) return
        levelKey = next
        viewModelScope.launch {
            val level = async {
                runCatching { container.statistics.fuelLevel(car.id, car) { container.logbook.refuels(car.id) } }.getOrNull()
            }
            val model = container.statistics.driveModel(car.id)
            // The period ending at the car's last reported moment is its last drive.
            val lastDrive = model?.lastDriveAt?.let { last ->
                container.statistics.drivePeriods(car.id, last.minusSeconds(1), Instant.now()).lastOrNull()
            }
            _state.update { it.copy(fuelLevel = level.await(), lastDrive = lastDrive, kmPerHour = model?.kmPerHour) }
        }
    }
}

data class NearbyState(
    val permission: Boolean = false,
    val locating: Boolean = false,
    val location: LatLng? = null,
    val stations: Loadable<List<StationPrice>> = Loadable.Ready(emptyList()),
    val error: String? = null,
    val selectedId: String? = null,
)

/** Live prices around the phone, from Tankerkoenig through the Edge Function. */
class NearbyViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(NearbyState(permission = container.locations.hasPermission()))
    val state: StateFlow<NearbyState> = _state.asStateFlow()

    val radiusKm: Double = BuildConfig.SEARCH_RADIUS_KM
    private var lastFuelType: String? = null

    fun onPermissionResult() {
        _state.update { it.copy(permission = container.locations.hasPermission()) }
    }

    fun refresh(fuelType: String, force: Boolean = false) {
        if (!force && fuelType == lastFuelType && _state.value.stations is Loadable.Ready && _state.value.location != null) return
        lastFuelType = fuelType
        viewModelScope.launch {
            if (!container.locations.hasPermission()) {
                _state.update { it.copy(permission = false) }
                return@launch
            }
            _state.update { it.copy(permission = true, locating = true, error = null) }
            val location = container.locations.current()
            if (location == null) {
                _state.update { it.copy(locating = false, error = "Your location is not available right now.") }
                return@launch
            }
            _state.update { it.copy(locating = false, location = location, stations = Loadable.Loading) }
            val stations = runCatching {
                container.prices.nearby(location.lat, location.lng, fuelType, radiusKm, force)
            }
            _state.update { state ->
                state.copy(
                    stations = stations.fold({ Loadable.Ready(it) }, { Loadable.Failed(it.describe()) }),
                    selectedId = state.selectedId?.takeIf { id -> stations.getOrNull().orEmpty().any { it.id == id } },
                )
            }
        }
    }

    fun select(id: String?) {
        _state.update { it.copy(selectedId = if (it.selectedId == id) null else id) }
    }
}

package io.github.numynox.nexus.vibilia.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.numynox.nexus.vibilia.AppContainer
import io.github.numynox.nexus.vibilia.data.CarStatistics
import io.github.numynox.nexus.vibilia.data.DailyDistance
import io.github.numynox.nexus.vibilia.data.DistanceMonth
import io.github.numynox.nexus.vibilia.data.DriveModel
import io.github.numynox.nexus.vibilia.data.DrivePeriod
import io.github.numynox.nexus.vibilia.data.PlotEvent
import io.github.numynox.nexus.vibilia.data.WeeklyMinimum
import io.github.numynox.nexus.core.ui.Loadable
import io.github.numynox.nexus.core.ui.loadable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The plot range, as the web app's select offers it. */
sealed class StatsRange(val label: String) {
    data object OneYear : StatsRange("12 months")
    data object ThreeYears : StatsRange("3 years")
    data object FiveYears : StatsRange("5 years")
    data object Overall : StatsRange("All")
    data class Year(val year: Int) : StatsRange(year.toString())
}

data class CarOverview(
    val statistics: CarStatistics?,
    val years: List<Int>,
    val model: DriveModel?,
    /** Daily distance over the last 30 days, and the drives behind it. */
    val recentDays: List<DailyDistance>,
    val recentDrives: List<DrivePeriod>,
)

data class Plots(
    val events: List<PlotEvent>,
    val months: List<DistanceMonth>,
    val weeklyMinima: List<WeeklyMinimum>,
)

data class StatisticsState(
    val overview: Loadable<CarOverview> = Loadable.Loading,
    val plots: Loadable<Plots> = Loadable.Loading,
    val range: StatsRange = StatsRange.OneYear,
)

class StatisticsViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(StatisticsState())
    val state: StateFlow<StatisticsState> = _state.asStateFlow()

    private var carId: String? = null
    private var fuelType: String = "E10"
    private var overviewKey: Pair<String, Int>? = null
    private var plotsJob: Job? = null

    fun load(carId: String, version: Int, fuelType: String, force: Boolean = false) {
        val carChanged = this.carId != carId
        val fuelChanged = this.fuelType != fuelType
        this.carId = carId
        this.fuelType = fuelType
        if (force || overviewKey != carId to version) {
            overviewKey = carId to version
            if (carChanged) _state.update { it.copy(overview = Loadable.Loading) }
            viewModelScope.launch { loadOverview(carId) }
            loadPlots()
        } else if (fuelChanged) {
            loadPlots()
        }
    }

    fun setRange(range: StatsRange) {
        if (range == _state.value.range) return
        _state.update { it.copy(range = range) }
        loadPlots()
    }

    private suspend fun loadOverview(carId: String) {
        val overview = loadable {
            val stats = viewModelScope.async { container.statistics.statistics(carId) }
            val bounds = viewModelScope.async { container.statistics.yearBounds(carId) }
            val model = viewModelScope.async { container.statistics.driveModel(carId) }
            val now = Instant.now()
            val monthAgo = LocalDate.now().minusDays(29).atStartOfDay(ZoneId.systemDefault()).toInstant()
            val days = viewModelScope.async { container.statistics.dailyDistance(carId, monthAgo, now) }
            val drives = viewModelScope.async { container.statistics.drivePeriods(carId, now.minusSeconds(14 * 86_400L), now) }
            val b = bounds.await()
            CarOverview(
                statistics = stats.await(),
                years = if (b.minYear != null && b.maxYear != null) (b.maxYear downTo b.minYear).toList() else emptyList(),
                model = model.await(),
                recentDays = days.await(),
                recentDrives = drives.await().sortedByDescending { it.startedAt },
            )
        }
        if (this.carId == carId) {
            _state.update { state ->
                val range = state.range
                val keep = range !is StatsRange.Year || overview is Loadable.Ready && range.year in overview.value.years
                state.copy(overview = overview, range = if (keep) range else StatsRange.OneYear)
            }
        }
    }

    private fun loadPlots() {
        val carId = carId ?: return
        val fuelType = fuelType
        plotsJob?.cancel()
        plotsJob = viewModelScope.launch {
            _state.update { it.copy(plots = Loadable.Loading) }
            val years = (_state.value.overview as? Loadable.Ready)?.value?.years.orEmpty()
            val (since, until) = resolve(_state.value.range, years.lastOrNull())
            val plots = loadable {
                val events = async { container.statistics.plotEvents(carId, since, until) }
                val months = async { container.statistics.distancePerMonth(carId, since, until) }
                val weekly = async { runCatching { container.prices.weeklyMinima(fuelType, since, until) }.getOrDefault(emptyList()) }
                Plots(events.await(), months.await(), weekly.await())
            }
            _state.update { it.copy(plots = plots) }
        }
    }

    private fun resolve(range: StatsRange, firstYear: Int?): Pair<Instant, Instant> {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        fun yearsAgo(n: Long) = now.minusYears(n).toLocalDate().atStartOfDay(zone).toInstant()
        return when (range) {
            StatsRange.OneYear -> yearsAgo(1) to now.toInstant()
            StatsRange.ThreeYears -> yearsAgo(3) to now.toInstant()
            StatsRange.FiveYears -> yearsAgo(5) to now.toInstant()
            StatsRange.Overall -> (firstYear?.let { LocalDate.of(it, 1, 1).atStartOfDay(zone).toInstant() } ?: yearsAgo(50)) to now.toInstant()
            is StatsRange.Year -> {
                val start = LocalDate.of(range.year, 1, 1).atStartOfDay(zone).toInstant()
                val end = if (range.year >= now.year) now.toInstant() else LocalDate.of(range.year + 1, 1, 1).atStartOfDay(zone).toInstant().minusSeconds(1)
                start to end
            }
        }
    }
}

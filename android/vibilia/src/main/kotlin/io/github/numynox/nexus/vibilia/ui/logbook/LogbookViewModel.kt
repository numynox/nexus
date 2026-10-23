package io.github.numynox.nexus.vibilia.ui.logbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.numynox.nexus.vibilia.AppContainer
import io.github.numynox.nexus.vibilia.data.CarExpense
import io.github.numynox.nexus.vibilia.data.DistanceMonth
import io.github.numynox.nexus.vibilia.data.RefuelEvent
import io.github.numynox.nexus.core.ui.Loadable
import io.github.numynox.nexus.core.ui.loadable
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LogEntry {
    val at: Instant
    val key: String

    data class Refuel(val event: RefuelEvent, val kmSincePrevious: Int?, val consumption: Double?) : LogEntry {
        override val at: Instant get() = event.fueledAt
        override val key: String get() = "refuel-${event.id}"
    }

    data class Expense(val expense: CarExpense) : LogEntry {
        override val at: Instant get() = expense.expensedAt
        override val key: String get() = "expense-${expense.id}"
    }
}

/**
 * One month of the diary. [distance] is the month's share of the odometer and
 * its driving time, when the server has it; [comparison] the one line that
 * sets its spend against the month before.
 */
data class LogMonth(
    val month: YearMonth,
    val entries: List<LogEntry>,
    val spent: Double,
    val distance: DistanceMonth? = null,
    /** What went into the tank; [spent] minus this is everything else. */
    val fuel: Double = spent,
    val comparison: String? = null,
) {
    val other: Double get() = spent - fuel
}

data class Logbook(val months: List<LogMonth>, val averageConsumption: Double?)

/** Refuels and expenses of one car, newest first, grouped by month. */
class LogbookViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow<Loadable<Logbook>>(Loadable.Loading)
    val state: StateFlow<Loadable<Logbook>> = _state.asStateFlow()

    /** A pull to refresh is running: the pull indicator shows it, the logbook stays. */
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private var key: Pair<String, Int>? = null

    fun load(carId: String, version: Int, force: Boolean = false) {
        if (!force && key == carId to version) return
        val switchingCar = key?.first != carId
        key = carId to version
        viewModelScope.launch {
            if (switchingCar) _state.value = Loadable.Loading
            if (force) _refreshing.value = true
            val result = loadable {
                val refuels = async { container.logbook.refuels(carId) }
                val expenses = async { container.logbook.expenses(carId) }
                // Extra detail: the logbook still loads without it.
                val distance = async {
                    runCatching { container.statistics.distancePerMonth(carId, Instant.EPOCH, Instant.now()) }.getOrDefault(emptyList())
                }
                build(refuels.await(), expenses.await(), distance.await())
            }
            // A refresh that failed leaves the logbook that was there.
            if (!(force && result is Loadable.Failed && _state.value is Loadable.Ready)) _state.value = result
            _refreshing.value = false
        }
    }

    companion object {
        /**
         * "12 € less than September" — the month's spend against the one
         * before, in whole euros. A month still running says "so far": its
         * total is not final yet.
         */
        fun spendComparison(spent: Double, previousSpent: Double, previous: YearMonth, running: Boolean = false, locale: Locale = Locale.getDefault()): String {
            val name = previous.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)
            val difference = (spent - previousSpent).roundToInt()
            val line = when {
                difference == 0 -> "Same as $name"
                difference < 0 -> "${-difference} € less than $name"
                else -> "$difference € more than $name"
            }
            return if (running) "$line so far" else line
        }

        /**
         * Consumption per refuel from the tank-level correction, the same
         * arithmetic as the web log: fuel used = litres + previous level −
         * level after, over the odometer gap; none across a missed refuel.
         */
        fun build(
            refuels: List<RefuelEvent>,
            expenses: List<CarExpense>,
            distance: List<DistanceMonth> = emptyList(),
            zone: ZoneId = ZoneId.systemDefault(),
        ): Logbook {
            val distanceByMonth = distance.associateBy { YearMonth.from(it.month) }
            val newestFirst = refuels.sortedByDescending { it.fueledAt }
            val refuelEntries = newestFirst.mapIndexed { index, event ->
                val previous = newestFirst.getOrNull(index + 1)
                val km = previous?.let { event.mileage - it.mileage }?.takeIf { it > 0 }
                val consumption = if (event.missedPreviousRefuel || km == null) {
                    null
                } else {
                    val used = if (event.fuelLevelAfter != null && previous.fuelLevelAfter != null) {
                        event.liters + previous.fuelLevelAfter - event.fuelLevelAfter
                    } else {
                        event.liters
                    }
                    used.takeIf { it > 0 }?.let { it / km * 100 }
                }
                LogEntry.Refuel(event, km, consumption)
            }
            val entries = (refuelEntries + expenses.map { LogEntry.Expense(it) }).sortedByDescending { it.at }
            val grouped = entries
                .groupBy { YearMonth.from(it.at.atZone(zone)) }
                .map { (month, items) ->
                    LogMonth(
                        month = month,
                        entries = items,
                        spent = items.sumOf {
                            when (it) {
                                is LogEntry.Refuel -> it.event.totalPrice
                                is LogEntry.Expense -> it.expense.amount
                            }
                        },
                        distance = distanceByMonth[month],
                        fuel = items.filterIsInstance<LogEntry.Refuel>().sumOf { it.event.totalPrice },
                    )
                }
            val spentByMonth = grouped.associate { it.month to it.spent }
            val first = grouped.minOfOrNull { it.month }
            val current = YearMonth.now(zone)
            val months = grouped
                .map { month ->
                    // A month before the first entry is not a month with nothing spent: no line.
                    val previous = month.month.minusMonths(1).takeIf { first != null && it >= first }
                    month.copy(comparison = previous?.let { spendComparison(month.spent, spentByMonth[it] ?: 0.0, it, month.month == current) })
                }
                .sortedByDescending { it.month }
            val average = refuelEntries.mapNotNull { it.consumption }.takeIf { it.isNotEmpty() }?.average()
            return Logbook(months, average)
        }
    }
}

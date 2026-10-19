package io.github.numynox.nexus.vibilia.ui.logbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.numynox.nexus.vibilia.AppContainer
import io.github.numynox.nexus.vibilia.data.CarExpense
import io.github.numynox.nexus.vibilia.data.RefuelEvent
import io.github.numynox.nexus.vibilia.ui.common.Loadable
import io.github.numynox.nexus.vibilia.ui.common.loadable
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
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

data class LogMonth(val month: YearMonth, val entries: List<LogEntry>, val spent: Double)

data class Logbook(val months: List<LogMonth>, val averageConsumption: Double?)

/** Refuels and expenses of one car, newest first, grouped by month. */
class LogbookViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow<Loadable<Logbook>>(Loadable.Loading)
    val state: StateFlow<Loadable<Logbook>> = _state.asStateFlow()

    private var key: Pair<String, Int>? = null

    fun load(carId: String, version: Int, force: Boolean = false) {
        if (!force && key == carId to version) return
        val switchingCar = key?.first != carId
        key = carId to version
        viewModelScope.launch {
            if (switchingCar) _state.value = Loadable.Loading
            _state.value = loadable {
                val refuels = async { container.logbook.refuels(carId) }
                val expenses = async { container.logbook.expenses(carId) }
                build(refuels.await(), expenses.await())
            }
        }
    }

    companion object {
        /**
         * Consumption per refuel from the tank-level correction, the same
         * arithmetic as the web log: fuel used = litres + previous level −
         * level after, over the odometer gap; none across a missed refuel.
         */
        fun build(refuels: List<RefuelEvent>, expenses: List<CarExpense>, zone: ZoneId = ZoneId.systemDefault()): Logbook {
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
            val months = entries
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
                    )
                }
                .sortedByDescending { it.month }
            val average = refuelEntries.mapNotNull { it.consumption }.takeIf { it.isNotEmpty() }?.average()
            return Logbook(months, average)
        }
    }
}

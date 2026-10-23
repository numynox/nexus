package io.github.numynox.nexus.annona.ui.pantry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.numynox.nexus.annona.AppContainer
import io.github.numynox.nexus.annona.data.DashboardSummary
import io.github.numynox.nexus.annona.data.Item
import io.github.numynox.nexus.core.data.describe
import io.github.numynox.nexus.core.ui.Loadable
import io.github.numynox.nexus.core.ui.loadable
import io.github.numynox.nexus.core.ui.valueOrNull
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class Pantry(val summary: DashboardSummary, val items: List<Item>)

class PantryViewModel(container: AppContainer) : ViewModel() {
    private val repository = container.repository

    private val _pantry = MutableStateFlow<Loadable<Pantry>>(Loadable.Loading)
    val pantry: StateFlow<Loadable<Pantry>> = _pantry.asStateFlow()

    fun load(): Job = viewModelScope.launch {
        if (_pantry.value !is Loadable.Ready) _pantry.value = Loadable.Loading
        _pantry.value = loadable {
            coroutineScope {
                val summary = async { repository.summary() }
                val items = async { repository.activeItems() }
                Pantry(summary.await(), items.await())
            }
        }
    }

    /** Uses an item up at once, and puts it back if the database says no. Returns an error to show, if any. */
    fun consume(item: Item, consumed: Boolean, onError: (String) -> Unit, onDone: () -> Unit) {
        val current = _pantry.value.valueOrNull
        if (current != null && consumed) _pantry.value = Loadable.Ready(current.copy(items = current.items - item))
        viewModelScope.launch {
            runCatching { repository.setConsumed(item.id, consumed) }
                .onSuccess { onDone() }
                .onFailure {
                    onError(it.describe())
                    load()
                }
        }
    }
}

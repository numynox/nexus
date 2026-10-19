package io.github.numynox.nexus.annona.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.numynox.nexus.annona.AppContainer
import io.github.numynox.nexus.annona.data.Category
import io.github.numynox.nexus.annona.data.StorageLocation
import io.github.numynox.nexus.core.data.NexusConfig
import io.github.numynox.nexus.core.data.describe
import io.github.numynox.nexus.core.ui.Loadable
import io.github.numynox.nexus.core.ui.loadable
import io.github.numynox.nexus.core.ui.valueOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class Lists(val categories: List<Category>, val locations: List<StorageLocation>)

/** The household's shared lists — categories and locations — managed from settings. */
class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val repository = container.repository
    val config: NexusConfig get() = container.core.config

    private val _lists = MutableStateFlow<Loadable<Lists>>(Loadable.Loading)
    val lists: StateFlow<Loadable<Lists>> = _lists.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _lists.value = loadable {
                coroutineScope {
                    val categories = async { repository.categories() }
                    val locations = async { repository.locations() }
                    Lists(categories.await(), locations.await())
                }
            }
        }
    }

    private fun act(onDone: () -> Unit, block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.onSuccess { onDone() }.onFailure { _messages.tryEmit(it.describe()) }
            load()
        }
    }

    fun saveCategory(id: Long?, name: String, color: String?, icon: String?, onDone: () -> Unit) = act(onDone) { repository.saveCategory(id, name, color, icon) }
    fun deleteCategory(id: Long, onDone: () -> Unit) = act(onDone) { repository.deleteCategory(id) }
    fun saveLocation(id: Long?, name: String, onDone: () -> Unit) = act(onDone) { repository.saveLocation(id, name) }
    fun deleteLocation(id: Long, onDone: () -> Unit) = act(onDone) { repository.deleteLocation(id) }

    fun moveLocation(location: StorageLocation, by: Int, onDone: () -> Unit) {
        val lists = _lists.value.valueOrNull ?: return
        val from = lists.locations.indexOf(location)
        val to = from + by
        if (from < 0 || to !in lists.locations.indices) return
        val reordered = lists.locations.toMutableList().apply { add(to, removeAt(from)) }
        _lists.value = Loadable.Ready(lists.copy(locations = reordered))
        act(onDone) { repository.reorderLocations(reordered) }
    }
}

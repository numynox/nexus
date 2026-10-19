package io.github.numynox.nexus.noctua.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.numynox.nexus.core.data.NexusConfig
import io.github.numynox.nexus.core.data.describe
import io.github.numynox.nexus.core.ui.Loadable
import io.github.numynox.nexus.core.ui.loadable
import io.github.numynox.nexus.core.ui.valueOrNull
import io.github.numynox.nexus.noctua.AppContainer
import io.github.numynox.nexus.noctua.data.Feed
import io.github.numynox.nexus.noctua.data.Section
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The section list in settings, and the account-wide actions. */
class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val repository = container.repository

    val config: NexusConfig get() = container.core.config

    private val _sections = MutableStateFlow<Loadable<List<Section>>>(Loadable.Loading)
    val sections: StateFlow<Loadable<List<Section>>> = _sections.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch { _sections.value = loadable { repository.sections() } }
    }

    private fun act(message: String? = null, block: suspend () -> Unit, after: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess {
                    message?.let { _messages.tryEmit(it) }
                    after()
                }
                .onFailure { _messages.tryEmit(it.describe()) }
            load()
        }
    }

    fun create(title: String, icon: String, onDone: () -> Unit) = act(block = { repository.createSection(title, icon) }, after = onDone)

    /** Moves a section one place up or down; the list redraws at once, the database follows. */
    fun move(section: Section, by: Int, onDone: () -> Unit) {
        val list = _sections.value.valueOrNull ?: return
        val from = list.indexOfFirst { it.id == section.id }
        val to = from + by
        if (from < 0 || to !in list.indices) return
        val reordered = list.toMutableList().apply { add(to, removeAt(from)) }
        _sections.value = Loadable.Ready(reordered)
        act(block = { repository.reorderSections(reordered) }, after = onDone)
    }

    fun clearSeen() = viewModelScope.launch {
        container.preferences.clearSeen()
        _messages.tryEmit("Seen history cleared on this phone.")
    }

    fun clearReadHistory() = act("Read history cleared.", { repository.clearReadHistory() })
}

data class SectionEditing(val section: Section, val catalogue: List<Feed>)

/** One section: its name and icon, and its feeds from the shared catalogue. */
class SectionEditorViewModel(container: AppContainer, private val sectionId: Long) : ViewModel() {
    private val repository = container.repository

    private val _state = MutableStateFlow<Loadable<SectionEditing>>(Loadable.Loading)
    val state: StateFlow<Loadable<SectionEditing>> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** Whether anything was saved here, so the feed knows to reload. */
    var changed = false
        private set

    /** Set once the section is gone, so the screen can close. */
    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = loadable {
                val section = repository.sections().firstOrNull { it.id == sectionId } ?: error("This section no longer exists.")
                SectionEditing(section, repository.catalogue())
            }
        }
    }

    private fun act(block: suspend (SectionEditing) -> Unit) {
        val current = _state.value.valueOrNull ?: return
        changed = true
        viewModelScope.launch {
            runCatching { block(current) }.onFailure { _messages.tryEmit(it.describe()) }
            if (!_deleted.value) load()
        }
    }

    private fun optimistic(feeds: List<Feed>) {
        val current = _state.value.valueOrNull ?: return
        _state.value = Loadable.Ready(current.copy(section = current.section.copy(feeds = feeds)))
    }

    fun rename(title: String, icon: String) = act { repository.updateSection(sectionId, title, icon) }

    fun add(feed: Feed) = act { editing ->
        optimistic(editing.section.feeds + feed)
        repository.addFeed(sectionId, feed.id, editing.section.feeds.size)
    }

    fun remove(feed: Feed) = act { editing ->
        optimistic(editing.section.feeds - feed)
        repository.removeFeed(sectionId, feed.id)
    }

    fun move(feed: Feed, by: Int) = act { editing ->
        val feeds = editing.section.feeds.toMutableList()
        val from = feeds.indexOf(feed)
        val to = from + by
        if (from < 0 || to !in feeds.indices) return@act
        feeds.add(to, feeds.removeAt(from))
        optimistic(feeds)
        repository.reorderFeeds(sectionId, feeds.map { it.id })
    }

    fun delete() = act {
        repository.deleteSection(sectionId)
        _deleted.value = true
    }
}

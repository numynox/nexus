package io.github.numynox.nexus.noctua.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.numynox.nexus.core.data.describe
import io.github.numynox.nexus.core.ui.Loadable
import io.github.numynox.nexus.core.ui.loadable
import io.github.numynox.nexus.noctua.AppContainer
import io.github.numynox.nexus.noctua.data.Article
import io.github.numynox.nexus.noctua.data.DatedArticle
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Glossary: starred is intent (what to come back to), read is history (where you have been). */
enum class Shelf { Starred, Read }

data class Library(val starred: List<DatedArticle>, val read: List<DatedArticle>)

class LibraryViewModel(container: AppContainer) : ViewModel() {
    private val repository = container.repository

    private val _library = MutableStateFlow<Loadable<Library>>(Loadable.Loading)
    val library: StateFlow<Loadable<Library>> = _library.asStateFlow()

    /** Stars and read marks changed here, over what the load returned; the rows stay until the next load. */
    private val _starOverrides = MutableStateFlow<Map<Long, Boolean>>(emptyMap())
    val starOverrides: StateFlow<Map<Long, Boolean>> = _starOverrides.asStateFlow()
    private val _readOverrides = MutableStateFlow<Map<Long, Boolean>>(emptyMap())
    val readOverrides: StateFlow<Map<Long, Boolean>> = _readOverrides.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            if (_library.value !is Loadable.Ready) _library.value = Loadable.Loading
            _library.value = loadable { Library(repository.starredArticles(), repository.readArticles(READ_LIMIT)) }
            _starOverrides.value = emptyMap()
            _readOverrides.value = emptyMap()
        }
    }

    private val loaded: Library? get() = (_library.value as? Loadable.Ready)?.value

    fun isStarred(id: Long): Boolean =
        _starOverrides.value[id] ?: (loaded?.starred?.any { it.article.id == id } == true)

    fun isRead(id: Long): Boolean =
        _readOverrides.value[id] ?: (loaded?.read?.any { it.article.id == id } == true)

    fun toggleStar(article: Article) {
        val star = !isStarred(article.id)
        _starOverrides.update { it + (article.id to star) }
        viewModelScope.launch {
            runCatching { if (star) repository.star(article.id) else repository.unstar(article.id) }
                .onFailure {
                    _starOverrides.update { map -> map + (article.id to !star) }
                    _messages.tryEmit(it.describe())
                }
        }
    }

    fun toggleRead(article: Article) {
        val read = !isRead(article.id)
        _readOverrides.update { it + (article.id to read) }
        viewModelScope.launch {
            runCatching { if (read) repository.markRead(listOf(article.id)) else repository.markUnread(listOf(article.id)) }
                .onFailure {
                    _readOverrides.update { map -> map + (article.id to !read) }
                    _messages.tryEmit(it.describe())
                }
        }
    }

    private companion object {
        const val READ_LIMIT = 300
    }
}

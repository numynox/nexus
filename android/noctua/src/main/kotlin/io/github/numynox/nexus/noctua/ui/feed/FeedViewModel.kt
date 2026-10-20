package io.github.numynox.nexus.noctua.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.numynox.nexus.core.data.describe
import io.github.numynox.nexus.core.ui.Loadable
import io.github.numynox.nexus.core.ui.loadable
import io.github.numynox.nexus.core.ui.valueOrNull
import io.github.numynox.nexus.noctua.AppContainer
import io.github.numynox.nexus.noctua.BuildConfig
import io.github.numynox.nexus.noctua.data.Article
import io.github.numynox.nexus.noctua.data.FeedContent
import io.github.numynox.nexus.noctua.data.NoctuaSettings
import io.github.numynox.nexus.noctua.data.Section
import java.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One load of the feed, and what was already seen when it happened. */
data class FeedLoad(
    val content: FeedContent,
    val sectionId: Long?,
    /** Seen at load and hidden for it (if the user hides seen articles). */
    val hiddenAtLoad: Set<Long>,
    val groups: Map<Long, Long> = emptyMap(),
)

/** The feed as drawn, with the counts the header shows. */
data class FeedView(
    val days: List<DayGroup>,
    val unseen: Int,
    /** Share of this load's unseen stories seen since, for the progress bar. */
    val progress: Float,
)

class FeedViewModel(private val container: AppContainer) : ViewModel() {

    private val repository = container.repository
    private val preferences = container.preferences

    private val _load = MutableStateFlow<Loadable<FeedLoad>>(Loadable.Loading)
    val load: StateFlow<Loadable<FeedLoad>> = _load.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** Optimistic read marks and stars over what the load returned. */
    private val _reads = MutableStateFlow<Map<Long, Instant>>(emptyMap())
    val reads: StateFlow<Map<Long, Instant>> = _reads.asStateFlow()
    private val _starred = MutableStateFlow<Set<Long>>(emptySet())
    val starred: StateFlow<Set<Long>> = _starred.asStateFlow()

    val seen: StateFlow<Set<Long>> = preferences.seen.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val query = MutableStateFlow("")
    val feedFilter = MutableStateFlow<Long?>(null)

    private val _expanded = MutableStateFlow<Set<Long>>(emptySet())
    val expanded: StateFlow<Set<Long>> = _expanded.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val settings = preferences.settings.stateIn(viewModelScope, SharingStarted.Eagerly, NoctuaSettings())

    /**
     * Unseen articles shown when the list was loaded, or a feed chosen — the
     * web's `initialUnseen`, taken when the page loads. A search does not
     * retake it: searching narrows the list, it does not start a new one.
     */
    private val baseline = MutableStateFlow(0)

    private val days = combine(_load, settings, query, feedFilter) { load, settings, query, feed ->
        load.valueOrNull?.let { buildFeed(it.content.articles, it.hiddenAtLoad, query, feed, settings.sortOrder, it.groups) }.orEmpty()
    }

    val view: StateFlow<FeedView> = combine(days, seen, baseline) { days, seen, baseline ->
        val unseen = unseenCount(days, seen)
        FeedView(days, unseen, seenProgress(baseline, unseen))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, FeedView(emptyList(), 0, 0f))

    private suspend fun takeBaseline() {
        // The stored set, not [seen], which may not have loaded yet on the first call.
        baseline.value = unseenCount(days.first(), preferences.seen.first())
    }

    /** Shows one feed of the section, or all of them; like a new page on the web, the progress starts over. */
    fun setFeedFilter(feedId: Long?) {
        feedFilter.value = feedId
        viewModelScope.launch { takeBaseline() }
    }

    /** What the shown load was asked for: the section and the version of the sections. */
    private var shownFor: Pair<Long?, Int>? = null
    private var loading: Job? = null

    /**
     * Shows the given section, loading it unless it is already shown. Coming
     * back to the feed from another tab must not swap the list under the
     * reader: a reload re-hides what was seen and restarts the progress, so
     * only the reader's own refresh, a section switch or edited sections do.
     */
    fun show(sectionId: Long?, sectionsVersion: Int) {
        val key = sectionId to sectionsVersion
        if (key == shownFor && (_load.value is Loadable.Ready || loading?.isActive == true)) return
        shownFor = key
        load(sectionId)
    }

    /** (Re)loads the given section, or all of them; [sectionId] may be stale and falls back. */
    fun load(sectionId: Long?, refresh: Boolean = false) {
        // Only the latest request may land: an older one finishing late would
        // replace the list the reader is already scrolling.
        loading?.cancel()
        loading = viewModelScope.launch {
            // A superseded refresh never finishes, so the newest load owns the indicator.
            _refreshing.value = refresh
            if (!refresh && _load.value !is Loadable.Ready) _load.value = Loadable.Loading
            val result = loadable { fetch(sectionId) }
            if (result is Loadable.Ready || !refresh) _load.value = result
            if (result is Loadable.Ready) takeBaseline()
            if (result is Loadable.Failed && refresh) _messages.tryEmit(result.message)
            _refreshing.value = false
        }
    }

    private suspend fun fetch(requested: Long?): FeedLoad {
        val sections = repository.sections()
        val section = requested?.let { id -> sections.firstOrNull { it.id == id } }
        val feeds = (section?.let(::listOf) ?: sections).flatMap(Section::feeds).distinctBy { it.id }
        val content = repository.feedContent(sections, feeds, BuildConfig.ARTICLE_FETCH_LIMIT)
        val current = preferences.current()
        val seenNow = preferences.seen.first()
        val hidden = if (current.hideSeen) content.articles.mapNotNullTo(HashSet()) { a -> a.id.takeIf { it in seenNow } } else emptySet()
        // Grouping is a nicety: without it every story simply stands alone.
        val groups = runCatching {
            repository.similarGroups(content.articles.filter { it.id !in hidden }.map { it.id })
        }.getOrDefault(emptyMap())
        _reads.value = content.reads
        _starred.value = content.starred
        feedFilter.value = feedFilter.value?.takeIf { id -> feeds.any { it.id == id } }
        _expanded.value = emptySet()
        return FeedLoad(content, section?.id, hidden, groups)
    }

    fun toggleExpanded(articleId: Long) = _expanded.update { if (articleId in it) it - articleId else it + articleId }

    /** Seen is per device and per cluster: a story counts as seen as a whole. */
    fun markSeen(items: List<FeedItem>) {
        val ids = items.flatMap { item -> item.members.map { it.id } }.filter { it !in seen.value }
        if (ids.isNotEmpty()) viewModelScope.launch { preferences.markSeen(ids) }
    }

    /** Opening an article reads it — the whole cluster, so its twins do not come back. */
    fun opened(item: FeedItem?, article: Article) {
        val ids = (item?.members ?: listOf(article)).map { it.id }
        viewModelScope.launch { preferences.markSeen(ids) }
        if (article.id !in _reads.value) setRead(listOf(article.id), true)
    }

    fun toggleRead(article: Article) = setRead(listOf(article.id), article.id !in _reads.value)

    private fun setRead(ids: List<Long>, read: Boolean) {
        val before = _reads.value
        _reads.update { reads -> if (read) reads + ids.associateWith { Instant.now() } else reads - ids.toSet() }
        viewModelScope.launch {
            runCatching { if (read) repository.markRead(ids) else repository.markUnread(ids) }
                .onFailure {
                    // Put it back: the card should not claim what is not stored.
                    _reads.value = before
                    _messages.tryEmit(it.describe())
                }
        }
    }

    fun toggleStar(article: Article) {
        val starring = article.id !in _starred.value
        _starred.update { if (starring) it + article.id else it - article.id }
        viewModelScope.launch {
            runCatching { if (starring) repository.star(article.id) else repository.unstar(article.id) }
                .onFailure {
                    _starred.update { set -> if (starring) set - article.id else set + article.id }
                    _messages.tryEmit(it.describe())
                }
        }
    }

    /** Every unread story shown, the clusters' twins included. */
    fun unreadShown(): List<Long> =
        view.value.days.flatMap { it.items }.flatMap { it.members }.map { it.id }.filter { it !in _reads.value }

    fun markAllRead(ids: List<Long>) = setRead(ids, true)

    fun undoMarkAllRead(ids: List<Long>) = setRead(ids, false)
}

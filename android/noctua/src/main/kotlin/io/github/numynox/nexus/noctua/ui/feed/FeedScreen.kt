package io.github.numynox.nexus.noctua.ui.feed

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SpaceDashboard
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.numynox.nexus.core.ui.Loadable
import io.github.numynox.nexus.core.ui.components.EmptyState
import io.github.numynox.nexus.core.ui.components.ErrorState
import io.github.numynox.nexus.core.ui.components.LoadingState
import io.github.numynox.nexus.core.ui.components.UpdateBanner
import io.github.numynox.nexus.noctua.data.Density
import io.github.numynox.nexus.noctua.data.Section
import io.github.numynox.nexus.noctua.data.SortOrder
import io.github.numynox.nexus.noctua.ui.AppViewModel
import io.github.numynox.nexus.noctua.ui.common.ArticleActions
import io.github.numynox.nexus.noctua.ui.common.ArticleCard
import io.github.numynox.nexus.noctua.ui.common.FeedIcon
import io.github.numynox.nexus.noctua.ui.common.containerViewModel
import io.github.numynox.nexus.noctua.ui.common.openArticle
import io.github.numynox.nexus.noctua.ui.common.sectionIcon
import io.github.numynox.nexus.noctua.ui.common.shareArticle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** One row of the lazy list: a day's heading or a story. */
private sealed interface Row {
    val key: String
    data class Header(val label: String) : Row { override val key = "day-$label" }
    data class Story(val item: FeedItem) : Row { override val key = "story-${item.article.id}" }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeedScreen(app: AppViewModel, onManageSections: () -> Unit) {
    val viewModel = containerViewModel { FeedViewModel(it) }
    val settings by app.settings.collectAsStateWithLifecycle()
    val sectionsVersion by app.sectionsVersion.collectAsStateWithLifecycle()
    val update by app.update.collectAsStateWithLifecycle()
    val load by viewModel.load.collectAsStateWithLifecycle()
    val view by viewModel.view.collectAsStateWithLifecycle()
    val reads by viewModel.reads.collectAsStateWithLifecycle()
    val starred by viewModel.starred.collectAsStateWithLifecycle()
    val seen by viewModel.seen.collectAsStateWithLifecycle()
    val expanded by viewModel.expanded.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val feedFilter by viewModel.feedFilter.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val toolbarColor = MaterialTheme.colorScheme.surface
    var searching by rememberSaveable { mutableStateOf(false) }
    var confirmMarkAll by remember { mutableStateOf<List<Long>>(emptyList()) }

    LaunchedEffect(settings.selectedSectionId, sectionsVersion) {
        viewModel.show(settings.selectedSectionId, sectionsVersion)
    }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    val loaded = (load as? Loadable.Ready)?.value
    val sections = loaded?.content?.sections.orEmpty()
    val section = sections.firstOrNull { it.id == loaded?.sectionId }
    val rows = remember(view.days) {
        view.days.flatMap { day -> listOf<Row>(Row.Header(day.label)) + day.items.map { Row.Story(it) } }
    }
    val itemFor = remember(rows) { rows.mapNotNull { (it as? Row.Story)?.item }.associateBy { it.article.id } }

    val actions = ArticleActions(
        open = { article ->
            viewModel.opened(itemFor[article.id] ?: itemFor.values.firstOrNull { item -> item.similar.any { it.id == article.id } }, article)
            openArticle(context, article, settings.openInApp, toolbarColor)
        },
        toggleStar = viewModel::toggleStar,
        toggleRead = viewModel::toggleRead,
        share = { shareArticle(context, it) },
    )

    val listState = rememberLazyListState()
    LaunchedEffect(viewModel) {
        viewModel.refreshed.collect { listState.scrollToItem(0) }
    }
    // The update banner leads the list only while there is one: an empty item
    // would still take a gap, and push the end panel off where it rests below
    // the articles (see [EndOfFeed]).
    val lead = if (update != null) 1 else 0
    val pullThreshold = with(LocalDensity.current) { 96.dp.toPx() }
    val endPull = remember(pullThreshold) { EndPull(pullThreshold) { viewModel.load(settings.selectedSectionId, refresh = true) } }
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 } }
    // Seen on scroll: a story is seen once it has gone up past the top of the
    // list, and only while scrolling down — the web's rule, on the list's
    // own scroll position instead of the window's.
    LaunchedEffect(listState, rows, lead, settings.autoMarkSeen) {
        if (!settings.autoMarkSeen) return@LaunchedEffect
        var previous = listState.firstVisibleItemIndex
        snapshotFlow { listState.firstVisibleItemIndex }.distinctUntilChanged().collect { first ->
            if (first > previous) {
                // Row i is list item i + lead.
                val from = (previous - lead).coerceIn(0, rows.size)
                val to = (first - lead).coerceIn(0, rows.size)
                viewModel.markSeen(rows.subList(from, to).mapNotNull { (it as? Row.Story)?.item })
            }
            previous = first
        }
    }

    Scaffold(
        topBar = {
            // One surface behind the whole header — title, section chips and
            // progress — so cards never scroll through it. It tints once the
            // list is scrolled, as Material's top bars do.
            val headerColor by animateColorAsState(
                if (scrolled) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface,
                label = "header",
            )
            Surface(color = headerColor) {
                Column(Modifier.padding(bottom = 2.dp)) {
                    if (searching) {
                        SearchBar(query, onQuery = { viewModel.query.value = it }, onClose = {
                            searching = false
                            viewModel.query.value = ""
                        })
                    } else {
                        TopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                            title = {
                                Column {
                                    Text(feedFilter?.let { id -> section?.feeds?.firstOrNull { it.id == id }?.name } ?: section?.name ?: "All sections")
                                    if (loaded != null) {
                                        Text(
                                            if (view.unseen == 0) "All caught up" else "${view.unseen} new",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            },
                            actions = {
                                IconButton(onClick = { searching = true }) { Icon(Icons.Rounded.Search, contentDescription = "Search") }
                                if (section != null && section.feeds.size > 1) {
                                    FeedFilterButton(section, feedFilter, viewModel::setFeedFilter)
                                }
                                OverflowMenu(
                                    density = settings.density,
                                    sortOrder = settings.sortOrder,
                                    onDensity = app::setDensity,
                                    onSortOrder = app::setSortOrder,
                                    onMarkAllRead = { confirmMarkAll = viewModel.unreadShown() },
                                    onRefresh = { viewModel.load(settings.selectedSectionId, refresh = true) },
                                )
                            },
                        )
                    }
                    if (loaded != null && sections.isNotEmpty()) {
                        SectionChips(sections, loaded.sectionId, onSelect = { id ->
                            scope.launch { listState.scrollToItem(0) }
                            app.selectSection(id)
                        })
                        ReadingProgress(view.progress, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        // The list starts below the header, not behind it, so its sticky day
        // headings stick under the header rather than out of sight beneath it.
        val contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)
        when (val state = load) {
            Loadable.Loading -> LoadingState(Modifier.fillMaxSize().padding(padding))
            is Loadable.Failed -> ErrorState(state.message, onRetry = { viewModel.load(settings.selectedSectionId) }, modifier = Modifier.padding(padding))
            is Loadable.Ready -> Box(
                Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()).nestedScroll(endPull),
            ) {
                LazyColumn(
                    state = listState,
                    contentPadding = contentPadding,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (update != null) item(key = "update") { UpdateBanner(update) }
                    if (state.value.content.sections.isEmpty()) {
                        item(key = "no-sections") {
                            EmptyState(
                                icon = Icons.Rounded.Widgets,
                                title = "No sections yet",
                                message = "Sections group the feeds you follow. Make one and add feeds to it.",
                                action = { FilledTonalButton(onClick = onManageSections) { Text("Set up sections") } },
                            )
                        }
                    }
                    rows.forEach { row ->
                        when (row) {
                            is Row.Header -> stickyHeader(key = row.key) { DayHeader(row.label) }
                            is Row.Story -> item(key = row.key) {
                                val article = row.item.article
                                ArticleCard(
                                    article = article,
                                    read = article.id in reads,
                                    starred = article.id in starred,
                                    seen = row.item.members.all { it.id in seen },
                                    density = settings.density,
                                    actions = actions,
                                    similar = row.item.similar,
                                    similarExpanded = article.id in expanded,
                                    onToggleSimilar = { viewModel.toggleExpanded(article.id) },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                    if (state.value.content.sections.isNotEmpty()) {
                        // Takes over from the last day's heading, which would
                        // otherwise stay pinned above the end panel. Without
                        // articles there is none, and the panel alone fills the
                        // list exactly where it rests when scrolled to.
                        if (rows.isNotEmpty()) stickyHeader(key = "end-heading") { Spacer(Modifier.fillMaxWidth()) }
                        item(key = "end") {
                            EndOfFeed(
                                empty = rows.isEmpty(),
                                pull = endPull,
                                refreshing = refreshing,
                                onRefresh = { viewModel.load(settings.selectedSectionId, refresh = true) },
                                modifier = Modifier.fillParentMaxHeight(),
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmMarkAll.isNotEmpty()) {
        val ids = confirmMarkAll
        AlertDialog(
            onDismissRequest = { confirmMarkAll = emptyList() },
            icon = { Icon(Icons.Rounded.DoneAll, contentDescription = null) },
            title = { Text("Mark ${ids.size} as read?") },
            text = { Text("Every unread article shown here, the same stories from other feeds included. You can undo this for a few seconds.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmMarkAll = emptyList()
                    viewModel.markAllRead(ids)
                    scope.launch {
                        val result = snackbar.showSnackbar("${ids.size} marked as read", actionLabel = "Undo", duration = SnackbarDuration.Long)
                        if (result == SnackbarResult.ActionPerformed) viewModel.undoMarkAllRead(ids)
                    }
                }) { Text("Mark read") }
            },
            dismissButton = { TextButton(onClick = { confirmMarkAll = emptyList() }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DayHeader(label: String) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun SectionChips(sections: List<Section>, selected: Long?, onSelect: (Long?) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "all") {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text("All") },
                leadingIcon = { Icon(Icons.Rounded.SpaceDashboard, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) },
            )
        }
        items(sections, key = { it.id }) { section ->
            FilterChip(
                selected = selected == section.id,
                onClick = { onSelect(section.id) },
                label = { Text(section.name) },
                leadingIcon = { Icon(sectionIcon(section.icon), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) },
            )
        }
    }
}

@Composable
private fun FeedFilterButton(section: Section, selected: Long?, onSelect: (Long?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.FilterList, contentDescription = "Filter by feed", tint = if (selected != null) MaterialTheme.colorScheme.primary else Color.Unspecified)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Every feed in ${section.name}") },
                leadingIcon = { Icon(sectionIcon(section.icon), contentDescription = null) },
                trailingIcon = { if (selected == null) Icon(Icons.Rounded.Check, contentDescription = null) },
                onClick = { open = false; onSelect(null) },
            )
            HorizontalDivider()
            section.feeds.forEach { feed ->
                DropdownMenuItem(
                    text = { Text(feed.name) },
                    leadingIcon = { FeedIcon(feed.icon, Modifier.size(20.dp)) },
                    trailingIcon = { if (selected == feed.id) Icon(Icons.Rounded.Check, contentDescription = null) },
                    onClick = { open = false; onSelect(feed.id) },
                )
            }
        }
    }
}

@Composable
private fun OverflowMenu(
    density: Density,
    sortOrder: SortOrder,
    onDensity: (Density) -> Unit,
    onSortOrder: (SortOrder) -> Unit,
    onMarkAllRead: () -> Unit,
    onRefresh: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Mark all as read") },
                leadingIcon = { Icon(Icons.Rounded.DoneAll, contentDescription = null) },
                onClick = { open = false; onMarkAllRead() },
            )
            DropdownMenuItem(
                text = { Text("Refresh") },
                leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                onClick = { open = false; onRefresh() },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(if (sortOrder == SortOrder.Newest) "Oldest first" else "Newest first") },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = null) },
                onClick = { open = false; onSortOrder(if (sortOrder == SortOrder.Newest) SortOrder.Oldest else SortOrder.Newest) },
            )
            DropdownMenuItem(
                text = { Text(if (density == Density.Comfortable) "Compact list" else "Cards with images") },
                leadingIcon = { Icon(Icons.Rounded.ViewAgenda, contentDescription = null) },
                onClick = { open = false; onDensity(if (density == Density.Comfortable) Density.Compact else Density.Comfortable) },
            )
        }
    }
}

@Composable
private fun SearchBar(query: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        title = {
            TextField(
                value = query,
                onValueChange = onQuery,
                placeholder = { Text("Search titles, summaries, feeds") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "Close search") } },
    )
}


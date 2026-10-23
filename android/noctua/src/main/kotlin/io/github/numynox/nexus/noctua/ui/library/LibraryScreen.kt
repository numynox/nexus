package io.github.numynox.nexus.noctua.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.numynox.nexus.core.ui.Loadable
import io.github.numynox.nexus.core.ui.NexusFormat
import io.github.numynox.nexus.core.ui.components.ConnectedChoiceRow
import io.github.numynox.nexus.core.ui.components.EmptyState
import io.github.numynox.nexus.core.ui.components.ErrorState
import io.github.numynox.nexus.core.ui.components.rememberPullRefresh
import io.github.numynox.nexus.noctua.ui.AppViewModel
import io.github.numynox.nexus.noctua.ui.common.ArticleActions
import io.github.numynox.nexus.noctua.ui.common.ArticleCard
import io.github.numynox.nexus.noctua.ui.common.containerViewModel
import io.github.numynox.nexus.noctua.ui.common.openArticle
import io.github.numynox.nexus.noctua.ui.common.shareArticle
import io.github.numynox.nexus.noctua.ui.feed.FeedPlaceholders

/** The web's /starred and /read pages as one screen with two shelves. */
@Composable
fun LibraryScreen(app: AppViewModel) {
    val viewModel = containerViewModel { LibraryViewModel(it) }
    val settings by app.settings.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    // Read so the rows recompose when a star or read mark changes here.
    val starOverrides by viewModel.starOverrides.collectAsStateWithLifecycle()
    val readOverrides by viewModel.readOverrides.collectAsStateWithLifecycle()
    var shelf by rememberSaveable { mutableStateOf(Shelf.Starred) }
    val pull = rememberPullRefresh(viewModel::load)
    val context = LocalContext.current
    val toolbarColor = MaterialTheme.colorScheme.surface
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    val actions = ArticleActions(
        open = { openArticle(context, it, settings.openInApp, toolbarColor) },
        toggleStar = viewModel::toggleStar,
        toggleRead = viewModel::toggleRead,
        share = { shareArticle(context, it) },
    )

    Scaffold(
        topBar = { TopAppBar(title = { Text("Library") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = pull.refreshing,
            onRefresh = pull.onRefresh,
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "shelf") {
                    ConnectedChoiceRow(
                        options = Shelf.entries,
                        selected = shelf,
                        onSelect = { shelf = it },
                        label = { it.name },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    )
                }
                when (val state = library) {
                    Loadable.Loading -> item(key = "loading") { FeedPlaceholders(settings.density) }
                    is Loadable.Failed -> item(key = "error") { ErrorState(state.message, onRetry = viewModel::load) }
                    is Loadable.Ready -> {
                        val entries = if (shelf == Shelf.Starred) state.value.starred else state.value.read
                        if (entries.isEmpty()) {
                            item(key = "empty-$shelf") {
                                if (shelf == Shelf.Starred) {
                                    EmptyState(Icons.Rounded.StarOutline, "Nothing starred", "Swipe an article to the right to keep it here for later.")
                                } else {
                                    EmptyState(Icons.Rounded.History, "Nothing read yet", "Articles you open land here, newest first.")
                                }
                            }
                        }
                        items(entries, key = { "$shelf-${it.article.id}" }) { entry ->
                            val article = entry.article
                            val isStarred = starOverrides[article.id] ?: viewModel.isStarred(article.id)
                            val isRead = readOverrides[article.id] ?: viewModel.isRead(article.id)
                            ArticleCard(
                                article = article,
                                read = isRead,
                                starred = isStarred,
                                seen = false,
                                density = settings.density,
                                actions = actions,
                                // Fades only what was taken off this shelf just now.
                                faded = if (shelf == Shelf.Starred) !isStarred else !isRead,
                                caption = (if (shelf == Shelf.Starred) "Starred " else "Read ") + NexusFormat.dateTime(entry.at),
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }
}

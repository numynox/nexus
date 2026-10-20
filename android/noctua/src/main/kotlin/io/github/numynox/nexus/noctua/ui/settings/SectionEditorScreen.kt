package io.github.numynox.nexus.noctua.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.numynox.nexus.core.ui.Loadable
import io.github.numynox.nexus.core.ui.components.ErrorState
import io.github.numynox.nexus.core.ui.components.LoadingState
import io.github.numynox.nexus.core.ui.components.NexusCard
import io.github.numynox.nexus.core.ui.components.SectionHeader
import io.github.numynox.nexus.noctua.data.Feed
import io.github.numynox.nexus.noctua.ui.AppViewModel
import io.github.numynox.nexus.noctua.ui.common.FeedIcon
import io.github.numynox.nexus.noctua.ui.common.containerViewModel
import io.github.numynox.nexus.noctua.ui.common.sectionIcon

@Composable
fun SectionEditorScreen(app: AppViewModel, sectionId: Long, onBack: () -> Unit) {
    val viewModel = containerViewModel(key = "section-$sectionId") { SectionEditorViewModel(it, sectionId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(deleted) {
        if (deleted) {
            app.sectionsChanged()
            onBack()
        }
    }
    // Whatever changed here, the feed and the settings list show it on return.
    DisposableEffect(viewModel) {
        onDispose { if (viewModel.changed) app.sectionsChanged() }
    }

    val editing = (state as? Loadable.Ready)?.value
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(editing?.section?.name ?: "Section") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
                actions = {
                    if (editing != null) {
                        IconButton(onClick = { renaming = true }) { Icon(Icons.Rounded.Edit, contentDescription = "Rename") }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when (val current = state) {
            Loadable.Loading -> LoadingState(Modifier.fillMaxSize().padding(padding))
            is Loadable.Failed -> ErrorState(current.message, onRetry = viewModel::load, modifier = Modifier.padding(padding))
            is Loadable.Ready -> {
                val section = current.value.section
                val members = section.feeds.map { it.id }.toSet()
                val available = current.value.catalogue
                    .filter { it.id !in members }
                    .filter { search.isBlank() || it.name.contains(search.trim(), ignoreCase = true) || it.url.contains(search.trim(), ignoreCase = true) }
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item {
                        SectionHeader(
                            "In this section",
                            supporting = if (section.feeds.isEmpty()) "No feeds yet — add some from the catalogue below." else "Shown in this order in the feed's filter",
                        )
                    }
                    if (section.feeds.isNotEmpty()) {
                        item {
                            NexusCard {
                                section.feeds.forEachIndexed { index, feed ->
                                    if (index > 0) HorizontalDivider(Modifier.padding(vertical = 2.dp))
                                    FeedRow(feed) {
                                        IconButton(onClick = { viewModel.move(feed, -1) }, enabled = index > 0) {
                                            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Move ${feed.name} up")
                                        }
                                        IconButton(onClick = { viewModel.move(feed, 1) }, enabled = index < section.feeds.lastIndex) {
                                            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move ${feed.name} down")
                                        }
                                        IconButton(onClick = { viewModel.remove(feed) }) {
                                            Icon(Icons.Rounded.RemoveCircleOutline, contentDescription = "Remove ${feed.name}", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item { SectionHeader("Catalogue", supporting = "Feeds every Nexus account can follow") }
                    item {
                        OutlinedTextField(
                            value = search,
                            onValueChange = { search = it },
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                            placeholder = { Text("Find a feed") },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        NexusCard {
                            if (available.isEmpty()) {
                                Text("Nothing more to add.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            available.forEachIndexed { index, feed ->
                                if (index > 0) HorizontalDivider(Modifier.padding(vertical = 2.dp))
                                FeedRow(feed) {
                                    IconButton(onClick = { viewModel.add(feed) }) {
                                        Icon(Icons.Rounded.AddCircleOutline, contentDescription = "Add ${feed.name}", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                    item {
                        OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.padding(top = 8.dp)) {
                            Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                            Text("Delete section", modifier = Modifier.padding(start = 8.dp), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    if (renaming && editing != null) {
        SectionSheet(
            title = "Edit section",
            confirm = "Save",
            initialName = editing.section.name,
            initialIcon = editing.section.icon,
            onConfirm = { name, icon ->
                renaming = false
                viewModel.rename(name, icon)
            },
            onDismiss = { renaming = false },
        )
    }
    if (confirmDelete && editing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(sectionIcon(editing.section.icon), contentDescription = null) },
            title = { Text("Delete ${editing.section.name}?") },
            text = { Text("The feeds stay in the catalogue. Their articles disappear from your feed unless another of your sections follows them.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun FeedRow(feed: Feed, actions: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FeedIcon(feed.icon, Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            Text(feed.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                feed.url.removePrefix("https://").removePrefix("http://"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row { actions() }
    }
}

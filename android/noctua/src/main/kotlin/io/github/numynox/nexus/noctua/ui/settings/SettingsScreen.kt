package io.github.numynox.nexus.noctua.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.numynox.nexus.core.data.AuthState
import io.github.numynox.nexus.core.ui.Loadable
import io.github.numynox.nexus.core.ui.components.ConnectedChoiceRow
import io.github.numynox.nexus.core.ui.components.LoadingState
import io.github.numynox.nexus.core.ui.components.NexusCard
import io.github.numynox.nexus.core.ui.components.SectionHeader
import io.github.numynox.nexus.core.ui.components.UpdateBanner
import io.github.numynox.nexus.core.ui.settings.AboutItem
import io.github.numynox.nexus.core.ui.settings.AccountCard
import io.github.numynox.nexus.core.ui.settings.AppearanceCard
import io.github.numynox.nexus.core.ui.settings.SettingSwitch
import io.github.numynox.nexus.noctua.data.DEFAULT_SECTION_ICON
import io.github.numynox.nexus.noctua.data.Density
import io.github.numynox.nexus.noctua.ui.common.label
import io.github.numynox.nexus.noctua.data.Section
import io.github.numynox.nexus.noctua.data.SortOrder
import io.github.numynox.nexus.noctua.ui.AppViewModel
import io.github.numynox.nexus.noctua.ui.common.containerViewModel
import io.github.numynox.nexus.noctua.ui.common.sectionIcon

@Composable
fun SettingsScreen(app: AppViewModel, onOpenSection: (Long) -> Unit) {
    val viewModel = containerViewModel { SettingsViewModel(it) }
    val settings by app.settings.collectAsStateWithLifecycle()
    val auth by app.auth.collectAsStateWithLifecycle()
    val update by app.update.collectAsStateWithLifecycle()
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    val sectionsVersion by app.sectionsVersion.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var creating by rememberSaveable { mutableStateOf(false) }
    var confirmClearReads by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    // Back from a section's editor: show what it changed.
    LaunchedEffect(sectionsVersion) { viewModel.load() }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item { UpdateBanner(update) }

            item {
                SectionHeader("Sections", supporting = "Your arrangement of the shared feed catalogue", trailing = {
                    FilledTonalButton(onClick = { creating = true }) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("New", modifier = Modifier.padding(start = 6.dp))
                    }
                })
            }
            when (val state = sections) {
                Loadable.Loading -> item { LoadingState(Modifier.fillMaxWidth().padding(24.dp)) }
                is Loadable.Failed -> item { Text(state.message, color = MaterialTheme.colorScheme.error) }
                is Loadable.Ready -> item {
                    NexusCard {
                        if (state.value.isEmpty()) {
                            Text(
                                "No sections yet. A section is a named group of feeds; the feed shows one at a time.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        state.value.forEachIndexed { index, section ->
                            if (index > 0) HorizontalDivider(Modifier.padding(vertical = 4.dp))
                            SectionRow(
                                section = section,
                                canMoveUp = index > 0,
                                canMoveDown = index < state.value.lastIndex,
                                onMove = { by -> viewModel.move(section, by, app::sectionsChanged) },
                                onOpen = { onOpenSection(section.id) },
                            )
                        }
                    }
                }
            }

            item { SectionHeader("Reading") }
            item {
                NexusCard {
                    SettingSwitch(
                        title = "Hide seen articles",
                        description = "Articles you have seen on this phone are left out the next time the feed loads.",
                        checked = settings.hideSeen,
                        onCheckedChange = app::setHideSeen,
                    )
                    SettingSwitch(
                        title = "Mark as seen while scrolling",
                        description = "An article counts as seen once you have scrolled past it.",
                        checked = settings.autoMarkSeen,
                        onCheckedChange = app::setAutoMarkSeen,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    SettingSwitch(
                        title = "Open articles inside Noctua",
                        description = "In your browser's in-app tab, so Back returns to the feed. Off opens the browser app.",
                        checked = settings.openInApp,
                        onCheckedChange = app::setOpenInApp,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text("Layout", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
                    ConnectedChoiceRow(
                        options = Density.entries,
                        selected = settings.density,
                        onSelect = app::setDensity,
                        label = { it.label },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    Text("Order", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
                    ConnectedChoiceRow(
                        options = SortOrder.entries,
                        selected = settings.sortOrder,
                        onSelect = app::setSortOrder,
                        label = { "${it.name} first" },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }

            item { SectionHeader("Appearance") }
            item {
                AppearanceCard(
                    themeMode = settings.themeMode,
                    onThemeMode = app::setThemeMode,
                    dynamicColor = settings.dynamicColor,
                    onDynamicColor = app::setDynamicColor,
                    appColour = "Noctua blue",
                )
            }

            item { SectionHeader("History") }
            item {
                NexusCard {
                    Text(
                        "Seen is this phone's memory of what you scrolled past. Read is what you opened, kept in your account and behind the statistics.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = viewModel::clearSeen) {
                            Icon(Icons.Rounded.VisibilityOff, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Clear seen", modifier = Modifier.padding(start = 8.dp))
                        }
                        OutlinedButton(onClick = { confirmClearReads = true }) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                            Text("Clear read history", modifier = Modifier.padding(start = 8.dp), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            item { SectionHeader("Account") }
            item { AccountCard("Noctua", (auth as? AuthState.SignedIn)?.email, onSignOut = app::signOut) }
            item { AboutItem(viewModel.config) }
        }
    }

    if (creating) {
        SectionSheet(
            title = "New section",
            confirm = "Create",
            initialName = "",
            initialIcon = DEFAULT_SECTION_ICON,
            onConfirm = { name, icon ->
                creating = false
                viewModel.create(name, icon, app::sectionsChanged)
            },
            onDismiss = { creating = false },
        )
    }
    if (confirmClearReads) {
        AlertDialog(
            onDismissRequest = { confirmClearReads = false },
            icon = { Icon(Icons.Rounded.DeleteSweep, contentDescription = null) },
            title = { Text("Clear your read history?") },
            text = { Text("Every article becomes unread again and the statistics start over. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearReads = false
                    viewModel.clearReadHistory()
                }) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClearReads = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionRow(section: Section, canMoveUp: Boolean, canMoveDown: Boolean, onMove: (Int) -> Unit, onOpen: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, MaterialShapes.Cookie6Sided.toShape()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(sectionIcon(section.icon), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(section.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    if (section.feeds.size == 1) "1 feed" else "${section.feeds.size} feeds",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = { onMove(-1) }, enabled = canMoveUp) { Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Move ${section.name} up") }
        IconButton(onClick = { onMove(1) }, enabled = canMoveDown) { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move ${section.name} down") }
        IconButton(onClick = onOpen) { Icon(Icons.Rounded.ChevronRight, contentDescription = "Edit ${section.name}") }
    }
}

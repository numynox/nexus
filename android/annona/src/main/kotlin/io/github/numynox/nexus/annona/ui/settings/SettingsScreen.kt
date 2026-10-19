package io.github.numynox.nexus.annona.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.numynox.nexus.annona.data.Category
import io.github.numynox.nexus.annona.data.StorageLocation
import io.github.numynox.nexus.annona.ui.AppViewModel
import io.github.numynox.nexus.annona.ui.common.CategoryAvatar
import io.github.numynox.nexus.annona.ui.common.CategoryColors
import io.github.numynox.nexus.annona.ui.common.CategoryIcons
import io.github.numynox.nexus.annona.ui.common.containerViewModel
import io.github.numynox.nexus.annona.data.CategoryRef
import io.github.numynox.nexus.core.data.AuthState
import io.github.numynox.nexus.core.ui.Loadable
import io.github.numynox.nexus.core.ui.components.LoadingState
import io.github.numynox.nexus.core.ui.components.NexusCard
import io.github.numynox.nexus.core.ui.components.SectionHeader
import io.github.numynox.nexus.core.ui.components.UpdateBanner
import io.github.numynox.nexus.core.ui.settings.AboutItem
import io.github.numynox.nexus.core.ui.settings.AccountCard
import io.github.numynox.nexus.core.ui.settings.AppearanceCard
import io.github.numynox.nexus.core.ui.settings.SettingSwitch

private sealed interface Editing {
    data class CategoryEdit(val category: Category?) : Editing
    data class LocationEdit(val location: StorageLocation?) : Editing
}

@Composable
fun SettingsScreen(app: AppViewModel) {
    val viewModel = containerViewModel { SettingsViewModel(it) }
    val settings by app.settings.collectAsStateWithLifecycle()
    val auth by app.auth.collectAsStateWithLifecycle()
    val update by app.update.collectAsStateWithLifecycle()
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<Editing?>(null) }
    val context = LocalContext.current
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) app.setRemindersOn(true)
    }

    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item { UpdateBanner(update) }

            item { SectionHeader("Reminders") }
            item {
                NexusCard {
                    SettingSwitch(
                        title = "Remind me before things expire",
                        description = "Once a day this phone lists what is about to go off. Only this phone: everyone in the household decides for themselves.",
                        checked = settings.remindersOn,
                        onCheckedChange = { on ->
                            val allowed = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                            if (on && !allowed) notifications.launch(Manifest.permission.POST_NOTIFICATIONS) else app.setRemindersOn(on)
                        },
                    )
                    if (settings.remindersOn) {
                        SliderRow(
                            label = "Look ahead",
                            value = if (settings.reminderDays == 1) "1 day" else "${settings.reminderDays} days",
                            current = settings.reminderDays,
                            range = 1..14,
                            onChange = app::setReminderDays,
                        )
                        SliderRow(
                            label = "Remind at",
                            value = "%02d:00".format(settings.reminderHour),
                            current = settings.reminderHour,
                            range = 6..21,
                            onChange = app::setReminderHour,
                        )
                    }
                }
            }

            when (val state = lists) {
                Loadable.Loading -> item { LoadingState(Modifier.fillMaxWidth().padding(24.dp)) }
                is Loadable.Failed -> item { Text(state.message, color = MaterialTheme.colorScheme.error) }
                is Loadable.Ready -> {
                    item {
                        SectionHeader("Places", supporting = "Shared by the household", trailing = {
                            FilledTonalButton(onClick = { editing = Editing.LocationEdit(null) }) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("New", modifier = Modifier.padding(start = 6.dp))
                            }
                        })
                    }
                    item {
                        NexusCard {
                            if (state.value.locations.isEmpty()) Text("No places yet — Fridge, Freezer, Pantry…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            state.value.locations.forEachIndexed { index, location ->
                                if (index > 0) HorizontalDivider(Modifier.padding(vertical = 2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Text(location.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).padding(start = 12.dp))
                                    IconButton(onClick = { viewModel.moveLocation(location, -1) { app.changed() } }, enabled = index > 0) {
                                        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Move ${location.name} up")
                                    }
                                    IconButton(onClick = { viewModel.moveLocation(location, 1) { app.changed() } }, enabled = index < state.value.locations.lastIndex) {
                                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move ${location.name} down")
                                    }
                                    IconButton(onClick = { editing = Editing.LocationEdit(location) }) { Icon(Icons.Rounded.Edit, contentDescription = "Edit ${location.name}") }
                                }
                            }
                        }
                    }
                    item {
                        SectionHeader("Categories", supporting = "Shared by the household", trailing = {
                            FilledTonalButton(onClick = { editing = Editing.CategoryEdit(null) }) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("New", modifier = Modifier.padding(start = 6.dp))
                            }
                        })
                    }
                    item {
                        NexusCard {
                            state.value.categories.forEachIndexed { index, category ->
                                if (index > 0) HorizontalDivider(Modifier.padding(vertical = 2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                                    CategoryAvatar(CategoryRef(category.name, category.color, category.icon), size = 36.dp)
                                    Text(category.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).padding(start = 12.dp))
                                    IconButton(onClick = { editing = Editing.CategoryEdit(category) }) { Icon(Icons.Rounded.Edit, contentDescription = "Edit ${category.name}") }
                                }
                            }
                        }
                    }
                }
            }

            item { SectionHeader("Appearance") }
            item {
                AppearanceCard(
                    themeMode = settings.themeMode,
                    onThemeMode = app::setThemeMode,
                    dynamicColor = settings.dynamicColor,
                    onDynamicColor = app::setDynamicColor,
                    appColour = "Annona gold",
                )
            }
            item { SectionHeader("Account") }
            item { AccountCard("Annona", (auth as? AuthState.SignedIn)?.email, onSignOut = app::signOut) }
            item { AboutItem(viewModel.config) }
        }
    }

    when (val current = editing) {
        is Editing.CategoryEdit -> CategorySheet(
            category = current.category,
            onSave = { name, color, icon -> viewModel.saveCategory(current.category?.id, name, color, icon) { app.changed() }; editing = null },
            onDelete = current.category?.let { category -> { viewModel.deleteCategory(category.id) { app.changed() }; editing = null } },
            onDismiss = { editing = null },
        )
        is Editing.LocationEdit -> LocationSheet(
            location = current.location,
            onSave = { name -> viewModel.saveLocation(current.location?.id, name) { app.changed() }; editing = null },
            onDelete = current.location?.let { location -> { viewModel.deleteLocation(location.id) { app.changed() }; editing = null } },
            onDismiss = { editing = null },
        )
        null -> Unit
    }
}

@Composable
private fun SliderRow(label: String, value: String, current: Int, range: IntRange, onChange: (Int) -> Unit) {
    Column(Modifier.padding(top = 12.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = current.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = range.last - range.first - 1,
        )
    }
}

@Composable
private fun CategorySheet(category: Category?, onSave: (String, String?, String?) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(category?.name.orEmpty()) }
    var color by remember { mutableStateOf(category?.color ?: "amber") }
    var icon by remember { mutableStateOf(category?.icon ?: "package") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 24.dp).navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CategoryAvatar(CategoryRef(name, color, icon))
                Text(if (category == null) "New category" else "Edit category", style = MaterialTheme.typography.headlineSmall)
            }
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth())
            Text("Colour", style = MaterialTheme.typography.labelLarge)
            CategoryColors.chunked(7).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { option ->
                        Surface(
                            onClick = { color = option.key },
                            shape = CircleShape,
                            color = option.color,
                            modifier = Modifier.size(36.dp).then(
                                if (option.key == color) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier,
                            ),
                        ) {
                            if (option.key == color) Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Check, contentDescription = option.label, tint = MaterialTheme.colorScheme.surface) }
                        }
                    }
                }
            }
            Text("Icon", style = MaterialTheme.typography.labelLarge)
            CategoryIcons.chunked(6).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { option ->
                        FilledIconToggleButton(checked = option.key == icon, onCheckedChange = { icon = option.key }) {
                            Icon(option.icon, contentDescription = option.label)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = { onSave(name.trim(), color, icon) }, enabled = name.isNotBlank()) { Text("Save") }
                }
            }
        }
    }
}

@Composable
private fun LocationSheet(location: StorageLocation?, onSave: (String) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(location?.name.orEmpty()) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 24.dp).navigationBarsPadding().imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (location == null) "New place" else "Edit place", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, placeholder = { Text("Fridge") }, singleLine = true, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth())
            if (onDelete != null) {
                Text("Deleting a place keeps its items; they move to “No location”.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text("Save") }
                }
            }
        }
    }
}


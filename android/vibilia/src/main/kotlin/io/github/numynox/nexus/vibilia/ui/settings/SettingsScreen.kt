package io.github.numynox.nexus.vibilia.ui.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.numynox.nexus.core.data.AuthState
import io.github.numynox.nexus.core.ui.NexusFormat
import io.github.numynox.nexus.core.ui.components.ConnectedChoiceRow
import io.github.numynox.nexus.core.ui.components.NexusCard
import io.github.numynox.nexus.core.ui.components.SectionHeader
import io.github.numynox.nexus.core.ui.components.UpdateBanner
import io.github.numynox.nexus.core.ui.theme.ThemeMode
import io.github.numynox.nexus.vibilia.BuildConfig
import io.github.numynox.nexus.vibilia.data.FuelTypes
import io.github.numynox.nexus.vibilia.ui.AppViewModel
import io.github.numynox.nexus.vibilia.ui.common.PlateBadge
import io.github.numynox.nexus.vibilia.ui.common.containerViewModel
import io.github.numynox.nexus.vibilia.ui.common.valueOrNull

@Composable
fun SettingsScreen(app: AppViewModel, onOpenVehicle: (String?) -> Unit) {
    val viewModel = containerViewModel { SettingsViewModel(it) }
    val settings by app.settings.collectAsStateWithLifecycle()
    val cars by app.cars.collectAsStateWithLifecycle()
    val auth by app.auth.collectAsStateWithLifecycle()
    val update by app.update.collectAsStateWithLifecycle()
    val links by viewModel.links.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    val bluetoothLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.setRecording(true)
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item { UpdateBanner(update) }

            item {
                SectionHeader("Vehicles", trailing = {
                    FilledTonalButton(onClick = { onOpenVehicle(null) }) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Add", modifier = Modifier.padding(start = 6.dp))
                    }
                })
            }
            items(cars.valueOrNull.orEmpty(), key = { it.id }) { car ->
                val linked = links.filter { it.carId == car.id }
                NexusCard(onClick = { onOpenVehicle(car.id) }, modifier = Modifier.animateItem()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(
                            Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.DirectionsCar, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(car.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                listOfNotNull(car.description.ifBlank { null }, car.tankCapacity?.let { "${NexusFormat.liters(it, 0)} tank" })
                                    .joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (linked.isNotEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Rounded.Bluetooth, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
                                    Text(
                                        linked.joinToString { it.name ?: it.address },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.tertiary,
                                    )
                                }
                            }
                        }
                        car.plate?.takeIf { it.isNotBlank() }?.let { PlateBadge(it) }
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item { SectionHeader("Fuel prices") }
            item {
                NexusCard {
                    Text("Fuel type", style = MaterialTheme.typography.titleSmall)
                    ConnectedChoiceRow(
                        options = FuelTypes,
                        selected = settings.fuelType,
                        onSelect = app::setFuelType,
                        label = { it },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Previous days in the price chart", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text("${settings.priceHistoryDays}", style = MaterialTheme.typography.titleMedium)
                    }
                    Slider(
                        value = settings.priceHistoryDays.toFloat(),
                        onValueChange = { app.setPriceHistoryDays(it.toInt()) },
                        valueRange = 1f..7f,
                        steps = 5,
                    )
                }
            }

            item { SectionHeader("Drive tracking") }
            item {
                NexusCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Record drives on this phone", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "While this phone is connected to a linked car's Bluetooth, Vibilia notes the time. " +
                                    "That teaches each car how far and how much fuel an hour of driving is, for a better fuel estimate " +
                                    "and statistics on the days you actually drove.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = settings.trackDrives && viewModel.hasBluetoothPermission,
                            onCheckedChange = { enable ->
                                val permission = viewModel.bluetoothPermission
                                when {
                                    !enable -> viewModel.setRecording(false)
                                    permission != null && !viewModel.hasBluetoothPermission -> bluetoothLauncher.launch(permission)
                                    else -> viewModel.setRecording(true)
                                }
                            },
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                    if (links.isEmpty()) {
                        Text(
                            "No car is linked yet — open a vehicle above and link its Bluetooth.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }

            item { SectionHeader("Appearance") }
            item {
                NexusCard {
                    SettingRow(Icons.Rounded.Palette, "Theme")
                    ConnectedChoiceRow(
                        options = ThemeMode.entries,
                        selected = settings.themeMode,
                        onSelect = app::setThemeMode,
                        label = { it.name },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Wallpaper colours", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Use your wallpaper's palette instead of Vibilia red.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(checked = settings.dynamicColor, onCheckedChange = app::setDynamicColor)
                        }
                    }
                }
            }

            item { SectionHeader("Account") }
            item {
                NexusCard {
                    Text(
                        (auth as? AuthState.SignedIn)?.email ?: "Signed in",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        "The same account signs you in to Noctua and Annona.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = app::signOut, modifier = Modifier.padding(top = 12.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Sign out", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }

            item {
                ListItem(
                    headlineContent = { Text("Vibilia ${viewModel.versionName}") },
                    supportingContent = { Text("Installed from GitHub Releases") },
                    leadingContent = { Icon(Icons.Rounded.Info, contentDescription = null) },
                    trailingContent = {
                        TextButton(onClick = { uriHandler.openUri("https://github.com/${BuildConfig.RELEASE_REPOSITORY}/releases") }) {
                            Icon(Icons.Rounded.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Releases", modifier = Modifier.padding(start = 6.dp))
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                )
            }
        }
    }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, style = MaterialTheme.typography.titleSmall)
    }
}

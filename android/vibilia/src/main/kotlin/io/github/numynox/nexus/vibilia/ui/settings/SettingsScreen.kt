package io.github.numynox.nexus.vibilia.ui.settings

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.numynox.nexus.core.data.AuthState
import io.github.numynox.nexus.core.ui.NexusFormat
import io.github.numynox.nexus.core.ui.components.ConnectedChoiceRow
import io.github.numynox.nexus.core.ui.components.NexusCard
import io.github.numynox.nexus.core.ui.components.SectionHeader
import io.github.numynox.nexus.core.ui.components.UpdateBanner
import io.github.numynox.nexus.core.ui.settings.AboutItem
import io.github.numynox.nexus.core.ui.settings.AccountCard
import io.github.numynox.nexus.core.ui.settings.AppearanceCard
import io.github.numynox.nexus.core.ui.settings.SettingSwitch
import io.github.numynox.nexus.vibilia.data.FuelTypes
import io.github.numynox.nexus.vibilia.ui.AppViewModel
import io.github.numynox.nexus.vibilia.ui.common.PlateBadge
import io.github.numynox.nexus.vibilia.ui.common.containerViewModel
import io.github.numynox.nexus.core.ui.valueOrNull

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
    val context = LocalContext.current
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) app.setRefuelRemindersOn(true)
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

            item { SectionHeader("Reminders") }
            item {
                NexusCard {
                    SettingSwitch(
                        title = "Remind me to refuel",
                        description = "When a tank runs low, this phone tells you where and when to refuel: as prices reach their daily low, or shortly before. Only this phone.",
                        checked = settings.refuelRemindersOn,
                        onCheckedChange = { on ->
                            val allowed = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                            if (on && !allowed) notifications.launch(Manifest.permission.POST_NOTIFICATIONS) else app.setRefuelRemindersOn(on)
                        },
                    )
                    if (settings.refuelRemindersOn) {
                        SliderRow(
                            label = "Low below",
                            value = "${settings.refuelReminderPercent} %",
                            current = settings.refuelReminderPercent,
                            range = 10..40,
                            step = 5,
                            onChange = app::setRefuelReminderPercent,
                        )
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
                    appColour = "Vibilia red",
                )
            }

            item { SectionHeader("Account") }
            item { AccountCard("Vibilia", (auth as? AuthState.SignedIn)?.email, onSignOut = app::signOut) }

            item { AboutItem(viewModel.config) }
        }
    }
}

@Composable
private fun SliderRow(label: String, value: String, current: Int, range: IntRange, onChange: (Int) -> Unit, step: Int = 1) {
    Column(Modifier.padding(top = 12.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = current.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first) / step - 1,
        )
    }
}

package io.github.numynox.nexus.vibilia.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.numynox.nexus.core.ui.components.LoadingState
import io.github.numynox.nexus.core.ui.components.NexusCard
import io.github.numynox.nexus.core.ui.components.SectionHeader
import io.github.numynox.nexus.vibilia.tracking.PairedDevice
import io.github.numynox.nexus.vibilia.ui.AppViewModel
import io.github.numynox.nexus.core.ui.Loadable
import io.github.numynox.nexus.vibilia.ui.common.containerViewModel
import io.github.numynox.nexus.vibilia.ui.logbook.FormError

@Composable
fun VehicleEditorScreen(app: AppViewModel, carId: String?, onBack: () -> Unit) {
    val viewModel = containerViewModel(key = "vehicle-$carId") { VehicleEditorViewModel(it, carId) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pickDevice by remember { mutableStateOf(false) }

    val bluetoothLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pickDevice = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isNew) "New vehicle" else viewModel.car?.name ?: "Vehicle") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    Button(
                        onClick = { viewModel.save { app.carsChanged(); onBack() } },
                        enabled = !viewModel.saving && !viewModel.loading,
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        if (viewModel.saving) LoadingIndicator(Modifier.size(22.dp), color = MaterialTheme.colorScheme.onPrimary) else Text("Save")
                    }
                },
            )
        },
    ) { padding ->
        if (viewModel.loading) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item { DetailsCard(viewModel) }
            if (!viewModel.isNew) {
                item { BluetoothCard(viewModel, onLink = {
                    val permission = viewModel.bluetoothPermission
                    if (permission != null && !viewModel.hasBluetoothPermission()) bluetoothLauncher.launch(permission) else pickDevice = true
                }) }
                item { MembersCard(viewModel) }
                item {
                    OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text("Delete vehicle", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
            title = { Text("Delete ${viewModel.car?.name ?: "vehicle"}?") },
            text = { Text("All refuels, expenses and drives of this vehicle are deleted too, for everyone it is shared with.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete { app.carsChanged(); onBack() }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }

    if (pickDevice) {
        DevicePicker(
            devices = remember { viewModel.pairedDevices() },
            linked = viewModel.links.map { it.address }.toSet(),
            onPick = { device ->
                pickDevice = false
                viewModel.link(device)
            },
            onDismiss = { pickDevice = false },
        )
    }
}

@Composable
private fun DetailsCard(viewModel: VehicleEditorViewModel) {
    NexusCard {
        SectionHeader("Details")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(viewModel.name, { viewModel.name = it }, "Name", error = viewModel.nameError, capitalize = true)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Field(viewModel.tank, { viewModel.tank = it }, "Tank (L)", Modifier.weight(1f), error = viewModel.tankError, keyboard = KeyboardType.Decimal)
                Field(viewModel.plate, { viewModel.plate = it.uppercase() }, "Plate", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Field(viewModel.make, { viewModel.make = it }, "Make", Modifier.weight(1f), capitalize = true)
                Field(viewModel.model, { viewModel.model = it }, "Model", Modifier.weight(1f), capitalize = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Field(viewModel.year, { viewModel.year = it.filter(Char::isDigit).take(4) }, "Year", Modifier.weight(1f), error = viewModel.yearError, keyboard = KeyboardType.Number)
                Field(viewModel.vin, { viewModel.vin = it.uppercase() }, "VIN", Modifier.weight(2f))
            }
            FormError(viewModel.error)
        }
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    error: String? = null,
    keyboard: KeyboardType = KeyboardType.Text,
    capitalize: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboard,
            capitalization = if (capitalize) KeyboardCapitalization.Words else KeyboardCapitalization.None,
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier,
    )
}

/**
 * Linking a car's Bluetooth, the Android-only half of drive tracking. The link
 * is stored with the car, so every member's phone recognises it.
 */
@Composable
private fun BluetoothCard(viewModel: VehicleEditorViewModel, onLink: () -> Unit) {
    NexusCard {
        SectionHeader(
            "Bluetooth",
            supporting = "Connecting to it means driving: refuels preselect this car, and drives are recorded if tracking is on.",
        )
        viewModel.links.forEach { link ->
            ListItem(
                headlineContent = { Text(link.name ?: "Unnamed device") },
                supportingContent = { Text(link.address) },
                leadingContent = { Icon(Icons.Rounded.Bluetooth, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
                trailingContent = {
                    IconButton(onClick = { viewModel.unlink(link.address) }) {
                        Icon(Icons.Rounded.LinkOff, contentDescription = "Unlink")
                    }
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            )
        }
        FilledTonalButton(onClick = onLink, modifier = Modifier.padding(top = 4.dp)) {
            Icon(Icons.Rounded.BluetoothSearching, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(if (viewModel.links.isEmpty()) "Link car Bluetooth" else "Link another device", modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun DevicePicker(devices: List<PairedDevice>, linked: Set<String>, onPick: (PairedDevice) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Bluetooth, contentDescription = null) },
        title = { Text("Which device is the car?") },
        text = {
            if (devices.isEmpty()) {
                Text("No paired devices. Pair the phone with the car in Android's Bluetooth settings first.")
            } else {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(devices.size) { index ->
                        val device = devices[index]
                        val already = device.address in linked
                        ListItem(
                            headlineContent = { Text(device.name) },
                            supportingContent = { Text(if (already) "Linked" else device.address) },
                            leadingContent = {
                                Icon(if (device.isCarAudio) Icons.Rounded.DirectionsCar else Icons.Rounded.Speaker, contentDescription = null)
                            },
                            modifier = Modifier.then(if (already) Modifier else Modifier.clickableItem { onPick(device) }),
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private fun Modifier.clickableItem(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)

@Composable
private fun MembersCard(viewModel: VehicleEditorViewModel) {
    var email by remember { mutableStateOf("") }
    NexusCard {
        SectionHeader("Shared with", supporting = "Everyone listed can see and edit this vehicle and its log.")
        when (val members = viewModel.members) {
            Loadable.Loading -> LoadingIndicator()
            is Loadable.Failed -> Text(members.message, color = MaterialTheme.colorScheme.error)
            is Loadable.Ready -> members.value.forEach { member ->
                ListItem(
                    headlineContent = { Text(member.displayName) },
                    supportingContent = { Text(if (member.isOwner) "Owner" else member.email ?: "Member") },
                    leadingContent = { Icon(Icons.Rounded.Person, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Share by email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.weight(1f),
            )
            FilledTonalButton(onClick = { viewModel.share(email); email = "" }, enabled = email.isNotBlank()) {
                Icon(Icons.Rounded.PersonAdd, contentDescription = "Share")
            }
        }
        viewModel.shareMessage?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

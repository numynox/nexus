package io.github.numynox.nexus.abeona.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.numynox.nexus.abeona.data.AlertSettings
import io.github.numynox.nexus.abeona.data.BahnCard
import io.github.numynox.nexus.abeona.data.STATION_MINUTES_CHOICES
import io.github.numynox.nexus.abeona.live.TripLocation
import io.github.numynox.nexus.abeona.data.HouseholdMember
import io.github.numynox.nexus.abeona.data.Traveller
import io.github.numynox.nexus.abeona.ui.AppViewModel
import io.github.numynox.nexus.abeona.ui.Navigator
import io.github.numynox.nexus.abeona.ui.bahncards.BahnCardRow
import io.github.numynox.nexus.abeona.ui.bahncards.BahnCardSheet
import io.github.numynox.nexus.abeona.ui.common.DbNavigator
import io.github.numynox.nexus.abeona.ui.common.containerViewModel
import io.github.numynox.nexus.core.data.AuthState
import io.github.numynox.nexus.core.ui.Loadable
import io.github.numynox.nexus.core.ui.components.LoadingState
import io.github.numynox.nexus.core.ui.components.NexusCard
import io.github.numynox.nexus.core.ui.components.SectionHeader
import io.github.numynox.nexus.core.ui.components.UpdateBanner
import io.github.numynox.nexus.core.ui.settings.AboutItem
import io.github.numynox.nexus.core.ui.settings.AccountCard
import io.github.numynox.nexus.core.ui.settings.AppearanceCard
import io.github.numynox.nexus.core.ui.settings.SettingLabel
import io.github.numynox.nexus.core.ui.settings.SettingSwitch

private sealed interface Editing {
    data object Invite : Editing
    data class TravellerEdit(val traveller: Traveller?) : Editing
    data class CardEdit(val traveller: Traveller, val card: BahnCard?) : Editing
    data object Import : Editing
}

@Composable
fun SettingsScreen(app: AppViewModel, navigator: Navigator) {
    val application = LocalContext.current.applicationContext as android.app.Application
    val viewModel = containerViewModel { SettingsViewModel(it, application) }
    val settings by app.settings.collectAsStateWithLifecycle()
    val auth by app.auth.collectAsStateWithLifecycle()
    val update by app.update.collectAsStateWithLifecycle()
    val household by viewModel.household.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<Editing?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) app.setLiveCompanion(true)
    }
    val reminderPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) app.setBahnCardReminder(true)
    }
    val claimPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) app.setClaimReminder(true)
    }
    val leavePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) app.setLeaveReminder(true)
    }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        app.setTripLocation(granted.values.any { it })
    }
    val location = remember { TripLocation(context) }
    fun canNotify() = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    LaunchedEffect(viewModel) { viewModel.load() }
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item { UpdateBanner(update) }

            item { SectionHeader("On the day") }
            item {
                NexusCard {
                    SettingSwitch(
                        title = "Start the live companion by itself",
                        description = "Half an hour before a trip, this phone starts following it: platform, delay and your changes in a notification, with an alert when something changes. Only this phone.",
                        checked = settings.liveCompanion,
                        onCheckedChange = { on ->
                            val allowed = Build.VERSION.SDK_INT < 33 ||
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                            if (on && !allowed) notifications.launch(Manifest.permission.POST_NOTIFICATIONS) else app.setLiveCompanion(on)
                        },
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text("Time to the station", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "From your door to the platform, in minutes. On the day, the trip says when to leave.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ChoiceChips(STATION_MINUTES_CHOICES, settings.stationMinutes, { app.setStationMinutes(it) }) { "$it min" }
                    SettingSwitch(
                        title = "Time to leave reminder",
                        description = "A notification when it is time to leave for the next trip, moved if the train is late. Only this phone.",
                        checked = settings.leaveReminder,
                        onCheckedChange = { on -> if (on && !canNotify()) leavePermission.launch(Manifest.permission.POST_NOTIFICATIONS) else app.setLeaveReminder(on) },
                    )
                    SettingSwitch(
                        title = "Use location during a trip",
                        description = "While the live companion runs, your position tells it whether you are at the station, on board or have missed a change. It never leaves this phone.",
                        checked = settings.tripLocation && location.hasPermission(),
                        onCheckedChange = { on -> if (on && !location.hasPermission()) locationPermission.launch(TripLocation.PERMISSIONS) else app.setTripLocation(on) },
                    )
                }
            }

            item { SectionHeader("Alerts", supporting = "What the live companion tells you at once") }
            item {
                NexusCard {
                    val alerts = settings.alerts
                    Text("Delays", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "When your train is this late, and again each time it is as much later.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ChoiceChips(AlertSettings.DELAY_CHOICES, alerts.delayMinutes, { app.setAlerts(alerts.copy(delayMinutes = it)) }) {
                        if (it == 0) "Off" else "$it min"
                    }
                    SettingSwitch(
                        title = "Connection at risk",
                        description = "A change gets too tight to make, or it looks like you missed it.",
                        checked = alerts.connectionAtRisk,
                        onCheckedChange = { app.setAlerts(alerts.copy(connectionAtRisk = it)) },
                    )
                    SettingSwitch(
                        title = "Platform change",
                        description = "Your train leaves from another platform.",
                        checked = alerts.platformChange,
                        onCheckedChange = { app.setAlerts(alerts.copy(platformChange = it)) },
                    )
                    SettingSwitch(
                        title = "Cancellation",
                        description = "A train on your trip is cancelled.",
                        checked = alerts.cancellation,
                        onCheckedChange = { app.setAlerts(alerts.copy(cancellation = it)) },
                    )
                }
            }

            when (val state = household) {
                Loadable.Loading -> item { LoadingState(Modifier.fillMaxWidth().padding(24.dp)) }
                is Loadable.Failed -> item { Text(state.message, color = MaterialTheme.colorScheme.error) }
                is Loadable.Ready -> {
                    item {
                        SectionHeader("Household", supporting = "Shares every trip and ticket", trailing = {
                            FilledTonalButton(onClick = { editing = Editing.Invite }) {
                                Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("Invite", modifier = Modifier.padding(start = 6.dp))
                            }
                        })
                    }
                    item {
                        NexusCard {
                            state.value.members.forEachIndexed { index, member ->
                                if (index > 0) HorizontalDivider(Modifier.padding(vertical = 6.dp))
                                MemberRow(member, isMe = member.userId == viewModel.userId)
                            }
                            if (state.value.members.size > 1) {
                                OutlinedButton(onClick = { confirmLeave = true }, modifier = Modifier.padding(top = 12.dp)) {
                                    Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Text("Leave household", modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                    }
                    item {
                        SectionHeader("Travellers", supporting = "Everyone who rides, with or without an account", trailing = {
                            FilledTonalButton(onClick = { editing = Editing.TravellerEdit(null) }) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("Add", modifier = Modifier.padding(start = 6.dp))
                            }
                        })
                    }
                    item {
                        NexusCard {
                            if (state.value.travellers.isEmpty()) Text("Nobody yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            state.value.travellers.forEachIndexed { index, traveller ->
                                if (index > 0) HorizontalDivider(Modifier.padding(vertical = 2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Initial(traveller.name)
                                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                        Text(traveller.name, style = MaterialTheme.typography.titleSmall)
                                        if (traveller.userId != null) {
                                            Text("Has an account", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    IconButton(onClick = { editing = Editing.TravellerEdit(traveller) }) {
                                        Icon(Icons.Rounded.Edit, contentDescription = "Edit ${traveller.name}")
                                    }
                                }
                            }
                        }
                    }
                    item {
                        SectionHeader("BahnCards", supporting = "Checked against your trips; only DB Navigator's counts as proof")
                    }
                    item {
                        NexusCard {
                            state.value.travellers.forEachIndexed { index, traveller ->
                                if (index > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(traveller.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                    TextButton(onClick = { editing = Editing.CardEdit(traveller, null) }) {
                                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Text("Add BahnCard", modifier = Modifier.padding(start = 6.dp))
                                    }
                                }
                                val cards = state.value.cardsOf(traveller)
                                if (cards.isEmpty()) {
                                    Text("No BahnCard", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                cards.forEach { card ->
                                    BahnCardRow(
                                        card,
                                        onShow = { navigator.bahnCard(card.id) },
                                        onEdit = { editing = Editing.CardEdit(traveller, card) },
                                        onDbNavigator = { DbNavigator.open(context) },
                                    )
                                }
                            }
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            SettingSwitch(
                                title = "Remind me before a card renews",
                                description = "Two weeks, one week, three days and a day before a subscription's cancellation deadline. Only this phone.",
                                checked = settings.bahnCardReminder,
                                onCheckedChange = { on ->
                                    if (on && !canNotify()) reminderPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else app.setBahnCardReminder(on)
                                },
                            )
                        }
                    }
                    item { SectionHeader("Compensation", supporting = "When a trip arrives an hour or more late") }
                    item {
                        NexusCard {
                            Row(
                                Modifier.fillMaxWidth().clickable { navigator.claims() }.padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("Claims", style = MaterialTheme.typography.titleSmall)
                                    Text("What to claim, what DB still owes, what came back", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                            }
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            SettingSwitch(
                                title = "Remind me to claim",
                                description = "The day after a late trip, and four weeks, a week and a day before the three-month deadline. Only this phone.",
                                checked = settings.claimReminder,
                                onCheckedChange = { on ->
                                    if (on && !canNotify()) claimPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else app.setClaimReminder(on)
                                },
                            )
                        }
                    }
                }
            }

            item { SectionHeader("Past trips", supporting = "For the history and the year in review") }
            item {
                NexusCard {
                    Row(
                        Modifier.fillMaxWidth().clickable { editing = Editing.Import }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Import from Träwelling", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Your check-ins, with their real times. Trips of the last month can also be saved from Search.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
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
                    appColour = "Abeona green",
                )
            }
            item { SectionHeader("Account") }
            item { AccountCard("Abeona", (auth as? AuthState.SignedIn)?.email, onSignOut = app::signOut) }
            item {
                NexusCard {
                    SettingLabel(Icons.Rounded.Map, "Data sources")
                    Text(
                        "Timetable data: Transitous (transitous.org), © OpenStreetMap contributors, and the respective transit operators.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            item { AboutItem(viewModel.config) }
        }
    }

    when (val current = editing) {
        Editing.Invite -> TextSheet(
            title = "Invite to household",
            help = "They need a Nexus account. If they have trips of their own and nobody else in their household, those come along.",
            label = "Email",
            initial = "",
            keyboard = KeyboardType.Email,
            confirm = "Invite",
            onSave = { viewModel.invite(it); editing = null },
            onDelete = null,
            onDismiss = { editing = null },
        )
        is Editing.TravellerEdit -> TextSheet(
            title = if (current.traveller == null) "New traveller" else "Edit traveller",
            help = "Travellers are who rides: you, and children or partners without an account.",
            label = "Name",
            initial = current.traveller?.name.orEmpty(),
            keyboard = KeyboardType.Text,
            confirm = "Save",
            onSave = { name ->
                current.traveller?.let { viewModel.renameTraveller(it.id, name) } ?: viewModel.addTraveller(name)
                editing = null
            },
            onDelete = current.traveller?.takeIf { it.userId == null }?.let { traveller -> { viewModel.deleteTraveller(traveller.id); editing = null } },
            onDismiss = { editing = null },
        )
        is Editing.CardEdit -> BahnCardSheet(
            traveller = current.traveller,
            card = current.card,
            onSave = { input, file, removeFile ->
                viewModel.saveCard(input, current.card?.id, file, removeFile)
                editing = null
            },
            onDelete = current.card?.let { card -> { viewModel.deleteCard(card); editing = null } },
            onDismiss = { editing = null },
        )
        Editing.Import -> ImportSheet(
            onImported = { count -> app.changed(if (count == 1) "1 trip imported" else "$count trips imported") },
            onDismiss = { editing = null },
        )
        null -> Unit
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Leave the household?") },
            text = { Text("The trips stay with the others. You start again with a household of your own.") },
            confirmButton = {
                TextButton(onClick = { confirmLeave = false; viewModel.leave { app.changed() } }) {
                    Text("Leave", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Stay") } },
        )
    }
}

@Composable
private fun MemberRow(member: HouseholdMember, isMe: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Initial(member.displayName)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(member.displayName + if (isMe) " (you)" else "", style = MaterialTheme.typography.titleSmall)
            member.email?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (member.role == "owner") Text("Owner", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun Initial(name: String) {
    Box(
        Modifier.size(36.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.take(1).uppercase(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
private fun TextSheet(
    title: String,
    help: String,
    label: String,
    initial: String,
    keyboard: KeyboardType,
    confirm: String,
    onSave: (String) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 24.dp).navigationBarsPadding().imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(help, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = { onSave(value.trim()) }, enabled = value.isNotBlank()) { Text(confirm) }
                }
            }
        }
    }
}

/** A short list of minutes as chips that wrap: seven choices do not fit one connected row. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ChoiceChips(options: List<Int>, selected: Int, onSelect: (Int) -> Unit, label: (Int) -> String) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
    ) {
        options.forEach { option ->
            androidx.compose.material3.FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
            )
        }
    }
}

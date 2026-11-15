package io.github.numynox.nexus.abeona.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Euro
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.numynox.nexus.abeona.data.AlertSettings
import io.github.numynox.nexus.abeona.data.BahnCard
import io.github.numynox.nexus.abeona.data.HouseholdMember
import io.github.numynox.nexus.abeona.data.STATION_MINUTES_CHOICES
import io.github.numynox.nexus.abeona.live.TripLocation
import io.github.numynox.nexus.abeona.ui.AbeonaType
import io.github.numynox.nexus.abeona.ui.AppViewModel
import io.github.numynox.nexus.abeona.ui.Navigator
import io.github.numynox.nexus.abeona.ui.bad
import io.github.numynox.nexus.abeona.ui.brand
import io.github.numynox.nexus.abeona.ui.hairline
import io.github.numynox.nexus.abeona.ui.muted
import io.github.numynox.nexus.abeona.ui.well
import io.github.numynox.nexus.abeona.ui.bahncards.BahnCardRow
import io.github.numynox.nexus.abeona.ui.bahncards.BahnCardSheet
import io.github.numynox.nexus.abeona.ui.common.Banner
import io.github.numynox.nexus.abeona.ui.common.FieldRow
import io.github.numynox.nexus.abeona.ui.common.PrimaryButton
import io.github.numynox.nexus.abeona.ui.common.Segmented
import io.github.numynox.nexus.abeona.ui.common.StatusChip
import io.github.numynox.nexus.abeona.ui.common.StatusTone
import io.github.numynox.nexus.abeona.ui.common.SurfaceCard
import io.github.numynox.nexus.abeona.ui.common.WellField
import io.github.numynox.nexus.abeona.ui.common.containerViewModel
import io.github.numynox.nexus.core.data.AuthState
import io.github.numynox.nexus.core.data.NexusConfig
import io.github.numynox.nexus.core.ui.Loadable
import io.github.numynox.nexus.core.ui.components.DownloadIndicator
import io.github.numynox.nexus.core.ui.components.LoadingState
import io.github.numynox.nexus.core.ui.components.downloadingLabel
import io.github.numynox.nexus.core.ui.components.rememberInstallUpdate
import io.github.numynox.nexus.core.ui.theme.ThemeMode
import io.github.numynox.nexus.core.update.AppUpdates

private sealed interface Editing {
    data object Invite : Editing
    data class CardEdit(val card: BahnCard?) : Editing
}

@Composable
fun SettingsScreen(app: AppViewModel, navigator: Navigator) {
    val application = LocalContext.current.applicationContext as android.app.Application
    val viewModel = containerViewModel { SettingsViewModel(it, application) }
    val settings by app.settings.collectAsStateWithLifecycle()
    val auth by app.auth.collectAsStateWithLifecycle()
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
        // Groups in the order of the moments (abeona-design.md §2, 14): before the trip, on the day, afterwards, the household, the app.
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "before") {
                Group("Before the trip") {
                    SettingLine(
                        "Time to the station",
                        Icons.AutoMirrored.Rounded.DirectionsWalk,
                        info = "From your door to the platform, in minutes. On the day, the trip says when to leave.",
                    ) {
                        ValueMenu(STATION_MINUTES_CHOICES, settings.stationMinutes, { app.setStationMinutes(it) }, "Time to the station") { "$it min" }
                    }
                    Hairline()
                    SwitchLine(
                        "Time to leave reminder",
                        Icons.Rounded.MeetingRoom,
                        info = "A notification when it is time to leave for the next trip, moved if the train is late. Only this phone.",
                        checked = settings.leaveReminder,
                    ) { on -> if (on && !canNotify()) leavePermission.launch(Manifest.permission.POST_NOTIFICATIONS) else app.setLeaveReminder(on) }
                }
            }

            item(key = "day") {
                Group("On the day") {
                    SwitchLine(
                        "Start the live companion by itself",
                        Icons.Rounded.Train,
                        info = "Before a trip (your time to the station plus a quarter of an hour, at least half an hour), this phone starts following it: when to leave, the platform, the next stop and your changes in a notification that updates every minute, with an alert when something changes. Opening a trip under way starts it too. Only this phone.",
                        checked = settings.liveCompanion,
                    ) { on -> if (on && !canNotify()) notifications.launch(Manifest.permission.POST_NOTIFICATIONS) else app.setLiveCompanion(on) }
                    Hairline()
                    SwitchLine(
                        "Use location during a trip",
                        Icons.Rounded.MyLocation,
                        info = "While the live companion runs, your position tells it whether you are at the station, on board or have missed a change. It never leaves this phone.",
                        checked = settings.tripLocation && location.hasPermission(),
                    ) { on -> if (on && !location.hasPermission()) locationPermission.launch(TripLocation.PERMISSIONS) else app.setTripLocation(on) }
                    Hairline()
                    val alerts = settings.alerts
                    SettingLine(
                        "Delay alert",
                        Icons.Rounded.Timelapse,
                        info = "What the live companion tells you at once: when your train is this late, and again each time it is as much later.",
                    ) {
                        ValueMenu(AlertSettings.DELAY_CHOICES, alerts.delayMinutes, { app.setAlerts(alerts.copy(delayMinutes = it)) }, "Delay alert") {
                            if (it == 0) "Off" else "$it min"
                        }
                    }
                    Hairline()
                    SwitchLine(
                        "Connection at risk",
                        Icons.Rounded.ErrorOutline,
                        info = "A change gets too tight to make, or it looks like you missed it.",
                        checked = alerts.connectionAtRisk,
                    ) { app.setAlerts(alerts.copy(connectionAtRisk = it)) }
                    Hairline()
                    // Obvious from the name: no info. "Your train leaves from another platform."
                    SwitchLine("Platform change", Icons.Rounded.NotificationsActive, checked = alerts.platformChange) {
                        app.setAlerts(alerts.copy(platformChange = it))
                    }
                    Hairline()
                    // "A train on your trip is cancelled."
                    SwitchLine("Cancellation", Icons.Rounded.Block, checked = alerts.cancellation) {
                        app.setAlerts(alerts.copy(cancellation = it))
                    }
                }
            }

            item(key = "after") {
                Group("Afterwards") {
                    SettingLine(
                        "Claims",
                        Icons.Rounded.Euro,
                        supporting = "What to claim, what DB still owes, what came back",
                        info = "Compensation is due when a trip arrives an hour or more late.",
                        onClick = navigator::claims,
                    ) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = muted) }
                    Hairline()
                    SwitchLine(
                        "Remind me to claim",
                        Icons.Rounded.Notifications,
                        info = "The day after a late trip, and four weeks, a week and a day before the three-month deadline. Only this phone.",
                        checked = settings.claimReminder,
                    ) { on -> if (on && !canNotify()) claimPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else app.setClaimReminder(on) }
                }
            }

            when (val state = household) {
                Loadable.Loading -> item(key = "household") { LoadingState(Modifier.fillMaxWidth().padding(24.dp)) }
                is Loadable.Failed -> item(key = "household") { Banner(StatusTone.Bad, Icons.Rounded.ErrorOutline, state.message) }
                is Loadable.Ready -> {
                    item(key = "household") {
                        Group("Household", info = "Everyone in the household shares every trip and ticket.") {
                            state.value.members.forEachIndexed { index, member ->
                                if (index > 0) Hairline()
                                MemberRow(member, isMe = member.userId == viewModel.userId)
                            }
                            Hairline()
                            SettingLine("Invite to household", Icons.Rounded.PersonAdd, nameColor = brand, onClick = { editing = Editing.Invite })
                            if (state.value.members.size > 1) {
                                Hairline()
                                SettingLine("Leave household", Icons.AutoMirrored.Rounded.Logout, nameColor = bad.soft.content, onClick = { confirmLeave = true })
                            }
                        }
                    }
                    item(key = "bahncards") {
                        SurfaceCard(padding = PaddingValues(vertical = 4.dp)) {
                            SettingLine(
                                "BahnCards",
                                Icons.Rounded.CreditCard,
                                info = "The household's cards. Only the one in DB Navigator counts as proof on the train.",
                            ) {
                                IconButton(onClick = { editing = Editing.CardEdit(null) }) {
                                    Icon(Icons.Rounded.Add, contentDescription = "Add a BahnCard", tint = brand)
                                }
                            }
                            val cards = state.value.cards
                            if (cards.isEmpty()) {
                                Text("No BahnCard yet", style = AbeonaType.meta, color = muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                            }
                            cards.forEach { card ->
                                Hairline()
                                BahnCardRow(
                                    card,
                                    onShow = { navigator.bahnCard(card.id) },
                                    onEdit = { editing = Editing.CardEdit(card) },
                                    modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                )
                            }
                            Hairline()
                            SwitchLine(
                                "Remind me before a card renews",
                                Icons.Rounded.Event,
                                info = "Two weeks, one week, three days and a day before a subscription's cancellation deadline. Only this phone.",
                                checked = settings.bahnCardReminder,
                            ) { on -> if (on && !canNotify()) reminderPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else app.setBahnCardReminder(on) }
                        }
                    }
                }
            }

            item(key = "app") {
                Group("App") {
                    SettingLine("Theme", Icons.Rounded.Palette) {
                        Segmented(ThemeMode.entries, settings.themeMode, app::setThemeMode, { it.name }, description = "Theme")
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Hairline()
                        SwitchLine(
                            "Wallpaper colours",
                            Icons.Rounded.Wallpaper,
                            info = "Use your wallpaper's palette instead of Abeona orange.",
                            checked = settings.dynamicColor,
                        ) { app.setDynamicColor(it) }
                    }
                    Hairline()
                    UpdateLine(viewModel.config, app.updates)
                    Hairline()
                    SettingLine(
                        (auth as? AuthState.SignedIn)?.email ?: "Signed in",
                        Icons.Rounded.Person,
                        info = "The same account signs you in to Noctua, Vibilia and Annona.",
                    ) {
                        TextButton(onClick = app::signOut) {
                            Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Sign out", modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                    Hairline()
                    SettingLine("Data sources", Icons.Rounded.Map)
                    Text(
                        "Timetable data: Transitous (transitous.org), © OpenStreetMap contributors, and the respective transit operators.",
                        style = AbeonaType.meta,
                        color = muted,
                        modifier = Modifier.padding(start = 48.dp, end = 16.dp, bottom = 12.dp),
                    )
                }
            }
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
        is Editing.CardEdit -> BahnCardSheet(
            card = current.card,
            onSave = { input, file, removeFile ->
                viewModel.saveCard(input, current.card?.id, file, removeFile)
                editing = null
            },
            onDelete = current.card?.let { card -> { viewModel.deleteCard(card); editing = null } },
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
                    Text("Leave", color = bad.soft.content)
                }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Stay") } },
        )
    }
}

@Composable
private fun MemberRow(member: HouseholdMember, isMe: Boolean) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Initial(member.displayName)
        Column(Modifier.weight(1f)) {
            Text(member.displayName + if (isMe) " (you)" else "", style = AbeonaType.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            member.email?.let { Text(it, style = AbeonaType.meta, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        if (member.role == "owner") StatusChip(StatusTone.Quiet, null, label = "Owner")
    }
}

@Composable
private fun Initial(name: String) {
    Box(Modifier.size(32.dp).background(well.container, CircleShape), contentAlignment = Alignment.Center) {
        Text(name.take(1).uppercase(), style = AbeonaType.meta.copy(fontWeight = FontWeight.SemiBold), color = well.content)
    }
}

/** A group: its title, and its lines in one card. [info] explains the group behind a small icon. */
@Composable
private fun Group(title: String, info: String? = null, content: @Composable ColumnScope.() -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.padding(start = 4.dp, top = 12.dp).heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = AbeonaType.title)
            if (info != null) {
                IconButton(onClick = { open = !open }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Info, contentDescription = "About $title", tint = muted, modifier = Modifier.size(18.dp))
                }
            }
        }
        if (info != null && open) Text(info, style = AbeonaType.meta, color = muted, modifier = Modifier.padding(horizontal = 4.dp))
        SurfaceCard(padding = PaddingValues(vertical = 4.dp), content = content)
    }
}

@Composable
private fun Hairline() = HorizontalDivider(color = hairline, modifier = Modifier.padding(horizontal = 16.dp))

/** A line in a settings card: icon, name, its value or action at the right. */
@Composable
private fun SettingLine(
    name: String,
    icon: ImageVector,
    supporting: String? = null,
    info: String? = null,
    nameColor: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
    control: @Composable RowScope.() -> Unit = {},
) = FieldRow(name, icon = icon, supporting = supporting, info = info, onClick = onClick, nameColor = nameColor, padding = 16.dp, control = control)

/** A setting that is on or off: the whole line toggles. */
@Composable
private fun SwitchLine(name: String, icon: ImageVector, info: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) =
    FieldRow(name, icon = icon, info = info, toggle = checked, onToggle = onChange, padding = 16.dp) {
        Switch(checked = checked, onCheckedChange = null)
    }

/** A choice of minutes as one value that opens a small menu, rather than rows of chips. */
@Composable
private fun ValueMenu(options: List<Int>, selected: Int, onSelect: (Int) -> Unit, description: String, label: (Int) -> String) {
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { open = true },
            color = well.container,
            contentColor = well.content,
            shape = CircleShape,
            modifier = Modifier.semantics { contentDescription = "$description: ${label(selected)}" },
        ) {
            Row(Modifier.height(32.dp).padding(start = 12.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label(selected), style = AbeonaType.meta.copy(fontWeight = FontWeight.SemiBold))
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option), style = AbeonaType.body) },
                    onClick = { open = false; onSelect(option) },
                    trailingIcon = if (option == selected) ({ Icon(Icons.Rounded.Check, contentDescription = "Chosen", tint = brand) }) else null,
                )
            }
        }
    }
}

/** The installed version and, when there is one, the newer release with its install button. */
@Composable
private fun UpdateLine(config: NexusConfig, updates: AppUpdates) {
    val update by updates.available.collectAsStateWithLifecycle()
    val download by updates.download.collectAsStateWithLifecycle(null)
    val install = rememberInstallUpdate(updates)
    val uriHandler = LocalUriHandler.current
    val current = download
    SettingLine(
        "${config.appName} ${config.versionName}",
        Icons.Rounded.SystemUpdate,
        supporting = when {
            update != null && current != null -> downloadingLabel(current)
            update != null -> "Version ${update?.versionName} available"
            else -> "Installed from GitHub Releases"
        },
    ) {
        when {
            update != null && current != null -> DownloadIndicator(current, Modifier.padding(horizontal = 12.dp))
            update != null -> TextButton(onClick = install) { Text("Install") }
            else -> TextButton(onClick = { uriHandler.openUri("https://github.com/${config.releaseRepository}/releases") }) { Text("Releases") }
        }
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
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding().imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = AbeonaType.headline)
                Text(help, style = AbeonaType.meta, color = muted)
            }
            WellField(value = value, onValueChange = { value = it }, placeholder = label, description = label, keyboard = keyboard, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete", color = bad.soft.content) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
                PrimaryButton(confirm, onClick = { onSave(value.trim()) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp), enabled = value.isNotBlank())
            }
        }
    }
}

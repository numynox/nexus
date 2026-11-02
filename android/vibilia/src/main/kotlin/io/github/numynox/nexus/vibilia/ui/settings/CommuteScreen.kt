package io.github.numynox.nexus.vibilia.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Surface
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import io.github.numynox.nexus.core.ui.settings.SettingSwitch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.numynox.nexus.core.data.describe
import io.github.numynox.nexus.core.ui.components.NexusCard
import io.github.numynox.nexus.core.ui.components.PlaceholderList
import io.github.numynox.nexus.core.ui.components.SectionHeader
import io.github.numynox.nexus.core.ui.valueOrNull
import io.github.numynox.nexus.vibilia.AppContainer
import io.github.numynox.nexus.vibilia.commute.CommuteAnnouncer
import io.github.numynox.nexus.vibilia.commute.CommuteDirection
import io.github.numynox.nexus.vibilia.commute.CommutePlan
import io.github.numynox.nexus.vibilia.data.Commute
import io.github.numynox.nexus.vibilia.data.CommuteExpectation
import io.github.numynox.nexus.vibilia.ui.AppViewModel
import io.github.numynox.nexus.vibilia.ui.common.containerViewModel
import io.github.numynox.nexus.vibilia.ui.logbook.FormError
import io.github.numynox.nexus.vibilia.ui.logbook.toDecimal
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val HourMinute: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Monday first, ISO numbers, as the table stores them. */
val CommuteWeekdays = listOf(1 to "Mo", 2 to "Tu", 3 to "We", 4 to "Th", 5 to "Fr", 6 to "Sa", 7 to "Su")

/** "Mo–Fr · 06:30–09:30 · 16:00–19:00", for the settings list. */
fun Commute.summary(): String {
    val days = weekdays.sorted()
    val dayText = if (days.size > 2 && days.zipWithNext().all { (a, b) -> b == a + 1 }) {
        "${CommuteWeekdays[days.first() - 1].second}–${CommuteWeekdays[days.last() - 1].second}"
    } else {
        days.joinToString(", ") { CommuteWeekdays[it - 1].second }
    }
    fun window(from: String, until: String) = "${from.take(5)}–${until.take(5)}"
    return "$dayText · ${window(toWorkFrom, toWorkUntil)} · ${window(toHomeFrom, toHomeUntil)}"
}

/**
 * Your commute in one car: weekdays, the windows each way, home and work.
 * Private to you, like on the web; the expected travel time is the median of
 * your own drives in each window.
 */
class CommuteViewModel(private val container: AppContainer, private val carId: String) : ViewModel() {
    var loading by mutableStateOf(true)
        private set
    var exists by mutableStateOf(false)
        private set
    var expectations by mutableStateOf<List<CommuteExpectation>>(emptyList())
        private set

    var weekdays by mutableStateOf(setOf(1, 2, 3, 4, 5))
    var toWorkFrom by mutableStateOf(LocalTime.of(6, 30))
    var toWorkUntil by mutableStateOf(LocalTime.of(9, 30))
    var toHomeFrom by mutableStateOf(LocalTime.of(16, 0))
    var toHomeUntil by mutableStateOf(LocalTime.of(19, 0))
    var homeLat by mutableStateOf("")
    var homeLng by mutableStateOf("")
    var homeRadius by mutableIntStateOf(300)
    var workLat by mutableStateOf("")
    var workLng by mutableStateOf("")
    var workRadius by mutableIntStateOf(300)
    var smsName by mutableStateOf("")
    var smsNumber by mutableStateOf("")
    var smsToWork by mutableStateOf(false)
    var smsToHome by mutableStateOf(false)

    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            val cached = container.preferences.cachedCommutes().firstOrNull { it.carId == carId }
            cached?.let(::fill)
            runCatching { container.commutes.commutes().firstOrNull { it.carId == carId } }
                .onSuccess { found -> found?.let(::fill) }
                .onFailure { if (cached == null) error = it.describe() }
            loading = false
            if (exists) loadExpectations()
        }
    }

    private fun fill(commute: Commute) {
        exists = true
        weekdays = commute.weekdays.toSet()
        CommutePlan.parseTime(commute.toWorkFrom)?.let { toWorkFrom = it }
        CommutePlan.parseTime(commute.toWorkUntil)?.let { toWorkUntil = it }
        CommutePlan.parseTime(commute.toHomeFrom)?.let { toHomeFrom = it }
        CommutePlan.parseTime(commute.toHomeUntil)?.let { toHomeUntil = it }
        homeLat = commute.homeLat.toString()
        homeLng = commute.homeLng.toString()
        homeRadius = commute.homeRadiusM
        workLat = commute.workLat.toString()
        workLng = commute.workLng.toString()
        workRadius = commute.workRadiusM
        smsName = commute.smsName.orEmpty()
        smsNumber = commute.smsNumber.orEmpty()
        smsToWork = commute.smsToWork
        smsToHome = commute.smsToHome
    }

    private suspend fun loadExpectations() {
        expectations = runCatching { container.commutes.expectation(carId) }.getOrDefault(emptyList())
    }

    fun toggle(day: Int) {
        weekdays = if (day in weekdays) weekdays - day else weekdays + day
    }

    /** Fills home or work with where the phone is now. */
    fun useHere(home: Boolean) {
        viewModelScope.launch {
            val here = container.locations.current()
            if (here == null) {
                error = "Could not get this phone's position."
                return@launch
            }
            val lat = "%.6f".format(Locale.ROOT, here.lat)
            val lng = "%.6f".format(Locale.ROOT, here.lng)
            if (home) { homeLat = lat; homeLng = lng } else { workLat = lat; workLng = lng }
            error = null
        }
    }

    /** A contact picked from the phone's address book. */
    fun setContact(name: String?, number: String) {
        smsName = name.orEmpty()
        smsNumber = CommutePlan.normalizeNumber(number) ?: number
    }

    fun travelText(direction: CommuteDirection): String {
        val expectation = expectations.firstOrNull { it.direction == direction.key }
        val seconds = expectation?.medianSeconds ?: return "No drive of yours in this window yet."
        val minutes = (seconds / 60.0).roundToInt()
        val drives = if (expectation.sampleCount == 1) "drive" else "drives"
        return "About $minutes min · median of ${expectation.sampleCount} $drives"
    }

    private fun draft(): Commute? {
        val hLat = homeLat.toDecimal()
        val hLng = homeLng.toDecimal()
        val wLat = workLat.toDecimal()
        val wLng = workLng.toDecimal()
        error = when {
            weekdays.isEmpty() -> "Pick at least one day."
            !toWorkFrom.isBefore(toWorkUntil) -> "The way to work must end after it starts."
            !toHomeFrom.isBefore(toHomeUntil) -> "The way home must end after it starts."
            hLat == null || hLng == null || hLat !in -90.0..90.0 || hLng !in -180.0..180.0 -> "Set where home is."
            wLat == null || wLng == null || wLat !in -90.0..90.0 || wLng !in -180.0..180.0 -> "Set where work is."
            smsNumber.isNotBlank() && CommutePlan.normalizeNumber(smsNumber) == null -> "That phone number does not look right."
            (smsToWork || smsToHome) && smsNumber.isBlank() -> "Pick whom to text, or turn texting off."
            else -> null
        }
        if (error != null) return null
        return Commute(
            carId = carId,
            weekdays = weekdays.sorted(),
            toWorkFrom = HourMinute.format(toWorkFrom),
            toWorkUntil = HourMinute.format(toWorkUntil),
            toHomeFrom = HourMinute.format(toHomeFrom),
            toHomeUntil = HourMinute.format(toHomeUntil),
            homeLat = hLat!!,
            homeLng = hLng!!,
            homeRadiusM = homeRadius,
            workLat = wLat!!,
            workLng = wLng!!,
            workRadiusM = workRadius,
            smsName = smsName.trim().take(100).ifEmpty { null }?.takeIf { smsNumber.isNotBlank() },
            smsNumber = smsNumber.takeIf { it.isNotBlank() }?.let(CommutePlan::normalizeNumber),
            smsToWork = smsToWork,
            smsToHome = smsToHome,
        )
    }

    fun save(onDone: () -> Unit) {
        val commute = draft() ?: return
        if (saving) return
        saving = true
        viewModelScope.launch {
            runCatching {
                val user = container.auth.currentUserId ?: error("You must be signed in.")
                container.commutes.save(user, commute)
                CommuteAnnouncer.refresh(container.commutes, container.preferences)
            }.onSuccess { onDone() }.onFailure { error = it.describe() }
            saving = false
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching {
                container.commutes.delete(carId)
                CommuteAnnouncer.refresh(container.commutes, container.preferences)
            }.onSuccess { onDone() }.onFailure { error = it.describe() }
        }
    }
}

@Composable
fun CommuteScreen(app: AppViewModel, carId: String, onBack: () -> Unit) {
    val viewModel = containerViewModel(key = "commute-$carId") { CommuteViewModel(it, carId) }
    val cars by app.cars.collectAsStateWithLifecycle()
    val carName = cars.valueOrNull?.firstOrNull { it.id == carId }?.name
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Picking one phone number grants read access to that row only, so the
    // app never needs the whole address book.
    val pickContact = rememberLauncherForActivityResult(PickPhoneNumber()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                null, null, null,
            )?.use { cursor -> if (cursor.moveToFirst()) viewModel.setContact(cursor.getString(0), cursor.getString(1).orEmpty()) }
        }
    }
    // Texting needs SMS, and — like the spoken announcement — the position
    // from the background when the car connects.
    val backgroundLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val smsPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (!CommuteAnnouncer.hasPermissions(context) &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        ) {
            backgroundLocation.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }
    fun askForTexting() {
        val missing = buildList {
            add(Manifest.permission.SEND_SMS)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(Manifest.permission.BLUETOOTH_CONNECT)
        }.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        when {
            missing.isNotEmpty() -> smsPermissions.launch(missing.toTypedArray())
            !CommuteAnnouncer.hasPermissions(context) -> backgroundLocation.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(carName?.let { "Commute · $it" } ?: "Commute") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    Button(
                        onClick = { viewModel.save(onBack) },
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
            PlaceholderList(count = 4, modifier = Modifier.padding(padding).padding(horizontal = 16.dp), lines = 2)
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Text(
                    "When this phone connects to the car inside a window, near where that trip starts, " +
                        "it says the arrival time through the car's speakers. Only you see your commute.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                NexusCard {
                    SectionHeader("Days")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        CommuteWeekdays.forEach { (iso, label) ->
                            DayToggle(label, iso in viewModel.weekdays, { viewModel.toggle(iso) }, Modifier.weight(1f))
                        }
                    }
                }
            }
            item {
                WindowCard(
                    title = "Way to work",
                    from = viewModel.toWorkFrom,
                    until = viewModel.toWorkUntil,
                    onFrom = { viewModel.toWorkFrom = it },
                    onUntil = { viewModel.toWorkUntil = it },
                    travel = viewModel.travelText(CommuteDirection.ToWork).takeIf { viewModel.exists },
                )
            }
            item {
                WindowCard(
                    title = "Way home",
                    from = viewModel.toHomeFrom,
                    until = viewModel.toHomeUntil,
                    onFrom = { viewModel.toHomeFrom = it },
                    onUntil = { viewModel.toHomeUntil = it },
                    travel = viewModel.travelText(CommuteDirection.ToHome).takeIf { viewModel.exists },
                )
            }
            item {
                PlaceCard(
                    title = "Home",
                    supporting = "The way to work is announced when it starts here.",
                    lat = viewModel.homeLat,
                    lng = viewModel.homeLng,
                    radius = viewModel.homeRadius,
                    onLat = { viewModel.homeLat = it },
                    onLng = { viewModel.homeLng = it },
                    onRadius = { viewModel.homeRadius = it },
                    onHere = { viewModel.useHere(home = true) },
                )
            }
            item {
                PlaceCard(
                    title = "Work",
                    supporting = "The way home is announced when it starts here.",
                    lat = viewModel.workLat,
                    lng = viewModel.workLng,
                    radius = viewModel.workRadius,
                    onLat = { viewModel.workLat = it },
                    onLng = { viewModel.workLng = it },
                    onRadius = { viewModel.workRadius = it },
                    onHere = { viewModel.useHere(home = false) },
                )
            }
            item {
                SmsCard(
                    viewModel = viewModel,
                    canSend = CommuteAnnouncer.canSendSms(context),
                    onPick = { pickContact.launch(Unit) },
                    onToggle = { toWork, on ->
                        if (toWork) viewModel.smsToWork = on else viewModel.smsToHome = on
                        if (on) askForTexting()
                    },
                    onAllow = { askForTexting() },
                )
            }
            item { FormError(viewModel.error) }
            if (viewModel.exists) {
                item {
                    OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text("Remove commute", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
            title = { Text("Remove your commute?") },
            text = { Text("The car no longer announces arrival times for you. Your drives stay.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(onBack)
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/** One contact, texted the arrival time on the chosen ways — usually only the way home. */
@Composable
private fun SmsCard(
    viewModel: CommuteViewModel,
    canSend: Boolean,
    onPick: () -> Unit,
    onToggle: (toWork: Boolean, on: Boolean) -> Unit,
    onAllow: () -> Unit,
) {
    NexusCard {
        SectionHeader("Text someone", supporting = "This phone texts the arrival time from your number, e.g. \"Bin losgefahren. Ankunft daheim ca. 17:45 Uhr.\"")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = viewModel.smsName,
                onValueChange = { viewModel.smsName = it },
                label = { Text("Name") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = viewModel.smsNumber,
                onValueChange = { next -> viewModel.smsNumber = next.filter { it.isDigit() || it in "+ -/()" } },
                label = { Text("Phone number") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.weight(1f),
            )
        }
        FilledTonalButton(onClick = onPick, modifier = Modifier.padding(top = 8.dp)) {
            Icon(Icons.Rounded.Contacts, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Pick a contact", modifier = Modifier.padding(start = 8.dp))
        }
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingSwitch(
                title = "On the way home",
                description = "When you leave work.",
                checked = viewModel.smsToHome,
                onCheckedChange = { onToggle(false, it) },
            )
            SettingSwitch(
                title = "On the way to work",
                description = "When you leave home.",
                checked = viewModel.smsToWork,
                onCheckedChange = { onToggle(true, it) },
            )
        }
        if ((viewModel.smsToWork || viewModel.smsToHome) && !canSend) {
            Text(
                "This phone may not send SMS yet. If Android refuses, allow \"restricted settings\" " +
                    "in Vibilia's app info first — it asks that of sideloaded apps.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
            TextButton(onClick = onAllow) { Text("Allow texting") }
        }
    }
}

/** ACTION_PICK on phone numbers: the result grants access to the one row picked. */
private class PickPhoneNumber : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        intent?.data?.takeIf { resultCode == Activity.RESULT_OK }
}

/** A round day toggle; seven fit a phone's width without the label wrapping. */
@Composable
private fun DayToggle(label: String, selected: Boolean, onToggle: () -> Unit, modifier: Modifier) {
    Surface(
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .aspectRatio(1f)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() }),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

@Composable
private fun WindowCard(
    title: String,
    from: LocalTime,
    until: LocalTime,
    onFrom: (LocalTime) -> Unit,
    onUntil: (LocalTime) -> Unit,
    travel: String?,
) {
    NexusCard {
        SectionHeader(title, supporting = "Leaving between")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimeChip(from, onFrom, "$title from")
            Text("and")
            TimeChip(until, onUntil, "$title until")
        }
        if (travel != null) {
            Text(
                travel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun TimeChip(value: LocalTime, onChange: (LocalTime) -> Unit, description: String) {
    var picking by remember { mutableStateOf(false) }
    AssistChip(
        onClick = { picking = true },
        label = { Text(HourMinute.format(value)) },
        leadingIcon = { Icon(Icons.Rounded.Schedule, contentDescription = description) },
    )
    if (picking) {
        val state = rememberTimePickerState(initialHour = value.hour, initialMinute = value.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    onChange(LocalTime.of(state.hour, state.minute))
                    picking = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
            text = { TimePicker(state) },
        )
    }
}

@Composable
private fun PlaceCard(
    title: String,
    supporting: String,
    lat: String,
    lng: String,
    radius: Int,
    onLat: (String) -> Unit,
    onLng: (String) -> Unit,
    onRadius: (Int) -> Unit,
    onHere: () -> Unit,
) {
    NexusCard {
        SectionHeader(title, supporting = supporting)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CoordinateField(lat, onLat, "Latitude", Modifier.weight(1f))
            CoordinateField(lng, onLng, "Longitude", Modifier.weight(1f))
        }
        FilledTonalButton(onClick = onHere, modifier = Modifier.padding(top = 8.dp)) {
            Icon(Icons.Rounded.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Use where I am", modifier = Modifier.padding(start = 8.dp))
        }
        Column(Modifier.padding(top = 12.dp)) {
            Row {
                Text("Radius", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text("$radius m", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = radius.toFloat(),
                onValueChange = { onRadius((it / 50f).roundToInt() * 50) },
                valueRange = 50f..2000f,
                steps = (2000 - 50) / 50 - 1,
            )
        }
    }
}

@Composable
private fun CoordinateField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { next -> onChange(next.filter { it.isDigit() || it == '.' || it == ',' || it == '-' }) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier,
    )
}

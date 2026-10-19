package io.github.numynox.nexus.vibilia.ui.logbook

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Euro
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.numynox.nexus.core.data.describe
import io.github.numynox.nexus.core.ui.NexusFormat
import io.github.numynox.nexus.core.ui.components.ConnectedChoiceRow
import io.github.numynox.nexus.core.ui.theme.tabular
import io.github.numynox.nexus.vibilia.AppContainer
import io.github.numynox.nexus.vibilia.data.Car
import io.github.numynox.nexus.vibilia.data.FuelStation
import io.github.numynox.nexus.vibilia.data.RefuelDraft
import io.github.numynox.nexus.vibilia.data.RefuelEvent
import io.github.numynox.nexus.vibilia.location.LatLng
import io.github.numynox.nexus.vibilia.location.distanceTo
import io.github.numynox.nexus.vibilia.ui.EditorRequest
import java.time.LocalDateTime
import kotlin.math.ceil
import kotlinx.coroutines.launch

/**
 * The refuel form's state and rules — the same limits as the web form
 * (RefuelForm.svelte), so a refuel valid in one client is valid in the other.
 */
class RefuelEditorViewModel(
    private val container: AppContainer,
    private val request: EditorRequest,
    private val cars: List<Car>,
) : ViewModel() {
    private val editing: RefuelEvent? = (request as? EditorRequest.EditRefuel)?.event

    var carId by mutableStateOf(
        when (request) {
            is EditorRequest.NewRefuel -> request.carId
            is EditorRequest.EditRefuel -> request.event.carId
            else -> cars.first().id
        },
    )
        private set
    val preselectedByBluetooth = (request as? EditorRequest.NewRefuel)?.preselectedByBluetooth == true
    val isNew get() = editing == null
    val car: Car? get() = cars.firstOrNull { it.id == carId }
    val selectableCars: List<Car> get() = cars

    var mileage by mutableStateOf(editing?.mileage?.toString().orEmpty())
    var liters by mutableStateOf(editing?.liters?.let { NexusFormat.number(it, 2) }.orEmpty())
    var total by mutableStateOf(editing?.totalPrice?.let { NexusFormat.number(it, 2) }.orEmpty())
    var fueledAt by mutableStateOf(editing?.fueledAt?.toLocalDateTimeHere() ?: LocalDateTime.now().withSecond(0).withNano(0))
    var isFull by mutableStateOf(editing?.isFullRefuel ?: true)
    var missedPrevious by mutableStateOf(editing?.missedPreviousRefuel ?: false)
    var levelPercent by mutableStateOf(
        editing?.let { event ->
            val tank = cars.firstOrNull { it.id == event.carId }?.tankCapacity ?: return@let 100f
            ((event.fuelLevelAfter ?: tank) / tank * 100).toFloat().coerceIn(0f, 100f)
        } ?: 100f,
    )
    var stationId by mutableStateOf(editing?.fuelStationId ?: editing?.fuelStation?.id)

    var stations by mutableStateOf<List<FuelStation>>(emptyList())
        private set
    var distances by mutableStateOf<Map<String, Double>>(emptyMap())
        private set
    private var refuels by mutableStateOf<List<RefuelEvent>>(emptyList())

    var submitted by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            stations = runCatching { container.prices.stations() }.getOrDefault(emptyList())
            if (isNew) guessStation()
        }
        loadRefuels()
    }

    fun selectCar(id: String) {
        if (!isNew || id == carId) return
        carId = id
        loadRefuels()
    }

    private fun loadRefuels() {
        val target = carId
        viewModelScope.launch {
            refuels = runCatching { container.logbook.refuels(target) }.getOrDefault(emptyList()).sortedByDescending { it.fueledAt }
        }
    }

    /** Preselects a station within 500 m, as the web form does, and sorts the rest by distance. */
    private suspend fun guessStation() {
        val here: LatLng = container.locations.current(timeoutMillis = 5_000) ?: return
        distances = stations.mapNotNull { s ->
            val lat = s.lat ?: return@mapNotNull null
            val lng = s.lng ?: return@mapNotNull null
            s.id to here.distanceTo(LatLng(lat, lng))
        }.toMap()
        if (stationId == null) {
            stationId = distances.filterValues { it <= 500 }.minByOrNull { it.value }?.key
        }
    }

    val sortedStations: List<FuelStation>
        get() = if (distances.isEmpty()) stations else stations.sortedBy { distances[it.id] ?: Double.MAX_VALUE }

    val previousMileage: Int?
        get() = if (editing == null) {
            refuels.firstOrNull()?.mileage
        } else {
            refuels.indexOfFirst { it.id == editing.id }.takeIf { it >= 0 }?.let { refuels.getOrNull(it + 1)?.mileage }
        }

    val nextMileage: Int?
        get() = editing?.let { e -> refuels.indexOfFirst { it.id == e.id }.takeIf { it > 0 }?.let { refuels[it - 1].mileage } }

    private val tank: Double get() = car?.tankCapacity ?: 0.0

    val mileageError: String?
        get() {
            val value = mileage.toIntOrNull()
            if (!submitted && (value == null || value == 0)) return null
            return when {
                value == null || value <= 0 -> "Enter a mileage greater than 0 km."
                value >= 1_000_000 -> "Mileage must be below 1,000,000 km."
                previousMileage != null && value <= previousMileage!! -> "Must be higher than the previous entry (${NexusFormat.km(previousMileage!!.toDouble())})."
                nextMileage != null && value >= nextMileage!! -> "Must be lower than the next entry (${NexusFormat.km(nextMileage!!.toDouble())})."
                else -> null
            }
        }

    val litersError: String?
        get() {
            val value = liters.toDecimal()
            if (!submitted && (value == null || value == 0.0)) return null
            return when {
                value == null || value < 5 -> "At least 5 L must be refuelled."
                value > 200 -> "Amount cannot exceed 200 L."
                tank > 0 && value > tank -> "Amount cannot exceed the tank capacity (${NexusFormat.liters(tank, 0)})."
                else -> null
            }
        }

    val priceError: String?
        get() {
            val value = total.toDecimal()
            if (!submitted && (value == null || value == 0.0)) return null
            return when {
                value == null || value < 5 -> "Price must be at least 5 €."
                value > 500 -> "Price cannot exceed 500 €."
                else -> null
            }
        }

    /** Even an empty tank holds at least what was just put in. */
    val minimumLevelPercent: Float
        get() = liters.toDecimal()?.takeIf { tank > 0 }?.let { ceil(it / tank * 100).toFloat().coerceAtMost(100f) } ?: 0f

    val levelError: String?
        get() = if (!isFull && levelPercent < minimumLevelPercent) {
            "Fuel level must be at least ${minimumLevelPercent.toInt()} % — you refuelled ${liters} L into a ${NexusFormat.liters(tank, 0)} tank."
        } else {
            null
        }

    val pricePerLiter: Double?
        get() = total.toDecimal()?.let { t -> liters.toDecimal()?.takeIf { it > 0 }?.let { t / it } }

    val kmSincePrevious: Int?
        get() = mileage.toIntOrNull()?.let { m -> previousMileage?.let { p -> (m - p).takeIf { it > 0 } } }

    /** The preview the web form shows: what this refuel will read as. */
    val consumptionPreview: Double?
        get() {
            val km = kmSincePrevious ?: return null
            val litres = liters.toDecimal()?.takeIf { it > 0 } ?: return null
            val used = litres + (1 - (if (isFull) 100f else levelPercent) / 100.0) * tank
            return used / km * 100
        }

    private val valid: Boolean
        get() = mileageError == null && litersError == null && priceError == null && levelError == null &&
            mileage.toIntOrNull() != null && liters.toDecimal() != null && total.toDecimal() != null && car != null

    fun save(onSaved: () -> Unit) {
        submitted = true
        if (!valid || saving) return
        val car = car ?: return
        val litres = liters.toDecimal()!!
        val price = total.toDecimal()!!
        val draft = RefuelDraft(
            mileage = mileage.toInt(),
            liters = litres,
            totalPrice = price,
            fuelLevelAfter = if (isFull) tank else levelPercent / 100.0 * tank,
            isFullRefuel = isFull,
            missedPreviousRefuel = missedPrevious,
            pricePerLiterCalculated = price / litres,
            fueledAt = fueledAt.toInstantHere(),
            fuelStationId = stationId,
        )
        saving = true
        error = null
        viewModelScope.launch {
            runCatching {
                if (editing != null) {
                    container.logbook.updateRefuel(editing.id, draft)
                } else {
                    val userId = container.auth.currentUserId ?: error("You must be signed in to add a refuel.")
                    container.logbook.createRefuel(car.id, userId, draft)
                }
            }.onSuccess { onSaved() }
                .onFailure { error = it.describe() }
            saving = false
        }
    }
}

@Composable
fun RefuelEditor(viewModel: RefuelEditorViewModel, onCancel: () -> Unit, onSaved: () -> Unit) {
    var stationMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(if (viewModel.isNew) "Log refuel" else "Edit refuel", style = MaterialTheme.typography.headlineSmall)

        if (viewModel.isNew && viewModel.selectableCars.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                viewModel.selectableCars.forEach { car ->
                    FilterChip(
                        selected = car.id == viewModel.carId,
                        onClick = { viewModel.selectCar(car.id) },
                        label = { Text(car.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = if (car.id == viewModel.carId && viewModel.preselectedByBluetooth) {
                            { Icon(Icons.Rounded.Bluetooth, contentDescription = "Connected", modifier = Modifier.size(18.dp)) }
                        } else {
                            null
                        },
                    )
                }
            }
        } else if (viewModel.preselectedByBluetooth) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.Bluetooth, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                Text("${viewModel.car?.name} — connected just now", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
            }
        }

        NumberField(
            value = viewModel.mileage,
            onValueChange = { viewModel.mileage = it },
            label = "Odometer",
            icon = Icons.Rounded.Speed,
            suffix = "km",
            decimal = false,
            error = viewModel.mileageError,
            supporting = listOfNotNull(
                viewModel.previousMileage?.let { "Previous: ${NexusFormat.km(it.toDouble())}" },
                viewModel.nextMileage?.let { "Next: ${NexusFormat.km(it.toDouble())}" },
            ).joinToString(" · ").ifEmpty { null },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(
                value = viewModel.liters,
                onValueChange = { viewModel.liters = it },
                label = "Amount",
                icon = Icons.Rounded.WaterDrop,
                suffix = "L",
                error = viewModel.litersError,
                modifier = Modifier.weight(1f),
            )
            NumberField(
                value = viewModel.total,
                onValueChange = { viewModel.total = it },
                label = "Price",
                icon = Icons.Rounded.Euro,
                suffix = "€",
                error = viewModel.priceError,
                modifier = Modifier.weight(1f),
            )
        }

        DateTimeChips(viewModel.fueledAt, onChange = { viewModel.fueledAt = it })

        Text("Tank after refuelling", style = MaterialTheme.typography.titleSmall)
        ConnectedChoiceRow(
            options = listOf(true, false),
            selected = viewModel.isFull,
            onSelect = { viewModel.isFull = it },
            label = { if (it) "Full tank" else "Partial" },
            modifier = Modifier.fillMaxWidth(),
        )
        AnimatedVisibility(visible = !viewModel.isFull) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                        value = viewModel.levelPercent,
                        onValueChange = { viewModel.levelPercent = it.coerceAtLeast(viewModel.minimumLevelPercent) },
                        valueRange = 0f..100f,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${viewModel.levelPercent.toInt()} %", style = MaterialTheme.typography.titleMedium.tabular(), modifier = Modifier.padding(start = 12.dp))
                }
                FormError(viewModel.levelError)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Missed a refuel before this one", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Distance still counts, consumption is skipped for this interval.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = viewModel.missedPrevious, onCheckedChange = { viewModel.missedPrevious = it })
        }

        ExposedDropdownMenuBox(expanded = stationMenu, onExpandedChange = { stationMenu = it }) {
            val selected = viewModel.stations.firstOrNull { it.id == viewModel.stationId }
            OutlinedTextField(
                value = selected?.summary?.label ?: "Other fuel station",
                onValueChange = {},
                readOnly = true,
                label = { Text("Fuel station") },
                leadingIcon = { Icon(Icons.Rounded.LocalGasStation, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = stationMenu, onDismissRequest = { stationMenu = false }) {
                DropdownMenuItem(text = { Text("Other fuel station") }, onClick = {
                    viewModel.stationId = null
                    stationMenu = false
                })
                viewModel.sortedStations.forEach { station ->
                    val distance = viewModel.distances[station.id]?.takeIf { it <= 20_000 }
                    DropdownMenuItem(
                        text = { Text(station.summary.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingIcon = distance?.let { { Text("${NexusFormat.number(it / 1000, 1)} km", style = MaterialTheme.typography.labelMedium) } },
                        onClick = {
                            viewModel.stationId = station.id
                            stationMenu = false
                        },
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            viewModel.pricePerLiter?.let { PreviewTile("Per litre", "${NexusFormat.number(it, 3)} €", Modifier.weight(1f)) }
            viewModel.kmSincePrevious?.let { PreviewTile("Since last", NexusFormat.km(it.toDouble()), Modifier.weight(1f)) }
            viewModel.consumptionPreview?.let { PreviewTile("Consumption", "${NexusFormat.number(it, 1)} L/100", Modifier.weight(1f)) }
        }

        FormError(viewModel.error)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Button(onClick = { viewModel.save(onSaved) }, enabled = !viewModel.saving, modifier = Modifier.height(48.dp)) {
                if (viewModel.saving) {
                    LoadingIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(if (viewModel.isNew) "Save refuel" else "Update refuel")
                }
            }
        }
    }
}

@Composable
private fun PreviewTile(label: String, value: String, modifier: Modifier = Modifier) {
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(value, style = MaterialTheme.typography.titleSmall.tabular(), maxLines = 1)
        }
    }
}

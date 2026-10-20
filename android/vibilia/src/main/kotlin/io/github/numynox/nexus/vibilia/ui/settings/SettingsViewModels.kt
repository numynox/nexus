package io.github.numynox.nexus.vibilia.ui.settings

import io.github.numynox.nexus.core.data.NexusConfig
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.numynox.nexus.core.data.describe
import io.github.numynox.nexus.vibilia.AppContainer
import io.github.numynox.nexus.vibilia.data.BluetoothLink
import io.github.numynox.nexus.vibilia.data.Car
import io.github.numynox.nexus.vibilia.data.CarDraft
import io.github.numynox.nexus.vibilia.data.CarMember
import io.github.numynox.nexus.vibilia.tracking.PairedDevice
import io.github.numynox.nexus.core.ui.Loadable
import io.github.numynox.nexus.core.ui.loadable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val _links = MutableStateFlow<List<BluetoothLink>>(emptyList())
    val links: StateFlow<List<BluetoothLink>> = _links.asStateFlow()

    val hasBluetoothPermission: Boolean get() = container.driveTracking.hasPermission()
    val bluetoothPermission: String? get() = container.driveTracking.permission
    val config: NexusConfig get() = container.config

    init {
        viewModelScope.launch {
            _links.value = container.preferences.cachedLinks()
            _links.value = container.driveTracking.refreshLinks()
        }
    }

    fun setRecording(enabled: Boolean) {
        viewModelScope.launch { container.driveTracking.setRecording(enabled) }
    }
}

/**
 * A vehicle's details, members and Bluetooth links. Editing a car is open to
 * everyone it is shared with, as on the web (vibilia_sharing_full_access_rls).
 */
class VehicleEditorViewModel(private val container: AppContainer, private val carId: String?) : ViewModel() {
    val isNew = carId == null

    var loading by mutableStateOf(!isNew)
        private set
    var car by mutableStateOf<Car?>(null)
        private set

    var name by mutableStateOf("")
    var tank by mutableStateOf("50")
    var plate by mutableStateOf("")
    var make by mutableStateOf("")
    var model by mutableStateOf("")
    var year by mutableStateOf("")
    var vin by mutableStateOf("")

    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var submitted by mutableStateOf(false)
        private set

    var members by mutableStateOf<Loadable<List<CarMember>>>(Loadable.Loading)
        private set
    var shareMessage by mutableStateOf<String?>(null)
        private set
    var links by mutableStateOf<List<BluetoothLink>>(emptyList())
        private set

    val bluetoothPermission: String? get() = container.driveTracking.permission
    fun hasBluetoothPermission() = container.driveTracking.hasPermission()
    fun pairedDevices(): List<PairedDevice> = container.driveTracking.pairedDevices()

    init {
        if (carId != null) {
            viewModelScope.launch {
                runCatching { container.cars.cars().firstOrNull { it.id == carId } }
                    .onSuccess { found ->
                        car = found
                        found?.let { c ->
                            name = c.name
                            tank = c.tankCapacity?.let { io.github.numynox.nexus.core.ui.NexusFormat.number(it, 0) }.orEmpty()
                            plate = c.plate.orEmpty()
                            make = c.make.orEmpty()
                            model = c.model.orEmpty()
                            year = c.year?.toString().orEmpty()
                            vin = c.vin.orEmpty()
                        }
                    }
                    .onFailure { error = it.describe() }
                loading = false
            }
            loadMembers()
            viewModelScope.launch { links = container.driveTracking.refreshLinks().filter { it.carId == carId } }
        }
    }

    val nameError: String? get() = if (submitted && name.isBlank()) "Give the vehicle a name." else null
    val tankError: String?
        get() {
            val value = tank.replace(',', '.').toDoubleOrNull()
            return if (submitted && (value == null || value <= 0 || value > 999)) "Enter the tank capacity in litres." else null
        }
    val yearError: String?
        get() = year.takeIf { it.isNotBlank() }?.toIntOrNull().let { parsed ->
            if (year.isNotBlank() && (parsed == null || parsed !in 1900..2100)) "Enter a year like 2019." else null
        }

    fun save(onDone: () -> Unit) {
        submitted = true
        if (nameError != null || tankError != null || yearError != null || saving) return
        val draft = CarDraft(
            name = name.trim(),
            tankCapacity = tank.replace(',', '.').toDouble(),
            vin = vin.trim().ifEmpty { null },
            plate = plate.trim().ifEmpty { null },
            make = make.trim().ifEmpty { null },
            model = model.trim().ifEmpty { null },
            year = year.trim().toIntOrNull(),
        )
        saving = true
        error = null
        viewModelScope.launch {
            runCatching {
                if (carId == null) {
                    val owner = container.auth.currentUserId ?: error("You must be signed in.")
                    container.cars.create(owner, draft)
                } else {
                    container.cars.update(carId, draft)
                }
            }.onSuccess { onDone() }.onFailure { error = it.describe() }
            saving = false
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = carId ?: return
        viewModelScope.launch {
            runCatching { container.cars.delete(id) }
                .onSuccess {
                    container.preferences.setSelectedCar(null)
                    onDone()
                }
                .onFailure { error = it.describe() }
        }
    }

    private fun loadMembers() {
        val id = carId ?: return
        viewModelScope.launch { members = loadable { container.cars.members(id) } }
    }

    fun share(email: String) {
        val id = carId ?: return
        if (email.isBlank()) return
        viewModelScope.launch {
            shareMessage = runCatching { container.cars.share(id, email) }
                .fold({ "Shared with ${email.trim()}." }, { it.describe() })
            loadMembers()
        }
    }

    fun link(device: PairedDevice) {
        val id = carId ?: return
        viewModelScope.launch {
            runCatching { container.driveTracking.link(id, device) }.onFailure { error = it.describe() }
            links = container.preferences.cachedLinks().filter { it.carId == id }
        }
    }

    fun unlink(address: String) {
        val id = carId ?: return
        viewModelScope.launch {
            runCatching { container.driveTracking.unlink(id, address) }.onFailure { error = it.describe() }
            links = container.preferences.cachedLinks().filter { it.carId == id }
        }
    }
}

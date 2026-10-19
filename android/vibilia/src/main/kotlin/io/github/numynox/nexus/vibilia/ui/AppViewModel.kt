package io.github.numynox.nexus.vibilia.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.numynox.nexus.core.data.AuthState
import io.github.numynox.nexus.core.data.AvailableUpdate
import io.github.numynox.nexus.core.ui.theme.ThemeMode
import io.github.numynox.nexus.vibilia.AppContainer
import io.github.numynox.nexus.vibilia.data.Car
import io.github.numynox.nexus.vibilia.data.CarExpense
import io.github.numynox.nexus.vibilia.data.RefuelEvent
import io.github.numynox.nexus.vibilia.data.VibiliaSettings
import io.github.numynox.nexus.vibilia.tracking.DriveSync
import io.github.numynox.nexus.vibilia.tracking.TrackerState
import io.github.numynox.nexus.vibilia.ui.common.Loadable
import io.github.numynox.nexus.vibilia.ui.common.loadable
import io.github.numynox.nexus.vibilia.ui.common.valueOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the logbook editor sheet is open for. */
sealed interface EditorRequest {
    /** [preselectedByBluetooth]: the car was chosen because the phone is (or just was) connected to it. */
    data class NewRefuel(val carId: String, val preselectedByBluetooth: Boolean) : EditorRequest
    data class EditRefuel(val event: RefuelEvent) : EditorRequest
    data class NewExpense(val carId: String) : EditorRequest
    data class EditExpense(val expense: CarExpense) : EditorRequest
}

/**
 * State shared by every screen: who is signed in, their cars, which car is
 * selected, the editor sheet, and the drive in progress.
 */
class AppViewModel(private val container: AppContainer, private val application: Application) : ViewModel() {

    val auth: StateFlow<AuthState> = container.auth.state

    val settings: StateFlow<VibiliaSettings> = container.preferences.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, VibiliaSettings())

    private val _cars = MutableStateFlow<Loadable<List<Car>>>(Loadable.Loading)
    val cars: StateFlow<Loadable<List<Car>>> = _cars.asStateFlow()

    /** The persisted selection, or the newest car when that is gone. */
    val selectedCar: StateFlow<Car?> = combine(_cars, settings) { cars, settings ->
        val list = cars.valueOrNull.orEmpty()
        list.firstOrNull { it.id == settings.selectedCarId } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val tracker: StateFlow<TrackerState> = container.driveTracking.tracker.state
        .stateIn(viewModelScope, SharingStarted.Eagerly, TrackerState())

    private val _update = MutableStateFlow<AvailableUpdate?>(null)
    val update: StateFlow<AvailableUpdate?> = _update.asStateFlow()

    private val _editor = MutableStateFlow<EditorRequest?>(null)
    val editor: StateFlow<EditorRequest?> = _editor.asStateFlow()

    /** Bumped after every save, so screens showing logbook data reload. */
    private val _dataVersion = MutableStateFlow(0)
    val dataVersion: StateFlow<Int> = _dataVersion.asStateFlow()

    init {
        viewModelScope.launch {
            auth.distinctUntilChanged { a, b -> a::class == b::class }.collect { state ->
                if (state is AuthState.SignedIn) onSignedIn()
            }
        }
        viewModelScope.launch { _update.value = container.updates.check() }
    }

    private fun onSignedIn() {
        refreshCars()
        viewModelScope.launch {
            container.driveTracking.refreshLinks()
            DriveSync.schedule(application)
        }
    }

    fun refreshCars() {
        viewModelScope.launch {
            if (_cars.value !is Loadable.Ready) _cars.value = Loadable.Loading
            _cars.value = loadable { container.cars.cars() }
        }
    }

    fun setFuelType(value: String) = viewModelScope.launch { container.preferences.setFuelType(value) }
    fun setPriceHistoryDays(value: Int) = viewModelScope.launch { container.preferences.setPriceHistoryDays(value) }
    fun setThemeMode(value: ThemeMode) = viewModelScope.launch { container.preferences.setThemeMode(value) }
    fun setDynamicColor(value: Boolean) = viewModelScope.launch { container.preferences.setDynamicColor(value) }

    fun selectCar(carId: String) {
        viewModelScope.launch { container.preferences.setSelectedCar(carId) }
    }

    /** Opens the refuel form, preselecting the car the phone is connected to. */
    fun newRefuel(carId: String? = null) {
        viewModelScope.launch {
            val connected = container.driveTracking.recentCarId()
                ?.takeIf { id -> _cars.value.valueOrNull.orEmpty().any { it.id == id } }
            val chosen = carId ?: connected ?: selectedCar.value?.id ?: return@launch
            _editor.value = EditorRequest.NewRefuel(chosen, preselectedByBluetooth = carId == null && connected == chosen)
        }
    }

    fun newExpense(carId: String) {
        _editor.value = EditorRequest.NewExpense(carId)
    }

    fun edit(event: RefuelEvent) {
        _editor.value = EditorRequest.EditRefuel(event)
    }

    fun edit(expense: CarExpense) {
        _editor.value = EditorRequest.EditExpense(expense)
    }

    fun closeEditor(saved: Boolean) {
        _editor.value = null
        if (saved) _dataVersion.update { it + 1 }
    }

    /** After a car was added, edited or deleted elsewhere. */
    fun carsChanged() {
        refreshCars()
        _dataVersion.update { it + 1 }
    }

    fun signOut() {
        viewModelScope.launch {
            runCatching { container.auth.signOut() }
            _cars.value = Loadable.Loading
        }
    }
}

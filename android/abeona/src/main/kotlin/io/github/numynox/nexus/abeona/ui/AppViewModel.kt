package io.github.numynox.nexus.abeona.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.numynox.nexus.abeona.AppContainer
import io.github.numynox.nexus.abeona.ui.intake.IncomingShare
import io.github.numynox.nexus.abeona.data.AbeonaSettings
import io.github.numynox.nexus.abeona.data.AlertSettings
import io.github.numynox.nexus.abeona.reminders.LeaveReminder
import io.github.numynox.nexus.abeona.live.Background
import io.github.numynox.nexus.abeona.reminders.BahnCardReminders
import io.github.numynox.nexus.abeona.reminders.ClaimReminders
import io.github.numynox.nexus.core.data.AuthState
import io.github.numynox.nexus.core.data.AvailableUpdate
import io.github.numynox.nexus.core.update.AppUpdates
import io.github.numynox.nexus.core.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** State every screen shares: the session, the phone's preferences, the update check, and what changed. */
class AppViewModel(val container: AppContainer, private val application: Application) : ViewModel() {

    val auth: StateFlow<AuthState> = container.core.auth.state

    val settings: StateFlow<AbeonaSettings> = container.preferences.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AbeonaSettings())

    val updates: AppUpdates get() = container.updates
    val update: StateFlow<AvailableUpdate?> = container.updates.available

    /** Bumped after every write, so screens showing trips reload. */
    private val _dataVersion = MutableStateFlow(0)
    val dataVersion: StateFlow<Int> = _dataVersion.asStateFlow()

    /** One-line confirmations ("Trip saved") for whichever screen is showing. */
    /** A share waiting for the search screen to read it (intake). */
    val share = MutableStateFlow<IncomingShare?>(null)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        container.updates.start()
        // Keep the BahnCard reminder's schedule in step with its setting (not on
        // the first, stored value: the schedule already exists then), as Annona does.
        viewModelScope.launch {
            container.preferences.settings.map { it.bahnCardReminder }.distinctUntilChanged().drop(1)
                .collect { BahnCardReminders.apply(application, it) }
        }
        viewModelScope.launch {
            container.preferences.settings.map { it.claimReminder }.distinctUntilChanged().drop(1)
                .collect { ClaimReminders.apply(application, it) }
        }
        viewModelScope.launch {
            auth.distinctUntilChanged { a, b -> a::class == b::class }.collect { state ->
                if (state is AuthState.SignedIn) {
                    // Made on first use; every household-scoped query waits for it anyway.
                    runCatching { container.repository.householdId() }
                    Background.schedule(application)
                }
            }
        }
    }

    fun changed(message: String? = null) {
        _dataVersion.update { it + 1 }
        message?.let { _messages.tryEmit(it) }
    }

    fun say(message: String) {
        _messages.tryEmit(message)
    }

    fun setThemeMode(value: ThemeMode) = viewModelScope.launch { container.preferences.setThemeMode(value) }
    fun setDynamicColor(value: Boolean) = viewModelScope.launch { container.preferences.setDynamicColor(value) }
    fun setLiveCompanion(value: Boolean) = viewModelScope.launch {
        container.preferences.setLiveCompanion(value)
        Background.schedule(application)
    }

    fun setBahnCardReminder(value: Boolean) = viewModelScope.launch { container.preferences.setBahnCardReminder(value) }
    fun setClaimReminder(value: Boolean) = viewModelScope.launch { container.preferences.setClaimReminder(value) }

    fun setStationMinutes(value: Int) = viewModelScope.launch {
        container.preferences.setStationMinutes(value)
        rescheduleLeaveReminder()
    }

    fun setLeaveReminder(value: Boolean) = viewModelScope.launch {
        container.preferences.setLeaveReminder(value)
        rescheduleLeaveReminder()
    }

    fun setAlerts(value: AlertSettings) = viewModelScope.launch { container.preferences.setAlerts(value) }

    /** Location during a trip: takes effect when the live companion next starts. */
    fun setTripLocation(value: Boolean) = viewModelScope.launch { container.preferences.setTripLocation(value) }

    fun dismissLocationPrompt() = viewModelScope.launch { container.preferences.setLocationAsked() }

    /** The reminder follows the setting at once, not at the next half-hourly job. */
    private suspend fun rescheduleLeaveReminder() {
        val settings = container.preferences.current()
        val trips = if (settings.leaveReminder) runCatching { container.trips.upcoming().value }.getOrDefault(emptyList()) else emptyList()
        LeaveReminder.schedule(application, trips, settings.leaveReminder, settings.stationMinutes)
    }

    fun signOut() {
        viewModelScope.launch { runCatching { container.core.auth.signOut() } }
    }
}

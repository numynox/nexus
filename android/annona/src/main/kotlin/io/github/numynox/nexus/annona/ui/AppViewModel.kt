package io.github.numynox.nexus.annona.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.numynox.nexus.annona.AppContainer
import io.github.numynox.nexus.annona.data.AnnonaSettings
import io.github.numynox.nexus.annona.reminders.ExpiryReminders
import io.github.numynox.nexus.core.data.AuthState
import io.github.numynox.nexus.core.data.AvailableUpdate
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** State every screen shares: the session, the phone's preferences, the update check, and what changed. */
class AppViewModel(val container: AppContainer, private val application: Application) : ViewModel() {

    val auth: StateFlow<AuthState> = container.core.auth.state

    val settings: StateFlow<AnnonaSettings> = container.preferences.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AnnonaSettings())

    private val _update = MutableStateFlow<AvailableUpdate?>(null)
    val update: StateFlow<AvailableUpdate?> = _update.asStateFlow()

    /** Bumped after every write, so screens showing stock reload. */
    private val _dataVersion = MutableStateFlow(0)
    val dataVersion: StateFlow<Int> = _dataVersion.asStateFlow()

    /** One-line confirmations ("Added 2 items…") for whichever screen is showing. */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        viewModelScope.launch { _update.value = container.core.updates.check() }
        viewModelScope.launch {
            auth.distinctUntilChanged { a, b -> a::class == b::class }.collect { state ->
                // The shared category list starts from the defaults, as on the web.
                if (state is AuthState.SignedIn) runCatching { container.repository.seedDefaultCategories() }
            }
        }
        // Keep the reminder schedule in step with its settings (not on the
        // first, stored value: the schedule already exists then).
        viewModelScope.launch {
            container.preferences.settings
                .distinctUntilChanged { a, b -> a.remindersOn == b.remindersOn && a.reminderHour == b.reminderHour }
                .drop(1)
                .collect { ExpiryReminders.apply(application, it) }
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
    fun setRemindersOn(value: Boolean) = viewModelScope.launch { container.preferences.setRemindersOn(value) }
    fun setReminderDays(value: Int) = viewModelScope.launch { container.preferences.setReminderDays(value) }
    fun setReminderHour(value: Int) = viewModelScope.launch { container.preferences.setReminderHour(value) }

    fun signOut() {
        viewModelScope.launch { runCatching { container.core.auth.signOut() } }
    }
}

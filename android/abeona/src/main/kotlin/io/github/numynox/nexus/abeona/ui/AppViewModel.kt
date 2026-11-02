package io.github.numynox.nexus.abeona.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.numynox.nexus.abeona.AppContainer
import io.github.numynox.nexus.abeona.data.AbeonaSettings
import io.github.numynox.nexus.abeona.live.Background
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** State every screen shares: the session, the phone's preferences, the update check, and what changed. */
class AppViewModel(val container: AppContainer, private val application: Application) : ViewModel() {

    val auth: StateFlow<AuthState> = container.core.auth.state

    val settings: StateFlow<AbeonaSettings> = container.preferences.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AbeonaSettings())

    private val _update = MutableStateFlow<AvailableUpdate?>(null)
    val update: StateFlow<AvailableUpdate?> = _update.asStateFlow()

    /** Bumped after every write, so screens showing trips reload. */
    private val _dataVersion = MutableStateFlow(0)
    val dataVersion: StateFlow<Int> = _dataVersion.asStateFlow()

    /** One-line confirmations ("Trip saved") for whichever screen is showing. */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        viewModelScope.launch { _update.value = container.core.updates.check() }
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

    fun signOut() {
        viewModelScope.launch { runCatching { container.core.auth.signOut() } }
    }
}

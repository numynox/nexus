package io.github.numynox.nexus.noctua.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.numynox.nexus.core.data.AuthState
import io.github.numynox.nexus.core.data.AvailableUpdate
import io.github.numynox.nexus.core.ui.theme.ThemeMode
import io.github.numynox.nexus.noctua.AppContainer
import io.github.numynox.nexus.noctua.data.Density
import io.github.numynox.nexus.noctua.data.NoctuaSettings
import io.github.numynox.nexus.noctua.data.SortOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** State every screen shares: the session, the device's preferences, the update check. */
class AppViewModel(val container: AppContainer) : ViewModel() {

    val auth: StateFlow<AuthState> = container.core.auth.state

    val settings: StateFlow<NoctuaSettings> = container.preferences.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, NoctuaSettings())

    private val _update = MutableStateFlow<AvailableUpdate?>(null)
    val update: StateFlow<AvailableUpdate?> = _update.asStateFlow()

    /** Bumped when sections change in settings, so the feed reloads. */
    private val _sectionsVersion = MutableStateFlow(0)
    val sectionsVersion: StateFlow<Int> = _sectionsVersion.asStateFlow()

    init {
        viewModelScope.launch { _update.value = container.core.updates.check() }
    }

    fun sectionsChanged() = _sectionsVersion.update { it + 1 }

    fun setThemeMode(value: ThemeMode) = viewModelScope.launch { container.preferences.setThemeMode(value) }
    fun setDynamicColor(value: Boolean) = viewModelScope.launch { container.preferences.setDynamicColor(value) }
    fun setHideSeen(value: Boolean) = viewModelScope.launch { container.preferences.setHideSeen(value) }
    fun setAutoMarkSeen(value: Boolean) = viewModelScope.launch { container.preferences.setAutoMarkSeen(value) }
    fun setDensity(value: Density) = viewModelScope.launch { container.preferences.setDensity(value) }
    fun setSortOrder(value: SortOrder) = viewModelScope.launch { container.preferences.setSortOrder(value) }
    fun setOpenInApp(value: Boolean) = viewModelScope.launch { container.preferences.setOpenInApp(value) }
    fun selectSection(id: Long?) = viewModelScope.launch { container.preferences.setSelectedSection(id) }

    fun signOut() {
        viewModelScope.launch {
            runCatching { container.core.auth.signOut() }
            container.preferences.clearCachedSections()
        }
    }
}

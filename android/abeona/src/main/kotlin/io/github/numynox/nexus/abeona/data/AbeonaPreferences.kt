package io.github.numynox.nexus.abeona.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.numynox.nexus.abeona.widget.NextTripSnapshot
import io.github.numynox.nexus.core.data.NexusJson
import io.github.numynox.nexus.core.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.abeonaStore by preferencesDataStore(name = "abeona")

data class AbeonaSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = false,
    /**
     * Start the live companion on its own, half an hour before a departure.
     * Per phone and off by default: it keeps a notification up for the ride.
     */
    val liveCompanion: Boolean = false,
)

/**
 * Device-local preferences, and what the widget last showed. Trips are shared
 * in Postgres (decisions/2026-04-06-device-local-preferences-in-localstorage.md).
 */
class AbeonaPreferences(private val context: Context) {

    private object Keys {
        val ThemeMode = stringPreferencesKey("theme_mode")
        val DynamicColor = booleanPreferencesKey("dynamic_color")
        val LiveCompanion = booleanPreferencesKey("live_companion")
        val Widget = stringPreferencesKey("widget_next_trip")
    }

    val settings: Flow<AbeonaSettings> = context.abeonaStore.data.map { it.toSettings() }

    private fun Preferences.toSettings() = AbeonaSettings(
        themeMode = ThemeMode.entries.firstOrNull { it.name == this[Keys.ThemeMode] } ?: ThemeMode.System,
        dynamicColor = this[Keys.DynamicColor] ?: false,
        liveCompanion = this[Keys.LiveCompanion] ?: false,
    )

    suspend fun current(): AbeonaSettings = settings.first()

    suspend fun setThemeMode(value: ThemeMode) = edit { it[Keys.ThemeMode] = value.name }
    suspend fun setDynamicColor(value: Boolean) = edit { it[Keys.DynamicColor] = value }
    suspend fun setLiveCompanion(value: Boolean) = edit { it[Keys.LiveCompanion] = value }

    /** What the home screen widget last loaded, shown while it cannot load. */
    suspend fun widgetSnapshot(): NextTripSnapshot? =
        context.abeonaStore.data.first()[Keys.Widget]
            ?.let { runCatching { NexusJson.decodeFromString<NextTripSnapshot>(it) }.getOrNull() }

    suspend fun setWidgetSnapshot(value: NextTripSnapshot?) = edit {
        if (value == null) it.remove(Keys.Widget) else it[Keys.Widget] = NexusJson.encodeToString(value)
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.abeonaStore.edit(block)
    }
}

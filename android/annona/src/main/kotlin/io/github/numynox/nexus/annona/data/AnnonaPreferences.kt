package io.github.numynox.nexus.annona.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.numynox.nexus.core.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.annonaStore by preferencesDataStore(name = "annona")

data class AnnonaSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = false,
    /** A daily notification of what is about to expire. Per phone: each person decides for themselves. */
    val remindersOn: Boolean = false,
    /** How far ahead the reminder looks, 1–14 days. */
    val reminderDays: Int = 3,
    /** Hour of day the reminder comes, 6–21. */
    val reminderHour: Int = 9,
)

/**
 * Device-local preferences. The inventory itself is shared in Postgres; what
 * stays here is how this phone looks and whether it nags
 * (decisions/2026-04-06-device-local-preferences-in-localstorage.md).
 */
class AnnonaPreferences(private val context: Context) {

    private object Keys {
        val ThemeMode = stringPreferencesKey("theme_mode")
        val DynamicColor = booleanPreferencesKey("dynamic_color")
        val RemindersOn = booleanPreferencesKey("reminders_on")
        val ReminderDays = intPreferencesKey("reminder_days")
        val ReminderHour = intPreferencesKey("reminder_hour")
    }

    val settings: Flow<AnnonaSettings> = context.annonaStore.data.map { it.toSettings() }

    private fun Preferences.toSettings() = AnnonaSettings(
        themeMode = ThemeMode.entries.firstOrNull { it.name == this[Keys.ThemeMode] } ?: ThemeMode.System,
        dynamicColor = this[Keys.DynamicColor] ?: false,
        remindersOn = this[Keys.RemindersOn] ?: false,
        reminderDays = (this[Keys.ReminderDays] ?: 3).coerceIn(1, 14),
        reminderHour = (this[Keys.ReminderHour] ?: 9).coerceIn(6, 21),
    )

    suspend fun current(): AnnonaSettings = settings.first()

    suspend fun setThemeMode(value: ThemeMode) = edit { it[Keys.ThemeMode] = value.name }
    suspend fun setDynamicColor(value: Boolean) = edit { it[Keys.DynamicColor] = value }
    suspend fun setRemindersOn(value: Boolean) = edit { it[Keys.RemindersOn] = value }
    suspend fun setReminderDays(value: Int) = edit { it[Keys.ReminderDays] = value.coerceIn(1, 14) }
    suspend fun setReminderHour(value: Int) = edit { it[Keys.ReminderHour] = value.coerceIn(6, 21) }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.annonaStore.edit(block)
    }
}

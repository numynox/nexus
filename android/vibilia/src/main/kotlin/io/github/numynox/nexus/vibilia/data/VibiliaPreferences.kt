package io.github.numynox.nexus.vibilia.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.numynox.nexus.core.data.NexusJson
import io.github.numynox.nexus.core.ui.theme.ThemeMode
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.vibiliaStore by preferencesDataStore(name = "vibilia")

data class VibiliaSettings(
    val fuelType: String = "E10",
    /** Previous days drawn behind today on the price chart, 1–7. */
    val priceHistoryDays: Int = 3,
    val selectedCarId: String? = null,
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = false,
    /** Whether this phone records drives over Bluetooth. Per phone, never synced. */
    val trackDrives: Boolean = false,
    /** A daily notification when a car's tank runs low. Per phone, off by default. */
    val refuelRemindersOn: Boolean = false,
    /** Below this fill level, in percent (10–40), a car counts as low. */
    val refuelReminderPercent: Int = 20,
    /** The hour of day the check runs, 6–21. */
    val refuelReminderHour: Int = 7,
)

/**
 * Device-local preferences — the Android counterpart of the web app's
 * lib/storage.ts, following the same rule
 * (decisions/2026-04-06-device-local-preferences-in-localstorage.md): a UI
 * default nobody else needs stays on the device. Every value is validated on
 * read, because an old shape may linger.
 */
class VibiliaPreferences(private val context: Context) {

    private object Keys {
        val FuelType = stringPreferencesKey("fuel_type")
        val PriceHistoryDays = intPreferencesKey("price_history_days")
        val SelectedCar = stringPreferencesKey("selected_car_id")
        val ThemeMode = stringPreferencesKey("theme_mode")
        val DynamicColor = booleanPreferencesKey("dynamic_color")
        val TrackDrives = booleanPreferencesKey("track_drives")
        val DeviceId = stringPreferencesKey("device_id")
        val BluetoothLinks = stringPreferencesKey("bluetooth_links")
        val RefuelRemindersOn = booleanPreferencesKey("refuel_reminders_on")
        val RefuelReminderPercent = intPreferencesKey("refuel_reminder_percent")
        val RefuelReminderHour = intPreferencesKey("refuel_reminder_hour")
    }

    val settings: Flow<VibiliaSettings> = context.vibiliaStore.data.map { it.toSettings() }

    private fun Preferences.toSettings() = VibiliaSettings(
        fuelType = this[Keys.FuelType]?.takeIf { it in FuelTypes } ?: "E10",
        priceHistoryDays = (this[Keys.PriceHistoryDays] ?: 3).coerceIn(1, 7),
        selectedCarId = this[Keys.SelectedCar]?.takeIf { it.isNotBlank() },
        themeMode = ThemeMode.entries.firstOrNull { it.name == this[Keys.ThemeMode] } ?: ThemeMode.System,
        dynamicColor = this[Keys.DynamicColor] ?: false,
        trackDrives = this[Keys.TrackDrives] ?: false,
        refuelRemindersOn = this[Keys.RefuelRemindersOn] ?: false,
        refuelReminderPercent = (this[Keys.RefuelReminderPercent] ?: 20).coerceIn(10, 40),
        refuelReminderHour = (this[Keys.RefuelReminderHour] ?: 7).coerceIn(6, 21),
    )

    suspend fun current(): VibiliaSettings = settings.first()

    suspend fun setFuelType(value: String) = edit { it[Keys.FuelType] = value }
    suspend fun setPriceHistoryDays(value: Int) = edit { it[Keys.PriceHistoryDays] = value.coerceIn(1, 7) }
    suspend fun setSelectedCar(carId: String?) = edit { if (carId == null) it.remove(Keys.SelectedCar) else it[Keys.SelectedCar] = carId }
    suspend fun setThemeMode(value: ThemeMode) = edit { it[Keys.ThemeMode] = value.name }
    suspend fun setDynamicColor(value: Boolean) = edit { it[Keys.DynamicColor] = value }
    suspend fun setTrackDrives(value: Boolean) = edit { it[Keys.TrackDrives] = value }
    suspend fun setRefuelRemindersOn(value: Boolean) = edit { it[Keys.RefuelRemindersOn] = value }
    suspend fun setRefuelReminderPercent(value: Int) = edit { it[Keys.RefuelReminderPercent] = value.coerceIn(10, 40) }
    suspend fun setRefuelReminderHour(value: Int) = edit { it[Keys.RefuelReminderHour] = value.coerceIn(6, 21) }

    /** A random id per installation, so the server can tell this phone's reports apart. */
    suspend fun deviceId(): String {
        context.vibiliaStore.data.first()[Keys.DeviceId]?.let { return it }
        val id = UUID.randomUUID().toString()
        edit { it[Keys.DeviceId] = id }
        return id
    }

    /**
     * The Bluetooth links last seen on the server, kept here so a connection
     * can be recognised without network — the car is often a dead spot.
     */
    suspend fun cachedLinks(): List<BluetoothLink> =
        context.vibiliaStore.data.first()[Keys.BluetoothLinks]
            ?.let { runCatching { NexusJson.decodeFromString<List<BluetoothLink>>(it) }.getOrNull() }
            .orEmpty()

    suspend fun setCachedLinks(links: List<BluetoothLink>) =
        edit { it[Keys.BluetoothLinks] = NexusJson.encodeToString(links) }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.vibiliaStore.edit(block)
    }
}

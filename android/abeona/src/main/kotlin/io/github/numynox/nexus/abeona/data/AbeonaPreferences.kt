package io.github.numynox.nexus.abeona.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.numynox.nexus.abeona.domain.TravelDayRules
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
    /** Notify before a BahnCard subscription's cancellation deadline. Per phone and off by default. */
    val bahnCardReminder: Boolean = false,
    /** From the door to the platform: the time to leave is the first departure minus this. */
    val stationMinutes: Int = TravelDayRules.DEFAULT_STATION_MINUTES,
    /** Notify when it is time to leave for the next trip. Per phone and off by default. */
    val leaveReminder: Boolean = false,
    /** What the live companion alerts about. */
    val alerts: AlertSettings = AlertSettings(),
    /** Use the phone's position while the live companion runs. Off until asked for. */
    val tripLocation: Boolean = false,
    /** The trip screen's "use your location?" card was answered or dismissed. */
    val locationAsked: Boolean = false,
)

/**
 * Which news the live companion posts as a heads-up. Per phone; the
 * defaults alert on everything that changes what to do, and on delays from
 * five minutes.
 */
data class AlertSettings(
    /** A ride's delay reaches this many minutes, and again each time it grows by as much; 0 = never. */
    val delayMinutes: Int = 5,
    val connectionAtRisk: Boolean = true,
    val platformChange: Boolean = true,
    val cancellation: Boolean = true,
) {
    companion object {
        /** The choices offered in Settings; 0 is "off". */
        val DELAY_CHOICES = listOf(0, 5, 10, 15, 20)
    }
}

/** The choices for the time to the station, in minutes; the same on the web. */
val STATION_MINUTES_CHOICES = listOf(5, 10, 15, 20, 30, 45, 60)

/**
 * Device-local preferences, and what the widget last showed. Trips are shared
 * in Postgres (decisions/2026-04-06-device-local-preferences-in-localstorage.md).
 */
class AbeonaPreferences(private val context: Context) {

    private object Keys {
        val ThemeMode = stringPreferencesKey("theme_mode")
        val DynamicColor = booleanPreferencesKey("dynamic_color")
        val LiveCompanion = booleanPreferencesKey("live_companion")
        val BahnCardReminder = booleanPreferencesKey("bahncard_reminder")
        val StationMinutes = intPreferencesKey("station_minutes")
        val LeaveReminder = booleanPreferencesKey("leave_reminder")
        val AlertDelay = intPreferencesKey("alert_delay_minutes")
        val AlertConnection = booleanPreferencesKey("alert_connection")
        val AlertPlatform = booleanPreferencesKey("alert_platform")
        val AlertCancellation = booleanPreferencesKey("alert_cancellation")
        val TripLocation = booleanPreferencesKey("trip_location")
        val LocationAsked = booleanPreferencesKey("location_asked")
        val Widget = stringPreferencesKey("widget_next_trip")
    }

    val settings: Flow<AbeonaSettings> = context.abeonaStore.data.map { it.toSettings() }

    private fun Preferences.toSettings() = AbeonaSettings(
        themeMode = ThemeMode.entries.firstOrNull { it.name == this[Keys.ThemeMode] } ?: ThemeMode.System,
        dynamicColor = this[Keys.DynamicColor] ?: false,
        liveCompanion = this[Keys.LiveCompanion] ?: false,
        bahnCardReminder = this[Keys.BahnCardReminder] ?: false,
        stationMinutes = this[Keys.StationMinutes]?.takeIf { it in 0..180 } ?: TravelDayRules.DEFAULT_STATION_MINUTES,
        leaveReminder = this[Keys.LeaveReminder] ?: false,
        alerts = AlertSettings(
            delayMinutes = this[Keys.AlertDelay]?.takeIf { it in 0..120 } ?: AlertSettings().delayMinutes,
            connectionAtRisk = this[Keys.AlertConnection] ?: true,
            platformChange = this[Keys.AlertPlatform] ?: true,
            cancellation = this[Keys.AlertCancellation] ?: true,
        ),
        tripLocation = this[Keys.TripLocation] ?: false,
        locationAsked = this[Keys.LocationAsked] ?: false,
    )

    suspend fun current(): AbeonaSettings = settings.first()

    suspend fun setThemeMode(value: ThemeMode) = edit { it[Keys.ThemeMode] = value.name }
    suspend fun setDynamicColor(value: Boolean) = edit { it[Keys.DynamicColor] = value }
    suspend fun setLiveCompanion(value: Boolean) = edit { it[Keys.LiveCompanion] = value }
    suspend fun setBahnCardReminder(value: Boolean) = edit { it[Keys.BahnCardReminder] = value }
    suspend fun setStationMinutes(value: Int) = edit { it[Keys.StationMinutes] = value.coerceIn(0, 180) }
    suspend fun setLeaveReminder(value: Boolean) = edit { it[Keys.LeaveReminder] = value }
    suspend fun setAlerts(value: AlertSettings) = edit {
        it[Keys.AlertDelay] = value.delayMinutes.coerceIn(0, 120)
        it[Keys.AlertConnection] = value.connectionAtRisk
        it[Keys.AlertPlatform] = value.platformChange
        it[Keys.AlertCancellation] = value.cancellation
    }
    suspend fun setTripLocation(value: Boolean) = edit {
        it[Keys.TripLocation] = value
        it[Keys.LocationAsked] = true
    }
    suspend fun setLocationAsked() = edit { it[Keys.LocationAsked] = true }

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

package io.github.numynox.nexus.vibilia.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import io.github.numynox.nexus.vibilia.data.BluetoothLink
import io.github.numynox.nexus.vibilia.data.CarRepository
import io.github.numynox.nexus.vibilia.data.VibiliaPreferences
import java.time.Duration
import java.time.Instant

/** A device this phone is paired with, offered when linking a car. */
data class PairedDevice(val name: String, val address: String, val isCarAudio: Boolean)

/**
 * The entry point for everything Bluetooth: the receivers call it, the
 * settings screen links and unlinks through it, and it decides when the
 * workers run. [DriveTracker] holds the state; this decides what to do with
 * an event.
 */
class DriveTracking(
    private val context: Context,
    val tracker: DriveTracker,
    private val preferences: VibiliaPreferences,
    private val cars: CarRepository,
) {
    /** Android 12+ asks for this before showing paired devices or connection events. */
    val permission: String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_CONNECT else null

    fun hasPermission(): Boolean =
        permission == null || ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    suspend fun onConnected(address: String) {
        val recording = preferences.current().trackDrives && hasPermission()
        val known = tracker.onConnected(address, preferences.cachedLinks(), recording)
        if (known && recording) DriveSync.startHeartbeat(context)
    }

    suspend fun onDisconnected(address: String) {
        tracker.onDisconnected(address, preferences.cachedLinks())
        if (tracker.current().open == null) DriveSync.stopHeartbeat(context)
        // After the grace period, so a quick reconnect is still one drive.
        DriveSync.schedule(context, DriveTracker.ReconnectGrace.plusSeconds(30))
    }

    suspend fun onBoot() {
        tracker.onBoot()
        DriveSync.schedule(context)
    }

    suspend fun setRecording(enabled: Boolean) {
        preferences.setTrackDrives(enabled)
        if (!enabled) {
            tracker.stopRecording()
            DriveSync.stopHeartbeat(context)
            DriveSync.schedule(context)
        }
    }

    /** Fetches the links from the server into the offline cache the receiver reads. */
    suspend fun refreshLinks(): List<BluetoothLink> {
        val links = runCatching { cars.bluetoothLinks() }.getOrElse { return preferences.cachedLinks() }
        preferences.setCachedLinks(links)
        return links
    }

    suspend fun link(carId: String, device: PairedDevice) {
        cars.link(carId, device.address, device.name)
        refreshLinks()
    }

    suspend fun unlink(carId: String, address: String) {
        cars.unlink(carId, address)
        refreshLinks()
    }

    /** Paired devices, cars first. Empty without permission or Bluetooth. */
    @SuppressLint("MissingPermission")
    fun pairedDevices(): List<PairedDevice> {
        if (!hasPermission()) return emptyList()
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
        return runCatching { adapter.bondedDevices.orEmpty() }.getOrDefault(emptySet())
            .map { device ->
                val major = device.bluetoothClass?.majorDeviceClass
                val minor = device.bluetoothClass?.deviceClass
                PairedDevice(
                    name = device.name?.takeIf { it.isNotBlank() } ?: device.address,
                    address = device.address.uppercase(),
                    isCarAudio = minor == BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO ||
                        (major == BluetoothClass.Device.Major.AUDIO_VIDEO && minor == BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE),
                )
            }
            .sortedWith(compareByDescending<PairedDevice> { it.isCarAudio }.thenBy { it.name.lowercase() })
    }

    /**
     * The car to preselect in the refuel form: one connected now, or within
     * the last hour — you usually log a refuel at the pump, just after
     * switching the engine off.
     */
    suspend fun recentCarId(now: Instant = Instant.now()): String? =
        tracker.current().lastConnection
            ?.takeIf { it.connected || Duration.between(it.at, now) <= Duration.ofHours(1) }
            ?.carId
}

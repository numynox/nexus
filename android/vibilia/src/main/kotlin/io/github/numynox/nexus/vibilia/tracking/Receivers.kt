package io.github.numynox.nexus.vibilia.tracking

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.numynox.nexus.vibilia.VibiliaApplication
import kotlinx.coroutines.launch

/**
 * Registered in the manifest, so Android starts the app for it even when it is
 * not running. ACL_CONNECTED/DISCONNECTED are among the implicit broadcasts
 * still delivered to manifest receivers; on Android 12+ they need
 * BLUETOOTH_CONNECT, which the app asks for when tracking is turned on.
 */
class BluetoothConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        } ?: return
        val address = device.address ?: return
        val container = (context.applicationContext as VibiliaApplication).container
        val result = goAsync()
        container.scope.launch {
            try {
                when (intent.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> container.driveTracking.onConnected(address)
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> container.driveTracking.onDisconnected(address)
                }
            } finally {
                result.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val container = (context.applicationContext as VibiliaApplication).container
        val result = goAsync()
        container.scope.launch {
            try {
                container.driveTracking.onBoot()
            } finally {
                result.finish()
            }
        }
    }
}

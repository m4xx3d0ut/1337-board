package org.leetboard.ime.remote

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

data class RemoteHidDevice(
    val address: String,
    val name: String,
)

object BluetoothHidSupport {
    private const val CONTROLLER_DEBUG_SNAPSHOT_FILE = "bluetooth_hid_debug_snapshot.txt"

    fun hasConnectPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun adapter(context: Context): BluetoothAdapter? {
        return context.getSystemService(BluetoothManager::class.java)?.adapter
    }

    @SuppressLint("MissingPermission")
    fun bondedDevices(context: Context): List<RemoteHidDevice> {
        if (!hasConnectPermission(context)) return emptyList()
        val adapter = adapter(context) ?: return emptyList()
        return adapter.bondedDevices
            .map { device ->
                RemoteHidDevice(
                    address = device.address,
                    name = device.safeDisplayName(),
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    @SuppressLint("MissingPermission")
    fun bondedDevice(context: Context, address: String?): BluetoothDevice? {
        if (address.isNullOrBlank() || !hasConnectPermission(context)) return null
        return adapter(context)?.bondedDevices?.firstOrNull { device -> device.address == address }
    }

    fun diagnosticSummary(context: Context): String {
        val apiReady = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        val permissionReady = hasConnectPermission(context)
        val adapter = adapter(context)
        val adapterStatus = when {
            adapter == null -> "unavailable"
            adapter.isEnabled -> "on"
            else -> "off"
        }
        val bondedCount = if (permissionReady) bondedDevices(context).size.toString() else "permission required"
        return buildString {
            appendLine("Bluetooth HID Device")
            appendLine("apiReady=$apiReady")
            appendLine("connectPermission=$permissionReady")
            appendLine("adapter=$adapterStatus")
            appendLine("bondedDevices=$bondedCount")
            appendLine()
            append(controllerSnapshot(context))
        }
    }

    fun writeControllerSnapshot(context: Context, state: RemoteHidState) {
        runCatching {
            context.filesDir.resolve(CONTROLLER_DEBUG_SNAPSHOT_FILE).writeText(
                buildString {
                    appendLine("Controller")
                    appendLine("enabled=${state.enabled}")
                    appendLine("status=${state.status}")
                    appendLine("profileReady=${state.profileReady}")
                    appendLine("registered=${state.registered}")
                    appendLine("activeDeviceName=${state.activeDeviceName.orEmpty()}")
                    appendLine("activeDeviceAddress=${state.activeDeviceAddress.orEmpty()}")
                    appendLine("message=${state.message.orEmpty()}")
                },
            )
        }
    }

    private fun controllerSnapshot(context: Context): String {
        val debugFile = context.filesDir.resolve(CONTROLLER_DEBUG_SNAPSHOT_FILE)
        return if (debugFile.isFile) {
            debugFile.readText()
        } else {
            "Controller\nstatus=no live IME controller snapshot yet\n"
        }
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.safeDisplayName(): String {
        return name?.takeIf { it.isNotBlank() } ?: address
    }
}

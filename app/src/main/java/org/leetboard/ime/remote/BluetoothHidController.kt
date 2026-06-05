package org.leetboard.ime.remote

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Log
import java.util.concurrent.Executor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RemoteHidStatus {
    DISABLED,
    API_TOO_OLD,
    PERMISSION_MISSING,
    BLUETOOTH_UNAVAILABLE,
    BLUETOOTH_OFF,
    NO_DEVICE_SELECTED,
    PROFILE_CONNECTING,
    REGISTERING,
    READY,
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    ERROR,
}

data class RemoteHidState(
    val enabled: Boolean = false,
    val status: RemoteHidStatus = RemoteHidStatus.DISABLED,
    val activeDeviceAddress: String? = null,
    val activeDeviceName: String? = null,
    val message: String? = null,
    val profileReady: Boolean = false,
    val registered: Boolean = false,
) {
    val connected: Boolean
        get() = enabled && status == RemoteHidStatus.CONNECTED
}

class BluetoothHidController(context: Context) {
    private val appContext = context.applicationContext
    private val executor = Executor { command -> command.run() }
    private val _state = MutableStateFlow(RemoteHidState())
    val state: StateFlow<RemoteHidState> = _state.asStateFlow()

    private var hidDevice: BluetoothHidDevice? = null
    private var enabled = false
    private var targetAddress: String? = null
    private var registered = false
    private var activeDevice: BluetoothDevice? = null

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            Log.i(TAG, "HID device profile connected")
            hidDevice = proxy as? BluetoothHidDevice
            if (enabled) registerApp()
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            Log.i(TAG, "HID device profile disconnected")
            hidDevice = null
            registered = false
            activeDevice = null
            updateState(RemoteHidStatus.DISCONNECTED, "Bluetooth HID profile disconnected")
        }
    }

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            Log.i(
                TAG,
                "HID app status changed: registered=$registered pluggedDevice=${pluggedDevice?.debugName()}",
            )
            this@BluetoothHidController.registered = registered
            if (!enabled) return
            if (registered) {
                connectSelectedDevice()
            } else {
                updateState(RemoteHidStatus.ERROR, "Bluetooth HID registration failed")
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            val remoteDevice = device ?: return
            Log.i(TAG, "HID connection state changed: state=$state device=${remoteDevice.debugName()}")
            activeDevice = if (state == BluetoothProfile.STATE_CONNECTED) remoteDevice else activeDevice
            if (!enabled) {
                updateState(RemoteHidStatus.DISABLED, "Bluetooth remote idle", remoteDevice)
                return
            }
            val status = when (state) {
                BluetoothProfile.STATE_CONNECTING -> RemoteHidStatus.CONNECTING
                BluetoothProfile.STATE_CONNECTED -> RemoteHidStatus.CONNECTED
                BluetoothProfile.STATE_DISCONNECTING -> RemoteHidStatus.DISCONNECTED
                else -> RemoteHidStatus.DISCONNECTED
            }
            updateState(
                status = status,
                message = when (status) {
                    RemoteHidStatus.CONNECTED -> "Connected to ${remoteDevice.displayName()}"
                    RemoteHidStatus.CONNECTING -> "Connecting to ${remoteDevice.displayName()}"
                    else -> "Disconnected from ${remoteDevice.displayName()}"
                },
                device = remoteDevice,
            )
        }
    }

    fun setEnabled(enabled: Boolean, activeDeviceAddress: String?) {
        Log.i(TAG, "setEnabled enabled=$enabled activeDeviceAddress=$activeDeviceAddress")
        this.enabled = enabled
        targetAddress = activeDeviceAddress
        if (!enabled) {
            if (activeDeviceAddress.isNullOrBlank()) {
                stop()
            } else {
                suspendInput()
            }
            return
        }
        start()
    }

    fun selectDevice(address: String?) {
        Log.i(TAG, "selectDevice address=$address")
        targetAddress = address
        activeDevice = null
        if (enabled) start()
    }

    fun destroy() {
        stop()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val adapter = BluetoothHidSupport.adapter(appContext)
            hidDevice?.let { adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, it) }
        }
        hidDevice = null
    }

    fun sendKeyboardReport(modifier: Int, usages: List<Int>): Boolean {
        return sendKeyboardReport(modifier, usages, allowWhenDisabled = false)
    }

    private fun sendKeyboardReport(modifier: Int, usages: List<Int>, allowWhenDisabled: Boolean): Boolean {
        val report = ByteArray(KEYBOARD_REPORT_SIZE)
        report[0] = modifier.coerceIn(0, 0xFF).toByte()
        usages.take(KEYBOARD_USAGE_SLOTS).forEachIndexed { index, usage ->
            report[index + 2] = usage.coerceIn(0, 0xFF).toByte()
        }
        return sendReport(KEYBOARD_REPORT_ID, report, allowWhenDisabled)
    }

    fun sendKeyboardChord(modifier: Int, usage: Int): Boolean {
        val down = sendKeyboardReport(modifier, listOf(usage))
        val up = sendKeyboardReport(0, emptyList())
        return down && up
    }

    fun sendMouseReport(buttons: Int, dx: Int, dy: Int, wheel: Int = 0): Boolean {
        return sendMouseReport(buttons, dx, dy, wheel, allowWhenDisabled = false)
    }

    private fun sendMouseReport(
        buttons: Int,
        dx: Int,
        dy: Int,
        wheel: Int,
        allowWhenDisabled: Boolean,
    ): Boolean {
        val report = byteArrayOf(
            buttons.coerceIn(0, 0x07).toByte(),
            dx.coerceIn(MOUSE_AXIS_MIN, MOUSE_AXIS_MAX).toByte(),
            dy.coerceIn(MOUSE_AXIS_MIN, MOUSE_AXIS_MAX).toByte(),
            wheel.coerceIn(MOUSE_AXIS_MIN, MOUSE_AXIS_MAX).toByte(),
        )
        return sendReport(MOUSE_REPORT_ID, report, allowWhenDisabled)
    }

    private fun start() {
        val preflightStatus = preflightStatus()
        if (preflightStatus != null) {
            Log.w(TAG, "start blocked by preflight status=$preflightStatus")
            updateState(preflightStatus)
            return
        }
        if (targetAddress.isNullOrBlank()) {
            Log.w(TAG, "start blocked: no target device selected")
            updateState(RemoteHidStatus.NO_DEVICE_SELECTED, "Pick a paired host in settings")
            return
        }
        val hid = hidDevice
        if (hid == null) {
            updateState(RemoteHidStatus.PROFILE_CONNECTING, "Opening Bluetooth HID profile")
            val proxyRequested = BluetoothHidSupport.adapter(appContext)
                ?.getProfileProxy(appContext, profileListener, BluetoothProfile.HID_DEVICE) == true
            Log.i(TAG, "HID profile proxy requested=$proxyRequested")
            if (!proxyRequested) {
                updateState(RemoteHidStatus.ERROR, "Unable to open Bluetooth HID profile")
            }
            return
        }
        if (!registered) {
            registerApp()
        } else {
            connectSelectedDevice()
        }
    }

    @SuppressLint("MissingPermission")
    private fun registerApp() {
        val hid = hidDevice ?: return
        if (!BluetoothHidSupport.hasConnectPermission(appContext)) {
            updateState(RemoteHidStatus.PERMISSION_MISSING)
            return
        }
        updateState(RemoteHidStatus.REGISTERING, "Registering Bluetooth keyboard")
        val sdp = BluetoothHidDeviceAppSdpSettings(
            HID_NAME,
            HID_DESCRIPTION,
            HID_PROVIDER,
            BluetoothHidDevice.SUBCLASS1_COMBO,
            BluetoothHidReportDescriptor.keyboardMouseCombo,
        )
        val requested = hid.registerApp(sdp, null, null, executor, callback)
        Log.i(TAG, "HID registerApp requested=$requested")
        if (!requested) {
            updateState(RemoteHidStatus.ERROR, "Bluetooth HID registration request failed")
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectSelectedDevice() {
        if (!BluetoothHidSupport.hasConnectPermission(appContext)) {
            updateState(RemoteHidStatus.PERMISSION_MISSING)
            return
        }
        val hid = hidDevice ?: return
        val device = BluetoothHidSupport.bondedDevice(appContext, targetAddress)
        if (device == null) {
            Log.w(TAG, "selected Bluetooth host is not paired: $targetAddress")
            updateState(RemoteHidStatus.NO_DEVICE_SELECTED, "Selected Bluetooth host is not paired")
            return
        }
        activeDevice = device
        if (hid.getConnectionState(device) == BluetoothProfile.STATE_CONNECTED) {
            Log.i(TAG, "selected HID host already connected: ${device.debugName()}")
            updateState(RemoteHidStatus.CONNECTED, "Connected to ${device.displayName()}", device)
            return
        }
        updateState(RemoteHidStatus.CONNECTING, "Connecting to ${device.displayName()}", device)
        val requested = hid.connect(device)
        Log.i(TAG, "HID connect requested=$requested device=${device.debugName()}")
        if (!requested) {
            updateState(RemoteHidStatus.ERROR, "Bluetooth HID connect request failed", device)
        }
    }

    @SuppressLint("MissingPermission")
    private fun suspendInput() {
        releaseReports()
        updateState(RemoteHidStatus.DISABLED, "Bluetooth remote idle")
    }

    @SuppressLint("MissingPermission")
    private fun stop() {
        val hid = hidDevice
        releaseReports()
        activeDevice?.let { device ->
            if (BluetoothHidSupport.hasConnectPermission(appContext)) {
                hid?.disconnect(device)
            }
        }
        if (registered && BluetoothHidSupport.hasConnectPermission(appContext)) {
            hid?.unregisterApp()
        }
        registered = false
        activeDevice = null
        updateState(RemoteHidStatus.DISABLED, "Bluetooth remote disabled")
    }

    private fun releaseReports() {
        sendKeyboardReport(0, emptyList(), allowWhenDisabled = true)
        sendMouseReport(buttons = 0, dx = 0, dy = 0, wheel = 0, allowWhenDisabled = true)
    }

    @SuppressLint("MissingPermission")
    private fun sendReport(reportId: Int, report: ByteArray, allowWhenDisabled: Boolean = false): Boolean {
        if (!BluetoothHidSupport.hasConnectPermission(appContext)) {
            updateState(RemoteHidStatus.PERMISSION_MISSING)
            return false
        }
        val device = activeDevice ?: return false
        val hid = hidDevice ?: return false
        if (!allowWhenDisabled && (!enabled || _state.value.status != RemoteHidStatus.CONNECTED)) {
            Log.w(TAG, "sendReport blocked: enabled=$enabled status=${_state.value.status}")
            return false
        }
        val sent = hid.sendReport(device, reportId, report)
        if (!sent) Log.w(TAG, "sendReport failed: reportId=$reportId device=${device.debugName()}")
        return sent
    }

    private fun preflightStatus(): RemoteHidStatus? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return RemoteHidStatus.API_TOO_OLD
        if (!BluetoothHidSupport.hasConnectPermission(appContext)) return RemoteHidStatus.PERMISSION_MISSING
        val adapter = BluetoothHidSupport.adapter(appContext) ?: return RemoteHidStatus.BLUETOOTH_UNAVAILABLE
        if (!adapter.isEnabled) return RemoteHidStatus.BLUETOOTH_OFF
        return null
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.displayName(): String {
        return name?.takeIf { it.isNotBlank() } ?: address
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.debugName(): String {
        return "${displayName()} <$address>"
    }

    private fun updateState(
        status: RemoteHidStatus,
        message: String? = null,
        device: BluetoothDevice? = activeDevice,
    ) {
        val nextState = RemoteHidState(
            enabled = enabled,
            status = status,
            activeDeviceAddress = device?.address ?: targetAddress,
            activeDeviceName = device?.displayName(),
            message = message,
            profileReady = hidDevice != null,
            registered = registered,
        )
        _state.value = nextState
        BluetoothHidSupport.writeControllerSnapshot(appContext, nextState)
        Log.i(
            TAG,
            "state=${nextState.status} enabled=${nextState.enabled} " +
                "profileReady=${nextState.profileReady} registered=${nextState.registered} " +
                "device=${nextState.activeDeviceName ?: nextState.activeDeviceAddress} message=${nextState.message}",
        )
    }

    private companion object {
        const val TAG = "LeetBluetoothHid"
        const val HID_NAME = "1337 Board"
        const val HID_DESCRIPTION = "1337 Board Remote Keyboard"
        const val HID_PROVIDER = "1337 Board"
        const val KEYBOARD_REPORT_ID = 1
        const val MOUSE_REPORT_ID = 2
        const val KEYBOARD_REPORT_SIZE = 8
        const val KEYBOARD_USAGE_SLOTS = 6
        const val MOUSE_AXIS_MIN = -127
        const val MOUSE_AXIS_MAX = 127
    }
}

private object BluetoothHidReportDescriptor {
    val keyboardMouseCombo: ByteArray = intArrayOf(
        0x05, 0x01, 0x09, 0x06, 0xA1, 0x01, 0x85, 0x01,
        0x05, 0x07, 0x19, 0xE0, 0x29, 0xE7, 0x15, 0x00,
        0x25, 0x01, 0x75, 0x01, 0x95, 0x08, 0x81, 0x02,
        0x95, 0x01, 0x75, 0x08, 0x81, 0x01, 0x95, 0x06,
        0x75, 0x08, 0x15, 0x00, 0x25, 0x65, 0x05, 0x07,
        0x19, 0x00, 0x29, 0x65, 0x81, 0x00, 0xC0,
        0x05, 0x01, 0x09, 0x02, 0xA1, 0x01, 0x85, 0x02,
        0x09, 0x01, 0xA1, 0x00, 0x05, 0x09, 0x19, 0x01,
        0x29, 0x03, 0x15, 0x00, 0x25, 0x01, 0x95, 0x03,
        0x75, 0x01, 0x81, 0x02, 0x95, 0x01, 0x75, 0x05,
        0x81, 0x01, 0x05, 0x01, 0x09, 0x30, 0x09, 0x31,
        0x09, 0x38, 0x15, 0x81, 0x25, 0x7F, 0x75, 0x08,
        0x95, 0x03, 0x81, 0x06, 0xC0, 0xC0,
    ).map { it.toByte() }.toByteArray()
}

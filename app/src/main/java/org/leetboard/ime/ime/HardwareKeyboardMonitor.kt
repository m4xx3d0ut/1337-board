package org.leetboard.ime.ime

import android.content.Context
import android.content.res.Configuration
import android.hardware.input.InputManager
import android.view.InputDevice

class HardwareKeyboardMonitor(
    context: Context,
    private val onChanged: (Boolean) -> Unit,
) : InputManager.InputDeviceListener {
    private val appContext = context.applicationContext
    private val inputManager = appContext.getSystemService(InputManager::class.java)
    private var started = false

    fun start() {
        if (started) return
        started = true
        inputManager?.registerInputDeviceListener(this, null)
        notifyCurrentState()
    }

    fun stop() {
        if (!started) return
        started = false
        inputManager?.unregisterInputDeviceListener(this)
    }

    fun isConnected(): Boolean {
        val configuration = appContext.resources.configuration
        if (!isHardwareKeyboardConfigurationActive(configuration.keyboard, configuration.hardKeyboardHidden)) {
            return false
        }
        val inputKeyboard = InputDevice.getDeviceIds().any { deviceId ->
            val device = InputDevice.getDevice(deviceId) ?: return@any false
            !device.isVirtual &&
                device.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC &&
                device.sources and InputDevice.SOURCE_KEYBOARD == InputDevice.SOURCE_KEYBOARD
        }
        if (inputKeyboard) return true
        return configuration.keyboard == Configuration.KEYBOARD_QWERTY
    }

    override fun onInputDeviceAdded(deviceId: Int) = notifyCurrentState()

    override fun onInputDeviceRemoved(deviceId: Int) = notifyCurrentState()

    override fun onInputDeviceChanged(deviceId: Int) = notifyCurrentState()

    private fun notifyCurrentState() {
        onChanged(isConnected())
    }
}

internal fun isHardwareKeyboardConfigurationActive(keyboard: Int, hardKeyboardHidden: Int): Boolean {
    return keyboard != Configuration.KEYBOARD_NOKEYS &&
        hardKeyboardHidden != Configuration.HARDKEYBOARDHIDDEN_YES
}

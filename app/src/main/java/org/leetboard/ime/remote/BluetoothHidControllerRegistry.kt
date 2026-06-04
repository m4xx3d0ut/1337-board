package org.leetboard.ime.remote

import android.content.Context

object BluetoothHidControllerRegistry {
    private var controller: BluetoothHidController? = null
    private var referenceCount = 0

    @Synchronized
    fun acquire(context: Context): BluetoothHidController {
        val existing = controller
        if (existing != null) {
            referenceCount += 1
            return existing
        }
        return BluetoothHidController(context.applicationContext).also { next ->
            controller = next
            referenceCount = 1
        }
    }

    @Synchronized
    fun release(controller: BluetoothHidController?) {
        if (controller == null || controller !== this.controller) return
        referenceCount = (referenceCount - 1).coerceAtLeast(0)
        if (referenceCount == 0) {
            controller.destroy()
            this.controller = null
        }
    }
}

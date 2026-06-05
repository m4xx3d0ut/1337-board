package org.leetboard.ime.remote

import android.content.Context
import android.os.Handler
import android.os.Looper

object BluetoothHidControllerRegistry {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var controller: BluetoothHidController? = null
    private var referenceCount = 0
    private var idleDestroyRunnable: Runnable? = null

    @Synchronized
    fun acquire(context: Context): BluetoothHidController {
        cancelIdleDestroy()
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
            scheduleIdleDestroy()
        }
    }

    @Synchronized
    private fun cancelIdleDestroy() {
        idleDestroyRunnable?.let(mainHandler::removeCallbacks)
        idleDestroyRunnable = null
    }

    @Synchronized
    private fun scheduleIdleDestroy() {
        cancelIdleDestroy()
        val runnable = Runnable {
            destroyIfStillIdle()
        }
        idleDestroyRunnable = runnable
        mainHandler.postDelayed(runnable, IDLE_DESTROY_DELAY_MS)
    }

    private fun destroyIfStillIdle() {
        val idleController = synchronized(this) {
            if (referenceCount != 0) return
            val current = controller ?: return
            controller = null
            idleDestroyRunnable = null
            current
        }
        idleController.destroy()
    }

    private const val IDLE_DESTROY_DELAY_MS = 120_000L
}

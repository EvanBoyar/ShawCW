package com.shawcw.feedback

import android.content.Context
import android.hardware.camera2.CameraManager

/**
 * Flashes the camera torch while a tone is present. Optional output; not every
 * device has a flash, so failures are swallowed rather than crashing capture.
 */
class TorchFeedback(context: Context) {

    private val cameraManager = context.getSystemService(CameraManager::class.java)
    private val torchCameraId: String? = runCatching {
        cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }.getOrNull()

    private var on = false

    fun setActive(active: Boolean) {
        val id = torchCameraId ?: return
        if (active == on) return
        on = active
        runCatching { cameraManager.setTorchMode(id, active) }
    }

    fun release() {
        setActive(false)
    }
}

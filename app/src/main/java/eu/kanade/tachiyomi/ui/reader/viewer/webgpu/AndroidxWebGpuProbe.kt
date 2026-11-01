package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

import androidx.webgpu.ExperimentalWebGpuApi
import androidx.webgpu.GPU

/**
 * Smoke probe for the androidx.webgpu runtime (WebGPU migration, phase 1).
 *
 * Exercises the minimal bring-up chain:
 * [GPU.createInstance] -> requestAdapter -> requestDevice -> native handle -> close.
 *
 * Deliberately avoids `GPUAdapter.getInfo()` and any ca.mpreg APIs: this only verifies that the
 * library loads and a logical device can be created on the current hardware.
 */
@OptIn(ExperimentalWebGpuApi::class)
object AndroidxWebGpuProbe {

    /**
     * Returns true when an androidx.webgpu device can be created and exposes a valid native
     * handle. Never throws: any failure is reported as false.
     */
    suspend fun probe(): Boolean {
        return try {
            val instance = GPU.createInstance()
            try {
                val adapter = instance.requestAdapter()
                try {
                    val device = adapter.requestDevice()
                    try {
                        device.handle != 0L
                    } finally {
                        device.close()
                    }
                } finally {
                    adapter.close()
                }
            } finally {
                instance.close()
            }
        } catch (e: Exception) {
            false
        }
    }
}

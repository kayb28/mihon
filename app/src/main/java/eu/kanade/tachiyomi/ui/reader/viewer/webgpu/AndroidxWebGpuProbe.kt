package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

import androidx.webgpu.ExperimentalWebGpuApi
import androidx.webgpu.GPU
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

    /** Result of the first probe that finished in this process; null until one does. */
    @Volatile
    private var cachedResult: Boolean? = null

    /** Serializes concurrent first probes so the runtime is only brought up once. */
    private val mutex = Mutex()

    /**
     * Returns true when an androidx.webgpu device can be created and exposes a valid native
     * handle. Never throws: any failure is reported as false.
     *
     * The first call probes the runtime; later calls reuse the process-wide result, so viewers
     * rebuilt on mode changes neither re-pay a known-good runtime nor re-probe one known bad
     * (that viewer falls back instead).
     */
    suspend fun probe(): Boolean {
        cachedResult?.let { return it }
        return mutex.withLock {
            cachedResult ?: probeRuntime().also { cachedResult = it }
        }
    }

    private suspend fun probeRuntime(): Boolean {
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

package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

import androidx.webgpu.ExperimentalWebGpuApi
import androidx.webgpu.GPU
import androidx.webgpu.helper.initLibrary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Smoke probe for the androidx.webgpu runtime (WebGPU migration, phase 1).
 *
 * Exercises the minimal bring-up chain:
 * [initLibrary] -> [GPU.createInstance] -> requestAdapter -> requestDevice -> native handle ->
 * close.
 *
 * Deliberately avoids `GPUAdapter.getInfo()` and any ca.mpreg APIs: this only verifies that
 * androidx.webgpu's own helper can load the bundled Dawn library and that a logical device can
 * be created on the current hardware.
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

    private suspend fun probeRuntime(): Boolean = withContext(Dispatchers.Default) {
        try {
            // GPU.createInstance is a native (JNI) method: the JVM can only resolve it after the
            // bundled Dawn library (libwebgpu_c_bundled.so) is loaded. The renderer's
            // createWebGpu() path does this inside androidx.webgpu.helper's internals; the probe
            // must load the same library before its own createInstance call, or that call throws
            // UnsatisfiedLinkError ("No implementation found ... is the library loaded?").
            initLibrary()
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // UnsatisfiedLinkError (and its LinkageError siblings) are Errors, not Exceptions:
            // catching only Exception let them escape and crash the reader on runtimes that
            // cannot even load the library. Any failure here means "cannot run on this device",
            // which the callers translate into the fallback viewer.
            false
        }
    }
}

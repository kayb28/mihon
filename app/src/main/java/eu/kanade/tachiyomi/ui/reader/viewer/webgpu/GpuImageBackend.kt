package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

/**
 * Minimal lifecycle contract for the GPU-side images the reader viewer draws
 * (WebGPU migration, phase 2).
 *
 * The viewer currently needs exactly three operations on a decoded page image:
 * - [isDestroyed]: true once its GPU resources have been released
 * - [destroy]: release those GPU resources
 * - [invalidate]: request a redraw of the surface showing the image
 *
 * The current behavior is provided by [MpregGpuImageBackend]; the viewer keeps
 * calling ca.mpreg directly until later migration phases switch it over.
 */
interface GpuImageBackend<in T> {
    /** True once the GPU resources behind [image] have been released. */
    fun isDestroyed(image: T): Boolean

    /** Releases the GPU resources behind [image]. Safe to call more than once. */
    fun destroy(image: T)

    /** Requests a redraw of the surface currently showing [image]. */
    fun invalidate(image: T)
}

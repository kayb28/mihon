package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

import android.graphics.Bitmap
import java.nio.ByteBuffer

/**
 * Minimal lifecycle contract for the GPU-side images the reader viewer draws
 * (WebGPU migration, phase 2).
 *
 * The viewer currently needs exactly three operations on a decoded page image:
 * - [isDestroyed]: true once its GPU resources have been released
 * - [destroy]: release those GPU resources
 * - [invalidate]: request a redraw of the surface showing the image
 *
 * plus [createFromBitmap], which starts routing the page model's decoded bitmaps
 * into the GPU pipeline.
 *
 * The current behavior is provided by [MpregGpuImageBackend]; the viewer keeps
 * calling ca.mpreg directly until later migration phases switch it over.
 */
interface GpuImageBackend<T> {
    /** True once the GPU resources behind [image] have been released. */
    fun isDestroyed(image: T): Boolean

    /** Releases the GPU resources behind [image]. Safe to call more than once. */
    fun destroy(image: T)

    /** Requests a redraw of the surface currently showing [image]. */
    fun invalidate(image: T)

    /**
     * Creates a GPU image wrapping the pixels of a decoded page [bitmap]. This starts
     * routing the page model's bitmaps into the GPU pipeline; callers own the returned
     * image and must [destroy] it.
     */
    suspend fun createFromBitmap(bitmap: Bitmap): T

    /**
     * Creates a GPU image wrapping the pixel buffer of a decoder frame [image] produced
     * by the image decoder. Callers own the returned image and must [destroy] it.
     */
    suspend fun createFromImage(image: ByteBuffer, width: Int, height: Int): T
}

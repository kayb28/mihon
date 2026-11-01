package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

import android.graphics.Bitmap
import ca.mpreg.webgpuviewer.renderer.Image
import ca.mpreg.webgpuviewer.viewer.ImagePage
import java.nio.ByteBuffer

/**
 * ca.mpreg bridge for [GpuImageBackend] (WebGPU migration, phase 2).
 *
 * Maps the lifecycle contract onto the [ImagePage] API the viewer uses today.
 * It intentionally does not change any behavior: it is an adapter over the
 * existing calls (`destroyed`, `cleanup()`, `invalidate()`) until the viewer is
 * migrated off ca.mpreg in later phases.
 */
object MpregGpuImageBackend : GpuImageBackend<ImagePage> {

    override fun isDestroyed(image: ImagePage): Boolean = image.destroyed

    override fun destroy(image: ImagePage) {
        image.cleanup()
    }

    override fun invalidate(image: ImagePage) {
        image.invalidate()
    }

    override suspend fun createFromBitmap(bitmap: Bitmap): ImagePage {
        val pixels = if (bitmap.config == Bitmap.Config.ARGB_8888) {
            bitmap
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }
        val buffer = ByteBuffer.allocate(pixels.byteCount)
        pixels.copyPixelsToBuffer(buffer)
        buffer.rewind()
        val image = Image(buffer, pixels.width, pixels.height, createMipMaps = true)
        return ImagePage.ImageSingle(image)
    }

    override suspend fun createFromImage(image: Image, width: Int, height: Int): ImagePage {
        return ImagePage.ImageSingle(Image(image, width, height, createMipMaps = true))
    }
}

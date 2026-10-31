package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

import ca.mpreg.webgpuviewer.viewer.ImagePage

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
}

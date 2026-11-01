package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * WebGPU's default `maxTextureDimension2D` (8192) is too low for tall webtoon pages: a single
 * 800x15005 page throws `ValidationException` in `device.createTexture`, the reader eats the
 * exception per-page, and the screen goes black after the loading animation
 * (POCO peridot / Android 16 / build 8b00e5b: "hace la animación de carga del capítulo pero no
 * muestra imagen solo negro" — the WebGPU error overlay itself names 16384 as the adapter's
 * supported maximum).
 *
 * The renderer must request `maxTextureDimension2D = 16384` in `requiredLimits` when it calls
 * `createWebGpu`, or every page taller than 8192 px reproduces the black screen.
 */
class AndroidxWebGpuDeviceDescriptorTest {

    @Test
    fun `the device descriptor requests maxTextureDimension2D equal to 16384`() {
        val descriptor = buildWebGpuDeviceDescriptor()
        // A null requiredLimits silently falls back to the 8192 spec default — the exact bug
        // this test guards against — so assert the block is present and the field is bumped.
        val limits = descriptor.requiredLimits ?: error("requiredLimits must not be null")
        limits.maxTextureDimension2D shouldBe 16384
    }

    @Test
    fun `the device descriptor is built fresh on every call`() {
        // The helper does not memoize: a stale descriptor retained across environments would
        // outlive a device tear-down and could be observed by the next environment's close path.
        val first = buildWebGpuDeviceDescriptor()
        val second = buildWebGpuDeviceDescriptor()
        (first === second) shouldBe false
        first.requiredLimits?.maxTextureDimension2D shouldBe 16384
        second.requiredLimits?.maxTextureDimension2D shouldBe 16384
    }
}

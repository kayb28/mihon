package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.math.abs

/**
 * Fallo F (PROMPT 27, T-F): after T-E, tall pages render (no more ValidationException)
 * but the paged EXPERIMENTAL viewer aspect-fitted the WHOLE page inside the viewport —
 * an 800x50000 strip squeezed to ~3% scale, "comprimida a ilegible", while the HQ
 * viewer reads fine (region decode windows the content). The placement is pure math,
 * so it is JVM-testable: [LongStripFitPolicy].
 */
class LongStripFitPolicyTest {

    private fun assertClose(expected: Float, actual: Float) {
        // The policy and these expectations run the same float expression order, so the
        // values match exactly; a 1e-4 tolerance guards future refactorings.
        (abs(expected - actual) < 1e-4f) shouldBe true
    }

    // F1: no global scale<1 — a page taller than the viewport is placed at width-fit.
    @Test
    fun `F1 a tall page is placed at width fit, never shrunk to fit the whole page`() {
        val fit = LongStripFitPolicy.place(
            frameWidth = 800,
            frameHeight = 50000,
            surfaceWidth = 1080,
            surfaceHeight = 2400,
            zoom = 1f,
            panX = 0f,
            panY = 0f,
        )
        // Width-fit: the page exactly fills the width (scaleX = zoom = 1).
        assertClose(1f, fit.scaleX)
        // No vertical compression below width-fit: the half-extent is the width-fit's.
        assertClose(50000f * (1080f / 800f) / 2400f, fit.scaleY)
        fit.scaleY shouldBeGreaterThan 1f
    }

    // F1: the window opens at the page's TOP, like HQ's reading position.
    @Test
    fun `F1 the initial view pins the top of the tall page`() {
        val fit = LongStripFitPolicy.place(
            frameWidth = 800,
            frameHeight = 50000,
            surfaceWidth = 1080,
            surfaceHeight = 2400,
            zoom = 1f,
            panX = 0f,
            panY = 0f,
        )
        assertClose(1f, fit.offsetY + fit.scaleY)
    }

    // F1: panning walks the whole page; the viewport never leaves the page.
    @Test
    fun `F1 panning reaches the bottom of the tall page without gaps`() {
        val scaleY = 50000f * (1080f / 800f) / 2400f
        val maxPanY = (scaleY - 1f) * 2400f / 2f
        val top = LongStripFitPolicy.place(800, 50000, 1080, 2400, 1f, 0f, 0f)
        val bottom = LongStripFitPolicy.place(800, 50000, 1080, 2400, 1f, 0f, maxPanY)
        val beyond = LongStripFitPolicy.place(800, 50000, 1080, 2400, 1f, 0f, maxPanY * 10f)
        // Bottom: the page's last row sits on the viewport's bottom edge.
        assertClose(-1f, bottom.offsetY - bottom.scaleY)
        // Over-panning clamps: no gap below the page.
        assertClose(bottom.offsetY, beyond.offsetY)
        // Top: pinned at pan 0.
        assertClose(1f, top.offsetY + top.scaleY)
    }

    // E3 preserved: a page that fits the viewport keeps the pre-existing fit bit-for-bit.
    @Test
    fun `E3 a normal page keeps the pre-existing aspect fit bit for bit`() {
        val fit = LongStripFitPolicy.place(800, 1200, 1080, 2400, 1f, 0f, 0f)
        val fitWidth = 1080f / 800f
        val wholeFit = minOf(fitWidth, 2400f / 1200f)
        assertClose(800f * wholeFit / 1080f, fit.scaleX)
        assertClose(1200f * wholeFit / 2400f, fit.scaleY)
        assertClose(0f, fit.offsetX)
        assertClose(0f, fit.offsetY)
        // Pan has no effect on a page that fits: the old clamp (0) is preserved.
        val panned = LongStripFitPolicy.place(800, 1200, 1080, 2400, 1f, 500f, 500f)
        assertClose(0f, panned.offsetX)
        assertClose(0f, panned.offsetY)
    }

    // F2: HQ equivalence — same readability as HQ's windowed tall page: width filled,
    // content at native placement scale (>= width-fit), never compressed.
    @Test
    fun `F2 a tall page reads like HQ: width filled, scale at least width-fit`() {
        val fit = LongStripFitPolicy.place(800, 50000, 1080, 2400, 1f, 0f, 0f)
        assertClose(1f, fit.scaleX)
        assertClose(1f * 50000f * (1080f / 800f) / 2400f, fit.scaleY)
        // Zoom never shrinks either axis below the width-fit placement.
        val zoomed = LongStripFitPolicy.place(800, 50000, 1080, 2400, 2f, 0f, 0f)
        assertClose(2f, zoomed.scaleX)
        assertClose(2f * 50000f * (1080f / 800f) / 2400f, zoomed.scaleY)
    }

    // F2: paged/continuous parity. The continuous EXPERIMENTAL viewer draws each page
    // at stripPageWidthPx / surfaceWidth; with the default preferences (0% side padding,
    // 100% strip scale — the stock-parity values) that fraction is exactly 1.0. The
    // paged policy's tall-page placement must match it, so a long strip reads at the
    // same scale in both EXPERIMENTAL modes (and like HQ's width windowing).
    @Test
    fun `F2 the paged tall-page window matches the continuous strip's default width placement`() {
        val fit = LongStripFitPolicy.place(800, 50000, 1080, 2400, 1f, 0f, 0f)
        assertClose(1f, fit.scaleX)
    }

    // F3: the fit change does not regress Fallo E — the tile plan is untouched, and the
    // placed tiles still partition the page quad exactly (top of the first tile at the
    // page top, bottom of the last at the page bottom).
    @Test
    fun `F3 the tile plan still bounds every tile and the placed quads partition the page`() {
        val plan = GpuTilePlanPolicy.planTiles(width = 800, height = 50000, maxDimension = 16384)
        plan.size shouldBe 4
        plan.forEach { tile ->
            (tile.width <= 16384) shouldBe true
            (tile.height <= 16384) shouldBe true
        }
        val fit = LongStripFitPolicy.place(800, 50000, 1080, 2400, 1f, 0f, 0f)
        val first = GpuTilePlanPolicy.tileQuad(
            frameWidth = 800, frameHeight = 50000, tile = plan.first(),
            pageScaleX = fit.scaleX, pageScaleY = fit.scaleY,
            pageOffsetX = fit.offsetX, pageOffsetY = fit.offsetY,
        )
        val last = GpuTilePlanPolicy.tileQuad(
            frameWidth = 800, frameHeight = 50000, tile = plan.last(),
            pageScaleX = fit.scaleX, pageScaleY = fit.scaleY,
            pageOffsetX = fit.offsetX, pageOffsetY = fit.offsetY,
        )
        assertClose(fit.offsetY + fit.scaleY, first.offsetY + first.scaleY)
        assertClose(fit.offsetY - fit.scaleY, last.offsetY - last.scaleY)
    }
}

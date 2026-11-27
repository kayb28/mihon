package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeAtMost
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Behavior tests for the tile planning policy of Fallo E (PROMPT 9, T-E, behaviors E1-E3).
 * Red state: they run against the baseline where every frame becomes ONE texture of the
 * frame's full size — taller than the GPU limit it throws ValidationException — and fail
 * on BEHAVIOR, one group per behavior ID.
 *
 * Pure JVM tests: the policy only computes tile rectangles; the GPU wiring (limits query,
 * per-tile textures, per-tile quads) is device-validated per the merge gate.
 */
class GpuTilePlanPolicyTest {

    // ---------------------------------------------------------------- E1

    @Test
    fun `E1 content under the limit is a single tile`() {
        val tiles = GpuTilePlanPolicy.planTiles(width = 800, height = 4000, maxDimension = 8192)
        tiles shouldHaveSize 1
        tiles[0] shouldBe GpuTilePlanPolicy.Tile(sourceX = 0, sourceY = 0, width = 800, height = 4000)
    }

    @Test
    fun `E1 tall content splits into ordered tiles within the limit at 8192`() {
        // The reported case: 800x50000 on a GPU capped at 8192.
        val tiles = GpuTilePlanPolicy.planTiles(width = 800, height = 50000, maxDimension = 8192)
        // 50000 = 7 * 8192 = 57344 covered, remainder 2656 -> 8 tiles
        tiles shouldHaveSize 8
        tiles.forEach { it.height shouldBe 8192 }
        tiles[7].height shouldBe 2656
        tiles.forEachIndexed { i, t -> t.sourceY shouldBe i * 8192 }
        // every tile respects the GPU ceiling
        tiles.forEach { it.height shouldBe 8192 }
    }

    @Test
    fun `E1 tall content splits correctly at 16384`() {
        val tiles = GpuTilePlanPolicy.planTiles(width = 800, height = 50000, maxDimension = 16384)
        // 50000 = 3 * 16384 = 49152 covered, remainder 848 -> 4 tiles
        tiles shouldHaveSize 4
        tiles[0].height shouldBe 16384
        tiles[3].height shouldBe 848
        tiles.forEachIndexed { i, t -> t.sourceY shouldBe i * 16384 }
    }

    @Test
    fun `E1 exact multiples have no zero-height tail`() {
        val tiles = GpuTilePlanPolicy.planTiles(width = 800, height = 16384, maxDimension = 16384)
        tiles shouldHaveSize 1
        GpuTilePlanPolicy.planTiles(width = 800, height = 32768, maxDimension = 16384).let {
            it shouldHaveSize 2
            it.forEach { t -> t.height shouldBe 16384 }
        }
    }

    @Test
    fun `E1 tiles cover the content with no gaps and no overlaps`() {
        for (height in intArrayOf(50000, 50001, 16385, 24576, 8193)) {
            val tiles = GpuTilePlanPolicy.planTiles(width = 800, height = height, maxDimension = 8192)
            var expectedY = 0
            for (t in tiles) {
                t.sourceY shouldBe expectedY
                t.height shouldBeGreaterThan 0
                expectedY += t.height
            }
            expectedY shouldBe height
        }
    }

    // ---------------------------------------------------------------- E2 (pure part)

    @Test
    fun `E2 oversized width also tiles horizontally`() {
        // A panorama page wider than the limit: the plan must split the width too.
        val tiles = GpuTilePlanPolicy.planTiles(width = 20000, height = 1000, maxDimension = 16384)
        tiles.size shouldBe 2
        tiles[0].sourceX shouldBe 0
        tiles[1].sourceX shouldBe 16384
        tiles.forEach {
            it.sourceY shouldBe 0
            it.height shouldBe 1000
        }
    }

    @Test
    fun `E2 every planned tile fits the device limit in both dimensions`() {
        for (w in intArrayOf(800, 8192, 8193, 16384, 20000)) {
            for (h in intArrayOf(1000, 8193, 16384, 50000, 65536)) {
                GpuTilePlanPolicy.planTiles(width = w, height = h, maxDimension = 8192).forEach { t ->
                    t.width shouldBeAtMost 8192
                    t.height shouldBeAtMost 8192
                }
            }
        }
    }

    // ---------------------------------------------------------------- E3 (regression, pure part)

    @Test
    fun `E3 normal pages keep today's single-tile plan`() {
        // Ordinary page sizes (portrait manga, landscape, webtoon strip shorter than the
        // limit): the plan is exactly ONE tile covering the whole frame.
        for (h in intArrayOf(1000, 2000, 4000, 8192)) {
            val tiles = GpuTilePlanPolicy.planTiles(width = 1080, height = h, maxDimension = 8192)
            tiles shouldHaveSize 1
            tiles[0] shouldBe GpuTilePlanPolicy.Tile(sourceX = 0, sourceY = 0, width = 1080, height = h)
        }
    }

    @Test
    fun `E3 degenerate input does not explode`() {
        GpuTilePlanPolicy.planTiles(width = 0, height = 0, maxDimension = 8192).shouldBeEmpty()
        GpuTilePlanPolicy.planTiles(width = 10, height = 10, maxDimension = 0).shouldBeEmpty()
    }

    // ---------------------------------------------------------------- E3 (quad regression)

    @Test
    fun `E3 single-tile quad equals the page quad bit-for-bit`() {
        // A normal page (one tile covering the whole frame): the quad the shader receives
        // must be EXACTLY the page's own quad — the pre-tiling behavior.
        val quad = GpuTilePlanPolicy.tileQuad(
            frameWidth = 1080,
            frameHeight = 4000,
            tile = GpuTilePlanPolicy.Tile(sourceX = 0, sourceY = 0, width = 1080, height = 4000),
            pageScaleX = 0.5f,
            pageScaleY = 1.2f,
            pageOffsetX = 0.1f,
            pageOffsetY = -0.3f,
        )
        quad.scaleX shouldBe 0.5f
        quad.scaleY shouldBe 1.2f
        quad.offsetX shouldBe 0.1f
        quad.offsetY shouldBe -0.3f
    }

    @Test
    fun `E3 tiled quads partition the page quad with no gaps or overlaps`() {
        // Two vertical tiles of an 800x50000 page (8192 cap): each band's quad must cover
        // exactly its share of the page quad, in order, without seam or overlap.
        val pageScaleX = 0.8f
        val pageScaleY = 2.0f
        val pageOffsetX = 0f
        val pageOffsetY = 0.4f
        val tiles = GpuTilePlanPolicy.planTiles(width = 800, height = 16384, maxDimension = 8192)
        tiles shouldHaveSize 2
        val quads = tiles.map {
            GpuTilePlanPolicy.tileQuad(800, 16384, it, pageScaleX, pageScaleY, pageOffsetX, pageOffsetY)
        }
        // Each quad's height is its exact share of the page quad.
        quads[0].scaleY shouldBe pageScaleY / 2f
        quads[1].scaleY shouldBe pageScaleY / 2f
        quads.forEach {
            it.scaleX shouldBe pageScaleX
            it.offsetX shouldBe pageOffsetX
        }
        // Band centers: top band sits above the page center, bottom band below, by exactly
        // half the page quad's height — together they rebuild the page quad seamlessly.
        (quads[0].offsetY - quads[1].offsetY) shouldBe pageScaleY
        ((quads[0].offsetY + quads[0].scaleY) - (pageOffsetY + pageScaleY)) shouldBe 0f
        ((quads[1].offsetY - quads[1].scaleY) - (pageOffsetY - pageScaleY)) shouldBe 0f
        // Order preserved: tile 0 is the TOP band (bigger NDC y).
        (quads[0].offsetY > quads[1].offsetY) shouldBe true
    }
}

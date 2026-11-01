package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AndroidxWebGpuPageCachePolicyTest {

    @Test
    fun `window covers the anchor and clips at both ends`() {
        AndroidxWebGpuPageCachePolicy.window(0, 10, behind = 2, ahead = 3) shouldBe 0..3
        AndroidxWebGpuPageCachePolicy.window(5, 10, behind = 2, ahead = 3) shouldBe 3..8
        AndroidxWebGpuPageCachePolicy.window(9, 10, behind = 2, ahead = 3) shouldBe 7..9
    }

    @Test
    fun `an anchor outside the list clips to its nearest end`() {
        AndroidxWebGpuPageCachePolicy.window(99, 10, behind = 2, ahead = 3) shouldBe 7..9
        AndroidxWebGpuPageCachePolicy.window(-3, 10, behind = 2, ahead = 3) shouldBe 0..3
    }

    @Test
    fun `an empty page list has no window`() {
        AndroidxWebGpuPageCachePolicy.window(0, 0, behind = 2, ahead = 3) shouldBe IntRange.EMPTY
    }

    @Test
    fun `a single page is its own whole window`() {
        AndroidxWebGpuPageCachePolicy.window(0, 1, behind = 2, ahead = 3) shouldBe 0..0
    }

    @Test
    fun `victim is the page farthest from the anchor, never the anchor`() {
        AndroidxWebGpuPageCachePolicy.victim(
            candidates = setOf("near", "anchor", "far"),
            positions = mapOf("anchor" to 5, "near" to 6, "far" to 0),
            anchor = "anchor",
            anchorPosition = 5,
        ) shouldBe "far"
    }

    @Test
    fun `a page without a position is evicted before anything positioned`() {
        AndroidxWebGpuPageCachePolicy.victim(
            candidates = setOf("positioned", "orphan"),
            positions = mapOf("anchor" to 1, "positioned" to 2),
            anchor = "anchor",
            anchorPosition = 1,
        ) shouldBe "orphan"
    }

    @Test
    fun `nothing to evict when only the anchor is cached`() {
        AndroidxWebGpuPageCachePolicy.victim(
            candidates = setOf("anchor"),
            positions = mapOf("anchor" to 4),
            anchor = "anchor",
            anchorPosition = 4,
        ) shouldBe null
    }

    @Test
    fun `ties keep the caller's iteration order`() {
        AndroidxWebGpuPageCachePolicy.victim(
            candidates = linkedSetOf("first", "second"),
            positions = mapOf("anchor" to 4, "first" to 0, "second" to 8),
            anchor = "anchor",
            anchorPosition = 4,
        ) shouldBe "first"
    }

    @Test
    fun `a missing anchor evicts by distance from position zero`() {
        AndroidxWebGpuPageCachePolicy.victim(
            candidates = setOf("close", "distant"),
            positions = mapOf("close" to 2, "distant" to 6),
            anchor = null,
            anchorPosition = 0,
        ) shouldBe "distant"
    }
}

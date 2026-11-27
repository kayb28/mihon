package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

/**
 * Fallo E (PROMPT 9, T-E): pure tile-planning policy for GPU textures taller/wider than
 * the device's `maxTextureDimension2D`. The WebGPU viewer used to compose long webtoon
 * strips / ultra-tall pages into ONE giant texture (e.g. 800x50000) and `createTexture`
 * threw ValidationException once the size passed the limit (16384 on most GPUs, 8192 on
 * some). This policy splits oversized content into ordered tiles; the wiring uploads one
 * texture per tile and draws one quad per tile — no scaling, no quality loss, correct
 * order (same pattern as ContinuousPageReportPolicy: pure decisions, JVM-testable).
 *
 * RED SKELETON: returns an empty plan; the red tests fail on BEHAVIOR (today the plan is
 * "one texture the full frame size, whatever the device limit").
 */
object GpuTilePlanPolicy {

    /** One tile: a source rectangle of the decoded frame, at ([sourceX], [sourceY]). */
    data class Tile(
        val sourceX: Int = 0,
        val sourceY: Int = 0,
        val width: Int,
        val height: Int,
    )

    /**
     * E1/E2: plan the tiles for a [width]x[height] frame on a device whose texture
     * dimension limit is [maxDimension]. Tiles are ordered top-to-bottom (and, for
     * oversized widths, left-to-right within each band), cover the frame exactly with no
     * gaps or overlaps, never exceed the limit in either dimension, and never scale the
     * content. Frames that fit return a single tile covering everything (E3: normal
     * pages keep today's one-texture plan). Degenerate frames return an empty list.
     */
    fun planTiles(width: Int, height: Int, maxDimension: Int): List<Tile> = emptyList()
}

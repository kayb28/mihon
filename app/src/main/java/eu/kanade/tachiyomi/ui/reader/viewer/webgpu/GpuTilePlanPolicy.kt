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
 * Bands run top-to-bottom; tiles within a band left-to-right: reading order.
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
    /**
     * E3: the NDC quad for one tile of a page whose full-frame quad is
     * ([pageScaleX], [pageScaleY], [pageOffsetX], [pageOffsetY]). Single-tile pages
     * (sourceX=0, sourceY=0, width=frameWidth, height=frameHeight) return the page's
     * own quad bit-for-bit — the pre-tiling behavior, so normal pages are unchanged.
     */
    fun tileQuad(
        frameWidth: Int,
        frameHeight: Int,
        tile: Tile,
        pageScaleX: Float,
        pageScaleY: Float,
        pageOffsetX: Float,
        pageOffsetY: Float,
    ): Quad {
        val cx = (tile.sourceX + tile.width / 2f) / frameWidth * 2f - 1f
        val cy = 1f - (tile.sourceY + tile.height / 2f) / frameHeight * 2f
        return Quad(
            scaleX = pageScaleX * tile.width / frameWidth,
            scaleY = pageScaleY * tile.height / frameHeight,
            offsetX = pageOffsetX + pageScaleX * cx,
            offsetY = pageOffsetY + pageScaleY * cy,
        )
    }

    /** One drawable quad's NDC transform (half-extents + center), matching the WGSL Params. */
    data class Quad(
        val scaleX: Float,
        val scaleY: Float,
        val offsetX: Float,
        val offsetY: Float,
    )

    fun planTiles(width: Int, height: Int, maxDimension: Int): List<Tile> {
        if (width <= 0 || height <= 0 || maxDimension <= 0) return emptyList()
        val tiles = ArrayList<Tile>()
        var bandY = 0
        while (bandY < height) {
            val bandHeight = minOf(maxDimension, height - bandY)
            var tileX = 0
            while (tileX < width) {
                val tileWidth = minOf(maxDimension, width - tileX)
                tiles.add(Tile(sourceX = tileX, sourceY = bandY, width = tileWidth, height = bandHeight))
                tileX += tileWidth
            }
            bandY += bandHeight
        }
        return tiles
    }
}

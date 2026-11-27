package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

/**
 * Fallo F (PROMPT 27, T-F): pure placement policy for the paged EXPERIMENTAL viewer.
 * The pre-T-E code aspect-fitted the WHOLE page inside the viewport
 * (`fit = min(surfaceWidth/pageWidth, surfaceHeight/pageHeight)`): before T-E a tall
 * page crashed at createTexture, so nobody saw the placement; after T-E it renders —
 * compressed. An 800x50000 strip on a 1080x2400 screen squeezed to ~3% scale, which the
 * user reported as "imágenes largas COMPRIMIDAS" while the HQ viewer reads fine.
 *
 * HQ never compresses: its subsampling path windows the content — it fits the WIDTH and
 * lets you pan/scroll the rest (the same windowing T6a wired as loadPage por página de
 * la ventana). This policy mirrors that placement for the render path:
 *
 * - A page that fits the viewport whole keeps the pre-existing aspect-fit math
 *   bit-for-bit (E3: normal pages are unchanged, one tile, the usual quad).
 * - A page taller than the viewport is placed at WIDTH-FIT (never a global scale<1
 *   below width-fit), pinned to the page's TOP on arrival, and pannable through the
 *   rest — same readability HQ gives, without touching the tile plan (E/F3: tiles
 *   still sample native pixels and never exceed maxTextureDimension2D).
 */
object LongStripFitPolicy {

    /** One placed page: NDC half-extents + center, matching the WGSL Params layout. */
    data class Fit(
        val scaleX: Float,
        val scaleY: Float,
        val offsetX: Float,
        val offsetY: Float,
    )

    /**
     * The NDC transform for a [frameWidth]x[frameHeight] page on a
     * [surfaceWidth]x[surfaceHeight] viewport, with [zoom] >= 1 and the user's pan
     * ([panX], [panY], surface px, y down — the gesture layer's conventions).
     */
    fun place(
        frameWidth: Int,
        frameHeight: Int,
        surfaceWidth: Int,
        surfaceHeight: Int,
        zoom: Float,
        panX: Float,
        panY: Float,
    ): Fit {
        val sw = surfaceWidth.coerceAtLeast(1).toFloat()
        val sh = surfaceHeight.coerceAtLeast(1).toFloat()
        val fw = frameWidth.coerceAtLeast(1).toFloat()
        val fh = frameHeight.coerceAtLeast(1).toFloat()
        val z = zoom.coerceAtLeast(1f)

        val fitWidth = sw / fw
        val tallPage = fh * fitWidth > sh

        val scaleX: Float
        val scaleY: Float
        val baseOffsetY: Float
        if (tallPage) {
            // F1: width-fit — the content is never shrunk below the width-fit scale.
            scaleX = z
            scaleY = fh * fitWidth * z / sh
            // F1: the window opens at the page's TOP (HQ's reading position for a
            // windowed strip), not at a shrunk whole-page overview.
            baseOffsetY = 1f - scaleY
        } else {
            // E3: the whole page fits — the pre-existing aspect-fit math, bit-for-bit.
            val fit = minOf(fitWidth, sh / fh)
            scaleX = fw * fit / sw * z
            scaleY = fh * fit / sh * z
            baseOffsetY = 0f
        }

        val maxPanX = (scaleX - 1f).coerceAtLeast(0f)
        val offsetX = (panX / sw * 2f).coerceIn(-maxPanX, maxPanX)
        // Surface px are y down, NDC y up, so the pan's y flips. The tall-page clamp is
        // ASYMMETRIC around the top-pinned base: the window slides from the page's top
        // edge (pan 0) to its bottom edge, and the viewport never leaves the page. The
        // normal page keeps the pre-existing symmetric clamp (±maxPanY, 0 when the page
        // fits vertically — exactly the old behavior).
        val offsetY = if (tallPage) {
            (baseOffsetY - panY / sh * 2f).coerceIn(1f - scaleY, scaleY - 1f)
        } else {
            val maxPanY = (scaleY - 1f).coerceAtLeast(0f)
            (baseOffsetY - panY / sh * 2f).coerceIn(-maxPanY, maxPanY)
        }
        return Fit(scaleX = scaleX, scaleY = scaleY, offsetX = offsetX, offsetY = offsetY)
    }
}

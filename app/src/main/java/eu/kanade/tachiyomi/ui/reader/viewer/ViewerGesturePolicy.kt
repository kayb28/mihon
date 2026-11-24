package eu.kanade.tachiyomi.ui.reader.viewer

/**
 * Shared gesture decision layer for every viewer (T4): tap zones already funnel through
 * [eu.kanade.tachiyomi.ui.reader.ReaderActivity.onViewerTap]; what this object unifies is the
 * rest of the manual that used to live duplicated per viewer — the long-press gate, the
 * double-tap zoom toggle, the pinch zoom clamp and anchor math, and the swipe-to-turn
 * accumulator.
 *
 * Pure and JVM-testable (no Android imports): viewers keep the Compose plumbing but ask this
 * policy what a gesture MEANS, so all viewers answer identically and a new viewer gets the
 * whole manual for free.
 */
object ViewerGesturePolicy {

    /** Zoom cap for pinch and double tap; 1 shows the page aspect-fitted. */
    const val MAX_ZOOM = 4f

    /** Zoom a double tap toggles to (and back from). */
    const val DOUBLE_TAP_ZOOM = 2.5f

    /** Fraction of the viewport a fitted swipe must travel to turn one page. */
    const val SWIPE_THRESHOLD_FRACTION = 0.18f

    /**
     * Long-press gate shared by the stock, HQ and experimental viewers: the page dialog is
     * opt-in — it opens when the reader menu is already visible or the user enabled long tap.
     */
    fun longPressOpensPageDialog(menuVisible: Boolean, longTapEnabled: Boolean): Boolean =
        menuVisible || longTapEnabled

    /**
     * Pan that keeps the point under the gesture centroid stationary when the zoom changes by
     * [zoomRatio] (new/old). Coordinates are center-relative because the GPU renderers scale
     * about the surface center; using top-left coordinates makes double taps land on the wrong
     * point and pinches drift.
     */
    fun anchoredPan(
        oldPanX: Float,
        oldPanY: Float,
        zoomRatio: Float,
        centroidX: Float,
        centroidY: Float,
    ): Pair<Float, Float> = Pair(
        centroidX - (centroidX - oldPanX) * zoomRatio,
        centroidY - (centroidY - oldPanY) * zoomRatio,
    )

    /** Zoom is clamped to [1, MAX_ZOOM]; 1 is fit and cannot be undershot by a pinch. */
    fun clampZoom(zoom: Float): Float = zoom.coerceIn(1f, MAX_ZOOM)

    /** A double tap toggles between fit and [DOUBLE_TAP_ZOOM]; zoomed → back to fit. */
    fun doubleTapTargetZoom(currentZoom: Float): Float = if (currentZoom > 1f) 1f else DOUBLE_TAP_ZOOM

    /**
     * Swipe-to-turn-page accumulator (paged viewers): while the page is fitted, one-finger drag
     * distance accumulates along the navigation axis (Y for vertical modes, X otherwise) and a
     * right-to-left layout inverts the axis so the finger always means "forward in reading
     * order". Crossing [SWIPE_THRESHOLD_FRACTION] of the viewport turns one page and resets.
     */
    class SwipeAccumulator {
        /** Accumulated reading-axis distance; main thread only. */
        var accumulated: Float = 0f
            private set

        fun reset() {
            accumulated = 0f
        }

        /**
         * Feeds one drag event. Returns +1 to turn to the next page in reading order, -1 for
         * the previous page, or 0 while below the threshold.
         */
        fun onDrag(
            panX: Float,
            panY: Float,
            isVertical: Boolean,
            isRightToLeft: Boolean,
            viewportWidth: Float,
            viewportHeight: Float,
        ): Int {
            val delta = if (isVertical) panY else panX
            val readingDelta = if (isRightToLeft) -delta else delta
            accumulated += readingDelta
            val threshold = (if (isVertical) viewportHeight else viewportWidth) * SWIPE_THRESHOLD_FRACTION
            if (kotlin.math.abs(accumulated) >= threshold) {
                val step = if (accumulated > 0) 1 else -1
                accumulated = 0f
                return step
            }
            return 0
        }
    }
}

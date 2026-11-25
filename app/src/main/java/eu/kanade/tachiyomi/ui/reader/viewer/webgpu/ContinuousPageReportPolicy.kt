package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

/**
 * Pure layout-and-report policy for the androidx.webgpu continuous viewer (device fix R1).
 *
 * The viewer's strip is a list of page offsets and heights; which page the reader reports as
 * current derives from just three numbers: the scroll position, the viewport height and that
 * layout. Keeping the rule here makes the restore-drift the device showed reproducible and
 * fixable in a plain JVM test — no views, no WebGPU, no Android framework.
 */
internal object ContinuousPageReportPolicy {

    /** A strip layout: each page's top offset and height, both in surface pixels. */
    data class Layout(
        val offsets: List<Float>,
        val heights: List<Float>,
    )

    /**
     * Index of the page the reader reports as current at [scrollPos]: the page that contains
     * the TOP of the viewport — exactly the page a restore is anchored to.
     *
     * Why top-of-viewport and not end-visible: the end-visible rule from the first drift fix
     * only stabilized restores for pages between half and one viewport tall. A page TALLER
     * than the viewport never has its end inside the viewport at restore, so the page before
     * it won and every reopen drifted one page BACK; a page SHORTER than half the viewport
     * let whole later pages fit below the anchor and drifted FORWARD. Reporting the page
     * under the viewport top is parametric in page height: a restore of page s at
     * scroll = offsets[s] always reports s, tall, short or mixed.
     *
     * The single exemption (P2): when the viewport already reaches the end of the strip
     * (scroll + viewport >= totalHeight), the end-of-chapter rule wins and the LAST page is
     * reported — restoring the penultimate page with the final in view converges there in
     * one step and then holds. Outside that cap, P1 governs.
     */
    fun reportedPageIndex(layout: Layout, scrollPos: Float, viewportHeight: Float): Int {
        if (layout.offsets.isEmpty()) return -1
        val totalHeight = layout.offsets.last() + layout.heights.last()
        val viewport = viewportHeight.coerceAtLeast(1f)
        if (scrollPos + viewport >= totalHeight) return layout.offsets.lastIndex
        // The page whose span contains the viewport top: the last page starting at or
        // before scrollPos (a gap between chapters counts into the page above it).
        var lo = 0
        var hi = layout.offsets.lastIndex
        var index = 0
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (layout.offsets[mid] <= scrollPos) {
                index = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return index
    }
}

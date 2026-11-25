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
     * Index of the page the reader reports as current at [scrollPos].
     *
     * BUG (device failure B, drift +1): the old rule reported the deepest page whose TOP is
     * strictly above the viewport bottom — a page merely peeking into the viewport counts as
     * read. A restore glues the saved page's top to the viewport top, so whenever the saved
     * page is shorter than the viewport, the next page's top peeks in and gets reported and
     * saved: every open→close→reopen cycle advances the saved page by one, deterministically.
     */
    fun reportedPageIndex(layout: Layout, scrollPos: Float, viewportHeight: Float): Int {
        if (layout.offsets.isEmpty()) return -1
        val viewportBottom = scrollPos + viewportHeight.coerceAtLeast(1f)
        var lo = 0
        var hi = layout.offsets.lastIndex
        var index = 0
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (layout.offsets[mid] < viewportBottom) {
                index = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return index
    }
}

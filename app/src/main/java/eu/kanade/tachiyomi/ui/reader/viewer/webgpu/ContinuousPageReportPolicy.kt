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
     * Index of the page the reader reports as current at [scrollPos]: the deepest page whose
     * END has entered the viewport.
     *
     * Device failure B (drift +1) and the fix: the old rule reported the deepest page whose
     * TOP was above the viewport bottom — a page merely peeking in counted as read. A restore
     * glues the saved page's top to the viewport top, so a saved page shorter than the
     * viewport immediately reported the next page peeking below it, saving +1 per open→
     * close→reopen cycle. Counting pages by their END instead keeps a restore reporting
     * exactly the restored page (its end is inside the viewport; the next page's end is not
     * until the reader actually scrolls it in), while the end-of-chapter "read" case still
     * reports the last page as soon as its end is on screen — matching the HQ continuous
     * viewer, which selects a page once its bottom has cleared the viewport.
     */
    fun reportedPageIndex(layout: Layout, scrollPos: Float, viewportHeight: Float): Int {
        if (layout.offsets.isEmpty()) return -1
        val viewportBottom = scrollPos + viewportHeight.coerceAtLeast(1f)
        var lo = 0
        var hi = layout.offsets.lastIndex
        var index = -1
        // First page whose end reaches past the viewport bottom: everything before it has
        // its end fully on screen, so the deepest end-visible page is the one right before.
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            val end = layout.offsets[mid] + layout.heights[mid]
            if (end <= viewportBottom) {
                index = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        if (index >= 0) return index
        // No page's end is fully on screen yet (a tall page at the very top): report the
        // page the viewport starts inside — same as the restore target it is anchored to.
        var start = 0
        var result = 0
        while (start < layout.offsets.size && layout.offsets[start] <= scrollPos) {
            result = start
            start++
        }
        return result
    }
}

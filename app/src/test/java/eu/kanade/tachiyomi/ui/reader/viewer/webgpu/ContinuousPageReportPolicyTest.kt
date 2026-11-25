package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Device-validation fix R1 (drift +1): a continuous-viewer chapter restore must be stable
 * across open→close→reopen cycles. The old report rule ("deepest page whose TOP is on
 * screen") counts the next page as read the moment its first pixel peeks below the
 * viewport — and since a restore glues the saved page's top to the viewport top, a saved
 * page shorter than the viewport immediately reports the page after it, saving +1. Five
 * reopen cycles then drift five pages.
 *
 * The policy here is pure: offsets, page heights, viewport height, saved index. No views,
 * no WebGPU, no Android framework — plain numbers, so the drift reproduces in a JVM test.
 */
class ContinuousPageReportPolicyTest {

    private fun layout(pageCount: Int, pageHeight: Float, gap: Float = 0f): ContinuousPageReportPolicy.Layout {
        val heights = List(pageCount) { pageHeight }
        val offsets = ArrayList<Float>(pageCount)
        var y = 0f
        repeat(pageCount) {
            offsets.add(y)
            y += pageHeight
            if (it != pageCount - 1) y += gap
        }
        return ContinuousPageReportPolicy.Layout(offsets = offsets, heights = heights)
    }

    @Test
    fun `restore is stable across five open-close-reopen cycles`() {
        // Device scenario: 10 pages shorter than the viewport, page 4 saved, so the next
        // page partially peeks below it after a restore. Each reopen anchors page 4's top
        // to the viewport top; whatever the policy reports gets saved as lastPageRead,
        // which the next cycle restores. After 5 cycles the drift must still be zero —
        // the reader reopens on page 4 every time.
        val viewport = 2000f
        val pages = layout(pageCount = 10, pageHeight = 1200f)
        var savedIndex = 4
        repeat(5) {
            val scroll = pages.offsets[savedIndex]
            val reported = ContinuousPageReportPolicy.reportedPageIndex(
                layout = pages,
                scrollPos = scroll,
                viewportHeight = viewport,
            )
            assertEquals(
                savedIndex,
                reported,
                "reopen cycle ${it + 1}: restore of page $savedIndex must report page $savedIndex back",
            )
            savedIndex = reported
        }
    }

    @Test
    fun `scrolled to the very bottom still reports the last page`() {
        // The end-of-chapter "read" case must keep working: at the bottom of the document
        // the deepest end-visible page is the last one.
        val viewport = 2000f
        val pages = layout(pageCount = 10, pageHeight = 1000f)
        val bottomScroll = pages.offsets.last() + 1000f - viewport
        val reported = ContinuousPageReportPolicy.reportedPageIndex(
            layout = pages,
            scrollPos = bottomScroll,
            viewportHeight = viewport,
        )
        assertEquals(9, reported)
    }

    @Test
    fun `a page fully cleared keeps reporting the next one`() {
        // Progress while actually reading: at scroll 1510 page 1's end (2000) has cleared
        // the viewport bottom (2510) while page 2's end (3000) has not, so page 1 is the
        // deepest end-visible page; once page 2's end clears it (scroll 2010), page 2
        // becomes the reported one.
        val viewport = 1000f
        val pages = layout(pageCount = 10, pageHeight = 1000f)
        assertEquals(
            1,
            ContinuousPageReportPolicy.reportedPageIndex(
                layout = pages,
                scrollPos = 1510f,
                viewportHeight = viewport,
            ),
        )
        assertEquals(
            2,
            ContinuousPageReportPolicy.reportedPageIndex(
                layout = pages,
                scrollPos = 2010f,
                viewportHeight = viewport,
            ),
        )
    }

    @Test
    fun `restore with a full-viewport page reports exactly that page`() {
        // A page exactly as tall as the viewport (or taller): nothing else is visible, so
        // the restore must report it — this was already true with the old rule and must
        // stay true with the fix.
        val viewport = 1000f
        val pages = layout(pageCount = 10, pageHeight = 1000f)
        val reported = ContinuousPageReportPolicy.reportedPageIndex(
            layout = pages,
            scrollPos = pages.offsets[7],
            viewportHeight = viewport,
        )
        assertEquals(7, reported)
    }
}

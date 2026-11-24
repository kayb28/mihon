package eu.kanade.tachiyomi.ui.reader.viewer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

/** JVM tests for the shared gesture decision layer (T4). */
class ViewerGesturePolicyTest {

    @Test
    fun `long press gate is opt-in unless menu is visible`() {
        assertFalse(ViewerGesturePolicy.longPressOpensPageDialog(menuVisible = false, longTapEnabled = false))
        assertTrue(ViewerGesturePolicy.longPressOpensPageDialog(menuVisible = true, longTapEnabled = false))
        assertTrue(ViewerGesturePolicy.longPressOpensPageDialog(menuVisible = false, longTapEnabled = true))
    }

    @Test
    fun `zoom is clamped to fit and max`() {
        assertEquals(1f, ViewerGesturePolicy.clampZoom(0.5f))
        assertEquals(4f, ViewerGesturePolicy.clampZoom(9f))
        assertEquals(1f, ViewerGesturePolicy.clampZoom(1f))
    }

    @Test
    fun `double tap toggles fit and double tap zoom`() {
        assertEquals(ViewerGesturePolicy.DOUBLE_TAP_ZOOM, ViewerGesturePolicy.doubleTapTargetZoom(1f))
        assertEquals(1f, ViewerGesturePolicy.doubleTapTargetZoom(2.5f))
    }

    @Test
    fun `anchored pan keeps the centroid point stationary`() {
        // Point at center-relative (100, 50) with pan (10, 5); doubling zoom carries the pan so
        // the underlying point stays put: newPan = c - (c - pan) * ratio.
        val (x, y) = ViewerGesturePolicy.anchoredPan(10f, 5f, 2f, 100f, 50f)
        assertEquals(100f - (100f - 10f) * 2f, x)
        assertEquals(50f - (50f - 5f) * 2f, y)
    }

    @Test
    fun `swipe accumulator turns one page per threshold in LTR`() {
        val swipe = ViewerGesturePolicy.SwipeAccumulator()
        val w = 1000f
        // Below threshold: nothing.
        assertEquals(0, swipe.onDrag(100f, 0f, isVertical = false, isRightToLeft = false, viewportWidth = w, viewportHeight = 1000f))
        assertEquals(100f, swipe.accumulated)
        // Crossing the 18% threshold forwards one page and resets.
        assertEquals(1, swipe.onDrag(100f, 0f, isVertical = false, isRightToLeft = false, viewportWidth = w, viewportHeight = 1000f))
        assertEquals(0f, swipe.accumulated)
    }

    @Test
    fun `swipe accumulator inverts RTL and handles vertical`() {
        val swipe = ViewerGesturePolicy.SwipeAccumulator()
        // RTL: finger moving right (positive X) is backward.
        assertEquals(-1, swipe.onDrag(200f, 0f, isVertical = false, isRightToLeft = true, viewportWidth = 1000f, viewportHeight = 1000f))
        swipe.reset()
        // Vertical: Y axis governs, positive Y is forward (down).
        assertEquals(1, swipe.onDrag(0f, 200f, isVertical = true, isRightToLeft = false, viewportWidth = 1000f, viewportHeight = 1000f))
    }

    @Test
    fun `fuzz swipe accumulator never returns a bogus step`() {
        val rng = kotlin.random.Random(0x6E57)
        repeat(1_000) {
            val swipe = ViewerGesturePolicy.SwipeAccumulator()
            val step = swipe.onDrag(
                rng.nextFloat() * 2000f - 1000f,
                rng.nextFloat() * 2000f - 1000f,
                isVertical = rng.nextBoolean(),
                isRightToLeft = rng.nextBoolean(),
                viewportWidth = 1000f,
                viewportHeight = 1000f,
            )
            assertTrue(step in -1..1)
            if (step != 0) assertEquals(0f, swipe.accumulated)
            assertTrue(abs(swipe.accumulated) < 1000f * ViewerGesturePolicy.SWIPE_THRESHOLD_FRACTION || step != 0)
        }
    }
}

package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import eu.kanade.presentation.reader.ReaderError
import eu.kanade.presentation.util.formattedMessage
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerGesturePolicy
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import tachiyomi.presentation.core.components.CombinedCircularProgressIndicator

// Zoom caps and gestures come from the shared gesture policy so every viewer answers
// identically (T4: one gesture manual for all viewers).
private const val DOUBLE_TAP_ZOOM = ViewerGesturePolicy.DOUBLE_TAP_ZOOM

/**
 * Paged viewer on androidx.webgpu (WebGPU migration, phase 7: pinch zoom and pan). Extends
 * [AndroidxWebGpuBaseViewer] for the surface lifecycle, the windowed page presentation and the
 * page cache; what it adds is drawing the current page full-screen and the paged interactions.
 *
 * Selected by the unified viewer preference
 * [eu.kanade.domain.base.BasePreferences.webGpuViewerMode] set to EXPERIMENTAL
 * ([eu.kanade.tachiyomi.ui.reader.setting.ReadingMode] builds this viewer in that mode; STOCK
 * and HQ keep the stock and ca.mpreg HQ viewers). When the androidx.webgpu runtime cannot
 * draw on the device, the reader degrades to the HQ viewer for the rest of the session.
 *
 * The drawn page is a plain tap-and-zoom surface: taps are forwarded to
 * [ReaderActivity.onViewerTap] at a normalized position so the configured navigation
 * overlay decides (center opens the menu, edges advance or go back per layout — L, kindle,
 * right-to-left, vertical), a pinch zooms about its centroid, a one-finger drag pans a
 * zoomed page, a double tap toggles between fit and [DOUBLE_TAP_ZOOM] about the tapped
 * point, and a long press opens the stock page dialog. Every page starts fitted;
 * zoom and pan never show empty margins around a zoomed page
 * ([AndroidxWebGpuSurfaceRenderer.setTransform] clamps per frame). While the current page
 * is not on the surface yet, the overlay shows its loader progress or, on failure, the stock
 * [ReaderError] whose Retry routes by the page's loader state.
 *
 * If this viewer cannot draw — the androidx.webgpu probe fails, or the surface bring-up throws —
 * the shared base hands the reader back through [ReaderActivity.requestAndroidxWebGpuFallback],
 * which rebuilds the reader on the HQ (or, as a last resort, stock) viewer for the rest of the
 * session, instead of stranding the user on a black surface.
 */
class AndroidxWebGpuViewer(
    activity: ReaderActivity,
) : AndroidxWebGpuBaseViewer(activity) {

    /** Gesture state, main thread only: page zoom (1 fits) and pan in surface pixels. */
    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f

    /** Shared swipe-to-turn accumulator (ViewerGesturePolicy, T4). */
    private val swipe = ViewerGesturePolicy.SwipeAccumulator()

    @Composable
    override fun Content(modifier: Modifier) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // Consumes drag and pinch past touch slop; taps and long presses below the
                    // slop still reach the tap detector below.
                    detectTransformGestures { centroid, pan, gestureZoom, _ ->
                        onTransform(centroid, pan, gestureZoom, size)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset -> onTap(offset, size) },
                        onDoubleTap = { offset -> onDoubleTap(offset, size) },
                        onLongPress = {
                            // Shared long-press gate (ViewerGesturePolicy): the long-tap dialog
                            // is opt-in unless the menu is already open. Same for stock, HQ
                            // and experimental viewers.
                            if (ViewerGesturePolicy.longPressOpensPageDialog(
                                    activity.viewModel.state.value.menuVisible,
                                    activity.settingsViewModel.general.value.longTapEnabled,
                                )
                            ) {
                                current?.let(activity::onPageLongTap)
                            }
                        },
                    )
                },
        ) {
            SurfaceHost(Modifier.fillMaxSize(), surfaceDescription(current))
            val context = LocalContext.current
            val page = current
            val state = page?.let { presentation[it] }
            if (page != null && state != null) {
                when (state) {
                    is PagePresentation.Failed -> ReaderError(
                        message = with(context) { state.error.formattedMessage },
                        onRetry = { retryPageLoad(page) },
                        onOpenInWebView = page.imageUrl
                            ?.takeIf { it.startsWith("http", ignoreCase = true) }
                            ?.let { url ->
                                {
                                    context.startActivity(
                                        WebViewActivity.newIntent(
                                            context,
                                            url,
                                            activity.viewModel.manga?.source,
                                        ),
                                    )
                                }
                            },
                        modifier = Modifier.fillMaxSize(),
                    )

                    is PagePresentation.Loading, PagePresentation.Uploading -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CombinedCircularProgressIndicator(
                            progress = {
                                if (state is PagePresentation.Loading) {
                                    state.progress.coerceAtLeast(0) / 100f
                                } else {
                                    0f
                                }
                            },
                        )
                    }

                    PagePresentation.Drawn -> {}
                }
            }
        }
        ReaderColorFilterEffect { renderCurrentPage() }
    }

    override fun moveForward() = moveBy(1)

    override fun moveBackward() = moveBy(-1)

    /**
     * A page turn starts fitted; the window redraw that follows draws it without a transform.
     * Reports the page to the reader (progress saving, chapter tracking, page indicator) like
     * the stock viewers' page-change hooks — without this the paged mode would read silently,
     * never saving lastPageRead and never switching chapters.
     */
    override fun onCurrentChanged() {
        resetZoom()
        // Stock pager behavior (PagerViewer's onPageSelected): a settled page turn hides the
        // menu, unless the user is dragging through pages with the slider. Without this the
        // menu stayed up after every tap-zone turn.
        if (!activity.isScrollingThroughPages) activity.hideMenu()
        current?.let {
            activity.onPageSelected(it)
            maybePreloadNextChapter(it)
        }
    }

    override fun onWindowChanged() {
        renderCurrentPage()
    }

    override fun onPageStored(page: ReaderPage) {
        if (page === current) renderCurrentPage()
    }

    /** Points the renderer at the current page's texture (null clears) and draws a frame. */
    private fun renderCurrentPage() {
        // The shader applies the reader's grayscale/inverted settings; push them per draw so a
        // settings change mid-read is honored by the very next frame.
        applyColorFilter()
        val image = current?.let { page -> synchronized(gpuImages) { gpuImages[page] } }
        imageBackend.setPage(image)
        imageBackend.requestRender()
    }

    /**
     * Delegates taps to the reader at a normalized position so the configured navigation
     * overlay decides: the center zone opens the menu (default [Action.MENU]), the edges
     * advance or go back per the chosen layout (L / kindle / right-to-left / vertical). This
     * matches the stock viewers' tap hookups (PagerViewer's `pager.tapListener`,
     * WebtoonViewer's `recycler.tapListener`), which all funnel into
     * [ReaderActivity.onViewerTap]; that is also where a tap with no navigation overlay
     * configured falls back to [Action.MENU]. The previous in-viewer forward/backward
     * decision meant the WebGPU readers could never surface the app bars, the settings
     * dialog (color filter, reading mode, etc.) or the chapter navigator.
     */
    private fun onTap(offset: Offset, container: IntSize) {
        val pos = Offset(
            offset.x / container.width.coerceAtLeast(1),
            offset.y / container.height.coerceAtLeast(1),
        )
        activity.onViewerTap(pos)
    }

    /**
     * Pinch and pan. Zoom keeps the point under the pinch centroid stationary, a pinch collapsing
     * below fit snaps back to fit, and a page that fits cannot pan — the renderer clamps the pan
     * per frame, this only feeds it what the fingers did. The anchor math works in center-relative
     * coordinates because the renderer scales about the surface center ([AndroidxWebGpuSurfaceRenderer]
     * "zoom about the page center, then pan"); using raw coordinates from the top-left corner made
     * double taps land on the wrong point and pinches drift.
     */
    private fun onTransform(centroid: Offset, pan: Offset, gestureZoom: Float, container: IntSize) {
        if (current == null) return
        val oldZoom = zoom
        val newZoom = ViewerGesturePolicy.clampZoom(zoom * gestureZoom)
        if (newZoom != oldZoom) {
            // Anchor pan from the shared policy (center-relative coordinates).
            val (ax, ay) = ViewerGesturePolicy.anchoredPan(
                panX,
                panY,
                newZoom / oldZoom,
                centroid.x - container.width / 2f,
                centroid.y - container.height / 2f,
            )
            panX = ax
            panY = ay
            zoom = newZoom
            swipe.reset()
        }
        // Page turn by swipe only while fitted (zoom == 1). Pinch and pan when zoomed stay as
        // before. The axis/RTL decision lives in the shared SwipeAccumulator (T4).
        if (zoom <= 1f && kotlin.math.abs(gestureZoom - 1f) < 0.02f) {
            val mode = eu.kanade.tachiyomi.ui.reader.setting.ReadingMode.fromPreference(
                activity.viewModel.getMangaReadingMode(),
            )
            val vertical = mode.direction is eu.kanade.tachiyomi.ui.reader.setting.ReadingMode.Direction.Vertical
            // Fallo F (T-F): a tall page's window pans at fit — the page is placed at
            // width-fit (LongStripFitPolicy), so vertical drags slide the window (the
            // renderer clamps it to the page) and page turns happen only at the window's
            // edges, the same windowing HQ gives. Normal pages turn as always.
            val panRange = tallPagePanRange(container.height.toFloat())
            val edgeTurn = when {
                panRange == null -> true
                !vertical -> kotlin.math.abs(pan.x) >= kotlin.math.abs(pan.y)
                pan.y > 0f -> panY >= -1f
                else -> panY <= -panRange + 1f
            }
            if (!edgeTurn) {
                panY += pan.y
                panX = 0f
                imageBackend.setTransform(zoom, panX, panY)
                imageBackend.requestRender()
                swipe.reset()
                return
            }
            if (panRange != null && vertical) {
                // Snap the window to the edge the turn starts from.
                panY = if (pan.y > 0f) 0f else -panRange
            }
            val step = swipe.onDrag(
                panX = pan.x,
                panY = pan.y,
                isVertical = vertical,
                isRightToLeft = mode == eu.kanade.tachiyomi.ui.reader.setting.ReadingMode.RIGHT_TO_LEFT,
                viewportWidth = container.width.toFloat(),
                viewportHeight = container.height.toFloat(),
            )
            // Positive readingDelta = next page in reading order (LTR forward, RTL forward, vertical down).
            if (step != 0) moveBy(step)
            zoom = 1f
            panX = 0f
            panY = 0f
            imageBackend.setTransform(zoom, panX, panY)
            imageBackend.requestRender()
            return
        }
        panX += pan.x
        panY += pan.y
        if (zoom <= 1f) {
            zoom = 1f
            panX = 0f
            panY = 0f
            swipe.reset()
        }
        imageBackend.setTransform(zoom, panX, panY)
        imageBackend.requestRender()
    }

    /**
     * Fallo F (T-F): the tall-page window's pan traversal at fit (zoom 1), in surface px,
     * or null when the current page is not taller than the viewport at width-fit —
     * [LongStripFitPolicy] places tall pages at width-fit with a top-pinned window. The
     * renderer clamps the pan per frame; this only bounds where page turns are allowed.
     */
    private fun tallPagePanRange(containerHeight: Float): Float? {
        val page = current ?: return null
        val image = synchronized(gpuImages) { gpuImages[page] } ?: return null
        val sw = imageBackend.surfaceWidthPx.takeIf { it > 0 }?.toFloat() ?: return null
        val sh = imageBackend.surfaceHeightPx.takeIf { it > 0 }?.toFloat() ?: containerHeight
        if (image.width <= 0 || image.height <= 0) return null
        val heightAtFitWidth = image.height * (sw / image.width)
        return (heightAtFitWidth - sh).takeIf { it > 0f }
    }

    /** Toggles between fit and [DOUBLE_TAP_ZOOM], zooming into the tapped point (shared policy). */
    private fun onDoubleTap(centroid: Offset, container: IntSize) {
        val oldZoom = zoom
        zoom = ViewerGesturePolicy.doubleTapTargetZoom(oldZoom)
        if (zoom > 1f) {
            val (ax, ay) = ViewerGesturePolicy.anchoredPan(
                panX,
                panY,
                zoom / oldZoom,
                centroid.x - container.width / 2f,
                centroid.y - container.height / 2f,
            )
            panX = ax
            panY = ay
        } else {
            panX = 0f
            panY = 0f
        }
        imageBackend.setTransform(zoom, panX, panY)
        imageBackend.requestRender()
    }

    /** A new page always starts fitted; the caller renders right after. */
    private fun resetZoom() {
        zoom = 1f
        panX = 0f
        panY = 0f
        swipe.reset()
        imageBackend.setTransform(zoom, panX, panY)
    }

    private fun moveBy(delta: Int) {
        val chapters = loadedChapters ?: return
        // The three loaded chapters, flattened: like the stock pager (whose adapter spans them
        // all), a page turn at a chapter's end walks into the next chapter instead of
        // dead-ending. Crossing over reports the page to the reader (onCurrentChanged), which
        // loads the crossed-into chapter just like the stock flow does.
        val pages = buildList {
            chapters.prevChapter?.let { addAll(it.pages.orEmpty()) }
            addAll(chapters.currChapter.pages.orEmpty())
            chapters.nextChapter?.let { addAll(it.pages.orEmpty()) }
        }
        if (pages.isEmpty()) return
        val index = current?.let { page -> pages.indexOfFirst { it === page } }?.takeIf { it >= 0 } ?: 0
        val page = pages[(index + delta).coerceIn(pages.indices)]
        if (page === current) return
        current = page
        onCurrentChanged()
        updatePageWindow()
    }
}

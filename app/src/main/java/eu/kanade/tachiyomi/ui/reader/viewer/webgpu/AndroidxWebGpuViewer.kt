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
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import tachiyomi.presentation.core.components.CombinedCircularProgressIndicator

/** Zoom cap for pinch and double tap; 1 shows the page aspect-fitted. */
private const val MAX_ZOOM = 4f

/** Zoom a double tap toggles to (and back from). */
private const val DOUBLE_TAP_ZOOM = 2.5f

/**
 * Paged viewer on androidx.webgpu (WebGPU migration, phase 7: pinch zoom and pan). Extends
 * [AndroidxWebGpuBaseViewer] for the surface lifecycle, the windowed page presentation and the
 * page cache; what it adds is drawing the current page full-screen and the paged interactions.
 *
 * Work in progress behind [eu.kanade.domain.base.BasePreferences.androidxWebGpuViewer]
 * (default OFF): when the flag is off, [eu.kanade.tachiyomi.ui.reader.setting.ReadingMode]
 * keeps building the ca.mpreg HQ viewers exactly as before.
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
 * which rebuilds the reader on the viewer it would use with the preference off (HQ or stock),
 * instead of stranding the user on a black surface.
 */
class AndroidxWebGpuViewer(
    activity: ReaderActivity,
) : AndroidxWebGpuBaseViewer(activity) {

    /** Gesture state, main thread only: page zoom (1 fits) and pan in surface pixels. */
    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f

    @Composable
    override fun Content(modifier: Modifier) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // Consumes drag and pinch past touch slop; taps and long presses below the
                    // slop still reach the tap detector below.
                    detectTransformGestures { centroid, pan, gestureZoom, _ ->
                        onTransform(centroid, pan, gestureZoom)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset -> onTap(offset, size) },
                        onDoubleTap = ::onDoubleTap,
                        onLongPress = { current?.let(activity::onPageLongTap) },
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
    }

    override fun moveForward() = moveBy(1)

    override fun moveBackward() = moveBy(-1)

    /** A page turn starts fitted; the window redraw that follows draws it without a transform. */
    override fun onCurrentChanged() {
        resetZoom()
    }

    override fun onWindowChanged() {
        renderCurrentPage()
    }

    override fun onPageStored(page: ReaderPage) {
        if (page === current) renderCurrentPage()
    }

    /** Points the renderer at the current page's texture (null clears) and draws a frame. */
    private fun renderCurrentPage() {
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
     * per frame, this only feeds it what the fingers did.
     */
    private fun onTransform(centroid: Offset, pan: Offset, gestureZoom: Float) {
        if (current == null) return
        val oldZoom = zoom
        val newZoom = (zoom * gestureZoom).coerceIn(1f, MAX_ZOOM)
        if (newZoom != oldZoom) {
            val carry = newZoom / oldZoom
            panX = centroid.x - (centroid.x - panX) * carry
            panY = centroid.y - (centroid.y - panY) * carry
            zoom = newZoom
        }
        panX += pan.x
        panY += pan.y
        if (zoom <= 1f) {
            zoom = 1f
            panX = 0f
            panY = 0f
        }
        imageBackend.setTransform(zoom, panX, panY)
        imageBackend.requestRender()
    }

    /** Toggles between fit and [DOUBLE_TAP_ZOOM], zooming into the tapped point. */
    private fun onDoubleTap(centroid: Offset) {
        val oldZoom = zoom
        if (oldZoom > 1f) {
            zoom = 1f
            panX = 0f
            panY = 0f
        } else {
            zoom = DOUBLE_TAP_ZOOM
            panX = centroid.x * (1f - zoom / oldZoom)
            panY = centroid.y * (1f - zoom / oldZoom)
        }
        imageBackend.setTransform(zoom, panX, panY)
        imageBackend.requestRender()
    }

    /** A new page always starts fitted; the caller renders right after. */
    private fun resetZoom() {
        zoom = 1f
        panX = 0f
        panY = 0f
        imageBackend.setTransform(zoom, panX, panY)
    }

    private fun moveBy(delta: Int) {
        val pages = loadedChapters?.currChapter?.pages ?: return
        if (pages.isEmpty()) return
        val index = current?.let { page -> pages.indexOfFirst { it === page } }?.takeIf { it >= 0 } ?: 0
        val page = pages[(index + delta).coerceIn(pages.indices)]
        current = page
        resetZoom()
        updatePageWindow()
    }
}

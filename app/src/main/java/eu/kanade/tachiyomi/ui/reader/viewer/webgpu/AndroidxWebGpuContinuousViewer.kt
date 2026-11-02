package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import eu.kanade.presentation.reader.ReaderError
import eu.kanade.presentation.util.formattedMessage
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import tachiyomi.presentation.core.components.CombinedCircularProgressIndicator
import android.view.Choreographer
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Pages uploaded behind the scroll position; a screen shows parts of a few pages at once. */
private const val CONTINUOUS_PRELOAD_BEHIND = 2

/** Pages uploaded ahead of the scroll position: deeper than paged, a whole screen of headroom. */
private const val CONTINUOUS_PRELOAD_AHEAD = 5

/** Page texture cap: the window, the visible pages and the chapter gap in between. */
private const val CONTINUOUS_CACHE_SIZE = 1 + CONTINUOUS_PRELOAD_AHEAD + CONTINUOUS_PRELOAD_BEHIND + 2

/** Width-over-height assumed for a page whose texture (and shape) is not known yet. */
private const val DEFAULT_PAGE_ASPECT = 0.7f

/** Gap between chapters as a fraction of the surface width, in the gaps-using mode. */
private const val CHAPTER_GAP_FRACTION = 0.1f

/**
 * Continuous (webtoon-style) viewer on androidx.webgpu (WebGPU migration, phase 5: the continuous
 * mode). Extends [AndroidxWebGpuBaseViewer] for the surface lifecycle, the windowed page
 * presentation and the page cache; what it adds is a scrollable vertical document.
 *
 * The three loaded chapters are laid out as one long strip at surface width, pages stacked by
 * their decoded aspect (an assumed one until their texture arrives). One Compose scroll layer
 * owns the drag and fling gestures and the document height; the surface beneath it is redrawn
 * per scroll tick with every page intersecting the viewport ([PageDraw] placements), so the GPU
 * draws exactly what a scroll position shows. The page holding the viewport center is the
 * current one: it re-anchors the shared page window and reports to the reader through
 * [ReaderActivity.onPageSelected] like the HQ viewer does. Overlays ride the document: a page
 * still loading shows its spinner at its own position, and a failed one the stock [ReaderError]
 * with its Retry, both scrolling with the strip.
 *
 * Work in progress behind [eu.kanade.domain.base.BasePreferences.androidxWebGpuViewer]
 * (default OFF): when the flag is off, [eu.kanade.tachiyomi.ui.reader.setting.ReadingMode] keeps
 * building the ca.mpreg HQ continuous viewer exactly as before. Pinch zoom stays on the HQ
 * viewer for this mode until a later phase.
 */
class AndroidxWebGpuContinuousViewer(
    activity: ReaderActivity,
    private val useGap: Boolean,
) : AndroidxWebGpuBaseViewer(activity) {

    override val preloadBehind: Int get() = CONTINUOUS_PRELOAD_BEHIND

    override val preloadAhead: Int get() = CONTINUOUS_PRELOAD_AHEAD

    override val cacheSize: Int get() = CONTINUOUS_CACHE_SIZE

    private val scrollState = ScrollState(0)

    /**
     * Frame clock for [viewerScope]'s scroll animations.
     *
     * `ScrollState.animateScrollTo` drives a frame-based animation that needs
     * a [MonotonicFrameClock] in the coroutine context. Inside a composition
     * the clock is supplied by AndroidUiDispatcher, but this scope runs on the
     * plain Main dispatcher — without a clock, `shiftPage` crashed with
     * "A MonotonicFrameClock is not available in this CoroutineContext". This
     * implementation mirrors what AndroidUiDispatcher does: hop one Choreographer
     * frame and resume with its timestamp. It must run on a Looper thread,
     * which [Dispatchers.Main] guarantees.
     */
    private val frameClock = object : MonotonicFrameClock {
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R =
            suspendCancellableCoroutine { cont ->
                val choreographer = Choreographer.getInstance()
                val callback = Choreographer.FrameCallback { frameTimeNanos ->
                    try {
                        cont.resume(onFrame(frameTimeNanos))
                    } catch (e: Throwable) {
                        // Propagate only while the continuation is still live;
                        // resuming a cancelled one is silently ignored.
                        if (cont.isActive) cont.resumeWithException(e)
                    }
                }
                choreographer.postFrameCallback(callback)
                cont.invokeOnCancellation { choreographer.removeFrameCallback(callback) }
            }
    }

    private val viewerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main + frameClock)

    /** Width-over-height of every decoded page; a default until its texture arrives. */
    private val aspects = ConcurrentHashMap<ReaderPage, Float>()

    /** The document: pages in reading order and each one's top offset in surface pixels. */
    @Volatile
    private var layoutPages: List<ReaderPage> = emptyList()

    @Volatile
    private var offsets: List<Float> = emptyList()

    @Volatile
    private var totalHeight = 0f

    /** Bumped whenever the layout changes, so the scroll layer's height recomposes. */
    private var layoutVersion by mutableStateOf(0)

    /** The page last reported to the reader; [onPageSelected] fires when it changes. */
    private var lastNotified: ReaderPage? = null

    @Composable
    override fun Content(modifier: Modifier) {
        val density = LocalDensity.current
        val context = LocalContext.current
        // Reads the version so a relayout recomposes the scroll layer's height.
        val version = layoutVersion
        Box(modifier = modifier.fillMaxSize()) {
            SurfaceHost(Modifier.fillMaxSize(), surfaceDescription(current))
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { offset -> onTap(offset, size) },
                            onLongPress = {
                            // Same gate as the stock viewers and the HQ viewer: the long-tap
                            // dialog is opt-in unless the menu is already open.
                            if (activity.viewModel.state.value.menuVisible ||
                                activity.settingsViewModel.general.value.longTapEnabled
                            ) {
                                current?.let(activity::onPageLongTap)
                            }
                        },
                        )
                    },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(with(density) { totalHeight.toInt().coerceAtLeast(0).toDp() }),
                ) {
                    layoutPages.forEachIndexed { index, page ->
                        val state = presentation[page] ?: return@forEachIndexed
                        val pageHeight = with(density) { pageHeightPx(index).toInt().toDp() }
                        val pageTop = with(density) { offsets[index].toInt().coerceAtLeast(0).toDp() }
                        when (state) {
                            is PagePresentation.Failed -> Box(
                                modifier = Modifier
                                    .absoluteOffset(y = pageTop)
                                    .fillMaxWidth()
                                    .height(pageHeight),
                            ) {
                                ReaderError(
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
                            }

                            is PagePresentation.Loading, PagePresentation.Uploading -> Box(
                                modifier = Modifier
                                    .absoluteOffset(y = pageTop)
                                    .fillMaxWidth()
                                    .height(pageHeight),
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
        }
        LaunchedEffect(Unit) {
            snapshotFlow { scrollState.value }.collect { onPageScrolled() }
        }
    }

    override fun moveForward() = shiftPage(1)

    override fun moveBackward() = shiftPage(-1)

    override fun onCurrentChanged() {
        relayout()
        val page = current ?: return
        val index = layoutPages.indexOfFirst { it === page }
        if (index >= 0) {
            lastNotified = page
            // External jumps (moveToPage, chapter re-sets) never pass through onPageScrolled
            // at the target page — lastNotified suppresses it there — so report here or the
            // reader would never see them (progress saving, chapter tracking).
            activity.onPageSelected(page)
            viewerScope.launch {
                scrollState.scrollTo(offsets[index].toInt().coerceAtLeast(0))
            }
        }
    }

    override fun onChaptersChanged() {
        relayout()
    }

    override fun onWindowChanged() {
        drawVisible()
    }

    override fun onPageStored(page: ReaderPage) {
        val aspect = synchronized(gpuImages) { gpuImages[page] }
            ?.let { image -> image.width.toFloat() / image.height }
            ?: return
        aspects[page] = aspect
        // The strip's shapes changed: re-measure and redraw on the main thread.
        viewerScope.launch {
            relayout()
            drawVisible()
        }
    }

    override fun onDestroyed() {
        viewerScope.cancel()
    }

    /** Advances the document by one page: [delta] of 1 goes down, -1 up. */
    private fun shiftPage(delta: Int) {
        val page = current ?: return
        val index = layoutPages.indexOfFirst { it === page }.takeIf { it >= 0 } ?: return
        val target = (index + delta).coerceIn(layoutPages.indices)
        if (target == index) return
        viewerScope.launch {
            scrollState.animateScrollTo(offsets[target].toInt().coerceAtLeast(0))
        }
    }

    /**
     * Delegates taps to the reader at a normalized position so the configured navigation
     * overlay decides: the center zone opens the menu (default [Action.MENU]), the edges
     * advance or go back per the chosen layout. Matches the stock viewers' tap hookups
     * (PagerViewer's `pager.tapListener`, WebtoonViewer's `recycler.tapListener`), which
     * all funnel into [ReaderActivity.onViewerTap]; a tap with no navigation overlay
     * configured also falls back to [Action.MENU]. The previous in-viewer top/bottom-half
     * decision meant the WebGPU continuous reader could never surface the app bars, the
     * settings dialog or the chapter navigator.
     */
    private fun onTap(offset: Offset, container: IntSize) {
        val pos = Offset(
            offset.x / container.width.coerceAtLeast(1),
            offset.y / container.height.coerceAtLeast(1),
        )
        activity.onViewerTap(pos)
    }

    /** Rebuilds the document from the loaded chapters and their known page shapes. */
    private fun relayout() {
        val chapters = this.loadedChapters ?: return
        val width = imageBackend.surfaceWidthPx.coerceAtLeast(1)
        val pages = buildList {
            chapters.prevChapter?.let { addAll(it.pages.orEmpty()) }
            addAll(chapters.currChapter.pages.orEmpty())
            chapters.nextChapter?.let { addAll(it.pages.orEmpty()) }
        }
        val gap = if (useGap) width * CHAPTER_GAP_FRACTION else 0f
        var y = 0f
        var lastChapter: ReaderChapter? = null
        val newOffsets = ArrayList<Float>(pages.size)
        pages.forEach { page ->
            if (lastChapter != null && page.chapter !== lastChapter) y += gap
            newOffsets.add(y)
            y += width / (aspects[page] ?: DEFAULT_PAGE_ASPECT).coerceAtLeast(0.05f)
            lastChapter = page.chapter
        }
        layoutPages = pages
        offsets = newOffsets
        totalHeight = y
        layoutVersion++
    }

    /** Tracks the scroll: re-anchor the window on the center page and report it to the reader. */
    private fun onPageScrolled() {
        val page = pageAtCenter() ?: return
        if (current !== page) {
            current = page
            updatePageWindow()
        }
        if (lastNotified !== page) {
            lastNotified = page
            activity.onPageSelected(page)
        }
        drawVisible()
    }

    /** The page holding the viewport center: the one the reader is on. */
    private fun pageAtCenter(): ReaderPage? {
        if (layoutPages.isEmpty()) return null
        val center = scrollState.value + imageBackend.surfaceHeightPx / 2
        var lo = 0
        var hi = layoutPages.lastIndex
        var index = 0
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (offsets[mid] <= center) {
                index = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return layoutPages[index]
    }

    /** Draws every page intersecting the viewport, placed at its document position. */
    private fun drawVisible() {
        if (layoutPages.isEmpty()) {
            imageBackend.setPages(emptyList())
            imageBackend.requestRender()
            return
        }
        val width = imageBackend.surfaceWidthPx.coerceAtLeast(1)
        val viewport = imageBackend.surfaceHeightPx.coerceAtLeast(1)
        val scroll = scrollState.value.coerceAtLeast(0).toFloat()
        val top = scroll
        val bottom = scroll + viewport
        val draws = ArrayList<AndroidxWebGpuSurfaceRenderer.PageDraw>()
        for (index in layoutPages.indices) {
            val pageTop = offsets[index]
            val pageHeight = pageHeightPx(index, width)
            if (pageTop + pageHeight <= top) continue
            if (pageTop >= bottom) break
            val image = synchronized(gpuImages) { gpuImages[layoutPages[index]] } ?: continue
            // Full surface width; the page center sits at its document position minus the scroll.
            val scaleY = pageHeight / viewport
            val offsetY = (viewport / 2f - (pageTop + pageHeight / 2f - scroll)) / viewport * 2f
            draws.add(AndroidxWebGpuSurfaceRenderer.PageDraw(image, 1f, scaleY, 0f, offsetY))
        }
        imageBackend.setPages(draws)
        imageBackend.requestRender()
    }

    private fun pageHeightPx(index: Int, width: Int = imageBackend.surfaceWidthPx.coerceAtLeast(1)): Float {
        return width / (aspects[layoutPages[index]] ?: DEFAULT_PAGE_ASPECT).coerceAtLeast(0.05f)
    }
}

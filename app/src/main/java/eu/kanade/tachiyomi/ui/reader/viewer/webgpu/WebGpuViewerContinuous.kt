package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import ca.mpreg.webgpuviewer.viewer.ImagePage
import ca.mpreg.webgpuviewer.viewer.ImageViewerContinuous
import ca.mpreg.webgpuviewer.viewer.ImageViewerContinuousState
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.viewer.mouseWheelPaging
import kotlinx.coroutines.launch
import kotlin.math.max

class WebGpuViewerContinuous(activity: ReaderActivity, val useGap: Boolean = false) :
    WebGpuViewer(activity, isReversed = false, isVertical = true, viewerState = ImageViewerContinuousState()) {

    override val isContinuous: Boolean get() = true

    // How many pages the viewport shows depends on the zoom, and a page on screen has to be
    // decoded rather than merely reserved - so the window follows what the last frame reached.
    override val preloadAhead get() = max(3, state.pagesBelow)
    override val preloadBehind get() = max(2, state.pagesAbove)

    override val cacheSize get() = 2 + 2 * max(4, state.pagesAbove + state.pagesBelow)

    private val state get() = viewerState as ImageViewerContinuousState

    @Composable
    override fun Content(modifier: Modifier) {
        // Same viewport capture as the base: the node the strip fills, insets included.
        //
        // Modifier chain, verified against the decompiled library: the reader hands us
        // fillMaxSize().windowInsetsPadding(insets) and this is passed through untouched;
        // ImageViewerContinuous appends its own fillMaxSize + pointerInput and gives the
        // result to ViewerSurface. So the gesture node IS the viewport-sized box and the
        // tap offset it reports is viewport-local - the strip's document height never
        // sizes a composable on our side (the long content lives inside the library's
        // renderer, not in a Column we lay out).
        ImageViewerContinuous(
            modifier = modifier
                .onSizeChanged { viewportSize = it }
                .mouseWheelPaging(this),
            state = state,
        )
    }

    init {
        state.backgroundColor = readerBackgroundColor()

        state.onViewport = { readThrough ->
            val pageChanged = readThrough !== lastReadThrough
            if (pageChanged) {
                lastReadThrough = readThrough
                readThrough?.let { select(it) }
            }

            val page0 = state.getPage(0)
            val edge = (page0 is TransitionPage && page0.prevChapter == null) ||
                (readThrough is TransitionPage && readThrough.nextChapter == null)

            val first = wasAtEdge == null
            val edgeChanged = edge != wasAtEdge
            wasAtEdge = edge
            if (edge) {
                if (edgeChanged) scope.launch { activity.showMenu() }
            } else if (!first && (edgeChanged || pageChanged) && !activity.isScrollingThroughPages) {
                scope.launch { activity.hideMenu() }
            }
        }
    }

    /** Null until the first frame, so opening mid-document neither shows nor hides. */
    @Volatile
    private var wasAtEdge: Boolean? = null

    @Volatile
    private var lastReadThrough: ImagePage? = null

    /**
     * Mark [imagePage] read - its bottom has cleared the viewport, which is later than
     * onPageChange's "top arrived" and the reason this mode selects from here: scrolling up to
     * reveal the page above would otherwise walk progress back past pages still on screen.
     */
    private fun select(imagePage: ImagePage) {
        val page = readerPageFor(imagePage) ?: return
        scope.launch { activity.onPageSelected(page) }
    }

    private fun readerPageFor(imagePage: ImagePage): ReaderPage? {
        // Off the page itself: a transition page is never currentPage (the scroll walk stops
        // before it), so it is the first thing a preload evicts.
        if (imagePage is TransitionPage) return imagePage.prevChapter?.pages?.lastOrNull()

        // Only an image page needs the lookup - nothing on it names its ReaderPage. Evicted
        // means scrolled well clear of, so there is no progress left to report.
        return (viewerPageFor(imagePage) as? ViewerReaderPage)?.page
    }

    override fun destroy() {
        state.onViewport = null
        lastReadThrough = null
        wasAtEdge = null
        super.destroy()
    }

    /**
     * What one tap advances the strip. Stock webtoon scrolls 3/4 of the screen height
     * (WebtoonViewer.scrollDistance = heightPixels * 3 / 4), animated when the user keeps page
     * transitions on and instant when they turn them off. The previous half-screen spring made
     * every tap cover less ground than the stock reader, which read as the tap zones not
     * responding to their settings.
     */
    private fun scrollByTapDistance(direction: Int) {
        val distance = state.height * 3f / 4f
        if (settingsViewModel.general.value.usePageTransitions) {
            state.animateScroll(direction * distance)
        } else {
            state.scrollBy(direction * distance)
        }
    }

    override fun moveRight() = scrollByTapDistance(1)

    override fun moveLeft() = scrollByTapDistance(-1)

    override fun moveToPage(page: ReaderPage) {
        super.moveToPage(page)
        wasAtEdge = null
        lastReadThrough = null
        // Also for a jump to the page already showing, which turns nothing to slide in.
        state.resetScroll()
    }

    override fun animateTurn(direction: Int, fromSpread: ImagePage) {
        state.animateSlideIn(direction)
    }
}

package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import ca.mpreg.imagedecoder.ImageDecoder
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import java.io.InputStream

/** What the Compose layer draws over the surface while a page is not on it (yet). */
internal sealed interface PagePresentation {
    /** The loader is fetching the page: [progress] is its percent, or -1 when unknown. */
    data class Loading(val progress: Int) : PagePresentation

    /** The page's stream is being decoded and uploaded into a GPU texture right now. */
    data object Uploading : PagePresentation

    /** The page's texture is drawn on the surface: nothing to overlay. */
    data object Drawn : PagePresentation

    /** The loader or this viewer's decode failed; Retry routes by the page's loader state. */
    data class Failed(val error: Throwable) : PagePresentation
}

/** Pages uploaded behind the current one, mirroring the HQ viewer's preload window. */
private const val PRELOAD_BEHIND = 2

/** Pages uploaded ahead of the current one, mirroring the HQ viewer's preload window. */
private const val PRELOAD_AHEAD = 3

/**
 * Shared page lifecycle for the androidx.webgpu viewers (WebGPU migration phases 4-8): owning the
 * render surface and the GPU environment behind it, presenting the pages around the current one
 * with their loader watchers, capping the live page textures through the testable
 * [AndroidxWebGpuPageCachePolicy], and handing the reader back when the runtime cannot draw.
 *
 * Only the pages around the current one are presented — [preloadBehind] behind and
 * [preloadAhead] ahead of it in reading order ([updatePageWindow]): a ready stream is decoded and
 * uploaded, one still on the loader gets a watcher on [Page.statusFlow] (so its download progress
 * shows, and its bytes reach the surface as soon as they arrive), and a page the loader gave up
 * on turns into the overlay state its viewer draws, whose Retry goes back to the loader — or, for
 * a page whose stream is fine, re-decodes right here. Live page textures are capped at
 * [cacheSize]: past it the page farthest from the current one loses its texture ([trimCache]) and
 * re-decodes from its stream when the reader walks back to it, so three whole chapters never sit
 * in GPU memory at once. Pages that leave the window only lose their watcher — [Page.statusFlow]
 * is a StateFlow, so a fresh one replays their state on re-entry.
 *
 * Subclasses define what "current" means and how the window's textures reach the surface: the
 * paged viewer draws the current one full-screen ([AndroidxWebGpuViewer]), the continuous viewer
 * stacks the visible ones on a scrollable document ([AndroidxWebGpuContinuousViewer]).
 */
abstract class AndroidxWebGpuBaseViewer(
    val activity: ReaderActivity,
) : Viewer {

    protected var loadedChapters: ViewerChapters? = null
    protected var current by mutableStateOf<ReaderPage?>(null)

    protected val imageBackend = AndroidxWebGpuSurfaceRenderer()
    protected val gpuImages = mutableMapOf<ReaderPage, AndroidxWebGpuSurfaceRenderer.PageImage>()

    /**
     * Where each loaded page sits in reading order (prev + current + next chapter, flattened);
     * measures eviction distance in [trimCache]. Guarded by [gpuImages]' monitor.
     */
    protected val readingOrder = HashMap<ReaderPage, Int>()

    /** Each presented page's overlay state; the Compose layers of both viewers observe this map. */
    internal val presentation = mutableStateMapOf<ReaderPage, PagePresentation>()

    /** In-flight work per page, guarded by [gpuImages]' monitor: one watcher and one upload each. */
    private val watchJobs = mutableMapOf<ReaderPage, Job>()
    private val uploadJobs = mutableMapOf<ReaderPage, Job>()

    private val uploadScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var destroyed = false

    /** True between surfaceCreated and surfaceDestroyed; guards the async bring-up. */
    @Volatile
    private var surfaceReady = false

    /** Set once this viewer gave the reader back; [requestFallback] fires only once. */
    @Volatile
    private var fallbackRequested = false

    private var surfaceJob: Job? = null

    /** Pages uploaded behind the current one; continuous viewers prefetch deeper. */
    protected open val preloadBehind: Int get() = PRELOAD_BEHIND

    /** Pages uploaded ahead of the current one; continuous viewers prefetch deeper. */
    protected open val preloadAhead: Int get() = PRELOAD_AHEAD

    /** Live page textures at once: the window, the current page and a page of slack. */
    protected open val cacheSize: Int get() = 1 + preloadAhead + preloadBehind + 1

    /** Runs when the current page changed, before the window re-anchors on it. */
    protected open fun onCurrentChanged() {}

    /** Runs at the end of every [updatePageWindow]: draw the surface from the page cache. */
    protected open fun onWindowChanged() {}

    /** Runs when [page]'s texture was stored, on its upload thread; draw it if it is on screen. */
    protected open fun onPageStored(page: ReaderPage) {}

    /** Runs first in [destroy]: cancel anything subclass-owned. */
    protected open fun onDestroyed() {}

    /**
     * Owns the render surface. WebGPU needs the raw Android window, so a [SurfaceView] beats a
     * TextureView here: a dedicated composited layer, no extra texture copy, and a well-defined
     * surfaceCreated/surfaceDestroyed lifecycle to bind GPU resource lifetimes to.
     */
    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            surfaceReady = true
            surfaceJob?.cancel()
            surfaceJob = uploadScope.launch {
                if (destroyed) return@launch
                try {
                    val width = holder.surfaceFrame.width().coerceAtLeast(1)
                    val height = holder.surfaceFrame.height().coerceAtLeast(1)
                    imageBackend.start(holder.surface, width, height)
                    if (!surfaceReady || destroyed) {
                        // The surface died while bringing the environment up.
                        imageBackend.stop()
                        return@launch
                    }
                    // Textures died with the previous environment: forget every page's state
                    // (in-flight decodes could have logged stale failures against it) and
                    // bring the pages around the current one onto the fresh one.
                    synchronized(gpuImages) {
                        gpuImages.clear()
                    }
                    presentation.clear()
                    updatePageWindow()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "WebGPU surface bring-up failed" }
                    requestFallback()
                }
            }
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            imageBackend.resize(width, height)
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            surfaceReady = false
            surfaceJob?.cancel()
            synchronized(gpuImages) {
                // The textures died with the renderer about to stop; decodes still running
                // could only fail against it.
                gpuImages.clear()
                uploadJobs.values.forEach { it.cancel() }
            }
            // Synchronous: the renderer drains in-flight frames before this call returns,
            // so nothing touches the surface once Android releases it.
            imageBackend.stop()
        }
    }

    /**
     * The embedded surface, gated on the androidx.webgpu probe. [contentDescription] is what
     * TalkBack reads for the page: callers keep it in sync with their current page and its
     * presentation state.
     */
    @Composable
    protected fun SurfaceHost(modifier: Modifier, contentDescription: String) {
        LaunchedEffect(Unit) {
            if (!AndroidxWebGpuProbe.probe()) requestFallback()
        }
        AndroidView(
            modifier = modifier,
            factory = { context ->
                SurfaceView(context).apply { holder.addCallback(surfaceCallback) }
            },
            update = { view -> view.contentDescription = contentDescription },
            onRelease = { view -> view.holder.removeCallback(surfaceCallback) },
        )
    }

    /**
     * TalkBack text for the surface: which page is on it, and what state that page is in.
     */
    @Composable
    protected fun surfaceDescription(page: ReaderPage?): String {
        if (page == null) return ""
        return when (presentation[page]) {
            is PagePresentation.Failed -> stringResource(MR.strings.webgpu_surface_page_failed, page.number)
            is PagePresentation.Loading, is PagePresentation.Uploading -> stringResource(
                MR.strings.webgpu_surface_page_loading,
                page.number,
            )

            else -> stringResource(MR.strings.webgpu_surface_page, page.number)
        }
    }

    final override fun setChapters(chapters: ViewerChapters) {
        this.loadedChapters = chapters
        current = chapters.currChapter.pages?.firstOrNull()
        onCurrentChanged()
        updatePageWindow()
    }

    final override fun moveToPage(page: ReaderPage) {
        current = page
        onCurrentChanged()
        updatePageWindow()
    }

    final override fun destroy() {
        onDestroyed()
        destroyed = true
        surfaceReady = false
        surfaceJob?.cancel()
        uploadScope.cancel()
        // Releases every page texture and the GPU environment itself; gpuImages only held
        // references into it, so nothing here needs an extra destroy pass.
        imageBackend.stop()
        synchronized(gpuImages) {
            gpuImages.clear()
            readingOrder.clear()
            watchJobs.clear()
            uploadJobs.clear()
        }
        presentation.clear()
        loadedChapters = null
        current = null
    }

    /**
     * Brings [page] onto the surface from wherever it is: pages already drawn (or failed and
     * waiting for a retry) are left alone, a page whose stream is [Page.State.Ready] is decoded
     * and uploaded, and anything earlier gets a watcher on the loader's status flow so the page
     * reaches the surface as soon as its bytes do — and its progress shows until then.
     */
    private fun presentPage(page: ReaderPage) {
        val handled = synchronized(gpuImages) {
            destroyed || page in gpuImages || presentation[page] is PagePresentation.Failed
        }
        if (handled) return
        watchLoader(page)
        when (page.status) {
            Page.State.Ready -> uploadPage(page)

            // A watcher that already survived a surface bring-up does not replay this page's
            // state until the loader changes it again; restore its progress overlay here.
            Page.State.DownloadImage -> if (presentation[page] == null) {
                presentation[page] = PagePresentation.Loading(page.progress)
            }

            else -> {}
        }
    }

    /**
     * Presents the pages around the current one, and reclaims what the new window leaves behind.
     * Pages of chapters the reader dropped lose their texture — destroyed here, since those
     * [ReaderPage]s never come back — while pages outside the window keep texture and overlay but
     * lose their watcher (a fresh one replays their loader state when they re-enter). In-flight
     * uploads are left to finish: [trimCache] reclaims their textures by distance, and a store
     * for a page the chapters dropped is rejected anyway.
     */
    protected fun updatePageWindow() {
        val chapters = this.loadedChapters ?: return
        val pages = buildList {
            chapters.prevChapter?.let { addAll(it.pages.orEmpty()) }
            addAll(chapters.currChapter.pages.orEmpty())
            chapters.nextChapter?.let { addAll(it.pages.orEmpty()) }
        }
        val anchor = pages.indexOfFirst { it === current }.takeIf { it >= 0 } ?: 0
        val windowRange = AndroidxWebGpuPageCachePolicy.window(anchor, pages.size, preloadBehind, preloadAhead)
        val window = if (windowRange.isEmpty()) {
            emptyList()
        } else {
            pages.subList(windowRange.first, windowRange.last + 1)
        }
        val released = mutableListOf<AndroidxWebGpuSurfaceRenderer.PageImage>()
        synchronized(gpuImages) {
            readingOrder.clear()
            pages.forEachIndexed { index, page -> readingOrder[page] = index }

            // Chapters the reader dropped: destroy their pages' textures, cancel their in-flight
            // decodes (the store below rejects their late results regardless).
            val textureIterator = gpuImages.entries.iterator()
            while (textureIterator.hasNext()) {
                val (page, image) = textureIterator.next()
                if (page !in pages) {
                    textureIterator.remove()
                    presentation.remove(page)
                    released.add(image)
                }
            }
            val uploadIterator = uploadJobs.entries.iterator()
            while (uploadIterator.hasNext()) {
                val (page, job) = uploadIterator.next()
                if (page !in pages) {
                    uploadIterator.remove()
                    job.cancel()
                }
            }

            // Pages outside the window: their texture and overlay stay, their watcher goes —
            // a fresh watcher replays their state when they re-enter.
            val watchIterator = watchJobs.entries.iterator()
            while (watchIterator.hasNext()) {
                val (page, job) = watchIterator.next()
                if (page !in window) {
                    watchIterator.remove()
                    job.cancel()
                }
            }
        }
        // destroy() drains through the WebGPU dispatcher, so it stays off the monitor.
        released.forEach(imageBackend::destroy)
        window.forEach(::presentPage)
        trimCache()
        onWindowChanged()
    }

    /**
     * Keeps [cacheSize] page textures alive: past it, the page farthest from the current one in
     * reading order — never the current page itself — loses its texture and overlay, and
     * re-decodes from its stream when the reader walks back to it.
     */
    private fun trimCache() {
        val evicted: List<AndroidxWebGpuSurfaceRenderer.PageImage>
        synchronized(gpuImages) {
            evicted = if (destroyed) {
                emptyList()
            } else {
                buildList {
                    while (gpuImages.size > cacheSize) {
                        val reference = current
                        val refIndex = reference?.let { readingOrder[it] } ?: 0
                        val victim = AndroidxWebGpuPageCachePolicy.victim(
                            gpuImages.keys,
                            readingOrder,
                            reference,
                            refIndex,
                        ) ?: break
                        val image = gpuImages.remove(victim) ?: break
                        presentation.remove(victim)
                        add(image)
                    }
                }
            }
        }
        // destroy() drains through the WebGPU dispatcher, so it stays off the monitor.
        evicted.forEach(imageBackend::destroy)
    }

    /**
     * One watcher per page, reacting to the loader's status flow: pre-download states and
     * [Page.State.DownloadImage] show the page as loading (its download progress when there is
     * one), [Page.State.Ready] uploads the stream that just became available, and
     * [Page.State.Error] turns into the error overlay.
     */
    private fun watchLoader(page: ReaderPage) {
        synchronized(gpuImages) {
            if (destroyed || watchJobs[page]?.isActive == true) return
            watchJobs[page] = uploadScope.launch {
                try {
                    val progressJob = launch {
                        page.progressFlow.collect { value ->
                            presentation[page]?.let { state ->
                                if (state is PagePresentation.Loading) {
                                    presentation[page] = PagePresentation.Loading(value)
                                }
                            }
                        }
                    }
                    try {
                        page.statusFlow.takeWhile { status ->
                            when (status) {
                                Page.State.Queue, Page.State.LoadPage -> {
                                    presentation[page] = PagePresentation.Loading(-1)
                                    true
                                }

                                Page.State.DownloadImage -> {
                                    presentation[page] = PagePresentation.Loading(page.progress)
                                    true
                                }

                                Page.State.Ready -> {
                                    uploadPage(page)
                                    false
                                }

                                is Page.State.Error -> {
                                    presentation[page] = PagePresentation.Failed(status.error)
                                    false
                                }
                            }
                        }.collect { }
                    } finally {
                        progressJob.cancel()
                    }
                } finally {
                    synchronized(gpuImages) {
                        if (watchJobs[page] === coroutineContext[Job]) watchJobs.remove(page)
                    }
                }
            }
        }
    }

    /**
     * Decodes [page]'s stream and uploads the first full frame; one upload per page at a time.
     * The in-flight page shows as loading, a stored one as drawn, and a failure as the error
     * overlay. Without a running environment there is nothing to upload onto — surfaceCreated
     * presents the window again once it brings the renderer up.
     */
    private fun uploadPage(page: ReaderPage) {
        synchronized(gpuImages) {
            if (
                destroyed ||
                !imageBackend.isRunning ||
                page in gpuImages ||
                uploadJobs[page]?.isActive == true
            ) {
                return
            }
            uploadJobs[page] = uploadScope.launch {
                try {
                    presentation[page] = PagePresentation.Uploading
                    val input = page.stream?.invoke()
                        ?: throw IllegalStateException("Page ${page.number} has no stream to decode")
                    decodeAndUpload(page, input)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "uploadPage: page ${page.number}: ${e.message}" }
                    presentation[page] = PagePresentation.Failed(e)
                } finally {
                    synchronized(gpuImages) {
                        if (uploadJobs[page] === coroutineContext[Job]) uploadJobs.remove(page)
                    }
                }
            }
        }
    }

    /**
     * The error overlay's Retry. A page the loader failed on goes back to its loader — its
     * watcher uploads the fresh stream as soon as it is [Page.State.Ready] again — while a page
     * that only failed to decode still has its stream and is simply uploaded from scratch.
     */
    fun retryPageLoad(page: ReaderPage) {
        val retrying = synchronized(gpuImages) {
            when {
                destroyed -> false
                presentation[page] is PagePresentation.Failed -> {
                    presentation.remove(page)
                    true
                }

                else -> false
            }
        }
        if (!retrying) return
        if (page.status is Page.State.Error) {
            page.chapter.pageLoader?.retryPage(page)
        }
        presentPage(page)
    }

    /** Decodes [input] and uploads the first full frame's image into the page cache. */
    private suspend fun decodeAndUpload(page: ReaderPage, input: InputStream) {
        input.use { stream ->
            val dec = ImageDecoder.open(stream)
            try {
                var frame = dec.decodeNext()
                while (frame.partial) {
                    frame.use { }
                    frame = dec.decodeNext()
                }
                frame.use { full ->
                    val image = imageBackend.createFromImage(full.image, full.width, full.height)
                    val stored = synchronized(gpuImages) {
                        when {
                            destroyed -> {
                                imageBackend.destroy(image)
                                false
                            }

                            // The chapters swapped while this page decoded: it is gone for good.
                            readingOrder[page] == null -> {
                                imageBackend.destroy(image)
                                false
                            }

                            imageBackend.isDestroyed(image) -> false

                            else -> {
                                gpuImages[page] = image
                                presentation[page] = PagePresentation.Drawn
                                true
                            }
                        }
                    }
                    if (stored) {
                        onPageStored(page)
                        trimCache()
                    }
                }
            } finally {
                dec.close()
            }
        }
    }

    /**
     * One-shot handoff to the reader: rebuild on the viewer the flag-less path would have picked.
     * Fires when this viewer decides it cannot draw (probe failed or the surface bring-up threw),
     * whichever thread that surfaces on — [ReaderActivity.requestAndroidxWebGpuFallback] hops to
     * the main thread itself.
     */
    private fun requestFallback() {
        if (fallbackRequested) return
        fallbackRequested = true
        logcat(LogPriority.WARN) { "androidx.webgpu viewer cannot draw; falling back to the HQ/stock viewer" }
        activity.requestAndroidxWebGpuFallback()
    }
}

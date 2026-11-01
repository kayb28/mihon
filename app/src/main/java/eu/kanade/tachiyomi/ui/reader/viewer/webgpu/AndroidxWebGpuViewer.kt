package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import ca.mpreg.imagedecoder.ImageDecoder
import eu.kanade.presentation.reader.ReaderError
import eu.kanade.presentation.util.formattedMessage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
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
import tachiyomi.presentation.core.components.CombinedCircularProgressIndicator
import java.io.InputStream

/** What the Compose layer draws over the surface while a page is not on it (yet). */
private sealed interface PagePresentation {
    /** The loader is fetching the page: [progress] is its percent, or -1 when unknown. */
    data class Loading(val progress: Int) : PagePresentation

    /** The page's stream is being decoded and uploaded into a GPU texture right now. */
    data object Uploading : PagePresentation

    /** The page's texture is drawn on the surface: nothing to overlay. */
    data object Drawn : PagePresentation

    /** The loader or this viewer's decode failed; Retry routes by the page's loader state. */
    data class Failed(val error: Throwable) : PagePresentation
}

/**
 * Paged viewer on androidx.webgpu (WebGPU migration, phase 4: real rendering, phase 5: page
 * progress, errors and retry).
 *
 * Work in progress behind [eu.kanade.domain.base.BasePreferences.androidxWebGpuViewer]
 * (default OFF): when the flag is off, [eu.kanade.tachiyomi.ui.reader.setting.ReadingMode]
 * keeps building the ca.mpreg HQ viewers exactly as before.
 *
 * Rendering embeds a [SurfaceView] through AndroidView and drives the androidx.webgpu
 * environment ([AndroidxWebGpuSurfaceRenderer]) from the surface lifecycle: surfaceCreated
 * brings the renderer up, surfaceChanged reconfigures it, and surfaceDestroyed quiesces and
 * releases every GPU resource before Android frees the surface. Decoder frames are handed to
 * the renderer through the [GpuImageBackend] seam ([GpuImageBackend.createFromImage]) and drawn
 * aspect-fitted onto the surface. Transitions, zoom and the continuous mode stay on the ca.mpreg
 * HQ viewer until later migration phases.
 *
 * Every chapter page is presented from wherever it is ([presentPage]): a ready stream is decoded
 * and uploaded, one still on the loader gets a watcher on [Page.statusFlow] (so its download
 * progress shows, and its bytes reach the surface as soon as they arrive), and a page the loader
 * gave up on turns into the stock [ReaderError] overlay, whose Retry goes back to the loader —
 * or, for a page whose stream is fine, re-decodes right here.
 *
 * If this viewer cannot draw — the androidx.webgpu probe fails, or the surface bring-up throws —
 * it hands the reader back through [requestFallback] and [ReaderActivity.requestAndroidxWebGpuFallback]
 * rebuilds the reader on the viewer it would use with the preference off (HQ or stock), instead of
 * stranding the user on a black surface. The handoff is one-shot per viewer and sticky per
 * activity, so a runtime known bad is not retried on every mode change.
 */
class AndroidxWebGpuViewer(
    val activity: ReaderActivity,
    val isReversed: Boolean,
    val isVertical: Boolean,
) : Viewer {

    private var chapters: ViewerChapters? = null
    private var current by mutableStateOf<ReaderPage?>(null)

    private val imageBackend = AndroidxWebGpuSurfaceRenderer()
    private val gpuImages = mutableMapOf<ReaderPage, AndroidxWebGpuSurfaceRenderer.PageImage>()

    /** Each presented page's overlay state; the Compose layer observes this map. */
    private val presentation = mutableStateMapOf<ReaderPage, PagePresentation>()

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
                    // bring each chapter page onto the fresh one.
                    synchronized(gpuImages) {
                        gpuImages.clear()
                    }
                    presentation.clear()
                    presentAllPages()
                    renderCurrentPage()
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

    @Composable
    override fun Content(modifier: Modifier) {
        LaunchedEffect(Unit) {
            if (!AndroidxWebGpuProbe.probe()) requestFallback()
        }
        Box(
            modifier = modifier
                .fillMaxSize()
                .pointerInput(isReversed, isVertical) {
                    detectTapGestures { offset ->
                        val forward = if (isVertical) {
                            offset.y > size.height / 2f
                        } else {
                            // Mirrored in RTL: the left half advances, the right half goes back.
                            (offset.x > size.width / 2f) != isReversed
                        }
                        if (forward) moveForward() else moveBackward()
                    }
                },
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    SurfaceView(context).apply { holder.addCallback(surfaceCallback) }
                },
                onRelease = { view -> view.holder.removeCallback(surfaceCallback) },
            )
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

    override fun setChapters(chapters: ViewerChapters) {
        this.chapters = chapters
        current = chapters.currChapter.pages?.firstOrNull()
        presentAllPages()
        renderCurrentPage()
    }

    override fun moveToPage(page: ReaderPage) {
        current = page
        presentPage(page)
        renderCurrentPage()
    }

    override fun moveForward() = moveBy(1)

    override fun moveBackward() = moveBy(-1)

    override fun destroy() {
        destroyed = true
        surfaceReady = false
        surfaceJob?.cancel()
        uploadScope.cancel()
        // Releases every page texture and the GPU environment itself; gpuImages only held
        // references into it, so nothing here needs an extra destroy pass.
        imageBackend.stop()
        synchronized(gpuImages) {
            gpuImages.clear()
            watchJobs.clear()
            uploadJobs.clear()
        }
        presentation.clear()
        chapters = null
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
        if (page.status == Page.State.Ready) uploadPage(page)
    }

    /** Presents every page of the loaded chapters, and forgets pages dropped by a chapter swap. */
    private fun presentAllPages() {
        val chapters = this.chapters ?: return
        val pages = buildList {
            chapters.prevChapter?.let { addAll(it.pages.orEmpty()) }
            addAll(chapters.currChapter.pages.orEmpty())
            chapters.nextChapter?.let { addAll(it.pages.orEmpty()) }
        }
        synchronized(gpuImages) {
            (watchJobs.keys + uploadJobs.keys + presentation.keys).filterNot { it in pages }.forEach { page ->
                watchJobs.remove(page)?.cancel()
                uploadJobs.remove(page)?.cancel()
                presentation.remove(page)
            }
        }
        pages.forEach { presentPage(it) }
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
     * overlay.
     */
    private fun uploadPage(page: ReaderPage) {
        synchronized(gpuImages) {
            if (destroyed || page in gpuImages || uploadJobs[page]?.isActive == true) return
            uploadJobs[page] = uploadScope.launch {
                try {
                    presentation[page] = PagePresentation.Uploading
                    val input = page.stream?.invoke()
                        ?: throw IllegalStateException("Page ${page.number} has no stream to decode")
                    if (decodeAndUpload(page, input)) {
                        presentation[page] = PagePresentation.Drawn
                    }
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

    /** Decodes [input] and uploads the first full frame's image; true when the page got stored. */
    private suspend fun decodeAndUpload(page: ReaderPage, input: InputStream): Boolean {
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
                        if (destroyed || imageBackend.isDestroyed(image)) {
                            imageBackend.destroy(image)
                            false
                        } else {
                            gpuImages[page] = image
                            true
                        }
                    }
                    if (stored && current === page) renderCurrentPage()
                    return stored
                }
            } finally {
                dec.close()
            }
        }
    }

    /** Points the renderer at the current page's texture (null clears) and draws a frame. */
    private fun renderCurrentPage() {
        val image = current?.let { page -> synchronized(gpuImages) { gpuImages[page] } }
        imageBackend.setPage(image)
        imageBackend.requestRender()
    }

    private fun moveBy(delta: Int) {
        val pages = chapters?.currChapter?.pages ?: return
        if (pages.isEmpty()) return
        val index = current?.let { page -> pages.indexOfFirst { it === page } }?.takeIf { it >= 0 } ?: 0
        val page = pages[(index + delta).coerceIn(pages.indices)]
        current = page
        presentPage(page)
        renderCurrentPage()
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

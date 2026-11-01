package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import ca.mpreg.imagedecoder.ImageDecoder
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
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.InputStream

/**
 * Paged viewer on androidx.webgpu (WebGPU migration, phase 4: real rendering).
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
 */
class AndroidxWebGpuViewer(
    val activity: ReaderActivity,
    val isReversed: Boolean,
    val isVertical: Boolean,
) : Viewer {

    private var chapters: ViewerChapters? = null
    private var current by mutableStateOf<ReaderPage?>(null)
    private var probePassed by mutableStateOf<Boolean?>(null)

    private val imageBackend = AndroidxWebGpuSurfaceRenderer()
    private val gpuImages = mutableMapOf<ReaderPage, AndroidxWebGpuSurfaceRenderer.PageImage>()
    private var gpuImageCount by mutableStateOf(0)
    private val uploadScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var destroyed = false

    /** True between surfaceCreated and surfaceDestroyed; guards the async bring-up. */
    @Volatile
    private var surfaceReady = false

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
                    // Textures died with the previous environment: drop them and re-upload.
                    synchronized(gpuImages) {
                        gpuImages.clear()
                        gpuImageCount = 0
                    }
                    uploadPageBitmaps()
                    renderCurrentPage()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "WebGPU surface bring-up failed" }
                }
            }
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            imageBackend.resize(width, height)
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            surfaceReady = false
            surfaceJob?.cancel()
            // Synchronous: the renderer drains in-flight frames before this call returns,
            // so nothing touches the surface once Android releases it.
            imageBackend.stop()
        }
    }

    @Composable
    override fun Content(modifier: Modifier) {
        LaunchedEffect(Unit) {
            probePassed = AndroidxWebGpuProbe.probe()
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
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Androidx WebGPU viewer (WIP)")
                Text(
                    buildString {
                        append("Paged mode")
                        if (isVertical) append(", vertical")
                        if (isReversed) append(", reversed")
                    },
                )
                Text(current?.let { "Page ${it.index + 1}" } ?: "No page")
                Text("GPU images: $gpuImageCount")
                Text(
                    when (probePassed) {
                        null -> "androidx.webgpu probe: running…"
                        true -> "androidx.webgpu probe: OK"
                        false -> "androidx.webgpu probe: unavailable"
                    },
                )
                Text("Tap left/right (top/bottom) to page; disable the preference to return to the HQ viewer.")
            }
        }
    }

    override fun setChapters(chapters: ViewerChapters) {
        this.chapters = chapters
        current = chapters.currChapter.pages?.firstOrNull()
        uploadPageBitmaps()
        renderCurrentPage()
    }

    override fun moveToPage(page: ReaderPage) {
        current = page
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
        chapters = null
        current = null
        synchronized(gpuImages) {
            gpuImages.clear()
            gpuImageCount = 0
        }
    }

    /**
     * Decodes the page model's image stream with the image decoder and hands the first full
     * frame's pixel buffer to the GPU backend ([GpuImageBackend.createFromImage]); the renderer
     * uploads it as a texture and presents it on the surface. Only pages not yet uploaded are
     * decoded.
     */
    private fun uploadPageBitmaps() {
        val pages = chapters?.currChapter?.pages ?: return
        for (page in pages) {
            val needsUpload = synchronized(gpuImages) { !destroyed && page !in gpuImages }
            if (!needsUpload) continue
            uploadScope.launch {
                val input = page.stream?.invoke() ?: return@launch
                try {
                    decodeAndUpload(page, input)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "uploadPageBitmaps: page ${page.index}: ${e.message}" }
                }
            }
        }
    }

    /** Decodes [input] and uploads the first full frame's image through [imageBackend]. */
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
                    synchronized(gpuImages) {
                        if (destroyed || imageBackend.isDestroyed(image)) {
                            imageBackend.destroy(image)
                        } else {
                            gpuImages[page] = image
                            gpuImageCount = gpuImages.size
                        }
                    }
                    if (current === page) renderCurrentPage()
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
        current = pages[(index + delta).coerceIn(pages.indices)]
        renderCurrentPage()
    }
}

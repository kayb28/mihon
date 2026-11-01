package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
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
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Minimal paged viewer on androidx.webgpu (WebGPU migration, phase B).
 *
 * Work in progress behind [eu.kanade.domain.base.BasePreferences.androidxWebGpuViewer]
 * (default OFF): when the flag is off, [eu.kanade.tachiyomi.ui.reader.setting.ReadingMode]
 * keeps building the ca.mpreg HQ viewers exactly as before.
 *
 * This stub runs the androidx.webgpu bring-up probe ([AndroidxWebGpuProbe]) and starts
 * routing the page model's decoded bitmaps into the [GpuImageBackend] seam. Page rendering,
 * transitions and the continuous mode stay on the ca.mpreg HQ viewer until later migration
 * phases.
 */
class AndroidxWebGpuViewer(
    val activity: ReaderActivity,
    val isReversed: Boolean,
    val isVertical: Boolean,
) : Viewer {

    private var chapters: ViewerChapters? = null
    private var current by mutableStateOf<ReaderPage?>(null)
    private var probePassed by mutableStateOf<Boolean?>(null)

    private val imageBackend: GpuImageBackend<ca.mpreg.webgpuviewer.viewer.ImagePage> = MpregGpuImageBackend
    private val gpuImages = mutableMapOf<ReaderPage, ca.mpreg.webgpuviewer.viewer.ImagePage>()
    private var gpuImageCount by mutableStateOf(0)
    private val uploadScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var destroyed = false

    @Composable
    override fun Content(modifier: Modifier) {
        LaunchedEffect(Unit) {
            probePassed = AndroidxWebGpuProbe.probe()
        }
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp)
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
            verticalArrangement = Arrangement.Center,
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

    override fun setChapters(chapters: ViewerChapters) {
        this.chapters = chapters
        current = chapters.currChapter.pages?.firstOrNull()
        uploadPageBitmaps()
    }

    override fun moveToPage(page: ReaderPage) {
        current = page
    }

    override fun moveForward() = moveBy(1)

    override fun moveBackward() = moveBy(-1)

    override fun destroy() {
        destroyed = true
        uploadScope.cancel()
        chapters = null
        current = null
        synchronized(gpuImages) {
            gpuImages.values.forEach { imageBackend.destroy(it) }
            gpuImages.clear()
        }
    }

    /**
     * Migration step: hand the page model's decoded bitmaps to the GPU backend. Only
     * bitmaps already present on the page model are uploaded; feeding this from the
     * decode pipeline is a later step.
     */
    private fun uploadPageBitmaps() {
        val pages = chapters?.currChapter?.pages ?: return
        for (page in pages) {
            val bitmap = page.bitmap ?: continue
            val needsUpload = synchronized(gpuImages) { !destroyed && page !in gpuImages }
            if (!needsUpload) continue
            uploadScope.launch {
                val image = imageBackend.createFromBitmap(bitmap)
                synchronized(gpuImages) {
                    if (destroyed) {
                        imageBackend.destroy(image)
                    } else {
                        gpuImages[page] = image
                        gpuImageCount = gpuImages.size
                    }
                }
            }
        }
    }

    private fun moveBy(delta: Int) {
        val pages = chapters?.currChapter?.pages ?: return
        if (pages.isEmpty()) return
        val index = current?.let { page -> pages.indexOfFirst { it === page } }?.takeIf { it >= 0 } ?: 0
        current = pages[(index + delta).coerceIn(pages.indices)]
    }
}

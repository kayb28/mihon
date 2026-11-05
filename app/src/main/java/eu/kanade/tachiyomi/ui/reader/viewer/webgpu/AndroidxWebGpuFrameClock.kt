package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

import android.view.Choreographer
import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * A [MonotonicFrameClock] backed by the main thread's [Choreographer] — the same construction
 * AndroidUiDispatcher gives a composition. Two scopes outside any composition need to pace
 * themselves to display frames: the continuous viewer's animation scope (its
 * `animateScrollBy` needs a frame clock on a plain Main dispatcher) and the renderer's frame
 * loop, which draws at most one frame per vsync.
 */
internal val choreographerFrameClock = object : MonotonicFrameClock {
    override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R =
        suspendCancellableCoroutine { cont ->
            val choreographer = Choreographer.getInstance()
            val callback = Choreographer.FrameCallback { frameTimeNanos ->
                try {
                    cont.resume(onFrame(frameTimeNanos))
                } catch (e: Throwable) {
                    // Propagate only while the continuation is still live; resuming a
                    // cancelled one is silently ignored.
                    if (cont.isActive) cont.resumeWithException(e)
                }
            }
            choreographer.postFrameCallback(callback)
            cont.invokeOnCancellation { choreographer.removeFrameCallback(callback) }
        }
}

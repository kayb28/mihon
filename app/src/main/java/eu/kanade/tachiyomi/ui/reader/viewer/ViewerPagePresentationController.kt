package eu.kanade.tachiyomi.ui.reader.viewer

import androidx.compose.runtime.snapshots.SnapshotStateMap
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.viewer.webgpu.PagePresentation

/**
 * Bridges the page state machine (T6) with the androidx.webgpu presentation map: the machine is
 * the single source of truth for a page's phase, this controller mirrors every transition into
 * the [PagePresentation] overlay the Compose layer renders, and answers retry routing for
 * [AndroidxWebGpuBaseViewer.retryPageLoad].
 *
 * The [ViewerPageStateMachine] stays pure and JVM-testable; this class only adapts states to
 * overlays, so its behavior is fully covered by JVM tests without an emulator.
 */
internal class ViewerPagePresentationController(
    private val machine: ViewerPageStateMachine = ViewerPageStateMachine(),
) {

    /** Current machine state of [page]. */
    fun stateOf(page: Any): ViewerPageStateMachine.State = machine.stateOf(page)

    /** Loader picked the page up: overlay shows loading, attempt budget is preserved. */
    fun start(page: ReaderPage, presentation: SnapshotStateMap<ReaderPage, PagePresentation>) {
        machine.start(page)
        presentation[page] = PagePresentation.Loading(-1)
    }

    /** Download progress while loading; ignored once the page left the loading phase. */
    fun progress(
        page: ReaderPage,
        percent: Int,
        presentation: SnapshotStateMap<ReaderPage, PagePresentation>,
    ) {
        machine.progress(page, percent)
        if (machine.stateOf(page).phase == ViewerPageStateMachine.Phase.LOADING) {
            presentation[page] = PagePresentation.Loading(percent)
        }
    }

    /** First full frame drawn: overlay clears, failure state forgotten. */
    fun ready(page: ReaderPage, presentation: SnapshotStateMap<ReaderPage, PagePresentation>) {
        machine.ready(page)
        presentation[page] = PagePresentation.Drawn
    }

    /**
     * A load failed with [error]: the machine classifies it (HTTP/NETWORK/DECODE/STORAGE/
     * CANCELLED/UNKNOWN), records the attempt, and the overlay shows the failure.
     */
    fun failed(
        page: ReaderPage,
        error: Throwable?,
        presentation: SnapshotStateMap<ReaderPage, PagePresentation>,
    ) {
        machine.failed(page, ViewerPageStateMachine.classify(error))
        presentation[page] = PagePresentation.Failed(error ?: IllegalStateException("unknown"))
    }

    /** How a failed page should be retried; see [ViewerPageStateMachine.retryDecision]. */
    fun routeForRetry(page: Any): ViewerPageStateMachine.RetryDecision = machine.routeForRetry(page)

    /**
     * The error overlay's Retry: any failed page goes back to loading regardless of its failure
     * reason — the user's explicit retry always works, cancellations included.
     */
    fun manualRetry(
        page: ReaderPage,
        presentation: SnapshotStateMap<ReaderPage, PagePresentation>,
    ) {
        if (machine.stateOf(page).isFailed) {
            machine.start(page)
            presentation[page] = PagePresentation.Loading(-1)
        }
    }

    /** Viewer rebuilt or page evicted: forget this page entirely. */
    fun clear(page: Any) = machine.clear(page)

    /** Session reset (viewer destroy): forget everything. */
    fun reset() = machine.reset()
}

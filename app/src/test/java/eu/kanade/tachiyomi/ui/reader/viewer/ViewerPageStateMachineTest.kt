package eu.kanade.tachiyomi.ui.reader.viewer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.CancellationException

/** JVM tests for the page state machine (T6): transitions, failure classes, retry routes. */
class ViewerPageStateMachineTest {

    @Test
    fun `full life cycle queued loading ready`() {
        val sm = ViewerPageStateMachine()
        val page = Any()
        assertEquals(ViewerPageStateMachine.Phase.QUEUED, sm.stateOf(page).phase)
        sm.start(page)
        assertEquals(ViewerPageStateMachine.Phase.LOADING, sm.stateOf(page).phase)
        sm.progress(page, 50)
        assertEquals(50, sm.stateOf(page).progressPercent)
        sm.ready(page)
        assertTrue(sm.stateOf(page).isReady)
    }

    @Test
    fun `failure records reason and attempt`() {
        val sm = ViewerPageStateMachine()
        val page = Any()
        sm.start(page)
        sm.failed(page, ViewerPageStateMachine.FailureReason.NETWORK)
        val s = sm.stateOf(page)
        assertTrue(s.isFailed)
        assertEquals(ViewerPageStateMachine.FailureReason.NETWORK, s.reason)
        assertEquals(1, s.attempt)
        sm.start(page)
        sm.failed(page, ViewerPageStateMachine.FailureReason.NETWORK)
        assertEquals(2, sm.stateOf(page).attempt, "attempts accumulate across retries")
    }

    @Test
    fun `classification maps error types to reasons`() {
        assertEquals(
            ViewerPageStateMachine.FailureReason.CANCELLED,
            ViewerPageStateMachine.classify(CancellationException("gone")),
        )
        assertEquals(
            ViewerPageStateMachine.FailureReason.NETWORK,
            ViewerPageStateMachine.classify(SocketTimeoutException("t")),
        )
        assertEquals(
            ViewerPageStateMachine.FailureReason.NETWORK,
            ViewerPageStateMachine.classify(UnknownHostException("h")),
        )
        assertEquals(ViewerPageStateMachine.FailureReason.NETWORK, ViewerPageStateMachine.classify(IOException("i")))
        assertEquals(
            ViewerPageStateMachine.FailureReason.HTTP,
            ViewerPageStateMachine.classify(IllegalStateException("HTTP 502 upstream")),
        )
        assertEquals(ViewerPageStateMachine.FailureReason.UNKNOWN, ViewerPageStateMachine.classify(null))
        assertEquals(
            ViewerPageStateMachine.FailureReason.UNKNOWN,
            ViewerPageStateMachine.classify(RuntimeException("??")),
        )
    }

    @Test
    fun `retry routes by failure type`() {
        val d = ViewerPageStateMachine.retryDecision(ViewerPageStateMachine.FailureReason.NETWORK, attempt = 0)
        assertEquals(ViewerPageStateMachine.RetryDecision.RELOAD_FROM_LOADER, d)
        // Transient routes spend attempts, then stop.
        assertEquals(
            ViewerPageStateMachine.RetryDecision.DO_NOT_RETRY,
            ViewerPageStateMachine.retryDecision(
                ViewerPageStateMachine.FailureReason.NETWORK,
                ViewerPageStateMachine.MAX_RELOAD_ATTEMPTS,
            ),
        )
        // Source already answered: reuse the held stream.
        assertEquals(
            ViewerPageStateMachine.RetryDecision.REUPLOAD,
            ViewerPageStateMachine.retryDecision(ViewerPageStateMachine.FailureReason.DECODE, 0),
        )
        assertEquals(
            ViewerPageStateMachine.RetryDecision.REUPLOAD,
            ViewerPageStateMachine.retryDecision(ViewerPageStateMachine.FailureReason.STORAGE, 0),
        )
        // Cancellation is not retried on its own.
        assertEquals(
            ViewerPageStateMachine.RetryDecision.DO_NOT_RETRY,
            ViewerPageStateMachine.retryDecision(ViewerPageStateMachine.FailureReason.CANCELLED, 0),
        )
    }

    @Test
    fun `routeForRetry honours the recorded failure`() {
        val sm = ViewerPageStateMachine()
        val page = Any()
        assertEquals(
            ViewerPageStateMachine.RetryDecision.DO_NOT_RETRY,
            sm.routeForRetry(page),
            "non-failed page is not retried",
        )
        sm.start(page)
        sm.failed(page, ViewerPageStateMachine.FailureReason.HTTP)
        assertEquals(ViewerPageStateMachine.RetryDecision.RELOAD_FROM_LOADER, sm.routeForRetry(page))
    }

    @Test
    fun `no crash paths - any transition on any state is safe`() {
        val sm = ViewerPageStateMachine()
        val page = Any()
        sm.progress(page, 120) // progress on QUEUED, out of range
        assertEquals(ViewerPageStateMachine.Phase.LOADING, sm.stateOf(page).phase)
        assertEquals(100, sm.stateOf(page).progressPercent)
        sm.ready(page)
        sm.progress(page, 10) // progress after ready is ignored
        assertTrue(sm.stateOf(page).isReady)
        sm.failed(page, ViewerPageStateMachine.FailureReason.DECODE)
        sm.clear(page)
        assertEquals(ViewerPageStateMachine.Phase.QUEUED, sm.stateOf(page).phase)
        sm.reset()
        assertFalse(sm.stateOf(page).isFailed)
    }

    // region T-10.13 (P1-8) — DECODE/STORAGE REUPLOAD is capped, a corrupt image cannot loop

    @Test
    fun `T-10-13 DECODE with attempt budget spent does NOT retry`() {
        assertEquals(
            ViewerPageStateMachine.RetryDecision.DO_NOT_RETRY,
            ViewerPageStateMachine.retryDecision(ViewerPageStateMachine.FailureReason.DECODE, attempt = ViewerPageStateMachine.MAX_REUPLOAD_ATTEMPTS),
        )
        assertEquals(
            ViewerPageStateMachine.RetryDecision.REUPLOAD,
            ViewerPageStateMachine.retryDecision(ViewerPageStateMachine.FailureReason.DECODE, attempt = ViewerPageStateMachine.MAX_REUPLOAD_ATTEMPTS - 1),
        )
    }

    @Test
    fun `T-10-13 STORAGE with attempt budget spent does NOT retry`() {
        assertEquals(
            ViewerPageStateMachine.RetryDecision.DO_NOT_RETRY,
            ViewerPageStateMachine.retryDecision(ViewerPageStateMachine.FailureReason.STORAGE, attempt = 2),
        )
    }

    // endregion

}

package eu.kanade.tachiyomi.ui.reader.viewer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * T6a wiring contract: the androidx.webgpu viewers drive their page life cycle through
 * [ViewerPageStateMachine] via [ViewerPagePresentationController]. These tests fix the
 * observable behavior the wiring must keep — loading phases, failure classification with
 * the retry policy, manual retry always working, and cancelled loads never retried.
 */
class ViewerPagePresentationControllerTest {

    private class Recorder(vararg events: Pair<String, Any?>) {
        val log = mutableListOf<Pair<String, Any?>>()
        private val queue = ArrayDeque(events.toList())

        fun record(key: String, value: Any?) {
            log.add(key to value)
        }

        fun expect() = if (queue.isEmpty()) null else queue.removeFirst()
    }

    @Test
    fun `loader error marks the page failed with the classified reason`() {
        val controller = ViewerPagePresentationController()
        val page = Any()
        controller.start(page)
        assertTrue(controller.stateOf(page).phase == ViewerPageStateMachine.Phase.LOADING)
        val reason = ViewerPageStateMachine.classify(java.net.UnknownHostException("no dns"))
        controller.failed(page, reason)
        val state = controller.stateOf(page)
        assertTrue(state.isFailed)
        assertEquals(ViewerPageStateMachine.FailureReason.NETWORK, state.reason)
        assertEquals(1, state.attempt)
    }

    @Test
    fun `transient failure is reloaded from the loader while attempts last`() {
        val controller = ViewerPagePresentationController()
        val page = Any()
        controller.start(page)
        controller.failed(page, ViewerPageStateMachine.FailureReason.NETWORK)
        assertEquals(
            ViewerPageStateMachine.RetryDecision.RELOAD_FROM_LOADER,
            controller.routeForRetry(page),
        )
    }

    @Test
    fun `decode failure is re-uploaded from the held stream`() {
        val controller = ViewerPagePresentationController()
        val page = Any()
        controller.start(page)
        controller.failed(page, ViewerPageStateMachine.FailureReason.DECODE)
        assertEquals(
            ViewerPageStateMachine.RetryDecision.REUPLOAD,
            controller.routeForRetry(page),
        )
    }

    @Test
    fun `cancelled load is never retried automatically`() {
        val controller = ViewerPagePresentationController()
        val page = Any()
        controller.start(page)
        controller.failed(page, ViewerPageStateMachine.FailureReason.CANCELLED)
        assertEquals(
            ViewerPageStateMachine.RetryDecision.DO_NOT_RETRY,
            controller.routeForRetry(page),
        )
    }

    @Test
    fun `manual retry works on any failed page regardless of reason`() {
        val controller = ViewerPagePresentationController()
        val page = Any()
        controller.start(page)
        controller.failed(page, ViewerPageStateMachine.FailureReason.CANCELLED)
        // Manual retry resets the page to loading — the user's Retry always works.
        controller.manualRetry(page)
        assertEquals(ViewerPageStateMachine.Phase.LOADING, controller.stateOf(page).phase)
    }

    @Test
    fun `ready clears the failure and records a drawn page`() {
        val controller = ViewerPagePresentationController()
        val page = Any()
        controller.start(page)
        controller.failed(page, ViewerPageStateMachine.FailureReason.HTTP)
        controller.ready(page)
        assertTrue(controller.stateOf(page).isReady)
        assertFalse(controller.stateOf(page).isFailed)
    }

    @Test
    fun `attempt budget is enforced for transient reloads`() {
        val controller = ViewerPagePresentationController()
        val page = Any()
        controller.start(page)
        repeat(ViewerPageStateMachine.MAX_RELOAD_ATTEMPTS) {
            controller.failed(page, ViewerPageStateMachine.FailureReason.HTTP)
            assertEquals(
                ViewerPageStateMachine.RetryDecision.RELOAD_FROM_LOADER,
                controller.routeForRetry(page),
            )
            controller.start(page)
        }
        controller.failed(page, ViewerPageStateMachine.FailureReason.HTTP)
        assertEquals(
            ViewerPageStateMachine.RetryDecision.DO_NOT_RETRY,
            controller.routeForRetry(page),
        )
    }

    @Test
    fun `clear removes one page and reset clears the session`() {
        val controller = ViewerPagePresentationController()
        val a = Any()
        val b = Any()
        controller.start(a)
        controller.start(b)
        controller.clear(a)
        assertEquals(ViewerPageStateMachine.Phase.QUEUED, controller.stateOf(a).phase)
        assertEquals(ViewerPageStateMachine.Phase.LOADING, controller.stateOf(b).phase)
        controller.reset()
        assertEquals(ViewerPageStateMachine.Phase.QUEUED, controller.stateOf(b).phase)
    }
}

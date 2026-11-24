package eu.kanade.tachiyomi.ui.reader.viewer

/**
 * Page state machine for the viewers (T6): Queued→Loading→Ready→Failed(reason), with a retry
 * policy by failure type and NO crash paths — every transition accepts any current state and
 * never throws, so no loader surprise can take the reader down.
 *
 * Pure and JVM-testable; viewers keep their render plumbing and drive the machine with plain
 * events (start / progress / ready / failed).
 */
class ViewerPageStateMachine {

    /** Life-cycle phases of one page inside a viewer. */
    enum class Phase { QUEUED, LOADING, READY, FAILED }

    /**
     * Why a page failed, classified from the loader error. The classification decides the
     * retry route; it is deliberately coarse — the point is retry-vs-dont, not diagnostics.
     */
    enum class FailureReason {
        /** Source answered with a non-200 (rate limit, gone, server hiccup…). */
        HTTP,

        /** Connection-level trouble (timeout, DNS, socket reset). */
        NETWORK,

        /** The stream arrived but could not be decoded into a frame. */
        DECODE,

        /** The image never reached us (download/cache/storage layer). */
        STORAGE,

        /** The load was cancelled (chapter switch, viewer rebuild) — not an error to retry. */
        CANCELLED,

        /** Anything the classifier could not recognize. */
        UNKNOWN,
    }

    /** Immutable snapshot of one page's state; viewers render from this. */
    data class State(
        val phase: Phase,
        val progressPercent: Int = -1,
        val reason: FailureReason? = null,
        val attempt: Int = 0,
    ) {
        val isFailed: Boolean get() = phase == Phase.FAILED
        val isReady: Boolean get() = phase == Phase.READY
    }

    /** What to do after a failure. */
    enum class RetryDecision {
        /** Route back through the page loader (fresh stream: HTTP/NETWORK/UNKNOWN). */
        RELOAD_FROM_LOADER,

        /** Re-upload from the stream we still hold (DECODE/STORAGE: the source answered). */
        REUPLOAD,

        /** Do not retry on its own (CANCELLED); the user's explicit Retry still works. */
        DO_NOT_RETRY,
    }

    /** Attempts of the SAME page a RELOAD_FROM_LOADER route may spend before giving up. */
    companion object {
        const val MAX_RELOAD_ATTEMPTS = 2

        fun classify(error: Throwable?): FailureReason = when {
            error == null -> FailureReason.UNKNOWN
            error is java.util.concurrent.CancellationException -> FailureReason.CANCELLED
            error.message?.contains("HTTP", ignoreCase = true) == true -> FailureReason.HTTP
            else -> when (error) {
                is java.net.UnknownHostException,
                is java.net.SocketTimeoutException,
                is java.io.IOException,
                -> FailureReason.NETWORK
                is java.lang.IllegalStateException -> FailureReason.DECODE
                is java.lang.OutOfMemoryError -> FailureReason.DECODE
                else -> FailureReason.UNKNOWN
            }
        }

        /**
         * Retry route for a failure. Transient-looking failures go back through the loader
         * while attempts remain; a source that already answered gets its held stream
         * re-uploaded; cancellations are not retried automatically. Never throws.
         */
        fun retryDecision(reason: FailureReason?, attempt: Int): RetryDecision = when (reason) {
            null -> RetryDecision.RELOAD_FROM_LOADER
            FailureReason.HTTP, FailureReason.NETWORK, FailureReason.UNKNOWN ->
                if (attempt < MAX_RELOAD_ATTEMPTS) RetryDecision.RELOAD_FROM_LOADER else RetryDecision.DO_NOT_RETRY
            FailureReason.DECODE, FailureReason.STORAGE -> RetryDecision.REUPLOAD
            FailureReason.CANCELLED -> RetryDecision.DO_NOT_RETRY
        }
    }

    /** Per-page states, main thread only. */
    private val states = HashMap<Any, State>()

    fun stateOf(page: Any): State = states[page] ?: State(Phase.QUEUED)

    fun start(page: Any) {
        states[page] = State(Phase.LOADING, progressPercent = -1, attempt = stateOf(page).attempt)
    }

    fun progress(page: Any, percent: Int) {
        val s = stateOf(page)
        if (s.phase == Phase.LOADING || s.phase == Phase.QUEUED) {
            states[page] = s.copy(phase = Phase.LOADING, progressPercent = percent.coerceIn(0, 100))
        }
    }

    fun ready(page: Any) {
        states[page] = State(Phase.READY)
    }

    /** Records a failure; the previous attempt count is preserved and incremented. */
    fun failed(page: Any, reason: FailureReason) {
        val s = stateOf(page)
        states[page] = State(Phase.FAILED, reason = reason, attempt = s.attempt + 1)
    }

    /** Clears one page (viewer rebuilt, page evicted). */
    fun clear(page: Any) {
        states.remove(page)
    }

    /** Resets everything a session may have accumulated. */
    fun reset() {
        states.clear()
    }

    /**
     * Routes a failed page for retry: RELOAD_FROM_LOADER keeps spending attempts while they
     * last, REUPLOAD reuses the held stream, DO_NOT_RETRY leaves the error overlay in charge.
     * Never throws for any state.
     */
    fun routeForRetry(page: Any): RetryDecision {
        val s = stateOf(page)
        return if (!s.isFailed) RetryDecision.DO_NOT_RETRY else retryDecision(s.reason, s.attempt)
    }
}

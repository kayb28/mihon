package eu.kanade.tachiyomi.data.library

/**
 * Pure decisions of the anti-burst second pass (audit gap F4, B3).
 *
 * [LibraryUpdateWorker] runs the calm pass inline — sequential per source, paced,
 * budgeted — tangled with WorkManager, notifications and the source-health store, so
 * the whole loop cannot run on the JVM. The DECISIONS inside it can, and they are
 * what the audit asked to pin:
 *
 *  - [shouldRetryInCalmPass]: which failures of the main pass get a second chance
 *    before becoming an error. Transient network/HTTP problems do; what pacing cannot
 *    fix — no chapters, a confirmed broken link, an uninstalled source — does not.
 *  - [recordFailure] / [CutoffState]: a source that fails with 5xx [SOURCE_OUTAGE_BREAK_THRESHOLD]
 *    times in a row is declared down and the REST of its queue moves to the calm pass
 *    unattempted; any other outcome resets the outage counter.
 *  - [budgetExhausted]: the calm pass gives each source's queue at most
 *    [CALM_PASS_BUDGET_MS] of wall time; once past the deadline every remaining
 *    title is skipped WITHOUT an error (the worker records it in skippedNoAttempt so
 *    the store keeps the previous error via retainErrors). Skipped is not failed:
 *    the title keeps its history and the repairer keeps its trace.
 *
 * Every value mirrors the constants and branches the worker used inline; extraction
 * is verbatim, no behavior change.
 */
object CalmPassPolicy {

    /** HTTP codes that accumulate a broken-link streak (shared with UpdateErrorStore). */
    val BROKEN_LINK_HTTP_CODES: Set<Int> = setOf(404, 410)

    /** Consecutive 5xx failures that mark the origin as down and cut its queue. */
    const val SOURCE_OUTAGE_BREAK_THRESHOLD = 3

    /** Wall-time budget per source queue in the calm pass. */
    const val CALM_PASS_BUDGET_MS = 20 * 60_000L

    /**
     * True when a main-pass failure deserves a second chance in the calm pass:
     * transient network/HTTP problems (timeouts, 5xx, 429). Data-of-the-title
     * failures — no chapters, a confirmed broken link, an uninstalled source —
     * cannot be fixed by pacing, so they become errors immediately.
     */
    fun shouldRetryInCalmPass(
        isCancellation: Boolean,
        isSourceNotInstalled: Boolean,
        isNoChapters: Boolean,
        httpCode: Int?,
    ): Boolean {
        if (isCancellation) return false
        if (isSourceNotInstalled) return false
        if (isNoChapters) return false
        return httpCode?.let { it !in BROKEN_LINK_HTTP_CODES } ?: true
    }

    /**
     * Mutable tally of consecutive 5xx failures for one source's main-pass queue.
     * [recordFailure] returns true when the origin is declared down and the rest of
     * the queue should move to the calm pass unattempted.
     */
    class CutoffState {
        private var consecutiveOutageFailures = 0

        /** Records an attempt outcome; true = source is down, cut the queue. */
        fun recordFailure(httpCode: Int?, isCancellation: Boolean): Boolean {
            if (isCancellation) return false
            val outage = httpCode?.let { it in 500..599 } == true
            if (!outage) {
                consecutiveOutageFailures = 0
                return false
            }
            consecutiveOutageFailures++
            return consecutiveOutageFailures >= SOURCE_OUTAGE_BREAK_THRESHOLD
        }

        /** A success (or a skipped title) resets the outage tally. */
        fun reset() {
            consecutiveOutageFailures = 0
        }
    }

    /**
     * The calm pass's per-queue budget check: past [startedAt] + [CALM_PASS_BUDGET_MS]
     * every remaining title is skipped WITHOUT an error. [now] is elapsedRealtime in
     * the worker and a plain long in tests.
     */
    fun budgetExhausted(startedAt: Long, now: Long): Boolean = now - startedAt > CALM_PASS_BUDGET_MS
}

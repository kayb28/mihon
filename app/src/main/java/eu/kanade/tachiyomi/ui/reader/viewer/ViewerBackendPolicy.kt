package eu.kanade.tachiyomi.ui.reader.viewer

import eu.kanade.tachiyomi.ui.reader.setting.ReadingModePolicy

/**
 * Selection + degradation policy for the render backend (T5): the circuit breaker that watches
 * consecutive page failures and the stepwise degradation ladder EXPERIMENTAL→HQ→STOCK.
 *
 * Pure and JVM-testable. Everything here is session-scoped: nothing writes the user preference
 * (the degradation must never overwrite what the user chose — next session re-probes by
 * starting from the stored preference again).
 */
object ViewerBackendPolicy {

    /** Consecutive page failures after which the experimental backend is degraded (default 3). */
    const val FAILURE_THRESHOLD = 3

    /**
     * One step down the degradation ladder. Returns null when the backend is already the safest
     * option (STOCK) and nothing can degrade further — the caller must then keep the stock
     * viewer whatever happens.
     */
    fun degrade(mode: ReadingModePolicy.Backend): ReadingModePolicy.Backend? = when (mode) {
        ReadingModePolicy.Backend.EXPERIMENTAL -> ReadingModePolicy.Backend.HQ
        ReadingModePolicy.Backend.HQ -> ReadingModePolicy.Backend.STOCK
        ReadingModePolicy.Backend.STOCK -> null
    }

    /**
     * Circuit breaker over page failures: N consecutive failed pages trip the breaker and the
     * caller degrades the backend for the rest of the session. A page that draws resets the
     * count (isolated failures — one bad image, one dead link — must not degrade the reader).
     */
    class CircuitBreaker(private val threshold: Int = FAILURE_THRESHOLD) {

        private var consecutiveFailures = 0

        /**
         * Records one failed page. Returns true when the breaker is (or just got) tripped —
         * the caller degrades and degradation stays active until [reset], so later failures
         * keep reporting true.
         */
        fun onPageFailure(): Boolean {
            if (consecutiveFailures >= threshold) return true
            consecutiveFailures++
            return consecutiveFailures >= threshold
        }

        /** Records one successfully drawn page: an isolated failure never degrades. */
        fun onPageSuccess() {
            consecutiveFailures = 0
        }

        fun reset() {
            consecutiveFailures = 0
        }

        fun isTripped(): Boolean = consecutiveFailures >= threshold
    }
}

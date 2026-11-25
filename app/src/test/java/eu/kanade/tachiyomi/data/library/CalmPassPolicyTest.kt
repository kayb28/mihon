package eu.kanade.tachiyomi.data.library

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Characterization of the anti-burst calm pass decisions (audit gap F4, B3).
 *
 * The calm pass itself lives inside LibraryUpdateWorker (WorkManager, notifications,
 * source-health bookkeeping) and cannot run on the JVM; these tests pin the three
 * decisions that decide its behavior, extracted verbatim into CalmPassPolicy:
 *  1. which main-pass failures get the second chance (transient only),
 *  2. when a downed origin cuts its queue (3 consecutive 5xx, any other outcome
 *     resets the tally),
 *  3. the per-source budget: past the deadline the remaining titles are skipped
 *     without error (kept via retainErrors by the worker), never registered as
 *     failures.
 */
class CalmPassPolicyTest {

    // --- 1. which failures get a calm-pass second chance ---

    @Test
    fun `transient network failures retry in the calm pass`() {
        // No HTTP code (timeout/IO) and non-404/410 codes (500, 503, 429) are
        // second-chance material: pacing can recover them.
        CalmPassPolicy.shouldRetryInCalmPass(
            isCancellation = false,
            isSourceNotInstalled = false,
            isNoChapters = false,
            httpCode = null,
        ) shouldBe true
        CalmPassPolicy.shouldRetryInCalmPass(false, false, false, httpCode = 500) shouldBe true
        CalmPassPolicy.shouldRetryInCalmPass(false, false, false, httpCode = 429) shouldBe true
    }

    @Test
    fun `data-of-the-title failures never retry in the calm pass`() {
        // No chapters, a confirmed broken link (404/410) and an uninstalled source
        // cannot be fixed by pacing: they become errors in the main pass itself.
        CalmPassPolicy.shouldRetryInCalmPass(false, false, isNoChapters = true, httpCode = null) shouldBe false
        CalmPassPolicy.shouldRetryInCalmPass(false, false, false, httpCode = 404) shouldBe false
        CalmPassPolicy.shouldRetryInCalmPass(false, false, false, httpCode = 410) shouldBe false
        CalmPassPolicy.shouldRetryInCalmPass(
            false,
            isSourceNotInstalled = true,
            isNoChapters = false,
            httpCode = null,
        ) shouldBe false
    }

    @Test
    fun `cancellation is not a failure and never goes to the calm pass`() {
        CalmPassPolicy.shouldRetryInCalmPass(
            isCancellation = true,
            isSourceNotInstalled = false,
            isNoChapters = false,
            httpCode = null,
        ) shouldBe false
    }

    // --- 2. when a downed origin cuts its queue ---

    @Test
    fun `three consecutive 5xx cut the queue, non-outage failures reset the tally`() {
        val state = CalmPassPolicy.CutoffState()
        state.recordFailure(httpCode = 502, isCancellation = false) shouldBe false
        state.recordFailure(httpCode = 522, isCancellation = false) shouldBe false
        // A non-5xx failure (e.g. 404) is not outage evidence: the tally resets.
        state.recordFailure(httpCode = 404, isCancellation = false) shouldBe false
        state.recordFailure(httpCode = 503, isCancellation = false) shouldBe false
        state.recordFailure(httpCode = 502, isCancellation = false) shouldBe false
        state.recordFailure(httpCode = 526, isCancellation = false) shouldBe true
    }

    @Test
    fun `a success resets the outage tally`() {
        val state = CalmPassPolicy.CutoffState()
        state.recordFailure(502, false) shouldBe false
        state.recordFailure(502, false) shouldBe false
        state.reset()
        state.recordFailure(502, false) shouldBe false
        state.recordFailure(502, false) shouldBe false
        state.recordFailure(502, false) shouldBe true
    }

    @Test
    fun `cancellation never counts toward the outage cutoff`() {
        val state = CalmPassPolicy.CutoffState()
        state.recordFailure(502, false) shouldBe false
        state.recordFailure(502, false) shouldBe false
        state.recordFailure(null, isCancellation = true) shouldBe false
        // Still only two outage failures: the third 5xx cuts, not this one.
        state.recordFailure(502, false) shouldBe true
    }

    // --- 3. the per-source budget: skipped without error, never failed ---

    @Test
    fun `the budget only exhausts past the deadline`() {
        val startedAt = 10_000L
        val deadline = startedAt + CalmPassPolicy.CALM_PASS_BUDGET_MS
        CalmPassPolicy.budgetExhausted(startedAt, now = deadline) shouldBe false
        CalmPassPolicy.budgetExhausted(startedAt, now = deadline + 1) shouldBe true
    }

    @Test
    fun `the budget is twenty minutes per source queue`() {
        // The value the worker ran with (20 min); pinned so a change is deliberate.
        CalmPassPolicy.CALM_PASS_BUDGET_MS shouldBe 20 * 60_000L
        CalmPassPolicy.SOURCE_OUTAGE_BREAK_THRESHOLD shouldBe 3
    }
}

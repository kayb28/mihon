package mihon.feature.library.errors

import eu.kanade.tachiyomi.data.library.UpdateError
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Behavior tests for the "Reintentar títulos con error" button (PROMPT 9, T-B, behaviors
 * 5.1-5.5). Red state: they run against the baseline where UpdateErrorsScreen has no
 * retry button at all, and fail on BEHAVIOR, one group per behavior ID.
 *
 * Pure JVM tests of the decision layer (RetryErrorsPolicy): what the button shows, which
 * IDs one press retries, what happens while a pass runs, and that the pass is exactly
 * one batch. The Compose wiring (testTag update_errors_retry_button) is device-validated
 * per the merge gate.
 */
class RetryErrorsPolicyTest {

    @Suppress("unused")
    private fun err(id: Long, sourceId: Long = 1L) = UpdateError(
        mangaId = id,
        sourceId = sourceId,
        type = UpdateError.ErrorType.BROKEN_LINK,
        message = "boom",
        timestamp = 0L,
    )

    // ---------------------------------------------------------------- 5.1

    @Test
    fun `5_1 the button is visible only with at least one error`() {
        RetryErrorsPolicy.buttonVisible(errorCount = 0) shouldBe false
        RetryErrorsPolicy.buttonVisible(errorCount = 1) shouldBe true
        RetryErrorsPolicy.buttonVisible(errorCount = 47) shouldBe true
    }

    // ---------------------------------------------------------------- 5.2

    @Test
    fun `5_2 one press retries exactly the snapshot of error ids at press time`() {
        val snapshot = setOf(10L, 20L, 30L)
        val pass = RetryErrorsPolicy.beginPass(snapshot)
        pass.targetIds shouldContainExactly snapshot
        // A NEW error appearing mid-pass (id 40) is NOT part of this pass: the pass
        // scope was fixed at press time.
        pass.targets(40L) shouldBe false
        pass.targets(20L) shouldBe true
    }

    @Test
    fun `5_2 the pass never retries the whole library or a category`() {
        // The policy only ever exposes the pressed snapshot; there is no "all" mode.
        val pass = RetryErrorsPolicy.beginPass(emptySet())
        pass.targetIds shouldHaveSize 0
        pass.isWholeLibrary shouldBe false
    }

    // ---------------------------------------------------------------- 5.3

    @Test
    fun `5_3 successes leave the list, failures stay with their fresh error`() {
        val pass = RetryErrorsPolicy.beginPass(setOf(1L, 2L, 3L))
        // manga 1 and 2 updated fine, manga 3 failed again with a new message.
        val after = RetryErrorsPolicy.applyResults(
            pass = pass,
            results = mapOf(
                1L to null, // success: UpdateMangaFromRemote already removed its errors
                2L to null,
                3L to RetryErrorsPolicy.FreshError(message = "still broken"),
            ),
        )
        // The UI must refresh from the store (which no longer holds 1 and 2) and keep 3.
        after.needsStoreRefresh shouldBe true
        after.remainingIds shouldContainExactly setOf(3L)
    }

    // ---------------------------------------------------------------- 5.4

    @Test
    fun `5_4 the button is disabled while a pass runs; one batch per press`() {
        val pass = RetryErrorsPolicy.beginPass(setOf(5L))
        // While the pass is in flight the button must be disabled...
        RetryErrorsPolicy.buttonEnabled(pass = pass) shouldBe false
        // ...and a second press must NOT start a second batch.
        RetryErrorsPolicy.beginPass(setOf(6L), pass = pass) shouldBe null
        // When the pass finishes the button recovers.
        val done = RetryErrorsPolicy.completePass(pass)
        RetryErrorsPolicy.buttonEnabled(pass = done) shouldBe true
        RetryErrorsPolicy.beginPass(setOf(6L), pass = done) != null shouldBe true
    }

    @Test
    fun `5_4 all-success ends in the empty state`() {
        val pass = RetryErrorsPolicy.beginPass(setOf(7L, 8L))
        val after = RetryErrorsPolicy.applyResults(
            pass = pass,
            results = mapOf(7L to null, 8L to null),
        )
        after.remainingIds shouldHaveSize 0
        after.isEmptyState shouldBe true
    }

    // ---------------------------------------------------------------- 5.5

    @Test
    fun `5_5 the retry pass reads and writes only through the audited seams`() {
        // The policy itself is inert: it never touches UpdateErrorStore's calm pass
        // (B3), RepairOutcomePolicy or CalmPass — it only plans the batch and folds
        // results. The seams it names are exactly the audited ones.
        RetryErrorsPolicy.SEAMS shouldContainExactly listOf(
            "getErrorMangaIds", // snapshot at press time
            "UpdateMangaFromRemote", // one call per id (success self-cleans via removeErrorsForManga)
            "errorsFlow", // the screen refreshes from the store flow, not from ad-hoc state
        )
    }
}

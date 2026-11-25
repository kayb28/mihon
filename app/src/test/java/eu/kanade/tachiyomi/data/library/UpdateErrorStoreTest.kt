package eu.kanade.tachiyomi.data.library

import android.content.Context
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Characterization tests for the link-repairer's error store (audit gaps F4/F5, B2).
 *
 * The store persists the update errors under `filesDir/update_errors.json` with an
 * atomic tmp+rename save; on Android `context.filesDir` points at the app's private
 * storage, so these JVM tests fake it with a per-test temp directory — the only
 * Context surface the store touches. The [Json] instance mirrors the app binding
 * (ignoreUnknownKeys + no explicit nulls).
 *
 * These tests pin the semantics the worker relies on:
 *  - [setErrors] is a TOTAL replacement that still carries the accumulated
 *    retryCount of titles that fail again (full pass).
 *  - [replaceForManga] swaps exactly the processed titles and is a no-op when the
 *    processed set is empty (partial pass; nothing was attempted).
 *  - [retainErrors] appends verbatim, with NO merge, so an unattempted title keeps
 *    its previous error when the calm-pass budget cuts the queue.
 *  - incrementRetryCount / removeErrorsForManga / clear do exactly what the repair
 *    flow needs after a migration attempt (retry bookkeeping and cleanup).
 *  - the JSON file round-trips: a new store instance over the same directory reads
 *    back the exact persisted state.
 *
 * B1 (broken-link classification) is covered by the mergeWithPrevious tests at the
 * bottom, including the sticky-streak semantics once the confirmation threshold is
 * reached — a deliberate design point that differs from the audit's one-line
 * description (see the test's KDoc).
 */
class UpdateErrorStoreTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var store: UpdateErrorStore

    @BeforeEach
    fun setUp() {
        val context = mockk<Context>()
        every { context.filesDir } returns tempDir
        store = UpdateErrorStore(context, Json { ignoreUnknownKeys = true; explicitNulls = false })
    }

    private fun error(
        mangaId: Long,
        httpCode: Int? = null,
        type: UpdateError.ErrorType = UpdateError.ErrorType.TEMPORARY,
        retryCount: Int = 0,
        brokenLinkStreak: Int = 0,
    ) = UpdateError(
        mangaId = mangaId,
        sourceId = 1L,
        httpCode = httpCode,
        message = "manga $mangaId",
        type = type,
        timestamp = 1_000L + mangaId,
        retryCount = retryCount,
        brokenLinkStreak = brokenLinkStreak,
    )

    @Test
    fun `setErrors replaces the whole list but keeps the accumulated retryCount`() {
        store.setErrors(listOf(error(1, retryCount = 2), error(2)))
        // Full pass: title 1 fails again (retryCount restarts at 0), title 2 recovered,
        // title 3 is new. The list must become exactly this run's errors, with title 1
        // carrying its previous retryCount of 2.
        store.setErrors(listOf(error(1), error(3)))
        val errors = store.getErrors()
        errors.map { it.mangaId } shouldContainExactly listOf(1L, 3L)
        errors.first { it.mangaId == 1L }.retryCount shouldBe 2
    }

    @Test
    fun `replaceForManga swaps only the processed titles and keeps the rest`() {
        store.setErrors(listOf(error(1), error(2)))
        // Partial pass over {1, 3}: title 1's error is replaced by this run's, title 2
        // was not touched so its error survives, title 3 is new.
        store.replaceForManga(listOf(error(1, httpCode = 500), error(3)), processedIds = setOf(1L, 3L))
        store.getErrors().map { it.mangaId } shouldContainExactly listOf(2L, 1L, 3L)
    }

    @Test
    fun `replaceForManga with an empty processed set is a no-op`() {
        store.setErrors(listOf(error(1), error(2)))
        store.replaceForManga(listOf(error(3)), processedIds = emptySet())
        store.getErrors().map { it.mangaId } shouldContainExactly listOf(1L, 2L)
    }

    @Test
    fun `retainErrors appends verbatim without merging`() {
        store.setErrors(listOf(error(1, retryCount = 1, brokenLinkStreak = 2)))
        // The calm-pass budget cut the queue before title 1 was attempted: the worker
        // re-adds its carried error. Even if the incoming copy carried different
        // counters, the store must not merge — the KDoc contract says the caller does
        // not pass ids that are already present, and this test documents the append.
        store.retainErrors(listOf(error(1, retryCount = 9)))
        val errors = store.getErrors()
        errors.size shouldBe 2
        errors[0].retryCount shouldBe 1
        errors[1].retryCount shouldBe 9
    }

    @Test
    fun `incrementRetryCount bumps only the target manga`() {
        store.setErrors(listOf(error(1), error(2)))
        store.incrementRetryCount(1)
        val errors = store.getErrors()
        errors.first { it.mangaId == 1L }.retryCount shouldBe 1
        errors.first { it.mangaId == 2L }.retryCount shouldBe 0
    }

    @Test
    fun `removeErrorsForManga drops every error of that manga`() {
        store.setErrors(listOf(error(1), error(2), error(1, type = UpdateError.ErrorType.BROKEN_LINK)))
        store.removeErrorsForManga(1)
        store.getErrors().map { it.mangaId } shouldContainExactly listOf(2L)
    }

    @Test
    fun `clear empties the store`() {
        store.setErrors(listOf(error(1), error(2)))
        store.clear()
        store.getErrors() shouldContainExactly emptyList()
    }

    @Test
    fun `persisted state round-trips through the JSON file`() {
        store.setErrors(
            listOf(
                error(
                    1,
                    httpCode = 404,
                    type = UpdateError.ErrorType.BROKEN_LINK,
                    retryCount = 3,
                    brokenLinkStreak = 3,
                ),
                error(2, httpCode = 503),
            ),
        )
        val context = mockk<Context>()
        every { context.filesDir } returns tempDir
        val reloaded = UpdateErrorStore(context, Json { ignoreUnknownKeys = true; explicitNulls = false })
        reloaded.getErrors() shouldContainExactly store.getErrors()
    }

    // --- B1: broken-link classification (mergeWithPrevious semantics) ---

    @Test
    fun `three consecutive 404s confirm a broken link on the third`() {
        // 404/410 accumulate brokenLinkStreak; BROKEN_LINK requires
        // streak >= BROKEN_LINK_CONFIRMATION_THRESHOLD (3).
        store.setErrors(listOf(error(1, httpCode = 404)))
        store.getErrors().single().let {
            it.type shouldBe UpdateError.ErrorType.TEMPORARY
            it.brokenLinkStreak shouldBe 1
        }
        store.setErrors(listOf(error(1, httpCode = 404)))
        store.getErrors().single().let {
            it.type shouldBe UpdateError.ErrorType.TEMPORARY
            it.brokenLinkStreak shouldBe 2
        }
        store.setErrors(listOf(error(1, httpCode = 404)))
        store.getErrors().single().let {
            it.type shouldBe UpdateError.ErrorType.BROKEN_LINK
            it.brokenLinkStreak shouldBe 3
        }
    }

    @Test
    fun `a non-404-410 code resets the streak below the threshold`() {
        // Two 404s build a streak of 2; a 500 then resets it to 0 and the error stays
        // TEMPORARY — the threshold was never reached.
        store.setErrors(listOf(error(1, httpCode = 404)))
        store.setErrors(listOf(error(1, httpCode = 404)))
        store.setErrors(listOf(error(1, httpCode = 500)))
        store.getErrors().single().let {
            it.type shouldBe UpdateError.ErrorType.TEMPORARY
            it.brokenLinkStreak shouldBe 0
        }
    }

    @Test
    fun `non-404-410 codes never accumulate a streak`() {
        store.setErrors(listOf(error(1, httpCode = 500)))
        store.setErrors(listOf(error(1, httpCode = 503)))
        store.setErrors(listOf(error(1, httpCode = 429)))
        store.getErrors().single().let {
            it.type shouldBe UpdateError.ErrorType.TEMPORARY
            it.brokenLinkStreak shouldBe 0
        }
    }

    @Test
    fun `a confirmed broken link stays broken through a later transient code`() {
        // DELIBERATE semantics (differences vs the audit one-liner, kept as-is): once
        // the streak has confirmed a broken link, a later non-404/410 code does NOT
        // demote it back to TEMPORARY — an intermittent source serving a 500 between
        // 404s should not reset a link the store already confirmed as broken. The
        // streak resets to 0 for bookkeeping, but the type stays BROKEN_LINK.
        repeat(3) { store.setErrors(listOf(error(1, httpCode = 404))) }
        store.setErrors(listOf(error(1, httpCode = 500)))
        store.getErrors().single().let {
            it.type shouldBe UpdateError.ErrorType.BROKEN_LINK
            it.brokenLinkStreak shouldBe 0
        }
    }

    @Test
    fun `an explicitly broken type from the worker is preserved as-is`() {
        // The worker classifies SourceNotInstalledException as BROKEN_LINK directly;
        // the store must not downgrade it even without any streak.
        store.setErrors(listOf(error(1, type = UpdateError.ErrorType.BROKEN_LINK)))
        store.getErrors().single().type shouldBe UpdateError.ErrorType.BROKEN_LINK
    }
}

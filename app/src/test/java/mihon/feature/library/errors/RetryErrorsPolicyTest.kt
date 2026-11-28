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
        val pass = RetryErrorsPolicy.beginPass(snapshot)!!
        pass.targetIds shouldContainExactly snapshot
        // A NEW error appearing mid-pass (id 40) is NOT part of this pass: the pass
        // scope was fixed at press time.
        pass.targets(40L) shouldBe false
        pass.targets(20L) shouldBe true
    }

    @Test
    fun `5_2 the pass never retries the whole library or a category`() {
        // The policy only ever exposes the pressed snapshot; there is no "all" mode.
        val pass = RetryErrorsPolicy.beginPass(emptySet())!!
        pass.targetIds shouldHaveSize 0
        pass.isWholeLibrary shouldBe false
    }

    // ---------------------------------------------------------------- 5.3

    @Test
    fun `5_3 successes leave the list, failures stay with their fresh error`() {
        val pass = RetryErrorsPolicy.beginPass(setOf(1L, 2L, 3L))!!
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
    fun `5_4 the button is disabled while a pass runs - one batch per press`() {
        val pass = RetryErrorsPolicy.beginPass(setOf(5L))!!
        // While the pass is in flight the button must be disabled...
        RetryErrorsPolicy.buttonEnabled(pass = pass) shouldBe false
        // ...and a second press must NOT start a second batch.
        RetryErrorsPolicy.beginPass(setOf(6L), pass = pass) shouldBe null
        // When the pass finishes the button recovers.
        val done = RetryErrorsPolicy.completePass(pass)
        RetryErrorsPolicy.buttonEnabled(pass = done) shouldBe true
        (RetryErrorsPolicy.beginPass(setOf(6L), pass = done) != null) shouldBe true
    }

    @Test
    fun `5_4 all-success ends in the empty state`() {
        val pass = RetryErrorsPolicy.beginPass(setOf(7L, 8L))!!
        val after = RetryErrorsPolicy.applyResults(
            pass = pass,
            results = mapOf(7L to null, 8L to null),
        )
        after.remainingIds shouldHaveSize 0
        after.isEmptyState shouldBe true
    }

    // ---------------------------------------------------- T-C (PROMPT 12)

    /**
     * Red T-C: «pulsar no produce efecto visible». El store guarda errores de títulos que
     * ya NO están en la lista mostrada (la pantalla solo muestra favoritos: el filtro
     * `takeIf { it.favorite }` de UpdateErrorsViewModel), y hasta T-C el lote reintentaba
     * el snapshot crudo del store (`getErrorMangaIds()`). Si el store tiene ids invisibles
     * (no favoritos / borrados), el lote corre sobre títulos que no se ven, la lista no
     * cambia y el usuario ve una pulsación sin efecto.
     */
    @Test
    fun `tc_2 the pass scope is exactly the visible list, not the raw store`() {
        // Store: ids 1, 2, 3, 99 — pero en pantalla solo se ven 1 y 2 (favoritos).
        val scope = RetryErrorsPolicy.passScope(
            storeIds = setOf(1L, 2L, 3L, 99L),
            visibleIds = setOf(1L, 2L),
        )
        scope shouldContainExactly setOf(1L, 2L)
        (3L in scope) shouldBe false // no favorito: fuera del lote
        (99L in scope) shouldBe false // borrado de la biblioteca: fuera del lote
    }

    @Test
    fun `tc_2 a press with only invisible ids produces no batch at all`() {
        // Store con ids que no están en pantalla: el lote no debe existir (nada visible que reintentar).
        val scope = RetryErrorsPolicy.passScope(
            storeIds = setOf(99L, 100L),
            visibleIds = emptySet(),
        )
        scope shouldHaveSize 0
    }

    /**
     * Red T-C: «no cambia a Reintentando… ni hay aviso con recuento». Al terminar el lote
     * la UI debe mostrar «N reintentados: M bien, K siguen fallando».
     */
    @Test
    fun `tc_3 the pass ends with a summary of how many retried, ok and still failing`() {
        val pass = RetryErrorsPolicy.beginPass(setOf(1L, 2L, 3L))!!
        val summary = RetryErrorsPolicy.summarize(
            pass = pass,
            results = mapOf(
                1L to null, // ok: salió de la lista
                2L to RetryErrorsPolicy.FreshError(message = "still broken"),
                3L to RetryErrorsPolicy.FreshError(message = "timeout"),
            ),
        )
        summary.retried shouldBe 3
        summary.ok shouldBe 1
        summary.stillFailing shouldBe 2
    }

    @Test
    fun `tc_5 a running pass still blocks a second press (double press does not double the batch)`() {
        val first = RetryErrorsPolicy.beginPass(setOf(1L))!!
        RetryErrorsPolicy.beginPass(setOf(1L), pass = first) shouldBe null
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

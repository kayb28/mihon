package mihon.feature.library.errors

/**
 * Pure decision layer for the "Reintentar títulos con error" button (PROMPT 9, T-B).
 * The Compose wiring lives in UpdateErrorsScreen/ViewModel; this object plans what one
 * press does: retry exactly the error IDs snapshotted at press time, one batch, disabled
 * while it runs, refreshed from the store when it ends.

 * 5.5 by construction: the policy is inert (no store calls), so the calm pass (B3),
 * RepairOutcomePolicy and CalmPass are untouched.
 */
object RetryErrorsPolicy {

    /** The store seams this feature is allowed to use (audited facts, T-B). */
    val SEAMS: List<String> = listOf(
        "getErrorMangaIds", // snapshot at press time
        "UpdateMangaFromRemote", // one call per id (success self-cleans via removeErrorsForManga)
        "errorsFlow", // the screen refreshes from the store flow, not from ad-hoc state
    )

    /** 5.1: the button is visible only when the error list has at least one row. */
    fun buttonVisible(errorCount: Int): Boolean = errorCount > 0

    /** One retry pass: the snapshot of error manga IDs fixed at press time. */
    data class Pass(
        val targetIds: Set<Long>,
        val running: Boolean,
    )

    /** 5.2: begin a pass from the pressed snapshot; null if a pass already runs (5.4). */
    fun beginPass(snapshot: Set<Long>, pass: Pass? = null): Pass? {
        if (pass?.running == true) return null
        return Pass(targetIds = snapshot, running = true)
    }

    /** A fresh failure for a retried manga (its row stays with the new message). */
    data class FreshError(val message: String)

    /** What the UI must do once per-manga results are in. */
    data class PassOutcome(
        val needsStoreRefresh: Boolean,
        val remainingIds: Set<Long>,
        val isEmptyState: Boolean,
    )

    /** 5.3: fold per-manga results — success rows already left via the store; failures stay. */
    fun applyResults(pass: Pass, results: Map<Long, FreshError?>): PassOutcome {
        val remaining = results.filterValues { it != null }.keys.toSet()
        return PassOutcome(
            needsStoreRefresh = true,
            remainingIds = remaining,
            isEmptyState = remaining.isEmpty(),
        )
    }

    // ---------------------------------------------------- T-C (PROMPT 12)
    // RED state: these reproduce the reported bug («pulsar no produce efecto visible»),
    // they are NOT the fix. passScope returns the raw store snapshot (including ids that
    // are not on screen), and no pass summary exists yet.

    /** RED T-C: the batch scope — still the raw store snapshot (bug: includes invisible ids). */
    fun passScope(storeIds: Set<Long>, visibleIds: Set<Long>): Set<Long> = storeIds

    /** RED T-C: end-of-pass counts — still missing (zeros placeholder). */
    data class PassSummary(val retried: Int, val ok: Int, val stillFailing: Int)

    fun summarize(pass: Pass, results: Map<Long, FreshError?>): PassSummary = PassSummary(0, 0, 0)

    /** 5.4: the button is enabled only when no pass is running. */
    fun buttonEnabled(pass: Pass?): Boolean = pass?.running != true

    /** 5.4: a finished pass frees the button for the next press. */
    fun completePass(pass: Pass): Pass? = pass.copy(running = false)
}

/** Whether [id] belongs to this pass's fixed scope. */
fun RetryErrorsPolicy.Pass.targets(id: Long): Boolean = id in targetIds

/** The pass never covers the whole library or a category — only its snapshot. */
val RetryErrorsPolicy.Pass.isWholeLibrary: Boolean get() = false

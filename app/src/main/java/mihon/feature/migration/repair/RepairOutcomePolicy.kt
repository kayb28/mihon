package mihon.feature.migration.repair

/**
 * Pure decision layer of the link repairer's resolver pipeline (audit gap F5, B4).
 *
 * [RepairLinksViewModel.searchRepair] runs up to three network passes — exact-title on
 * the manga's own source, deep (cleaned-title) fallback on the same source, and the
 * secondary cross-source resolver over the OTHER installed sources — and then folds
 * their outcomes into the [ProposedRepair.Status] the UI acts on. That folding is
 * policy, not I/O, so it lives here where it is JVM-testable without the ViewModel's
 * Android dependencies. The ViewModel keeps ownership of the network calls and of
 * attaching the candidate manga / target source to the item; this policy only decides
 * WHAT the item becomes.
 *
 * Contract (extracted verbatim from searchRepair's decision tree):
 *  - An exact-title auto-accept on the own source is [Status.MATCH_FOUND]: the only
 *    status eligible for bulk repair (executeRepair filters on it), so only exact
 *    matches are ever migrated in bulk.
 *  - A deep-pass pick that re-checked as an exact title is equally [Status.MATCH_FOUND].
 *    A fuzzy deep pick is NEVER [Status.MATCH_FOUND]: it lands on
 *    [Status.NEEDS_REVIEW] with its similarity score, confirmed item by item.
 *  - A cross-source exact match is [Status.CROSS_SOURCE_MATCH]: never bulk repaired —
 *    migrating across sources can change chapter availability, so each one is
 *    confirmed with its own Repair button.
 *  - A pass that failed (network down, source error, source hanging past its
 *    per-source timeout) without producing any candidate is [Status.SEARCH_ERROR] —
 *    retryable per item, distinct from a clean [Status.NO_MATCH]. One hanging source
 *    does not sink the item: if any other source produced an exact candidate, the
 *    candidate wins over the error.
 *  - Ambiguous results (multiple identical titles, same URL as the current link) are
 *    [Status.NEEDS_REVIEW] with the reason the list shows, and NEVER fall through to
 *    the fuzzy/cross passes — a fuzzy pick cannot resolve a tie or a self-link.
 */
object RepairOutcomePolicy {

    /** What the deep pass found: an exact-title pick, a fuzzy pick, or nothing at all. */
    data class DeepPick(
        val isExactTitle: Boolean,
        val similarity: Double,
    )

    /** Results of the secondary resolver: how many exact candidates, and whether any source errored/timed out. */
    data class CrossOutcome(
        val candidateCount: Int,
        val hadError: Boolean,
    )

    /** The decision: the status plus the fields the UI needs beyond it. */
    data class Outcome(
        val status: Status,
        val isExactMatch: Boolean = true,
        val matchScore: Double? = null,
        val reason: String? = null,
        /** Extra cross-source candidates beyond the first (shown as "+N more"). */
        val crossSourceExtraMatches: Int = 0,
    )

    enum class Status {
        MATCH_FOUND,
        CROSS_SOURCE_MATCH,
        NEEDS_REVIEW,
        NO_MATCH,
        SEARCH_ERROR,
    }

    /**
     * Folds the resolver results into the item's outcome.
     *
     * @param sourceAvailable false when the manga's own source is not installed — the
     *   exact pass is skipped and the cross-source resolver is the only hope.
     * @param exact result of the exact-title pass on the own source; NeedsReview(NO_MATCH)
     *   is what the caller synthesizes when the pass threw or the source is missing.
     * @param exactFailed the exact pass threw (remembered for the final error verdict —
     *   the deep pass may still recover the item).
     * @param deep the deep pass result, or null when it did not run (it only runs when
     *   the exact pass found nothing at all AND the source is available).
     * @param deepFailed the deep pass threw after the exact pass found nothing.
     * @param cross the secondary resolver result, or null when it did not run (it runs
     *   only when neither the exact nor the deep pass produced anything).
     */
    fun resolve(
        sourceAvailable: Boolean,
        exact: TitleMatcher.MatchResult,
        exactFailed: Boolean,
        deep: DeepPick?,
        deepFailed: Boolean,
        cross: CrossOutcome?,
    ): Outcome = when (exact) {
        is TitleMatcher.MatchResult.AutoAccept -> Outcome(Status.MATCH_FOUND, isExactMatch = true)

        // The repair flow never produces this variant (findMatchWithEvidence is not
        // called there); the branch exists so the fold stays exhaustive and mirrors
        // the same defensive mapping searchRepair applies.
        is TitleMatcher.MatchResult.AutoAcceptWithoutEvidence ->
            Outcome(Status.NEEDS_REVIEW, isExactMatch = true)

        is TitleMatcher.MatchResult.NeedsReview -> when {
            // Only NO_MATCH goes to the deeper passes; MULTIPLE_EXACT_MATCHES, SAME_URL
            // and the metadata conflicts stay manual-review — a fuzzy pick cannot
            // resolve a tie or a self-link.
            exact.reason != TitleMatcher.MatchResult.NeedsReview.Reason.NO_MATCH ->
                Outcome(
                    Status.NEEDS_REVIEW,
                    reason = when (exact.reason) {
                        TitleMatcher.MatchResult.NeedsReview.Reason.MULTIPLE_EXACT_MATCHES ->
                            "Multiple identical titles"
                        TitleMatcher.MatchResult.NeedsReview.Reason.SAME_URL ->
                            "Same URL as current"
                        else -> null
                    },
                )

            // Source missing (extension uninstalled): the exact pass never ran and
            // there is no deep pass — the cross-source resolver decides alone, and
            // only ITS error can make the item retryable.
            !sourceAvailable -> crossOutcome(cross, otherFailed = false)

            // Deep pass produced nothing (no result or errored): cross-source resolver
            // decides, with the exact/deep failures remembered for the error verdict.
            deep == null || (!deep.isExactTitle && deep.similarity <= 0.0) ->
                crossOutcome(cross, otherFailed = exactFailed || deepFailed)

            // Deep pass found an exact title through a cleaned query: as trustworthy
            // as the strict pass.
            deep.isExactTitle -> Outcome(Status.MATCH_FOUND)

            // Fuzzy pick: review with its score, never bulk.
            else -> Outcome(
                Status.NEEDS_REVIEW,
                isExactMatch = false,
                matchScore = deep.similarity,
                reason = "Fuzzy match ${(deep.similarity * 100).toInt()}%",
            )
        }
    }

    /**
     * The cross-source resolver's verdict: a candidate wins over any error; without
     * candidates, an error from any pass that ran (its own, the exact or the deep)
     * makes the item retryable (SEARCH_ERROR) instead of a clean NO_MATCH.
     */
    private fun crossOutcome(cross: CrossOutcome?, otherFailed: Boolean): Outcome {
        if (cross != null && cross.candidateCount > 0) {
            return Outcome(
                Status.CROSS_SOURCE_MATCH,
                crossSourceExtraMatches = cross.candidateCount - 1,
            )
        }
        return if (cross?.hadError == true || otherFailed) {
            Outcome(Status.SEARCH_ERROR)
        } else {
            Outcome(Status.NO_MATCH)
        }
    }
}

package mihon.feature.migration.repair

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import mihon.feature.migration.repair.RepairOutcomePolicy.CrossOutcome
import mihon.feature.migration.repair.RepairOutcomePolicy.DeepPick
import mihon.feature.migration.repair.RepairOutcomePolicy.Outcome
import mihon.feature.migration.repair.RepairOutcomePolicy.Status
import mihon.feature.migration.repair.TitleMatcher.MatchResult
import org.junit.jupiter.api.Test

/**
 * Characterization of the link repairer's outcome policy (audit gap F5, B4).
 *
 * The policy folds the resolver pipeline's results into the status the UI acts on.
 * These tests pin the four behaviors the audit asked for:
 *  1. Bulk repair safety — only exact matches (own-source or deep-pass exact) reach
 *     MATCH_FOUND; fuzzy picks and cross-source matches can never be bulk migrated.
 *  2. SEARCH_ERROR ≠ NO_MATCH — a failed pass (network, source error, per-source
 *     timeout) with no candidate leaves the item retryable, not "no match".
 *  3. One hanging source does not sink the item — a cross-source candidate wins over
 *     the cross resolver's error flag (the other sources' exact match is real).
 *  4. Ambiguity never falls through — MULTIPLE_EXACT_MATCHES / SAME_URL /
 *     metadata conflicts go straight to NEEDS_REVIEW without any second pass.
 */
class RepairOutcomePolicyTest {

    private fun noMatch() = MatchResult.NeedsReview(MatchResult.NeedsReview.Reason.NO_MATCH)

    @Test
    fun `exact auto-accept on the own source is the only bulk-eligible status`() {
        val outcome = RepairOutcomePolicy.resolve(
            sourceAvailable = true,
            exact = MatchResult.AutoAccept(candidate = mihonDummy()),
            exactFailed = false,
            deep = null,
            deepFailed = false,
            cross = null,
        )
        outcome.status shouldBe Status.MATCH_FOUND
        outcome.isExactMatch shouldBe true
    }

    @Test
    fun `deep pass exact title is as trustworthy as the strict pass`() {
        val outcome = RepairOutcomePolicy.resolve(
            sourceAvailable = true,
            exact = noMatch(),
            exactFailed = false,
            deep = DeepPick(isExactTitle = true, similarity = 1.0),
            deepFailed = false,
            cross = null,
        )
        outcome.status shouldBe Status.MATCH_FOUND
    }

    @Test
    fun `fuzzy deep pick stays in review with its score and never bulk-migrates`() {
        val outcome = RepairOutcomePolicy.resolve(
            sourceAvailable = true,
            exact = noMatch(),
            exactFailed = false,
            deep = DeepPick(isExactTitle = false, similarity = 0.62),
            deepFailed = false,
            cross = null,
        )
        outcome.status shouldBe Status.NEEDS_REVIEW
        outcome.isExactMatch shouldBe false
        outcome.matchScore shouldBe 0.62
        outcome.reason shouldBe "Fuzzy match 62%"
    }

    @Test
    fun `cross-source exact match is CROSS_SOURCE_MATCH, never bulk`() {
        val outcome = RepairOutcomePolicy.resolve(
            sourceAvailable = true,
            exact = noMatch(),
            exactFailed = false,
            deep = null,
            deepFailed = false,
            cross = CrossOutcome(candidateCount = 2, hadError = false),
        )
        outcome.status shouldBe Status.CROSS_SOURCE_MATCH
        outcome.crossSourceExtraMatches shouldBe 1
    }

    @Test
    fun `a failed exact pass with no candidate anywhere is SEARCH_ERROR, not NO_MATCH`() {
        // The exact pass threw; the deep pass also threw; the cross resolver found
        // nothing and reported no error of its own. The item is retryable.
        val outcome = RepairOutcomePolicy.resolve(
            sourceAvailable = true,
            exact = noMatch(),
            exactFailed = true,
            deep = null,
            deepFailed = true,
            cross = CrossOutcome(candidateCount = 0, hadError = false),
        )
        outcome.status shouldBe Status.SEARCH_ERROR
    }

    @Test
    fun `a cross-source error with no candidates marks the item retryable`() {
        // One source hung past CROSS_SOURCE_TIMEOUT_MS and none produced a candidate.
        val outcome = RepairOutcomePolicy.resolve(
            sourceAvailable = true,
            exact = noMatch(),
            exactFailed = false,
            deep = null,
            deepFailed = false,
            cross = CrossOutcome(candidateCount = 0, hadError = true),
        )
        outcome.status shouldBe Status.SEARCH_ERROR
    }

    @Test
    fun `one hanging source does not sink an item another source resolved`() {
        // The cross resolver errored on some source BUT an exact candidate exists:
        // the candidate wins over the error.
        val outcome = RepairOutcomePolicy.resolve(
            sourceAvailable = true,
            exact = noMatch(),
            exactFailed = false,
            deep = null,
            deepFailed = false,
            cross = CrossOutcome(candidateCount = 1, hadError = true),
        )
        outcome.status shouldBe Status.CROSS_SOURCE_MATCH
    }

    @Test
    fun `clean exhaustion with the source present is NO_MATCH`() {
        // Everything ran, nothing failed, nothing found: a clean no-match.
        val outcome = RepairOutcomePolicy.resolve(
            sourceAvailable = true,
            exact = noMatch(),
            exactFailed = false,
            deep = null,
            deepFailed = false,
            cross = CrossOutcome(candidateCount = 0, hadError = false),
        )
        outcome.status shouldBe Status.NO_MATCH
    }

    @Test
    fun `missing own source lets the cross resolver decide alone`() {
        // Extension uninstalled: the exact and deep passes never ran, so only the
        // cross resolver's own error can make the item retryable.
        val clean = RepairOutcomePolicy.resolve(
            sourceAvailable = false,
            exact = noMatch(),
            exactFailed = false,
            deep = null,
            deepFailed = false,
            cross = CrossOutcome(candidateCount = 0, hadError = false),
        )
        clean.status shouldBe Status.NO_MATCH

        val errored = RepairOutcomePolicy.resolve(
            sourceAvailable = false,
            exact = noMatch(),
            exactFailed = false,
            deep = null,
            deepFailed = false,
            cross = CrossOutcome(candidateCount = 0, hadError = true),
        )
        errored.status shouldBe Status.SEARCH_ERROR
    }

    @Test
    fun `multiple identical titles go straight to review without second passes`() {
        val outcome = RepairOutcomePolicy.resolve(
            sourceAvailable = true,
            exact = MatchResult.NeedsReview(MatchResult.NeedsReview.Reason.MULTIPLE_EXACT_MATCHES),
            exactFailed = false,
            deep = null,
            deepFailed = false,
            cross = null,
        )
        outcome.status shouldBe Status.NEEDS_REVIEW
        outcome.reason shouldBe "Multiple identical titles"
    }

    @Test
    fun `same URL as current goes straight to review with its reason`() {
        val outcome = RepairOutcomePolicy.resolve(
            sourceAvailable = true,
            exact = MatchResult.NeedsReview(MatchResult.NeedsReview.Reason.SAME_URL),
            exactFailed = false,
            deep = null,
            deepFailed = false,
            cross = null,
        )
        outcome.status shouldBe Status.NEEDS_REVIEW
        outcome.reason shouldBe "Same URL as current"
    }

    @Test
    fun `author or genre conflicts stay in review`() {
        for (reason in listOf(
            MatchResult.NeedsReview.Reason.AUTHOR_MISMATCH,
            MatchResult.NeedsReview.Reason.GENRE_MISMATCH,
        )) {
            val outcome = RepairOutcomePolicy.resolve(
                sourceAvailable = true,
                exact = MatchResult.NeedsReview(reason),
                exactFailed = false,
                deep = null,
                deepFailed = false,
                cross = null,
            )
            outcome.status shouldBe Status.NEEDS_REVIEW
            outcome.reason shouldBe null
        }
    }

    @Test
    fun `the without-evidence variant is defensive review, never bulk`() {
        val outcome = RepairOutcomePolicy.resolve(
            sourceAvailable = true,
            exact = MatchResult.AutoAcceptWithoutEvidence(candidate = mihonDummy()),
            exactFailed = false,
            deep = null,
            deepFailed = false,
            cross = null,
        )
        outcome.shouldBeInstanceOf<Outcome>()
        outcome.status shouldBe Status.NEEDS_REVIEW
        outcome.isExactMatch shouldBe true
    }

    private fun mihonDummy() = eu.kanade.tachiyomi.source.model.SManga.create().apply {
        title = "t"
        url = "u"
    }
}

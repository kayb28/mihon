package mihon.feature.migration.repair

import eu.kanade.tachiyomi.source.model.SManga
import mihon.feature.migration.repair.TitleMatcher.MatchResult.AutoAccept
import mihon.feature.migration.repair.TitleMatcher.MatchResult.NeedsReview
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Covers the Unicode fix on [TitleMatcher.SYMBOLS_PATTERN]: previously \p{Alnum} was ASCII-only,
 * so any kanji/hangul/cyrillic title normalized to "" and either failed to match an identical
 * candidate, or — worse — collapsed two different series onto the same empty key and joined them
 * as an exact match.
 *
 * NOTE: this test does NOT run in CI. The fork's `build_apk.yml` workflow only invokes
 * `./gradlew assembleRelease`, which does not compile or execute the unit-test source set.
 * Run it locally with `./gradlew :app:testDebugUnitTest --tests "*TitleMatcherTest*"`
 * (or `:app:test`) to actually exercise these assertions.
 */
class TitleMatcherTest {

    private fun smanga(title: String, url: String = "u-$title"): SManga =
        SManga.create().apply { this.title = title; this.url = url }

    @Test
    fun `identical kanji title with different url is auto-accepted`() {
        val current = "ワンピース"
        val result = TitleMatcher.findMatch(current, "u-old", listOf(smanga(current, "u-new")))
        assertTrue(result is AutoAccept)
        assertEquals(current, (result as AutoAccept).candidate.title)
    }

    @Test
    fun `identical hangul title with different url is auto-accepted`() {
        val current = "나 혼자만 레벨업"
        val result = TitleMatcher.findMatch(current, "u-old", listOf(smanga(current, "u-new")))
        assertTrue(result is AutoAccept)
    }

    @Test
    fun `identical cyrillic title with different url is auto-accepted`() {
        val current = "Клинок, рассекающий демонов"
        val result = TitleMatcher.findMatch(current, "u-old", listOf(smanga(current, "u-new")))
        assertTrue(result is AutoAccept)
    }

    @Test
    fun `dakuten pair ハート and パート stay distinct`() {
        // Before the fix, both normalized to "" and were treated as the same title.
        val result = TitleMatcher.findMatch("ハート", "u-old", listOf(smanga("パート", "u-new")))
        assertTrue(result is NeedsReview)
        assertEquals(NeedsReview.Reason.NO_MATCH, (result as NeedsReview).reason)
    }

    @Test
    fun `kanji with mixed latin prefix Berserk 冒険 vs Berserk 記録 stay distinct`() {
        // Before the fix, both normalized to "berserk" and were joined as an exact match.
        val result = TitleMatcher.findMatch("Berserk 冒険", "u-old", listOf(smanga("Berserk 記録", "u-new")))
        assertTrue(result is NeedsReview)
        assertEquals(NeedsReview.Reason.NO_MATCH, (result as NeedsReview).reason)
    }

    @Test
    fun `mixed latin-cjk Tokyo Revengers variants stay distinct`() {
        val result = TitleMatcher.findMatch(
            "Tokyo Revengers 特別編",
            "u-old",
            listOf(smanga("Tokyo Revengers 番外編", "u-new")),
        )
        assertTrue(result is NeedsReview)
        assertEquals(NeedsReview.Reason.NO_MATCH, (result as NeedsReview).reason)
    }

    @Test
    fun `latin punctuation and case variants still collapse to the same key`() {
        // Regression guard: the Unicode change must not weaken the original Latin behavior.
        val result = TitleMatcher.findMatch("One Piece", "u-old", listOf(smanga("one piece!", "u-new")))
        assertTrue(result is AutoAccept)
    }

    @Test
    fun `latin accented title still matches its plain form`() {
        val result = TitleMatcher.findMatch("Pokémon", "u-old", listOf(smanga("pokemon", "u-new")))
        assertTrue(result is AutoAccept)
    }

    @Test
    fun `candidate sharing the current url goes to review`() {
        val result = TitleMatcher.findMatch("One Piece", "u-1", listOf(smanga("One Piece", "u-1")))
        assertTrue(result is NeedsReview)
        assertEquals(NeedsReview.Reason.SAME_URL, (result as NeedsReview).reason)
    }

    @Test
    fun `two candidates with the same title go to review`() {
        val result = TitleMatcher.findMatch(
            "One Piece",
            "u-old",
            listOf(smanga("One Piece", "u-a"), smanga("One Piece", "u-b")),
        )
        assertTrue(result is NeedsReview)
        assertEquals(NeedsReview.Reason.MULTIPLE_EXACT_MATCHES, (result as NeedsReview).reason)
    }

    @Test
    fun `no candidates goes to review`() {
        val result = TitleMatcher.findMatch("One Piece", "u-old", emptyList())
        assertTrue(result is NeedsReview)
        assertEquals(NeedsReview.Reason.NO_MATCH, (result as NeedsReview).reason)
    }
}

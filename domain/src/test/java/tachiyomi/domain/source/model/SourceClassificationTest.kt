package tachiyomi.domain.source.model

import io.kotest.matchers.shouldBe
import mihon.domain.extension.model.ContentWarning
import org.junit.jupiter.api.Test

class SourceClassificationTest {
    @Test
    fun `classification records use stable ids and ignore malformed values`() {
        SourceClassificationEntry(123L, SourceClassification.ADULT).serialize() shouldBe "123|ADULT"
        SourceClassificationEntry.deserialize("123|ADULT") shouldBe
            SourceClassificationEntry(123L, SourceClassification.ADULT)
        SourceClassificationEntry.deserialize("not-an-id|GENERAL") shouldBe null
        SourceClassificationEntry.deserialize("123|UNKNOWN") shouldBe null
    }

    @Test
    fun `unclassified sources default to mixed rather than general`() {
        sourceClassificationFor(999L, emptySet()) shouldBe SourceClassification.MIXED
        filterSourceIdsByClassification(
            sourceIds = listOf(1L, 2L, 3L, 4L),
            assignments = setOf(
                SourceClassificationEntry(1L, SourceClassification.GENERAL),
                SourceClassificationEntry(2L, SourceClassification.ADULT),
                SourceClassificationEntry(3L, SourceClassification.MIXED),
            ),
            allowed = setOf(SourceClassification.GENERAL, SourceClassification.MIXED),
        ) shouldBe listOf(1L, 3L, 4L)
    }

    @Test
    fun `content-warning fallback maps NSFW to ADULT, MIXED to MIXED, SAFE to GENERAL`() {
        ContentWarning.NSFW.toDefaultClassification() shouldBe SourceClassification.ADULT
        ContentWarning.MIXED.toDefaultClassification() shouldBe SourceClassification.MIXED
        ContentWarning.SAFE.toDefaultClassification() shouldBe SourceClassification.GENERAL
        null.toDefaultClassification() shouldBe SourceClassification.GENERAL
    }

    @Test
    fun `an explicit user assignment wins over the content-warning fallback`() {
        // NSFW extension says ADULT by default, but the user pinned it GENERAL: GENERAL wins.
        sourceClassificationFor(
            sourceId = 42L,
            assignments = setOf(SourceClassificationEntry(42L, SourceClassification.GENERAL)),
            contentWarning = ContentWarning.NSFW,
        ) shouldBe SourceClassification.GENERAL
    }

    @Test
    fun `sources without an explicit assignment inherit the extension content warning`() {
        // A source from an NSFW extension with no user tag reads as ADULT, not MIXED — the bug
        // that made the filter show every source as "Mixed" on a fresh install.
        sourceClassificationFor(7L, emptySet(), ContentWarning.NSFW) shouldBe SourceClassification.ADULT
        sourceClassificationFor(7L, emptySet(), ContentWarning.MIXED) shouldBe SourceClassification.MIXED
        sourceClassificationFor(7L, emptySet(), ContentWarning.SAFE) shouldBe SourceClassification.GENERAL
    }

    @Test
    fun `a source whose extension is not loaded falls back to GENERAL, not MIXED`() {
        // null content warning (stub source / extension not loaded) — kept as GENERAL so a filter
        // set to "General" still surfaces stub sources instead of hiding them under "Mixed".
        sourceClassificationFor(7L, emptySet(), null) shouldBe SourceClassification.GENERAL
    }
}

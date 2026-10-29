package tachiyomi.domain.source.model

import io.kotest.matchers.shouldBe
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
    fun `extension metadata is the default while manual classification wins`() {
        val extensionDefaults = mapOf(9L to SourceClassification.ADULT)
        sourceClassificationFor(9L, emptySet(), extensionDefaults) shouldBe SourceClassification.ADULT
        sourceClassificationFor(
            9L,
            setOf(SourceClassificationEntry(9L, SourceClassification.GENERAL)),
            extensionDefaults,
        ) shouldBe SourceClassification.GENERAL
    }
}

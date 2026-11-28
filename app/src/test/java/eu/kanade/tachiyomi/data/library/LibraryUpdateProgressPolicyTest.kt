package eu.kanade.tachiyomi.data.library

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Behavior tests for T-D (PROMPT 12): the library-update progress notification must show
 * the count («X de Y títulos» or «X títulos») in addition to the percentage, so the user
 * can validate the scope of the pass. Red state: fails against the baseline where the
 * notification shows only the percentage.
 */
class LibraryUpdateProgressPolicyTest {

    @Test
    fun `td_4 a normal pass shows the count`() {
        val count = LibraryUpdateProgressPolicy.countLabel(current = 3, total = 12)
        count.shown shouldBe true
        count.current shouldBe 3
        count.total shouldBe 12
    }

    @Test
    fun `td_4 a single-title pass shows the count too (X títulos)`() {
        val count = LibraryUpdateProgressPolicy.countLabel(current = 1, total = 1)
        count.shown shouldBe true
    }

    @Test
    fun `td_4 current is clamped into the 0-to-total range`() {
        LibraryUpdateProgressPolicy.countLabel(current = -1, total = 10).current shouldBe 0
        LibraryUpdateProgressPolicy.countLabel(current = 11, total = 10).current shouldBe 10
    }

    @Test
    fun `td_4 no count when there is no work`() {
        LibraryUpdateProgressPolicy.countLabel(current = 0, total = 0).shown shouldBe false
    }
}

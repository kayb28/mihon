package mihon.domain.migration.usecases

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * P0 correctivo (PROMPT 10, T-10.1..T-10.5): the migration decisions extracted from
 * MigrateMangaUseCase into a pure policy so they are JVM-testable (the use case itself
 * is too coupled to interactors to fake cheaply). Red state: the policy does not exist
 * yet — the module does not compile without it.
 */
class MigrationGuardPolicyTest {

    // -------------------------------------------------------------- T-10.1 (P0-2)

    @Test
    fun `T-10-1 a target with zero chapters aborts the migration`() {
        val e = assertThrows(IllegalStateException::class.java) {
            MigrationGuardPolicy.requireTargetChapters(
                currentTitle = "Old Title",
                currentId = 7L,
                targetId = 42L,
                targetSource = 999L,
                chapterCount = 0,
            )
        }
        assertEquals(true, e.message!!.contains("Old Title"))
        assertEquals(true, e.message!!.contains("42"))
        assertEquals(true, e.message!!.contains("999"))
    }

    @Test
    fun `T-10-1 a target with chapters passes`() {
        // must not throw
        MigrationGuardPolicy.requireTargetChapters(
            currentTitle = "Old Title",
            currentId = 7L,
            targetId = 42L,
            targetSource = 999L,
            chapterCount = 3,
        )
    }
}

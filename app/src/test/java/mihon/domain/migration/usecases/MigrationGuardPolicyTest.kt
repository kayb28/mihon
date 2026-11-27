package mihon.domain.migration.usecases

import kotlin.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.track.model.Track

/**
 * P0 correctivo (PROMPT 10, T-10.1..T-10.5): the migration decisions extracted from
 * MigrateMangaUseCase into a pure policy so they are JVM-testable (the use case itself
 * is too coupled to interactors to fake cheaply). Red state: the policy does not exist
 * yet — the module does not compile without it.
 */
class MigrationGuardPolicyTest {

    // -------------------------------------------------------------- T-10.4 (P0-4)

    @Test
    fun `T-10-4 a migrated track is upserted`() {
        val original = Track(id = 1L, mangaId = 7L, trackerId = 3L, remoteId = 11L, libraryId = null, title = "t", totalChapters = 0, chaptersRead = 0, score = 0.0, status = 0, startedReadingDate = 0, finishedReadingDate = 0, private = false)
        val migrated = original.copy(mangaId = 42L)
        val outcome = MigrationGuardPolicy.trackMigrationPlan(original, migrated)
        assertEquals(MigrationGuardPolicy.TrackOutcome.Upsert(migrated), outcome)
    }

    @Test
    fun `T-10-4 a null migration KEEPS the original track, not a silent drop`() {
        val original = Track(id = 1L, mangaId = 7L, trackerId = 3L, remoteId = 11L, libraryId = null, title = "t", totalChapters = 0, chaptersRead = 0, score = 0.0, status = 0, startedReadingDate = 0, finishedReadingDate = 0, private = false)
        val outcome = MigrationGuardPolicy.trackMigrationPlan(original, migrated = null)
        assertTrue(outcome is MigrationGuardPolicy.TrackOutcome.KeptUnmigrated)
        assertEquals(original, (outcome as MigrationGuardPolicy.TrackOutcome.KeptUnmigrated).track)
    }

    // -------------------------------------------------------------- T-10.2 (P0-3)

    @Test
    fun `T-10-2 replace keeps the current favoriteAt`() {
        val cur = Instant.parse("2024-01-01T00:00:00Z")
        val now = Instant.parse("2026-11-28T00:00:00Z")
        assertEquals(cur, MigrationGuardPolicy.resolveFavoriteAt(replace = true, currentFavoriteAt = cur, targetFavoriteAt = null, now = now))
        assertEquals(now, MigrationGuardPolicy.resolveFavoriteAt(replace = true, currentFavoriteAt = null, targetFavoriteAt = null, now = now))
    }

    @Test
    fun `T-10-2 without replace a target already in the library keeps ITS favoriteAt`() {
        val cur = Instant.parse("2024-01-01T00:00:00Z")
        val tgt = Instant.parse("2025-06-01T00:00:00Z")
        val now = Instant.parse("2026-11-28T00:00:00Z")
        // regression: the target's own date survived; falling through to now() re-dated it
        assertEquals(tgt, MigrationGuardPolicy.resolveFavoriteAt(replace = false, currentFavoriteAt = null, targetFavoriteAt = tgt, now = now))
        assertEquals(cur, MigrationGuardPolicy.resolveFavoriteAt(replace = false, currentFavoriteAt = cur, targetFavoriteAt = tgt, now = now))
        assertEquals(now, MigrationGuardPolicy.resolveFavoriteAt(replace = false, currentFavoriteAt = null, targetFavoriteAt = null, now = now))
    }

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

package mihon.domain.migration.usecases

import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.track.model.Track
import kotlin.time.Instant

/**
 * P0 correctivo (PROMPT 10, T-10.1..T-10.5): pure migration decisions extracted from
 * MigrateMangaUseCase so each guard is JVM-testable (MigrationGuardPolicyTest) and the
 * use case keeps only orchestration. No Android, no interactors, no database.
 */
object MigrationGuardPolicy {

    // ---------------------------------------------------------------- T-10.1 (P0-2)

    /**
     * P0-2: a target with zero chapters must abort the migration BEFORE the destructive
     * step — with `replace = true` the old manga leaves the library and an empty target
     * would strand the user's read state (data loss, not a cosmetic bug).
     */
    fun requireTargetChapters(
        currentTitle: String,
        currentId: Long,
        targetId: Long,
        targetSource: Long,
        chapterCount: Int,
    ) {
        if (chapterCount <= 0) {
            throw IllegalStateException(
                "Cannot migrate '$currentTitle' ($currentId): " +
                    "target $targetId has no chapters (source $targetSource)",
            )
        }
    }

    // ---------------------------------------------------------------- T-10.2 (P0-3)

    /**
     * P0-3: `favoriteAt` of the target. With `replace = false` over a target already in
     * the library, the CURRENT date must survive: falling straight through to `now()`
     * re-dates the entry and silently reorders the library.
     */
    fun resolveFavoriteAt(
        replace: Boolean,
        currentFavoriteAt: Instant?,
        targetFavoriteAt: Instant?,
        now: Instant,
    ): Instant = when {
        replace -> currentFavoriteAt ?: now
        else -> currentFavoriteAt ?: targetFavoriteAt ?: now
    }

    // ---------------------------------------------------------------- T-10.4 (P0-4)

    /** What to do with one track after the enhanced tracker's migration attempt. */
    sealed interface TrackOutcome {
        /** The migrated track is ready to upsert (service returned a migrated copy). */
        data class Upsert(val track: Track) : TrackOutcome

        /** The migration returned null: the ORIGINAL track survives untouched, with a log line. */
        data class KeptUnmigrated(val track: Track) : TrackOutcome
    }

    /**
     * P0-4: a `null` from `migrateTrack` must not silently drop the tracking follow —
     * the plan keeps the original track and flags it so the use case logs one warning.
     */
    fun trackMigrationPlan(track: Track, migrated: Track?): TrackOutcome =
        if (migrated != null) TrackOutcome.Upsert(migrated) else TrackOutcome.KeptUnmigrated(track)

    // ---------------------------------------------------------------- T-10.5 (P0-5)

    /**
     * P0-5: the highest READ chapter number carried over to the target, counted ONLY
     * among recognized numbers. A read chapter with `chapterNumber <= 0` (unrecognized)
     * would otherwise clamp `maxChapterRead` at ≤ 0 and mark every ≤ 0 target chapter
     * as read in one stroke.
     */
    fun maxChapterRead(prevChapters: List<Chapter>): Double? = prevChapters
        .filter { it.read && it.isRecognizedNumber }
        .maxOfOrNull { it.chapterNumber }
}

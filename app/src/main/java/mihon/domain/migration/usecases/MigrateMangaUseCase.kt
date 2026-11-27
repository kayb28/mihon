package mihon.domain.migration.usecases

import dev.zacsweers.metro.Inject
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.hasCustomCover
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.library.UpdateErrorStore
import eu.kanade.tachiyomi.data.track.EnhancedTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.core.metro.AppCoroutineScope
import mihon.domain.migration.models.MigrationFlag
import mihon.domain.source.interactor.UpdateMangaFromRemote
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.UpsertTrack
import kotlin.time.Clock

@Inject
class MigrateMangaUseCase(
    @AppCoroutineScope private val scope: CoroutineScope,
    private val sourcePreferences: SourcePreferences,
    private val trackerManager: TrackerManager,
    private val sourceManager: SourceManager,
    private val downloadManager: DownloadManager,
    private val updateManga: UpdateManga,
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val updateChapter: UpdateChapter,
    private val getCategories: GetCategories,
    private val setMangaCategories: SetMangaCategories,
    private val getTracks: GetTracks,
    private val upsertTrack: UpsertTrack,
    private val coverCache: CoverCache,
    private val updateMangaFromRemote: UpdateMangaFromRemote,
    private val updateErrorStore: UpdateErrorStore,
) {
    private val enhancedServices by lazy { trackerManager.trackers.filterIsInstance<EnhancedTracker>() }

    suspend operator fun invoke(current: Manga, target: Manga, replace: Boolean) {
        // A missing target source used to `return` silently: callers marked the migration as
        // done while nothing was copied (the manga kept its old source, chapters/tracks never
        // moved). Every caller (bulk migration list, single "migrate now", link repairer) now
        // reports failures, so a source that is not installed fails loudly instead.
        val targetSource = sourceManager.get(target.source)
            ?: throw IllegalStateException(
                "Cannot migrate '${target.title}' (${target.id}): source ${target.source} is not installed",
            )
        val currentSource = sourceManager.get(current.source)
        val flags = sourcePreferences.migrationFlags.get()

        try {
            updateMangaFromRemote(target, fetchChapters = true).getOrThrow()

            // P0-2 (T-10.1): the target must actually have chapters BEFORE anything
            // destructive happens — with `replace = true` the old manga leaves the library
            // and an empty target would strand the user's read state.
            MigrationGuardPolicy.requireTargetChapters(
                currentTitle = current.title,
                currentId = current.id,
                targetId = target.id,
                targetSource = target.source,
                chapterCount = getChaptersByMangaId.await(target.id).size,
            )

            // Update chapters read, bookmark and dateFetch
            if (MigrationFlag.CHAPTER in flags) {
                val prevMangaChapters = getChaptersByMangaId.await(current.id)
                val mangaChapters = getChaptersByMangaId.await(target.id)

                // P0-5 (T-10.5): only recognized numbers among READ chapters — a read
                // chapter with chapterNumber <= 0 would clamp the max at <= 0 and mark
                // every <= 0 target chapter as read in one stroke.
                val maxChapterRead = MigrationGuardPolicy.maxChapterRead(prevMangaChapters)

                val chapterUpdates = mangaChapters
                    .filter { it.isRecognizedNumber }
                    .map { mangaChapter ->
                        val prevChapter = prevMangaChapters
                            .find { it.isRecognizedNumber && it.chapterNumber == mangaChapter.chapterNumber }

                        ChapterUpdate(mangaChapter.id) {
                            if (prevChapter != null) {
                                dateFetch = prevChapter.dateFetch
                                bookmark = prevChapter.bookmark
                            }

                            if (maxChapterRead != null && mangaChapter.chapterNumber <= maxChapterRead) {
                                read = true
                            }
                        }
                    }
                updateChapter.awaitAll(chapterUpdates)
            }

            // Update categories
            if (MigrationFlag.CATEGORY in flags) {
                val categoryIds = getCategories.await(current.id).map { it.id }
                setMangaCategories.await(target.id, categoryIds)
            }

            // Update track
            // P0-4 (T-10.4): a null from migrateTrack is not a silent drop — the ORIGINAL
            // track survives untouched and one warning line records it.
            // R6 (D-B, auditoría pre-merge PR #23): explicit map (no mapNotNull); tracks
            // migrate BEFORE the destructive updateManga, and the outcomes decide whether
            // replace is effective at all.
            val trackOutcomes = getTracks.await(current.id)
                .map { track ->
                    val updatedTrack = track.copy(mangaId = target.id)

                    val service = enhancedServices
                        .firstOrNull { it.isTrackFrom(updatedTrack, current, currentSource) }

                    // No enhanced service: the plain re-point (copy with the new mangaId)
                    // is migrated as before. A service that FAILED returns null → kept.
                    val migrated = service?.migrateTrack(updatedTrack, target, targetSource) ?: updatedTrack
                    MigrationGuardPolicy.trackMigrationPlan(track, migrated)
                }
            trackOutcomes.forEach { outcome ->
                when (outcome) {
                    is MigrationGuardPolicy.TrackOutcome.Upsert -> upsertTrack.await(outcome.track)
                    is MigrationGuardPolicy.TrackOutcome.KeptUnmigrated -> logcat(LogPriority.WARN) {
                        "Track ${outcome.track.id} (${outcome.track.title}) could not be migrated " +
                            "to '${target.title}' (${target.id}) — kept on the original manga"
                    }
                }
            }

            // R6 (D-B): replace is only effective when EVERY track migrated. One failed
            // track aborts BEFORE the destructive step: the row is not replaced, the
            // original source stays, and the thrown motivo makes the caller mark the
            // migration for review (bulk list / link repairer surface the message).
            val replaceEfectivo = MigrationGuardPolicy.effectiveReplace(replace, trackOutcomes)
            if (replace && !replaceEfectivo) {
                val motivos = trackOutcomes
                    .filterIsInstance<MigrationGuardPolicy.TrackOutcome.KeptUnmigrated>()
                    .joinToString("; ") { "'${it.track.title}' (track ${it.track.id})" }
                throw IllegalStateException(
                    "Migration of '${current.title}' (${current.id}) to '${target.title}' " +
                        "(${target.id}) left unmigrated track(s): $motivos — row NOT replaced, " +
                        "marked for review",
                )
            }

            // Delete downloaded
            if (MigrationFlag.REMOVE_DOWNLOAD in flags && currentSource != null) {
                // Not awaited, so a slow storage delete doesn't hold up the migration.
                // P0-1 (T-10.3): this runs fire-and-forget on AppScope — an uncaught
                // IOException would hit the scope's handler, so it is contained here.
                scope.launch {
                    runCatching { downloadManager.deleteManga(current, currentSource) }
                        .onFailure { e ->
                            logcat(LogPriority.WARN, e) {
                                "Could not delete downloads of '${current.title}' (${current.id}) after migration"
                            }
                        }
                }
            }

            // Update custom cover (recheck if custom cover exists)
            if (MigrationFlag.CUSTOM_COVER in flags && current.hasCustomCover()) {
                // P0-1 (T-10.3): a missing/corrupt cover file (FileNotFoundException from
                // inputStream()) must not fail the whole migration — the cover is cosmetic.
                runCatching {
                    coverCache.getCustomCoverFile(current.id).inputStream().use {
                        coverCache.setCustomCoverToCache(target, it)
                    }
                }.onFailure { e ->
                    logcat(LogPriority.WARN, e) {
                        "Could not carry the custom cover of '${current.title}' (${current.id}) over to the target"
                    }
                }
            }

            val currentMangaUpdate = MangaUpdate(current.id) {
                favoriteAt = null
            }
                .takeIf { replace }
            val targetMangaUpdate = MangaUpdate(target.id) {
                // P0-3 (T-10.2): with `replace = false` the target's own date survives —
                // falling through to now() re-dated the entry and reordered the library.
                favoriteAt = MigrationGuardPolicy.resolveFavoriteAt(
                    replace = replace,
                    currentFavoriteAt = current.favoriteAt,
                    targetFavoriteAt = target.favoriteAt,
                    now = Clock.System.now(),
                )
                chapterFlags = current.chapterFlags
                viewerFlags = current.viewerFlags
                if (MigrationFlag.NOTES in flags) notes = current.notes
            }

            updateManga.awaitAll(listOfNotNull(currentMangaUpdate, targetMangaUpdate))

            // Con reemplazo, el manga viejo sale de la biblioteca: su error de enlace roto
            // (si lo tenía) ya no tiene a quién describir — sin esto, el reparador de enlaces
            // seguía listando títulos que el usuario ya había migrado por fuera de la pantalla.
            if (replace) {
                updateErrorStore.removeErrorsForManga(current.id)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Fail loudly: swallowing errors here made callers (the link repairer, the bulk
            // migration list) mark failed migrations as successful — data silently not copied.
            logcat(LogPriority.ERROR, e) { "Failed to migrate manga ${current.title} (${current.id})" }
            throw e
        }
    }
}

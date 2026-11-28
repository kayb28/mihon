package eu.kanade.tachiyomi.data.library

import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.manga.model.applyFilter

/**
 * T-D (PROMPT 12): the «Actualizar biblioteca según filtros» scope. It mirrors the library
 * screen's own filters (downloaded / unread / started / bookmarked / completed /
 * update-error, TriState semantics) and deliberately does NOT depend on categories — many
 * titles have none. The library list is favorites-only by construction (getLibraryManga).
 *
 * GREEN state (T-D): the scope applies the filters with the screen's TriState semantics;
 * an empty match yields an empty list so the caller can show a clear message.
 */
data class LibraryUpdateFilters(
    val downloaded: TriState = TriState.DISABLED,
    val unread: TriState = TriState.DISABLED,
    val started: TriState = TriState.DISABLED,
    val bookmarked: TriState = TriState.DISABLED,
    val completed: TriState = TriState.DISABLED,
    val updateError: TriState = TriState.DISABLED,
)

object LibraryUpdateScope {

    /**
     * The only per-title facts the scope needs, flattened from LibraryManga so the
     * decision stays pure (no DB, no Manga model in tests).
     */
    data class Entry(
        val id: Long,
        val isDownloaded: Boolean,
        val unreadCount: Long,
        val hasStarted: Boolean,
        val hasBookmarks: Boolean,
        val isCompleted: Boolean,
    )

    /** GREEN T-D: exact same TriState semantics as the library screen (applyFilter). */
    fun ids(
        library: List<Entry>,
        filters: LibraryUpdateFilters,
        errorMangaIds: Set<Long> = emptySet(),
    ): List<Long> = library.asSequence()
        .filter { entry ->
            applyFilter(filters.downloaded) { entry.isDownloaded } &&
                applyFilter(filters.unread) { entry.unreadCount > 0 } &&
                applyFilter(filters.started) { entry.hasStarted } &&
                applyFilter(filters.bookmarked) { entry.hasBookmarks } &&
                applyFilter(filters.completed) { entry.isCompleted } &&
                applyFilter(filters.updateError) { entry.id in errorMangaIds }
        }
        .map { it.id }
        .toList()
}

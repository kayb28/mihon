package eu.kanade.tachiyomi.data.library

import tachiyomi.core.common.preference.TriState

/**
 * T-D (PROMPT 12): the «Actualizar biblioteca según filtros» scope. It mirrors the library
 * screen's own filters (downloaded / unread / started / bookmarked / completed /
 * update-error, TriState semantics) and deliberately does NOT depend on categories — many
 * titles have none. The library list is favorites-only by construction (getLibraryManga).
 *
 * RED state: ids() ignores the filters and returns the whole library — the reported bug
 * («el filtro no se respeta») reproduced as failing tests, not the fix.
 */
data class LibraryUpdateFilters(
    val downloadedOnly: Boolean = false,
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

    /** RED T-D: still returns everything — the filters are not applied yet. */
    fun ids(
        library: List<Entry>,
        filters: LibraryUpdateFilters,
        errorMangaIds: Set<Long> = emptySet(),
    ): List<Long> = library.map { it.id }
}

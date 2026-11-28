package eu.kanade.tachiyomi.data.library

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.TriState

/**
 * Behavior tests for T-D (PROMPT 12): «Actualizar biblioteca según filtros». Red state:
 * they run against the baseline where there is no filtered update at all and fail on
 * BEHAVIOR, one group per requirement.
 *
 * Pure JVM tests of the decision layer (LibraryUpdateScope): which titles match the
 * library filters, with the exact same TriState semantics the library screen uses
 * (tachiyomi.domain.manga.model.applyFilter) — and crucially, WITHOUT depending on
 * categories (many titles have none).
 */
class LibraryUpdateScopeTest {

    private fun entry(
        id: Long,
        isDownloaded: Boolean = false,
        unreadCount: Long = 0L,
        hasStarted: Boolean = false,
        hasBookmarks: Boolean = false,
        isCompleted: Boolean = false,
    ) = LibraryUpdateScope.Entry(
        id = id,
        isDownloaded = isDownloaded,
        unreadCount = unreadCount,
        hasStarted = hasStarted,
        hasBookmarks = hasBookmarks,
        isCompleted = isCompleted,
    )

    private fun scope(
        library: List<LibraryUpdateScope.Entry>,
        filters: LibraryUpdateFilters = LibraryUpdateFilters(),
        errorMangaIds: Set<Long> = emptySet(),
    ): List<Long> = LibraryUpdateScope.ids(library, filters, errorMangaIds)

    // ---------------------------------------------------- filtrado

    @Test
    fun `td_1 no filters means the whole library (favorites)`() {
        val library = listOf(entry(1), entry(2), entry(3))
        scope(library) shouldContainExactly listOf(1L, 2L, 3L)
    }

    @Test
    fun `td_1 unread filter picks only titles with unread chapters`() {
        val library = listOf(entry(1, unreadCount = 10), entry(2, unreadCount = 0), entry(3, unreadCount = 4))
        val result = scope(library, filters = LibraryUpdateFilters(unread = TriState.ENABLED_IS))
        result shouldContainExactly listOf(1L, 3L)
    }

    @Test
    fun `td_1 completed filter picks only completed titles`() {
        val library = listOf(entry(1, isCompleted = true), entry(2, isCompleted = false))
        val result = scope(library, filters = LibraryUpdateFilters(completed = TriState.ENABLED_IS))
        result shouldContainExactly listOf(1L)
    }

    @Test
    fun `td_1 started filter picks only started titles`() {
        val library = listOf(entry(1, hasStarted = true), entry(2, hasStarted = false))
        val result = scope(library, filters = LibraryUpdateFilters(started = TriState.ENABLED_IS))
        result shouldContainExactly listOf(1L)
    }

    @Test
    fun `td_1 bookmarked filter picks only titles with bookmarks`() {
        val library = listOf(entry(1, hasBookmarks = true), entry(2, hasBookmarks = false))
        val result = scope(library, filters = LibraryUpdateFilters(bookmarked = TriState.ENABLED_IS))
        result shouldContainExactly listOf(1L)
    }

    @Test
    fun `td_1 downloaded filter uses the download fact (local or downloads over 0)`() {
        val library = listOf(entry(1, isDownloaded = true), entry(2, isDownloaded = false))
        val result = scope(
            library,
            filters = LibraryUpdateFilters(downloaded = TriState.ENABLED_IS, downloadedOnly = true),
        )
        result shouldContainExactly listOf(1L)
    }

    @Test
    fun `td_1 not-filters exclude matching titles (TriState ENABLED_NOT)`() {
        val library = listOf(entry(1, unreadCount = 10), entry(2, unreadCount = 0))
        val result = scope(library, filters = LibraryUpdateFilters(unread = TriState.ENABLED_NOT))
        // «not unread» = al día: solo el 2
        result shouldContainExactly listOf(2L)
    }

    @Test
    fun `td_1 combined filters intersect`() {
        val library = listOf(
            entry(1, unreadCount = 10, isCompleted = true),
            entry(2, unreadCount = 10, isCompleted = false),
            entry(3, unreadCount = 0, isCompleted = true),
        )
        val result = scope(
            library,
            filters = LibraryUpdateFilters(unread = TriState.ENABLED_IS, completed = TriState.ENABLED_IS),
        )
        result shouldContainExactly listOf(1L)
    }

    @Test
    fun `td_2 the scope never looks at categories`() {
        // El alcance solo consume datos del título: las categorías ni siquiera existen
        // en Entry — títulos sin categoría se actualizan igual.
        val library = listOf(entry(1), entry(2, unreadCount = 3), entry(3, unreadCount = 1))
        val result = scope(library, filters = LibraryUpdateFilters(unread = TriState.ENABLED_IS))
        result shouldContainExactly listOf(1L, 2L, 3L)
    }

    @Test
    fun `td_3 a filter that matches nothing yields an empty scope`() {
        val library = listOf(entry(1, unreadCount = 0), entry(2, unreadCount = 0))
        val result = scope(library, filters = LibraryUpdateFilters(unread = TriState.ENABLED_IS))
        result shouldHaveSize 0
    }

    @Test
    fun `td_1 the update-error filter picks titles recorded in the error store`() {
        val library = listOf(entry(1), entry(2))
        val result = scope(
            library,
            filters = LibraryUpdateFilters(updateError = TriState.ENABLED_IS),
            errorMangaIds = setOf(2L),
        )
        result shouldContainExactly listOf(2L)
    }
}

package mihon.feature.migration.list

import mihon.feature.migration.list.models.MigratingManga
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * M1: an exact-title match decided with vacuous evidence (author AND genres absent on
 * both sides) must stay on the list for review instead of joining the bulk run.
 */
class MigratingMangaTest {

    private fun success(
        isExactTitle: Boolean,
        exactUnverified: Boolean,
    ): MigratingManga.SearchResult.Success =
        MigratingManga.SearchResult.Success(
            manga = tachiyomi.domain.manga.model.Manga.create(),
            chapterCount = 1,
            latestChapter = null,
            source = "test",
            similarity = if (isExactTitle) 1.0 else 0.7,
            isExactTitle = isExactTitle,
            exactUnverified = exactUnverified,
        )

    @Test
    fun `verified exact title is preselected`() {
        assertTrue(success(isExactTitle = true, exactUnverified = false).isPreselected)
    }

    @Test
    fun `unverified exact title is NOT preselected`() {
        assertFalse(success(isExactTitle = true, exactUnverified = true).isPreselected)
    }

    @Test
    fun `fuzzy title is not preselected`() {
        assertFalse(success(isExactTitle = false, exactUnverified = false).isPreselected)
    }
}

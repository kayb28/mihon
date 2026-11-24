package mihon.feature.migration.list.models

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import mihon.feature.migration.repair.MetadataMatcher
import tachiyomi.domain.manga.model.Manga
import kotlin.coroutines.CoroutineContext

class MigratingManga(
    val manga: Manga,
    val chapterCount: Int,
    val latestChapter: Double?,
    val source: String,
    parentContext: CoroutineContext,
) {
    // SupervisorJob(parent's Job): keeps per-item failures isolated while staying a child of
    // the ViewModel's scope — cancelling viewModelScope now cancels every item search too.
    val migrationScope = CoroutineScope(parentContext + SupervisorJob(parentContext[Job]) + Dispatchers.Default)

    val searchResult = MutableStateFlow<SearchResult>(SearchResult.Searching)

    sealed interface SearchResult {
        data object Searching : SearchResult
        data object NotFound : SearchResult

        /**
         * Every source's search failed (network, source down) — distinct from [NotFound], which
         * means the sources answered and none had the title. The list keeps the item visible
         * with a retry action instead of counting it as a clean miss.
         */
        data object Error : SearchResult
        data class Success(
            val manga: Manga,
            val chapterCount: Int,
            val latestChapter: Double?,
            val source: String,
            /**
             * Normalized-Levenshtein similarity of the matched title vs the source manga's title,
             * in [0, 1]. 1.0 means the exact-title pass accepted it (titles equal after
             * normalization); anything below 1.0 is a fuzzy pick. Null only when the match came
             * from a manual override (no search engine involved).
             */
            val similarity: Double? = null,
            /**
             * True when the match was selected by the strict exact-title pass. Distinct from
             * `similarity == 1.0` because a manual override has no score but should not be
             * flagged as fuzzy either.
             */
            val isExactTitle: Boolean = false,
            /**
             * True when the exact-title match was decided with NO author/genre evidence on
             * either side (M1). The title match is real, but the candidate must stay on the
             * list for review instead of joining the bulk run: a remake sharing the exact
             * title would otherwise migrate silently.
             */
            val exactUnverified: Boolean = false,
            /**
             * True when the match was auto-accepted because the source returned a single result:
             * the 1.0 score means "only result found", not a verified title match. Distinct from
             * [isExactTitle] (titles actually compared equal) and from a manual override (null).
             */
            val isSingleResult: Boolean = false,
            /**
             * Metadata validation verdict (author/status/description/genres agreement), or null
             * when the entry was not validated — manual picks and results from before this gate
             * existed. Accepted carries the matching signals for the list badge; NoData marks a
             * candidate accepted via the title-only fallback. Purely informational: it does not
             * participate in [isPreselected].
             */
            val metadataVerdict: MetadataMatcher.MetadataVerdict? = null,
            /**
             * Chapters the candidate trails the library copy by, flagged by the chapter-count
             * gate (candidate within 50%-100% of the original but more than 5 chapters behind).
             * Null when there is no gap to flag; purely informational.
             */
            val chaptersBehind: Int? = null,
        ) : SearchResult {
            /**
             * Entries the bulk Migrate/Copy action processes unattended: exact-title matches and
             * manual picks (similarity == null — the user chose that candidate by hand). Fuzzy
             * and single-result matches stay unselected on the list so the user can review them
             * (cover, chapter count) and migrate per-row if they check out — or join the bulk
             * run by ticking «Include non-exact matches» in the confirmation dialog.
             */
            val isPreselected: Boolean
                get() = (isExactTitle && !exactUnverified) || similarity == null
        }
    }
}

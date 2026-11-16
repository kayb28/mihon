package mihon.feature.migration.list.search

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.applyEnabledContentFilters
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.domain.manga.model.toDomainManga
import mihon.feature.migration.repair.MetadataMatcher
import mihon.feature.migration.repair.Romanizer
import mihon.feature.migration.repair.TitleMatcher
import tachiyomi.domain.manga.model.Manga

/**
 * The best candidate a [SmartSourceSearchEngine] pass could agree on, with the information the UI
 * needs to show *why* it was picked: the normalized-Levenshtein similarity in [0, 1] of the
 * chosen entry against the searched title, and whether it was selected by the strict exact-title
 * pass (which beats any fuzzy score) or by the fuzzy fallback.
 *
 * [isExactTitle] being true implies [similarity] is 1.0; a fuzzy pick keeps whatever score the
 * Levenshtein pass gave it, clamped to the engine's eligible threshold. A single-result
 * auto-accept also carries 1.0, but [isSingleResult] marks it so the UI never presents it as a
 * verified title match.
 */
data class ScoredManga(
    val manga: Manga,
    val similarity: Double,
    val isExactTitle: Boolean,
    /**
     * True when the candidate was auto-accepted because the query returned exactly one result —
     * no title comparison happened, so 1.0 here means "only result", not "same title".
     */
    val isSingleResult: Boolean = false,
    /**
     * Outcome of the metadata validation applied after the pick (author/status/description/
     * genres agreement with the source manga). Null when the pick was not validated — e.g. the
     * link repair flow, which uses this class without the migration validation gate.
     */
    val metadataVerdict: MetadataMatcher.MetadataVerdict? = null,
    /**
     * How many chapters the candidate trails the library copy by, when the chapter-count gate
     * accepted it past the tolerance (the list shows a warning line). Null when the counts were
     * close, the candidate had more chapters, or no gate ran (manual picks, link repair).
     */
    val chaptersBehind: Int? = null,
)

class SmartSourceSearchEngine(
    extraSearchParams: String?,
    private val enabledContentFilters: (Long) -> Set<String> = { emptySet() },
    eligibleThreshold: Double = BaseSmartSearchEngine.MIN_ELIGIBLE_THRESHOLD,
) : BaseSmartSearchEngine<SManga>(extraSearchParams, eligibleThreshold) {

    override fun getTitle(result: SManga) = result.title

    suspend fun regularSearch(source: Source, title: String, currentUrl: String? = null): ScoredManga? {
        val entries = rankedCandidates(makeSearchAction(source), listOf(title)) {
            normalizedLevenshtein.similarity(title, it.title)
        }
        return pickHybridBest(entries, source.id, title, currentUrl)
    }

    suspend fun deepSearch(source: Source, title: String, currentUrl: String? = null): ScoredManga? {
        val cleanedTitle = cleanDeepSearchTitle(title)

        // One extra query per source, only when the title carries a romanizable script:
        // a source that indexes the Korean original ("전지적 독자 시점") needs the Latin form
        // ("jeonjijeok dokja sijeom") to be findable at all, and a Latin-indexing source may
        // list the work under its hangul title. isRomanizable is a pure string check — the
        // extra request only fires when the two query sets actually differ.
        val queries = getDeepSearchQueries(cleanedTitle).let { base ->
            val romanized = cleanDeepSearchTitle(Romanizer.romanize(title))
            if (romanized != cleanedTitle) base + listOf(romanized) else base
        }

        val entries = rankedCandidates(makeSearchAction(source), queries) {
            val cleanedMangaTitle = cleanDeepSearchTitle(getTitle(it))
            // Candidates kept in the original script score against the romanized search
            // title too, so a hangul listing still ranks when the query was romanized.
            val romanizedMangaTitle = cleanDeepSearchTitle(Romanizer.romanize(getTitle(it)))
            maxOf(
                normalizedLevenshtein.similarity(cleanedTitle, cleanedMangaTitle),
                normalizedLevenshtein.similarity(cleanedTitle, romanizedMangaTitle),
            )
        }
        return pickHybridBest(entries, source.id, title, currentUrl)
    }

    /**
     * Hybrid pick: when exactly one candidate has the same normalized title as the searched one
     * (and a different URL), it wins over the fuzzy best — Levenshtein over raw titles misses
     * case/punctuation variants that normalization collapses and can tie between near-identical
     * entries. This is the same strict discipline the link repairer uses, applied per source over
     * the candidates the fuzzy search already fetched: zero extra network calls. Falls back to the
     * fuzzy best otherwise. Either way the result carries the similarity score so the UI can tell
     * the user how confident the pick was.
     */
    private fun pickHybridBest(
        entries: List<SearchEntry<SManga>>,
        sourceId: Long,
        title: String,
        currentUrl: String?,
    ): ScoredManga? {
        if (currentUrl != null && entries.isNotEmpty()) {
            val candidates = entries.map { it.entry }.distinctBy { it.url }
            val exact = TitleMatcher.findMatch(title, currentUrl, candidates)
            if (exact is TitleMatcher.MatchResult.AutoAccept) {
                return ScoredManga(exact.candidate.toDomainManga(sourceId), 1.0, isExactTitle = true)
            }
        }
        val best = entries.maxByOrNull { it.distance } ?: return null
        return ScoredManga(
            best.entry.toDomainManga(sourceId),
            best.distance,
            isExactTitle = false,
            isSingleResult = best.autoAccepted,
        )
    }

    // Sources aren't main-safe: their default implementations block on the network
    private fun makeSearchAction(source: Source): SearchAction<SManga> = { query ->
        withContext(Dispatchers.IO) {
            val mangas = source.getSearchManga(1, query, source.getFilterList()).mangas
            source.applyEnabledContentFilters(mangas, enabledContentFilters(source.id))
        }
    }
}

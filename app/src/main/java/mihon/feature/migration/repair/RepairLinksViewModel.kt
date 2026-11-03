package mihon.feature.migration.repair

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.tachiyomi.data.library.UpdateErrorStore
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.domain.source.service.SourcePreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import mihon.domain.manga.model.toDomainManga
import mihon.domain.migration.usecases.MigrateMangaUseCase
import mihon.domain.source.interactor.UpdateMangaFromRemote
import mihon.feature.migration.list.search.SmartSourceSearchEngine
import mihon.feature.migration.repair.TitleMatcher.MatchResult.NeedsReview.Reason
import tachiyomi.domain.chapter.interactor.GetChapterSummaryByMangaId
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager

data class ProposedRepair(
    val currentManga: Manga,
    val proposedManga: SManga? = null,
    val status: Status = Status.SIMULATING,
    val reason: String? = null,
    /**
     * Normalized-Levenshtein similarity of the proposed candidate vs the broken manga's title,
     * in [0, 1]. Null for exact matches (where it is implicitly 1.0) and for non-match statuses.
     * Surfaced so the UI can show "Fuzzy match 87%" and let the user judge whether to trust it.
     */
    val matchScore: Double? = null,
    /**
     * True when the candidate was found by the strict exact-title pass (titles equal after
     * normalization) — the only kind a bulk repair button may migrate. False for fuzzy
     * deep-search picks, which stay in NEEDS_REVIEW: their similarity can be as low as the
     * user's migration threshold (default 40%), so accepting one happens item by item.
     * Defaults to true to keep exact matches the common, trusted case.
     */
    val isExactMatch: Boolean = true,
) {
    enum class Status {
        SIMULATING,
        MATCH_FOUND,
        NEEDS_REVIEW,
        NO_MATCH,

        /**
         * The search itself failed (network down, source error) — distinct from [NO_MATCH],
         * which means the sources answered and none had the title. Retryable per item.
         */
        SEARCH_ERROR,
        REPAIRED,
        FAILED,
    }
}

data class RepairState(
    val isLoading: Boolean = false,
    val isSimulating: Boolean = false,
    val isMigrating: Boolean = false,
    val progressCurrent: Int = 0,
    val progressTotal: Int = 0,
    val items: List<ProposedRepair> = emptyList(),
    val groups: List<RepairSourceGroup> = emptyList(),
    val repairedCount: Int = 0,
    val pendingReviewCount: Int = 0,
    val noMatchCount: Int = 0,
    val searchErrorCount: Int = 0,
    val matchFoundCount: Int = 0,
)

/**
 * Repair items grouped by their source, so the screen can show a header per source and the user
 * can see which sources have the most broken links and act on them selectively. Mirrors the
 * grouping the UpdateErrors screen already does.
 */
data class RepairSourceGroup(
    val sourceId: Long,
    val sourceName: String,
    val items: List<ProposedRepair>,
)

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class RepairLinksViewModel(
    private val updateErrorStore: UpdateErrorStore,
    private val getManga: GetManga,
    private val sourceManager: SourceManager,
    private val networkToLocalManga: NetworkToLocalManga,
    private val migrateMangaUseCase: MigrateMangaUseCase,
    private val updateMangaFromRemote: UpdateMangaFromRemote,
    private val getChapterSummaryByMangaId: GetChapterSummaryByMangaId,
    sourcePreferences: SourcePreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(RepairState())
    val state: StateFlow<RepairState> = _state.asStateFlow()

    /** Caps how many source searches run in parallel; shared by the bulk pass and per-item retries. */
    private val searchSemaphore = Semaphore(3)

    /**
     * Reuses the bulk-migration engine for the fuzzy fallback so the two flows agree on what
     * "similar enough" means: the user's configured [SourcePreferences.migrationSimilarityThreshold]
     * (default 40%) gates which fuzzy candidates are worth proposing, and the same deep-search
     * query variants (cleaned title, largest words, etc.) run when the strict exact-title pass
     * found nothing.
     */
    private val smartSearchEngine = SmartSourceSearchEngine(
        extraSearchParams = null,
        eligibleThreshold = sourcePreferences.migrationSimilarityThreshold.get() / 100.0,
    )

    private suspend fun rebuildGroups(items: List<ProposedRepair>): List<RepairSourceGroup> {
        return items
            .groupBy { it.currentManga.source }
            .map { (sourceId, groupItems) ->
                val sourceName = sourceManager.getOrStub(sourceId).name.ifBlank { "Source ($sourceId)" }
                RepairSourceGroup(
                    sourceId = sourceId,
                    sourceName = sourceName,
                    items = groupItems,
                )
            }
            .sortedBy { it.sourceName.lowercase() }
    }

    init {
        startSimulation()
    }

    fun startSimulation() {
        if (_state.value.isSimulating || _state.value.isMigrating) return

        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true, isSimulating = true, progressCurrent = 0, progressTotal = 0) }

            val brokenErrors = updateErrorStore.getBrokenLinkErrors()
            if (brokenErrors.isEmpty()) {
                _state.update { it.copy(isLoading = false, isSimulating = false) }
                return@launch
            }

            val brokenMangas = brokenErrors.mapNotNull { err ->
                getManga.await(err.mangaId)
            }

            val initialItems = brokenMangas.map { ProposedRepair(currentManga = it) }
            val initialGroups = rebuildGroups(initialItems)
            _state.update {
                it.copy(
                    isLoading = false,
                    progressTotal = brokenMangas.size,
                    items = initialItems,
                    groups = initialGroups,
                    repairedCount = 0,
                    pendingReviewCount = 0,
                    noMatchCount = 0,
                    matchFoundCount = 0,
                )
            }

            // Concurrencia real: coroutineScope + async(Dispatchers.IO) + awaitAll, con
            // Semaphore(3) solo como límite de paralelismo. Sin delays artificiales: las
            // búsquedas de todas las mangas se lanzan de inmediato y avanzan en paralelo.
            coroutineScope {
                brokenMangas.mapIndexed { index, manga ->
                    async(Dispatchers.IO) {
                        val proposedRepair = searchRepair(manga)

                        // Publicar el resultado de este item de forma atómica; el progreso
                        // avanza a medida que cada corrutina termina.
                        _state.update { current ->
                            val updatedList = current.items.toMutableList()
                            updatedList[index] = proposedRepair
                            publishRepairs(current, updatedList, progressDone = current.progressCurrent + 1)
                        }
                    }
                }.awaitAll()
            }

            _state.update { it.copy(isSimulating = false) }
        }
    }

    /** Re-runs the search of one item whose previous pass ended in SEARCH_ERROR. */
    fun retrySearch(mangaId: Long) {
        if (_state.value.isMigrating || _state.value.isSimulating) return
        viewModelScope.launch(Dispatchers.IO) {
            val item = _state.value.items.firstOrNull { it.currentManga.id == mangaId } ?: return@launch
            if (item.status != ProposedRepair.Status.SEARCH_ERROR) return@launch
            _state.update { current ->
                val updatedList = current.items.toMutableList()
                updatedList[updatedList.indexOfFirst { it.currentManga.id == mangaId }] =
                    item.copy(status = ProposedRepair.Status.SIMULATING)
                publishRepairs(current, updatedList, progressDone = current.progressCurrent)
            }

            val repaired = searchRepair(item.currentManga)
            _state.update { current ->
                val updatedList = current.items.toMutableList()
                updatedList[updatedList.indexOfFirst { it.currentManga.id == mangaId }] = repaired
                publishRepairs(current, updatedList, progressDone = current.progressCurrent)
            }
        }
    }

    /** Publishes [updatedList] atomically, recomputing every status counter from it. */
    private fun publishRepairs(
        current: RepairState,
        updatedList: List<ProposedRepair>,
        progressDone: Int,
    ): RepairState = current.copy(
        progressCurrent = progressDone,
        items = updatedList,
        groups = rebuildGroups(updatedList),
        matchFoundCount = updatedList.count { it.status == ProposedRepair.Status.MATCH_FOUND },
        pendingReviewCount = updatedList.count { it.status == ProposedRepair.Status.NEEDS_REVIEW },
        noMatchCount = updatedList.count { it.status == ProposedRepair.Status.NO_MATCH },
        searchErrorCount = updatedList.count { it.status == ProposedRepair.Status.SEARCH_ERROR },
    )

    /**
     * The two-pass search for one broken manga: the strict exact-title pass first, then the
     * deep-search fallback. A failing search is remembered per pass: when neither produced a
     * candidate but at least one errored (network down, source error), the item lands on
     * [ProposedRepair.Status.SEARCH_ERROR] — retryable — instead of being shown as a clean
     * "no match", which used to pass a network outage off as the title not existing anywhere.
     */
    private suspend fun searchRepair(manga: Manga): ProposedRepair {
        val source = sourceManager.get(manga.source)

        var searchHadError = false
        val exactResult = if (source != null) {
            searchSemaphore.withPermit {
                try {
                    val results = withContext(Dispatchers.IO) {
                        source.getSearchManga(1, manga.title, source.getFilterList()).mangas
                    }
                    TitleMatcher.findMatch(manga.title, manga.url, results)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // The deep pass below still runs (its other queries may get through), but
                    // the outage is remembered in case it produces nothing either.
                    searchHadError = true
                    TitleMatcher.MatchResult.NeedsReview(
                        TitleMatcher.MatchResult.NeedsReview.Reason.NO_MATCH,
                    )
                }
            }
        } else {
            TitleMatcher.MatchResult.NeedsReview(
                TitleMatcher.MatchResult.NeedsReview.Reason.NO_MATCH,
            )
        }

        // Deep-search fallback: only when the strict exact-title pass found nothing at all.
        // MULTIPLE_EXACT_MATCHES / SAME_URL stay manual-review (a fuzzy pick can't resolve
        // a tie or a self-link). NO_MATCH gets a second chance through the same engine the
        // bulk migration list uses — it re-runs the search with cleaned-title and word-
        // fragment queries and applies the user's configured similarity threshold, so a
        // title the exact pass missed (typo, parenthetical subtitle, romanization variant)
        // can still surface. A fuzzy pick never lands in MATCH_FOUND: it stays
        // NEEDS_REVIEW with its score as the reason, so "Repair first 10" and
        // "Repair all" — which only take MATCH_FOUND — can never migrate a
        // similar-but-different series without the user confirming it first. An exact-title
        // hit found via the deep pass (ScoredManga.isExactTitle) is as trustworthy as the
        // strict pass and does go to MATCH_FOUND.
        return when (exactResult) {
            is TitleMatcher.MatchResult.AutoAccept -> ProposedRepair(
                currentManga = manga,
                proposedManga = exactResult.candidate,
                status = ProposedRepair.Status.MATCH_FOUND,
                isExactMatch = true,
            )
            is TitleMatcher.MatchResult.NeedsReview -> {
                if (exactResult.reason == TitleMatcher.MatchResult.NeedsReview.Reason.NO_MATCH &&
                    source != null
                ) {
                    val fuzzy = try {
                        searchSemaphore.withPermit {
                            smartSearchEngine.deepSearch(source, manga.title, manga.url)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        searchHadError = true
                        null
                    }
                    when {
                        fuzzy == null -> ProposedRepair(
                            currentManga = manga,
                            status = if (searchHadError) {
                                ProposedRepair.Status.SEARCH_ERROR
                            } else {
                                ProposedRepair.Status.NO_MATCH
                            },
                        )
                        fuzzy.isExactTitle -> {
                            // The deep pass re-checked titles with TitleMatcher and found a
                            // single normalized-title match on a different URL: as trustworthy
                            // as the strict pass, just discovered through a cleaned query.
                            ProposedRepair(
                                currentManga = manga,
                                proposedManga = fuzzy.manga.toSManga(),
                                status = ProposedRepair.Status.MATCH_FOUND,
                            )
                        }
                        else -> ProposedRepair(
                            currentManga = manga,
                            proposedManga = fuzzy.manga.toSManga(),
                            status = ProposedRepair.Status.NEEDS_REVIEW,
                            isExactMatch = false,
                            matchScore = fuzzy.similarity,
                            reason = "Fuzzy match ${(fuzzy.similarity * 100).toInt()}%",
                        )
                    }
                } else if (exactResult.reason == TitleMatcher.MatchResult.NeedsReview.Reason.NO_MATCH) {
                    ProposedRepair(
                        currentManga = manga,
                        status = ProposedRepair.Status.NO_MATCH,
                    )
                } else {
                    ProposedRepair(
                        currentManga = manga,
                        status = ProposedRepair.Status.NEEDS_REVIEW,
                        reason = when (exactResult.reason) {
                            Reason.MULTIPLE_EXACT_MATCHES -> "Multiple identical titles"
                            Reason.SAME_URL -> "Same URL as current"
                            else -> null
                        },
                    )
                }
            }
        }
    }

    fun executeRepair(limit: Int? = null) {
        if (_state.value.isMigrating || _state.value.isSimulating) return

        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isMigrating = true, progressCurrent = 0) }

            val eligibleIndices = _state.value.items
                .mapIndexedNotNull { index, item ->
                    if (item.status ==
                        ProposedRepair.Status.MATCH_FOUND
                    ) {
                        index
                    } else {
                        null
                    }
                }
                .let { if (limit != null) it.take(limit) else it }

            if (eligibleIndices.isEmpty()) {
                _state.update { it.copy(isMigrating = false) }
                return@launch
            }

            _state.update { it.copy(progressTotal = eligibleIndices.size) }

            val currentItems = _state.value.items.toMutableList()
            var processed = 0

            for (index in eligibleIndices) {
                val item = currentItems[index]
                val candidate = item.proposedManga
                if (candidate == null) {
                    processed++
                    continue
                }

                try {
                    val localTarget = networkToLocalManga(candidate.toDomainManga(item.currentManga.source))
                    // Verify the target actually has chapters before migrating to it: an exact title
                    // match on an empty entry (or a source glitch) would migrate the user's read
                    // state into a stub and lose it.
                    updateMangaFromRemote(localTarget, fetchChapters = true).getOrThrow()
                    val summary = getChapterSummaryByMangaId.await(localTarget.id)
                    if (summary.chapterCount <= 0L) {
                        currentItems[index] = item.copy(
                            status = ProposedRepair.Status.NEEDS_REVIEW,
                            reason = "Target has no chapters",
                        )
                    } else {
                        migrateMangaUseCase(
                            current = item.currentManga,
                            target = localTarget,
                            replace = true,
                        )
                        updateErrorStore.removeErrorsForManga(item.currentManga.id)
                        currentItems[index] = item.copy(status = ProposedRepair.Status.REPAIRED)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    updateErrorStore.incrementRetryCount(item.currentManga.id)
                    currentItems[index] = item.copy(status = ProposedRepair.Status.FAILED)
                }

                processed++

                val currentList = currentItems.toList()
                val currentGroups = rebuildGroups(currentList)
                _state.update { state ->
                    state.copy(
                        progressCurrent = processed,
                        items = currentList,
                        groups = currentGroups,
                        repairedCount = currentItems.count { it.status == ProposedRepair.Status.REPAIRED },
                        matchFoundCount = currentItems.count { it.status == ProposedRepair.Status.MATCH_FOUND },
                    )
                }
            }

            _state.update { it.copy(isMigrating = false) }
        }
    }
}

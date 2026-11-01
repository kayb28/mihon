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
import kotlinx.coroutines.delay
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
import kotlin.time.Duration.Companion.milliseconds

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
     * normalization). False for fuzzy deep-search picks, which are auto-accepted but visibly
     * less certain. Defaults to true to keep exact matches the common, trusted case.
     */
    val isExactMatch: Boolean = true,
) {
    enum class Status {
        SIMULATING,
        MATCH_FOUND,
        NEEDS_REVIEW,
        NO_MATCH,
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

            val semaphore = Semaphore(3)
            val updatedItems = initialItems.toMutableList()

            for (index in brokenMangas.indices) {
                val manga = brokenMangas[index]
                val source = sourceManager.get(manga.source)

                val exactResult = if (source != null) {
                    semaphore.withPermit {
                        delay(400.milliseconds)
                        try {
                            val results = withContext(Dispatchers.IO) {
                                source.getSearchManga(1, manga.title, source.getFilterList()).mangas
                            }
                            TitleMatcher.findMatch(manga.title, manga.url, results)
                        } catch (e: Exception) {
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
                // can still surface. Fuzzy picks are auto-accepted but carry their score so the
                // user can judge them on the screen and via "Repair first 10".
                val proposedRepair: ProposedRepair = when (exactResult) {
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
                                semaphore.withPermit {
                                    delay(400.milliseconds)
                                    smartSearchEngine.deepSearch(source, manga.title, manga.url)
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                null
                            }
                            if (fuzzy != null) {
                                ProposedRepair(
                                    currentManga = manga,
                                    proposedManga = fuzzy.manga.toSManga(),
                                    status = ProposedRepair.Status.MATCH_FOUND,
                                    isExactMatch = false,
                                    matchScore = fuzzy.similarity,
                                )
                            } else {
                                ProposedRepair(
                                    currentManga = manga,
                                    status = ProposedRepair.Status.NO_MATCH,
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

                updatedItems[index] = proposedRepair
                val updatedList = updatedItems.toList()
                val updatedGroups = rebuildGroups(updatedList)

                _state.update { current ->
                    current.copy(
                        progressCurrent = index + 1,
                        items = updatedList,
                        groups = updatedGroups,
                        matchFoundCount = updatedItems.count { it.status == ProposedRepair.Status.MATCH_FOUND },
                        pendingReviewCount = updatedItems.count { it.status == ProposedRepair.Status.NEEDS_REVIEW },
                        noMatchCount = updatedItems.count { it.status == ProposedRepair.Status.NO_MATCH },
                    )
                }
            }

            _state.update { it.copy(isSimulating = false) }
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

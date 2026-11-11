package mihon.feature.migration.repair

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.library.UpdateErrorStore
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
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
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
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
     * Source the candidate lives on. Null means the manga's own source (same-source repair).
     * Set only for [Status.CROSS_SOURCE_MATCH] items found by the secondary resolver.
     */
    val targetSourceId: Long? = null,
    /** Display name of the cross-source candidate's source, for the UI label. */
    val targetSourceName: String? = null,
    /** How many additional sources beyond the proposed one also had an exact title match. */
    val crossSourceExtraMatches: Int = 0,
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

        /**
         * Exact title match found by the secondary resolver in a DIFFERENT installed source.
         * Never bulk-repaired: migrating across sources can change chapter availability, so
         * each item is confirmed individually with its own Repair button.
         */
        CROSS_SOURCE_MATCH,
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
    val crossSourceCount: Int = 0,
)

/** One exact-title candidate found by the secondary resolver, with the source it lives on. */
private data class CrossSourceCandidate(val source: HttpSource, val manga: SManga)

private data class CrossSourceOutcome(val candidates: List<CrossSourceCandidate>, val hadError: Boolean)

/** Per-source search cap inside the secondary resolver so one hung source can't stall an item. */
private const val CROSS_SOURCE_TIMEOUT_MS = 20_000L

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
    private val sourcePreferences: SourcePreferences,
) : ViewModel() {

    // Same eligibility rules the global search uses: languages the user reads, sources they
    // didn't hide, pinned sources first — so the secondary resolver never proposes a source
    // the user already told the app to ignore.
    private val enabledLanguages = sourcePreferences.enabledLanguages.get()
    private val disabledSources = sourcePreferences.disabledSources.get()
    private val pinnedSources = sourcePreferences.pinnedSources.get()

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
                    crossSourceCount = 0,
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

    /**
     * The secondary resolver: exact-title search across the OTHER installed online sources,
     * with the same eligibility rules the global search applies (user's languages, sources not
     * hidden, pinned sources first). Only single exact-title matches count — a source with two
     * identical titles is ambiguous and skipped (the manual search covers it). Sources are
     * searched in priority order with a shared concurrency cap; once one exact match exists the
     * remaining sources aren't launched, and every search has a hard per-source timeout so a
     * hanging source can't stall the whole item.
     */
    private suspend fun crossSourceSearch(manga: Manga): CrossSourceOutcome {
        val title = manga.title
        if (title.isBlank()) return CrossSourceOutcome(emptyList(), hadError = false)

        val sources = sourceManager.getOnlineSources()
            .filter { it.id != manga.source }
            .filter { it.lang in enabledLanguages }
            .filter { "${it.id}" !in disabledSources }
            .sortedWith(
                compareBy(
                    { "${it.id}" !in pinnedSources },
                    { "${it.name.lowercase()} (${it.lang})" },
                ),
            )

        val found = AtomicBoolean(false)
        val hadError = AtomicBoolean(false)
        val candidates = coroutineScope {
            sources.map { source ->
                async(Dispatchers.IO) {
                    // In-flight searches still finish, but nothing new is launched once an
                    // exact match already exists — pinned-first ordering makes the first hit
                    // the preferred candidate.
                    if (found.get()) return@async null
                    searchSemaphore.withPermit {
                        if (found.get()) return@withPermit null
                        val results = withTimeoutOrNull(CROSS_SOURCE_TIMEOUT_MS) {
                            source.getSearchManga(1, title, source.getFilterList()).mangas
                        }
                        if (results == null) {
                            hadError.set(true)
                            return@withPermit null
                        }
                        val match = TitleMatcher.findMatch(title, manga.url, results)
                        (match as? TitleMatcher.MatchResult.AutoAccept)?.let { auto ->
                            found.set(true)
                            CrossSourceCandidate(source, auto.candidate)
                        }
                    }
                }
            }.awaitAll()
        }
        val foundCandidates = candidates.filterNotNull()
        return CrossSourceOutcome(
            foundCandidates,
            hadError = hadError.get() && foundCandidates.isEmpty(),
        )
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
    private suspend fun publishRepairs(
        current: RepairState,
        updatedList: List<ProposedRepair>,
        progressDone: Int,
    ): RepairState = current.copy(
        progressCurrent = progressDone,
        items = updatedList,
        groups = rebuildGroups(updatedList),
        matchFoundCount = updatedList.count { it.status == ProposedRepair.Status.MATCH_FOUND },
        crossSourceCount = updatedList.count { it.status == ProposedRepair.Status.CROSS_SOURCE_MATCH },
        pendingReviewCount = updatedList.count { it.status == ProposedRepair.Status.NEEDS_REVIEW },
        noMatchCount = updatedList.count { it.status == ProposedRepair.Status.NO_MATCH },
        searchErrorCount = updatedList.count { it.status == ProposedRepair.Status.SEARCH_ERROR },
    )

    /**
     * Runs the migration for ONE item: verifies the target actually has chapters (a broken
     * migration would strand the user's read state on an empty stub), then migrates and clears
     * the broken-link error. Used by the bulk pass (same-source exact matches) and by the
     * per-item Repair button (cross-source candidates).
     */
    private suspend fun migrateOne(item: ProposedRepair): ProposedRepair {
        val candidate = item.proposedManga
            ?: return item.copy(status = ProposedRepair.Status.FAILED)
        val targetSourceId = item.targetSourceId ?: item.currentManga.source
        return try {
            val localTarget = networkToLocalManga(candidate.toDomainManga(targetSourceId))
            // Verify the target actually has chapters before migrating to it: an exact title
            // match on an empty entry (or a source glitch) would migrate the user's read
            // state into a stub and lose it.
            updateMangaFromRemote(localTarget, fetchChapters = true).getOrThrow()
            val summary = getChapterSummaryByMangaId.await(localTarget.id)
            if (summary.chapterCount <= 0L) {
                item.copy(
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
                item.copy(status = ProposedRepair.Status.REPAIRED)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            updateErrorStore.incrementRetryCount(item.currentManga.id)
            item.copy(status = ProposedRepair.Status.FAILED)
        }
    }

    /**
     * Per-item repair, the manual confirmation for cross-source candidates (and same-source
     * exact matches): migrates exactly this item to its proposed target.
     */
    fun repairSingle(mangaId: Long) {
        if (_state.value.isMigrating || _state.value.isSimulating) return
        viewModelScope.launch(Dispatchers.IO) {
            val index = _state.value.items.indexOfFirst { it.currentManga.id == mangaId }
            if (index < 0) return@launch
            val item = _state.value.items[index]
            if (item.status != ProposedRepair.Status.CROSS_SOURCE_MATCH &&
                item.status != ProposedRepair.Status.MATCH_FOUND
            ) {
                return@launch
            }

            _state.update { it.copy(isMigrating = true, progressTotal = 1, progressCurrent = 0) }
            val updatedItem = migrateOne(item)
            val currentItems = _state.value.items.toMutableList()
            currentItems[index] = updatedItem
            _state.update { state ->
                state.copy(
                    isMigrating = false,
                    progressCurrent = 1,
                    items = currentItems,
                    groups = rebuildGroups(currentItems),
                    repairedCount = currentItems.count { it.status == ProposedRepair.Status.REPAIRED },
                    matchFoundCount = currentItems.count { it.status == ProposedRepair.Status.MATCH_FOUND },
                    crossSourceCount = currentItems.count { it.status == ProposedRepair.Status.CROSS_SOURCE_MATCH },
                    pendingReviewCount = currentItems.count { it.status == ProposedRepair.Status.NEEDS_REVIEW },
                    noMatchCount = currentItems.count { it.status == ProposedRepair.Status.NO_MATCH },
                    searchErrorCount = currentItems.count { it.status == ProposedRepair.Status.SEARCH_ERROR },
                )
            }
        }
    }

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
                        fuzzy == null -> {
                            // Primary resolver exhausted — no trace of the title in its own
                            // source (or the source itself errored). Second chance: the
                            // secondary resolver looks for an exact title match in the OTHER
                            // installed sources; anything it finds needs the user's explicit
                            // confirmation, so it lands on CROSS_SOURCE_MATCH.
                            val cross = crossSourceSearch(manga)
                            val first = cross.candidates.firstOrNull()
                            if (first != null) {
                                ProposedRepair(
                                    currentManga = manga,
                                    proposedManga = first.manga,
                                    status = ProposedRepair.Status.CROSS_SOURCE_MATCH,
                                    targetSourceId = first.source.id,
                                    targetSourceName = first.source.name.ifBlank { "Source (${first.source.id})" },
                                    crossSourceExtraMatches = cross.candidates.size - 1,
                                )
                            } else {
                                ProposedRepair(
                                    currentManga = manga,
                                    status = if (searchHadError || cross.hadError) {
                                        ProposedRepair.Status.SEARCH_ERROR
                                    } else {
                                        ProposedRepair.Status.NO_MATCH
                                    },
                                )
                            }
                        }
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
                    // Only reachable with source == null (the branch above owns NO_MATCH when the
                    // source exists): the primary resolver doesn't exist — extension missing or
                    // uninstalled. Exactly the case where the secondary resolver matters most.
                    val cross = crossSourceSearch(manga)
                    val first = cross.candidates.firstOrNull()
                    if (first != null) {
                        ProposedRepair(
                            currentManga = manga,
                            proposedManga = first.manga,
                            status = ProposedRepair.Status.CROSS_SOURCE_MATCH,
                            targetSourceId = first.source.id,
                            targetSourceName = first.source.name.ifBlank { "Source (${first.source.id})" },
                            crossSourceExtraMatches = cross.candidates.size - 1,
                        )
                    } else {
                        ProposedRepair(
                            currentManga = manga,
                            status = if (cross.hadError) {
                                ProposedRepair.Status.SEARCH_ERROR
                            } else {
                                ProposedRepair.Status.NO_MATCH
                            },
                        )
                    }
                } else {
                    when (exactResult.reason) {
                        Reason.MULTIPLE_EXACT_MATCHES -> ProposedRepair(
                            currentManga = manga,
                            status = ProposedRepair.Status.NEEDS_REVIEW,
                            reason = "Multiple identical titles",
                        )
                        Reason.SAME_URL -> ProposedRepair(
                            currentManga = manga,
                            status = ProposedRepair.Status.NEEDS_REVIEW,
                            reason = "Same URL as current",
                        )
                        else -> ProposedRepair(currentManga = manga, status = ProposedRepair.Status.NEEDS_REVIEW)
                    }
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

                currentItems[index] = migrateOne(item)

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
                        crossSourceCount = currentItems.count { it.status == ProposedRepair.Status.CROSS_SOURCE_MATCH },
                    )
                }
            }

            _state.update { it.copy(isMigrating = false) }
        }
    }
}

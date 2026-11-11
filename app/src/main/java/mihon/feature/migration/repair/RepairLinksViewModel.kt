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
import logcat.LogPriority
import mihon.domain.manga.model.toDomainManga
import mihon.domain.migration.usecases.MigrateMangaUseCase
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager

data class ProposedRepair(
    val currentManga: Manga,
    val proposedManga: SManga? = null,
    val status: Status = Status.SIMULATING,
    val reason: Reason? = null,
    val reasonDetail: String? = null,
) {
    enum class Status {
        SIMULATING,
        MATCH_FOUND,
        NEEDS_REVIEW,
        NO_MATCH,
        REPAIRED,
        FAILED,
    }

    enum class Reason {
        MULTIPLE_EXACT_MATCHES,
        SAME_URL,
        AUTHOR_MISMATCH,
        GENRE_MISMATCH,
        SEARCH_ERROR,
        MIGRATION_ERROR,
    }
}

data class RepairState(
    val isLoading: Boolean = false,
    val isSimulating: Boolean = false,
    val isMigrating: Boolean = false,
    val progressCurrent: Int = 0,
    val progressTotal: Int = 0,
    val items: List<ProposedRepair> = emptyList(),
    val repairedCount: Int = 0,
    val pendingReviewCount: Int = 0,
    val noMatchCount: Int = 0,
    val matchFoundCount: Int = 0,
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
) : ViewModel() {

    private val _state = MutableStateFlow(RepairState())
    val state: StateFlow<RepairState> = _state.asStateFlow()

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
            _state.update {
                it.copy(
                    isLoading = false,
                    progressTotal = brokenMangas.size,
                    items = initialItems,
                    repairedCount = 0,
                    pendingReviewCount = 0,
                    noMatchCount = 0,
                    matchFoundCount = 0,
                )
            }

            // Concurrency real: coroutineScope + async + Semaphore(3)
            val semaphore = Semaphore(3)
            val results = coroutineScope {
                brokenMangas.mapIndexed { index, manga ->
                    async(Dispatchers.IO) {
                        val source = sourceManager.get(manga.source)

                        val outcome: Result<TitleMatcher.MatchResult> = if (source != null) {
                            semaphore.withPermit {
                                try {
                                    val searchResults = withContext(Dispatchers.IO) {
                                        source.getSearchManga(1, manga.title, source.getFilterList()).mangas
                                    }
                                    Result.success(
                                        TitleMatcher.findMatch(
                                            currentTitle = manga.title,
                                            currentUrl = manga.url,
                                            currentAuthor = manga.author,
                                            currentGenres = manga.genre,
                                            candidates = searchResults,
                                        ),
                                    )
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    logcat(LogPriority.WARN, e) {
                                        "Repair simulation: search failed for '${manga.title}' (${manga.id})"
                                    }
                                    Result.failure(e)
                                }
                            }
                        } else {
                            logcat(LogPriority.WARN) {
                                "Repair simulation: source ${manga.source} not installed for '${manga.title}'"
                            }
                            Result.failure(IllegalStateException("Source ${manga.source} not installed"))
                        }

                        index to outcome
                    }
                }.awaitAll()
            }

            // Procesar resultados en orden
            val updatedItems = initialItems.toMutableList()
            for ((index, outcome) in results) {
                val manga = brokenMangas[index]
                updatedItems[index] = outcome.fold(
                    onSuccess = { result ->
                        when (result) {
                            is TitleMatcher.MatchResult.AutoAccept -> ProposedRepair(
                                currentManga = manga,
                                proposedManga = result.candidate,
                                status = ProposedRepair.Status.MATCH_FOUND,
                            )
                            is TitleMatcher.MatchResult.NeedsReview -> {
                                val reason = when (result.reason) {
                                    TitleMatcher.MatchResult.NeedsReview.Reason.MULTIPLE_EXACT_MATCHES ->
                                        ProposedRepair.Reason.MULTIPLE_EXACT_MATCHES
                                    TitleMatcher.MatchResult.NeedsReview.Reason.SAME_URL ->
                                        ProposedRepair.Reason.SAME_URL
                                    TitleMatcher.MatchResult.NeedsReview.Reason.AUTHOR_MISMATCH ->
                                        ProposedRepair.Reason.AUTHOR_MISMATCH
                                    TitleMatcher.MatchResult.NeedsReview.Reason.GENRE_MISMATCH ->
                                        ProposedRepair.Reason.GENRE_MISMATCH
                                    TitleMatcher.MatchResult.NeedsReview.Reason.NO_MATCH -> null
                                }
                                if (reason == null) {
                                    ProposedRepair(currentManga = manga, status = ProposedRepair.Status.NO_MATCH)
                                } else {
                                    ProposedRepair(
                                        currentManga = manga,
                                        status = ProposedRepair.Status.NEEDS_REVIEW,
                                        reason = reason,
                                    )
                                }
                            }
                        }
                    },
                    onFailure = { e ->
                        ProposedRepair(
                            currentManga = manga,
                            status = ProposedRepair.Status.NEEDS_REVIEW,
                            reason = ProposedRepair.Reason.SEARCH_ERROR,
                            reasonDetail = e.message,
                        )
                    },
                )
            }

            _state.update {
                it.copy(
                    isSimulating = false,
                    items = updatedItems.toList(),
                    matchFoundCount = updatedItems.count { it.status == ProposedRepair.Status.MATCH_FOUND },
                    pendingReviewCount = updatedItems.count { it.status == ProposedRepair.Status.NEEDS_REVIEW },
                    noMatchCount = updatedItems.count { it.status == ProposedRepair.Status.NO_MATCH },
                )
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
                    migrateMangaUseCase(
                        current = item.currentManga,
                        target = localTarget,
                        replace = true,
                    )
                    updateErrorStore.removeErrorsForManga(item.currentManga.id)
                    currentItems[index] = item.copy(status = ProposedRepair.Status.REPAIRED)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logcat(LogPriority.WARN, e) {
                        "Repair execution: migration failed for '${item.currentManga.title}' (${item.currentManga.id})"
                    }
                    updateErrorStore.incrementRetryCount(item.currentManga.id)
                    currentItems[index] = item.copy(
                        status = ProposedRepair.Status.FAILED,
                        reason = ProposedRepair.Reason.MIGRATION_ERROR,
                        reasonDetail = e.message,
                    )
                }

                processed++

                _state.update { state ->
                    state.copy(
                        progressCurrent = processed,
                        items = currentItems.toList(),
                        repairedCount = currentItems.count { it.status == ProposedRepair.Status.REPAIRED },
                        matchFoundCount = currentItems.count { it.status == ProposedRepair.Status.MATCH_FOUND },
                    )
                }
            }

            _state.update { it.copy(isMigrating = false) }
        }
    }
}

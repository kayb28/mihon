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

            val brokenMangas = mutableListOf<Manga>()
            for (err in brokenErrors) {
                getManga.await(err.mangaId)?.let(brokenMangas::add)
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

            val semaphore = Semaphore(3)
            val updatedItems = initialItems.toMutableList()

            for (index in brokenMangas.indices) {
                val manga = brokenMangas[index]
                val source = sourceManager.get(manga.source)

                val result = if (source != null) {
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

                val proposedRepair = when (result) {
                    is TitleMatcher.MatchResult.AutoAccept -> ProposedRepair(
                        currentManga = manga,
                        proposedManga = result.candidate,
                        status = ProposedRepair.Status.MATCH_FOUND,
                    )
                    is TitleMatcher.MatchResult.NeedsReview -> {
                        if (result.reason == TitleMatcher.MatchResult.NeedsReview.Reason.NO_MATCH) {
                            ProposedRepair(
                                currentManga = manga,
                                status = ProposedRepair.Status.NO_MATCH,
                            )
                        } else {
                            ProposedRepair(
                                currentManga = manga,
                                status = ProposedRepair.Status.NEEDS_REVIEW,
                                reason = when (result.reason) {
                                    TitleMatcher.MatchResult.NeedsReview.Reason.MULTIPLE_EXACT_MATCHES -> "Multiple identical titles"
                                    TitleMatcher.MatchResult.NeedsReview.Reason.SAME_URL -> "Same URL as current"
                                    else -> null
                                },
                            )
                        }
                    }
                }

                updatedItems[index] = proposedRepair

                _state.update { current ->
                    current.copy(
                        progressCurrent = index + 1,
                        items = updatedItems.toList(),
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
                .mapIndexedNotNull { index, item -> if (item.status == ProposedRepair.Status.MATCH_FOUND) index else null }
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
                } catch (_: Exception) {
                    currentItems[index] = item.copy(status = ProposedRepair.Status.FAILED)
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

package mihon.feature.migration.list

import androidx.annotation.FloatRange
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.getNameForMangaInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import logcat.LogPriority
import mihon.domain.migration.usecases.MigrateMangaUseCase
import mihon.domain.source.interactor.UpdateMangaFromRemote
import mihon.feature.migration.list.models.MigratingManga
import mihon.feature.migration.list.models.MigratingManga.SearchResult
import mihon.feature.migration.list.search.ScoredManga
import mihon.feature.migration.list.search.SmartSourceSearchEngine
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.interactor.GetChapterSummaryByMangaId
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager

@AssistedInject
class MigrationListViewModel(
    @Assisted mangaIds: Collection<Long>,
    @Assisted extraSearchQuery: String?,
    private val preferences: SourcePreferences,
    private val sourceManager: SourceManager,
    private val getManga: GetManga,
    private val networkToLocalManga: NetworkToLocalManga,
    private val getChapterSummaryByMangaId: GetChapterSummaryByMangaId,
    private val migrateManga: MigrateMangaUseCase,
    private val updateMangaFromRemote: UpdateMangaFromRemote,
) : ViewModel() {

    val state: StateFlow<MigrationListViewModel.State>
        field = MutableStateFlow<MigrationListViewModel.State>(State())

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(mangaIds: Collection<Long>, extraSearchQuery: String?): MigrationListViewModel
    }

    private val smartSearchEngine = SmartSourceSearchEngine(
        extraSearchQuery,
        preferences::enabledContentFilterIds,
        preferences.migrationSimilarityThreshold.get() / 100.0,
    )

    val items
        inline get() = state.value.items

    private val hideUnmatched = preferences.migrationHideUnmatched.get()
    private val hideWithoutUpdates = preferences.migrationHideWithoutUpdates.get()

    private val navigateBackChannel = Channel<Unit>()
    val navigateBackEvent = navigateBackChannel.receiveAsFlow()

    private var migrateJob: Job? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val manga = mangaIds
                .map {
                    async {
                        val manga = getManga.await(it) ?: return@async null
                        val chapterInfo = getChapterInfo(it)
                        MigratingManga(
                            manga = manga,
                            chapterCount = chapterInfo.chapterCount,
                            latestChapter = chapterInfo.latestChapter,
                            source = sourceManager.getOrStub(manga.source).getNameForMangaInfo(),
                            parentContext = viewModelScope.coroutineContext,
                        )
                    }
                }
                .awaitAll()
                .filterNotNull()
            state.update { it.copy(items = manga) }
            runMigrations(manga)
        }
    }

    private suspend fun getChapterInfo(id: Long) = getChapterSummaryByMangaId.await(id).let { summary ->
        ChapterInfo(
            latestChapter = summary.latestChapterNumber,
            chapterCount = summary.chapterCount.toInt(),
        )
    }

    private suspend fun Manga.toSuccessSearchResult(
        similarity: Double? = null,
        isExactTitle: Boolean = false,
        isSingleResult: Boolean = false,
    ): SearchResult.Success {
        val chapterInfo = getChapterInfo(id)
        val source = sourceManager.getOrStub(source).getNameForMangaInfo()
        return SearchResult.Success(
            manga = this,
            chapterCount = chapterInfo.chapterCount,
            latestChapter = chapterInfo.latestChapter,
            source = source,
            similarity = similarity,
            isExactTitle = isExactTitle,
            isSingleResult = isSingleResult,
        )
    }

    private suspend fun migrationSources(): List<Source> {
        val sourceIds = preferences.eligibleMigrationSourceIds(preferences.migrationSources.get())
        return sourceIds.mapNotNull { sourceManager.get(it) }
    }

    private suspend fun runMigrations(mangas: List<MigratingManga>) {
        val sources = migrationSources()
        if (sources.isEmpty()) {
            mangas.forEach { it.searchResult.value = SearchResult.NotFound }
            state.update { it.copy(finishedCount = mangas.size, noEligibleSources = true) }
            return
        }

        for (manga in mangas) {
            if (!currentCoroutineContext().isActive) break
            if (manga.manga.id !in state.value.mangaIds) continue
            runSearch(manga, sources)
        }
    }

    /**
     * Searches one manga across [sources] and reports the outcome on it. A source that throws
     * (network down, source error) is logged and remembered: if no source produced a match but
     * at least one failed, the item lands on [SearchResult.Error] — visible, with a retry —
     * instead of [SearchResult.NotFound], which used to pass a network outage off as a clean
     * "no alternatives found". Reused by [retrySearch] so a failed pass runs the exact same
     * code as the initial one.
     */
    private suspend fun runSearch(manga: MigratingManga, sources: List<Source>) {
        if (manga.searchResult.value != SearchResult.Searching) return
        if (!manga.migrationScope.isActive) return

        val prioritizeByChapters = preferences.migrationPrioritizeByChapters.get()
        val deepSearchMode = preferences.migrationDeepSearchMode.get()

        var searchHadError = false
        val result = try {
            manga.migrationScope.async {
                if (prioritizeByChapters) {
                    val sourceSemaphore = Semaphore(5)
                    sources.map { source ->
                        async innerAsync@{
                            sourceSemaphore.withPermit {
                                try {
                                    val result = searchSource(manga.manga, source, deepSearchMode)
                                    if (result == null || result.second.chapterCount == 0) return@innerAsync null
                                    result
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    logcat(LogPriority.WARN, e) {
                                        "Source ${source.id} search failed for ${manga.manga.id}"
                                    }
                                    searchHadError = true
                                    null
                                }
                            }
                        }
                    }
                        .mapNotNull { it.await() }
                        .maxByOrNull { it.second.latestChapter ?: 0.0 }
                } else {
                    sources.forEach { source ->
                        val result = try {
                            searchSource(manga.manga, source, deepSearchMode)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            logcat(LogPriority.WARN, e) { "Source ${source.id} search failed for ${manga.manga.id}" }
                            searchHadError = true
                            null
                        }
                        if (result != null) return@async result
                    }
                    null
                }
            }
                .await()
        } catch (_: CancellationException) {
            return
        }

        if (result != null && result.first.manga.thumbnailUrl == null) {
            try {
                updateMangaFromRemote(result.first.manga, fetchDetails = true, manualFetch = true).getOrThrow().manga
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }

        manga.searchResult.value = when {
            result != null -> {
                val (scored, _) = result
                scored.manga.toSuccessSearchResult(scored.similarity, scored.isExactTitle, scored.isSingleResult)
            }
            searchHadError -> SearchResult.Error
            else -> SearchResult.NotFound
        }

        // "Hide unmatched" only hides clean misses: an errored search is not a "no match", and
        // hiding it would take the retry away with it.
        if (result == null && !searchHadError && hideUnmatched) {
            removeManga(manga)
        }
        if (result != null &&
            hideWithoutUpdates &&
            (result.second.latestChapter ?: 0.0) <= (manga.latestChapter ?: 0.0)
        ) {
            removeManga(manga)
        }

        updateMigrationProgress()
    }

    /** Re-runs the search of an item whose previous pass ended in [SearchResult.Error]. */
    fun retrySearch(mangaId: Long) {
        val manga = items.find { it.manga.id == mangaId } ?: return
        if (manga.searchResult.value != SearchResult.Error) return
        manga.searchResult.value = SearchResult.Searching
        viewModelScope.launch(Dispatchers.IO) {
            runSearch(manga, migrationSources())
        }
    }

    /**
     * Search errors propagate on purpose: the callers mark the failing source, so a network
     * outage lands on [SearchResult.Error] (retryable) instead of silently reading as a clean
     * "no match". Only a match that turns out to be the manga itself returns null.
     */
    private suspend fun searchSource(
        manga: Manga,
        source: Source,
        deepSearchMode: Boolean,
    ): Pair<ScoredManga, ChapterInfo>? {
        // The exact-match pass (hybrid pick) must only reject same-URL candidates on the manga's
        // own source: a matching URL on a different site is a different manga, not the current one.
        val currentUrl = if (source.id == manga.source) manga.url else null
        val searchResult = if (deepSearchMode) {
            smartSearchEngine.deepSearch(source, manga.title, currentUrl)
        } else {
            smartSearchEngine.regularSearch(source, manga.title, currentUrl)
        }
        if (searchResult == null || (searchResult.manga.url == manga.url && source.id == manga.source)) return null

        val localManga = networkToLocalManga(searchResult.manga)
        try {
            updateMangaFromRemote(localManga, fetchChapters = true).getOrThrow()
        } catch (e: Exception) {
            // A chapter-fetch failure does not invalidate the match itself; the migration
            // refetches chapters when it runs.
            logcat(LogPriority.ERROR, e)
        }
        // Preserve the engine's verdict (exact vs fuzzy + its score, and whether it was a
        // single-result auto-accept) on the local manga it resolved to — networkToLocalManga
        // returns the persisted row, same title/url.
        val scored = ScoredManga(
            localManga,
            searchResult.similarity,
            searchResult.isExactTitle,
            isSingleResult = searchResult.isSingleResult,
        )
        return scored to getChapterInfo(localManga.id)
    }

    private suspend fun updateMigrationProgress() {
        state.update { state ->
            state.copy(
                finishedCount = items.count { it.searchResult.value != SearchResult.Searching },
                migrationComplete = migrationComplete(),
            )
        }
        if (items.isEmpty()) {
            navigateBack()
        }
    }

    private fun migrationComplete() = items.all { it.searchResult.value != SearchResult.Searching } &&
        items.any { it.searchResult.value is SearchResult.Success }

    fun useMangaForMigration(current: Long, target: Long, onMissingChapters: () -> Unit) {
        val migratingManga = items.find { it.manga.id == current } ?: return
        migratingManga.searchResult.value = SearchResult.Searching
        viewModelScope.launch(Dispatchers.IO) {
            val result = migratingManga.migrationScope.async {
                val manga = getManga.await(target) ?: return@async null
                try {
                    val source = sourceManager.get(manga.source)!!
                    updateMangaFromRemote(source = source, manga = manga, fetchChapters = true).getOrThrow().manga
                } catch (_: Exception) {
                    null
                }
            }
                .await()

            if (result == null) {
                migratingManga.searchResult.value = SearchResult.NotFound
                withContext(Dispatchers.Main) { onMissingChapters() }
                return@launch
            }

            migratingManga.searchResult.value = result.toSuccessSearchResult()
            updateMigrationProgress()
        }
    }

    fun migrateMangas() {
        migrateMangas(replace = true)
    }

    fun copyMangas() {
        migrateMangas(replace = false)
    }

    private fun migrateMangas(replace: Boolean) {
        migrateJob = viewModelScope.launch(Dispatchers.IO) {
            state.update { it.copy(dialog = Dialog.Progress(0f)) }
            val items = items
            try {
                items.forEachIndexed { index, manga ->
                    try {
                        ensureActive()
                        val target = manga.searchResult.value.let {
                            if (it is SearchResult.Success) {
                                it.manga
                            } else {
                                null
                            }
                        }
                        if (target != null) {
                            migrateManga(current = manga.manga, target = target, replace = replace)
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        logcat(LogPriority.WARN, throwable = e)
                    }
                    state.update {
                        it.copy(dialog = Dialog.Progress((index.toFloat() / items.size).coerceAtMost(1f)))
                    }
                }

                navigateBack()
            } finally {
                state.update { it.copy(dialog = null) }
                migrateJob = null
            }
        }
    }

    fun cancelMigrate() {
        migrateJob?.cancel()
        migrateJob = null
    }

    private suspend fun navigateBack() {
        navigateBackChannel.send(Unit)
    }

    fun migrateNow(mangaId: Long, replace: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val manga = items.find { it.manga.id == mangaId } ?: return@launch
            val target = (manga.searchResult.value as? SearchResult.Success)?.manga ?: return@launch
            try {
                migrateManga(current = manga.manga, target = target, replace = replace)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // MigrateMangaUseCase now propagates failures; this single-item path has no dialog
                // to show them, so log instead of crashing the scope.
                logcat(LogPriority.ERROR, e) { "Single migration failed for manga ${manga.manga.id}" }
            }

            removeManga(mangaId)
        }
    }

    fun removeManga(mangaId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val item = items.find { it.manga.id == mangaId } ?: return@launch
            removeManga(item)
            item.migrationScope.cancel()
            updateMigrationProgress()
        }
    }

    private fun removeManga(item: MigratingManga) {
        state.update { it.copy(items = items.toMutableList().apply { remove(item) }) }
    }

    override fun onCleared() {
        items.forEach {
            it.migrationScope.cancel()
        }
    }

    fun showMigrateDialog(copy: Boolean) {
        state.update { state ->
            state.copy(
                dialog = Dialog.Migrate(
                    copy = copy,
                    totalCount = items.size,
                    skippedCount = items.count { it.searchResult.value == SearchResult.NotFound },
                ),
            )
        }
    }

    fun showExitDialog() {
        state.update {
            it.copy(dialog = Dialog.Exit)
        }
    }

    fun dismissDialog() {
        state.update { it.copy(dialog = null) }
    }

    data class ChapterInfo(
        val latestChapter: Double?,
        val chapterCount: Int,
    )

    sealed interface Dialog {
        data class Migrate(val copy: Boolean, val totalCount: Int, val skippedCount: Int) : Dialog
        data class Progress(@FloatRange(0.0, 1.0) val progress: Float) : Dialog
        data object Exit : Dialog
    }

    data class State(
        val items: List<MigratingManga> = listOf(),
        val finishedCount: Int = 0,
        val migrationComplete: Boolean = false,
        val noEligibleSources: Boolean = false,
        val dialog: Dialog? = null,
    ) {
        val mangaIds: List<Long> = items.map { it.manga.id }
    }
}

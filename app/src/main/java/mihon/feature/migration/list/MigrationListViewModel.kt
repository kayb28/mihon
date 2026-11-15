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
                        // Match quality beats raw recency: an exact title (or a higher
                        // similarity) must never lose to a fuzzy candidate that just happens
                        // to have a newer chapter — sequels and spin-offs tend to.
                        // latestChapter is only the tiebreaker.
                        .maxWithOrNull(
                            compareBy(
                                { it.first.isExactTitle },
                                { it.first.similarity },
                                { it.second.latestChapter ?: 0.0 },
                            ),
                        )
                } else {
                    // Keep the first fuzzy hit as a fallback but keep scanning: an exact-title
                    // match on a later source must win over a fuzzy one on an earlier source.
                    // Only an exact hit stops the pass early (nothing can outrank it).
                    var firstHit: Pair<ScoredManga, ChapterInfo>? = null
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
                        if (result != null) {
                            if (result.first.isExactTitle) return@async result
                            if (firstHit == null) firstHit = result
                        }
                    }
                    firstHit
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
        // A row back in Searching invalidates the "all searches finished" flag; without this
        // the bulk buttons stay enabled and the dialog counts can desync from the list.
        state.update { it.copy(migrationComplete = false) }
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
        // own source. On other sources the pass still runs — an identical normalized title is an
        // exact match worth reporting even cross-site — but the URL can never match there, so we
        // pass a sentinel instead of null, which used to disable the pass entirely.
        val currentUrl = if (source.id == manga.source) manga.url else ""
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
            val currentItems = state.items
            state.copy(
                finishedCount = currentItems.count { it.searchResult.value != SearchResult.Searching },
                migrationComplete = currentItems.all { it.searchResult.value != SearchResult.Searching } &&
                    currentItems.any { it.searchResult.value is SearchResult.Success },
            )
        }
        if (items.isEmpty()) {
            navigateBack()
        }
    }

    fun useMangaForMigration(current: Long, target: Long, onMissingChapters: () -> Unit) {
        val migratingManga = items.find { it.manga.id == current } ?: return
        migratingManga.searchResult.value = SearchResult.Searching
        state.update { it.copy(migrationComplete = false) }
        viewModelScope.launch(Dispatchers.IO) {
            val result = migratingManga.migrationScope.async {
                val manga = getManga.await(target) ?: return@async null
                try {
                    val source = sourceManager.get(manga.source)!!
                    updateMangaFromRemote(source = source, manga = manga, fetchChapters = true).getOrThrow().manga
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            }
                .await()

            if (result == null) {
                migratingManga.searchResult.value = SearchResult.NotFound
                updateMigrationProgress()
                withContext(Dispatchers.Main) { onMissingChapters() }
                return@launch
            }

            migratingManga.searchResult.value = result.toSuccessSearchResult()
            updateMigrationProgress()
        }
    }

    fun migrateMangas(includeNonExact: Boolean = false) {
        migrateMangas(replace = true, includeNonExact = includeNonExact)
    }

    fun copyMangas(includeNonExact: Boolean = false) {
        migrateMangas(replace = false, includeNonExact = includeNonExact)
    }

    private fun migrateMangas(replace: Boolean, includeNonExact: Boolean) {
        // Reentry guard: a double tap on the confirm button must not launch two overlapping
        // bulk runs over the same entries (the second would orphan the first's job handle).
        if (migrateJob?.isActive == true) return
        // Progress dialog is shown synchronously so the button visually locks before the
        // coroutine even dispatches, closing the double-tap window entirely.
        state.update { it.copy(dialog = Dialog.Progress(0f)) }
        migrateJob = viewModelScope.launch(Dispatchers.IO) {
            val items = items
            var failedCount = 0
            val failedIds = mutableSetOf<Long>()
            val attemptedCount = items.count { entry ->
                (entry.searchResult.value as? SearchResult.Success)
                    ?.let { it.isPreselected || includeNonExact } == true
            }
            try {
                items.forEachIndexed { index, manga ->
                    try {
                        ensureActive()
                        // Preselected entries (exact-title matches and manual picks) migrate
                        // unattended; fuzzy/single-result candidates keep their search result so
                        // the user reviews them on the list. When the user explicitly opts in via
                        // the confirmation dialog, those candidates join the bulk run too.
                        val target = (manga.searchResult.value as? SearchResult.Success)
                            ?.takeIf { it.isPreselected || includeNonExact }
                            ?.manga
                        if (target != null) {
                            migrateManga(current = manga.manga, target = target, replace = replace)
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        // MigrateMangaUseCase propagates failures; count them and remember WHICH
                        // entry failed so the post-run cleanup can tell migrated from failed.
                        failedCount++
                        failedIds.add(manga.manga.id)
                        logcat(LogPriority.WARN, throwable = e)
                    }
                    state.update {
                        it.copy(dialog = Dialog.Progress(((index + 1).toFloat() / items.size).coerceAtMost(1f)))
                    }
                }
            } finally {
                state.update { it.copy(dialog = null) }
                migrateJob = null
            }

            if (failedCount > 0) {
                // Stay on the list and tell the user what failed instead of navigateBack(), which
                // looked like a fully successful run. Only entries that actually MIGRATED are
                // dropped; the ones whose migration threw are flipped to SearchResult.Error —
                // visible, retryable — instead of silently vanishing as fake successes.
                // Intentionally skipped entries (no match / non-exact without opt-in) stay.
                items.filter { entry ->
                    entry.manga.id !in failedIds &&
                        (entry.searchResult.value as? SearchResult.Success)
                            ?.let { it.isPreselected || includeNonExact } == true
                }
                    .forEach { removeManga(it) }
                items.filter { it.manga.id in failedIds }
                    .forEach { it.searchResult.value = SearchResult.Error }
                updateMigrationProgress()
                state.update {
                    it.copy(
                        dialog = Dialog.MigrateFailed(
                            failedCount = failedCount,
                            totalCount = attemptedCount,
                        ),
                    )
                }
            } else {
                navigateBack()
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
                // MigrateMangaUseCase now propagates failures. A failed single migration must
                // NOT look like a success: the row stays on the list as a retryable Error
                // instead of disappearing, and the user gets a toast, not silence.
                logcat(LogPriority.ERROR, e) { "Single migration failed for manga ${manga.manga.id}" }
                manga.searchResult.value = SearchResult.Error
                singleMigrationFailedChannel.trySend(Unit)
                return@launch
            }

            removeManga(mangaId)
        }
    }

    /** One-shot event when a single-item migration fails; the Screen turns it into a toast. */
    private val singleMigrationFailedChannel = Channel<Unit>(Channel.CONFLATED)
    val singleMigrationFailedEvent = singleMigrationFailedChannel.receiveAsFlow()

    fun removeManga(mangaId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val item = items.find { it.manga.id == mangaId } ?: return@launch
            removeManga(item)
            item.migrationScope.cancel()
            updateMigrationProgress()
        }
    }

    private fun removeManga(item: MigratingManga) {
        // state.update is the atomic CAS; reading `items` (a snapshot of state.value) outside
        // the lambda races any concurrent removal. Read the current list inside the lambda,
        // and cancel the item's scope so no in-flight search outlives its row.
        state.update { state ->
            state.copy(items = state.items.filterNot { it.manga.id == item.manga.id })
        }
        item.migrationScope.cancel()
    }

    override fun onCleared() {
        items.forEach {
            it.migrationScope.cancel()
        }
    }

    fun showMigrateDialog(copy: Boolean) {
        // The dialog counts what the bulk action will actually do: preselected entries migrate,
        // the rest (no match, search errors and non-exact candidates) are reported as skipped.
        val migratingCount = items.count { entry ->
            (entry.searchResult.value as? SearchResult.Success)?.isPreselected == true
        }
        // Non-exact candidates (fuzzy and single-result) are not migrated unless the user
        // ticks the opt-in in the confirmation dialog; they get their own count there.
        val nonExactCount = items.count { entry ->
            (entry.searchResult.value as? SearchResult.Success)?.isPreselected == false
        }
        val skippedCount = items.count { entry ->
            when (entry.searchResult.value) {
                SearchResult.NotFound, SearchResult.Error -> true
                SearchResult.Searching, is SearchResult.Success -> false
            }
        }
        state.update { state ->
            state.copy(
                dialog = Dialog.Migrate(
                    copy = copy,
                    migratingCount = migratingCount,
                    nonExactCount = nonExactCount,
                    skippedCount = skippedCount,
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
        data class Migrate(
            val copy: Boolean,
            val migratingCount: Int,
            val nonExactCount: Int,
            val skippedCount: Int,
        ) : Dialog
        data class Progress(@FloatRange(0.0, 1.0) val progress: Float) : Dialog
        data class MigrateFailed(val failedCount: Int, val totalCount: Int) : Dialog
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

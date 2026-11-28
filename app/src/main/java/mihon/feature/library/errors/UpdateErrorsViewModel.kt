package mihon.feature.library.errors

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.tachiyomi.data.library.UpdateError
import eu.kanade.tachiyomi.data.library.UpdateErrorStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mihon.domain.source.interactor.UpdateMangaFromRemote
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager

data class ErrorMangaItem(
    val manga: Manga,
    val error: UpdateError,
)

data class SourceErrorGroup(
    val sourceName: String,
    val brokenLinkErrors: List<ErrorMangaItem>,
    val temporaryErrors: List<ErrorMangaItem>,
)

data class UpdateErrorsState(
    val isLoading: Boolean = true,
    val totalBrokenLinks: Int = 0,
    val totalTemporary: Int = 0,
    val groups: List<SourceErrorGroup> = emptyList(),
)

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class UpdateErrorsViewModel(
    private val updateErrorStore: UpdateErrorStore,
    private val getManga: GetManga,
    private val sourceManager: SourceManager,
    private val updateMangaFromRemote: UpdateMangaFromRemote,
) : ViewModel() {

    private val _state = MutableStateFlow(UpdateErrorsState())
    val state: StateFlow<UpdateErrorsState> = _state.asStateFlow()

    // T-B (PROMPT 9): the "Reintentar títulos con error" pass. One batch per press,
    // scoped to the error IDs snapshotted at press time; the button re-enables when it
    // ends. Successes leave the list by themselves (UpdateMangaFromRemote calls
    // removeErrorsForManga on success); failures stay refreshed by errorsFlow.
    private val _retryPass = MutableStateFlow<RetryErrorsPolicy.Pass?>(null)
    val retryPass: StateFlow<RetryErrorsPolicy.Pass?> = _retryPass.asStateFlow()

    // T-C (PROMPT 12): end-of-pass notice «N reintentados: M bien, K siguen fallando».
    private val _lastSummary = MutableStateFlow<RetryErrorsPolicy.PassSummary?>(null)
    val lastSummary: StateFlow<RetryErrorsPolicy.PassSummary?> = _lastSummary.asStateFlow()

    /** T-C (3): the screen acknowledges the notice so it does not re-show on rotation. */
    fun onSummaryShown() {
        _lastSummary.value = null
    }

    // T-C (2): ids currently shown on screen (favorites only). The pass must retry
    // exactly this set, not the raw store snapshot.
    private var visibleIds: Set<Long> = emptySet()

    fun retryButtonVisible(): Boolean =
        RetryErrorsPolicy.buttonVisible(updateErrorStore.getErrorMangaIds().size)

    fun retryButtonEnabled(): Boolean =
        RetryErrorsPolicy.buttonEnabled(_retryPass.value)

    fun retryErrorMangas() {
        // T-C (2): scope = exactly the visible list; T-C (5): null when a pass already
        // runs — one batch per press even if the button is hammered.
        val pass = RetryErrorsPolicy.beginPass(
            RetryErrorsPolicy.passScope(updateErrorStore.getErrorMangaIds(), visibleIds),
            _retryPass.value,
        ) ?: return
        _retryPass.value = pass
        viewModelScope.launch(Dispatchers.IO) {
            val results = mutableMapOf<Long, RetryErrorsPolicy.FreshError?>()
            try {
                for (mangaId in pass.targetIds) {
                    val manga = getManga.await(mangaId) ?: continue
                    // Success self-cleans the manga's errors inside UpdateMangaFromRemote;
                    // a failure leaves a fresh error in the store (errorsFlow refreshes UI).
                    val outcome = runCatching { updateMangaFromRemote(manga = manga, manualFetch = true) }
                    results[mangaId] = outcome.exceptionOrNull()?.let {
                        RetryErrorsPolicy.FreshError(message = it.message ?: "Error")
                    }
                }
            } finally {
                // T-C (3): the notice counts every attempted manga, success or failure.
                _lastSummary.value = RetryErrorsPolicy.summarize(pass, results)
                _retryPass.value = RetryErrorsPolicy.completePass(pass)
            }
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            updateErrorStore.errorsFlow.collectLatest { errors ->
                if (errors.isEmpty()) {
                    visibleIds = emptySet()
                    _state.update { UpdateErrorsState(isLoading = false) }
                    return@collectLatest
                }

                val mangaMap = errors.mapNotNull { err ->
                    getManga.await(err.mangaId)?.takeIf { it.favorite }?.let { err.mangaId to it }
                }.toMap()

                // T-C (2): keep the visible list in sync — the retry pass is scoped to it.
                visibleIds = mangaMap.keys

                val broken = mutableListOf<ErrorMangaItem>()
                val temporary = mutableListOf<ErrorMangaItem>()

                for (err in errors) {
                    val manga = mangaMap[err.mangaId] ?: continue
                    val item = ErrorMangaItem(manga, err)
                    if (err.type == UpdateError.ErrorType.BROKEN_LINK) {
                        broken.add(item)
                    } else {
                        temporary.add(item)
                    }
                }

                val allItems = broken + temporary
                val sourceIds = allItems.map { it.manga.source }.distinct()
                val groups = sourceIds.map { sourceId ->
                    val source = sourceManager.getOrStub(sourceId)
                    val sourceBroken = broken.filter { it.manga.source == sourceId }
                    val sourceTemp = temporary.filter { it.manga.source == sourceId }
                    SourceErrorGroup(
                        sourceName = source.name.ifBlank { "Source ($sourceId)" },
                        brokenLinkErrors = sourceBroken,
                        temporaryErrors = sourceTemp,
                    )
                }.filter { it.brokenLinkErrors.isNotEmpty() || it.temporaryErrors.isNotEmpty() }

                _state.update {
                    UpdateErrorsState(
                        isLoading = false,
                        totalBrokenLinks = broken.size,
                        totalTemporary = temporary.size,
                        groups = groups,
                    )
                }
            }
        }
    }
}

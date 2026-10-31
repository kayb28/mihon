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
) : ViewModel() {

    private val _state = MutableStateFlow(UpdateErrorsState())
    val state: StateFlow<UpdateErrorsState> = _state.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            updateErrorStore.errorsFlow.collectLatest { errors ->
                if (errors.isEmpty()) {
                    _state.update { UpdateErrorsState(isLoading = false) }
                    return@collectLatest
                }

                val mangaMap = errors.mapNotNull { err ->
                    getManga.await(err.mangaId)?.let { err.mangaId to it }
                }.toMap()

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

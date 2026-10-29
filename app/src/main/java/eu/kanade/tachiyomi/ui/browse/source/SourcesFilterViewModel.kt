package eu.kanade.tachiyomi.ui.browse.source

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.domain.source.interactor.GetLanguagesWithSources
import eu.kanade.domain.source.interactor.ToggleLanguage
import eu.kanade.domain.source.interactor.ToggleSource
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.source.SourceClassificationProvider
import eu.kanade.tachiyomi.source.ContentFilterableSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.domain.source.model.Source
import tachiyomi.domain.source.model.SourceClassification
import tachiyomi.domain.source.model.SourceContentFilterEntry
import tachiyomi.domain.source.service.SourceManager
import java.util.SortedMap
import java.util.TreeMap
import kotlin.time.Duration.Companion.seconds

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class SourcesFilterViewModel(
    private val preferences: SourcePreferences,
    private val getLanguagesWithSources: GetLanguagesWithSources,
    private val toggleSource: ToggleSource,
    private val toggleLanguage: ToggleLanguage,
    private val sourceManager: SourceManager,
    private val sourceClassificationProvider: SourceClassificationProvider,
) : ViewModel() {

    private val declaredContentFilters = sourceManager.sources.map { sources ->
        sources.mapNotNull { source ->
            val capability = source as? ContentFilterableSource ?: return@mapNotNull null
            source.id to capability.getContentFilterOptions().filter { it.id.isNotBlank() && '|' !in it.id }
        }.toMap()
    }
    private val sourceMetadata = combine(declaredContentFilters, sourceClassificationProvider.classifications, ::Pair)

    val state: StateFlow<State> = combine(
        getLanguagesWithSources.subscribe(),
        preferences.enabledLanguages.changes(),
        preferences.disabledSources.changes(),
        combine(
            preferences.sourceClassificationFilter.changes(),
            preferences.enabledSourceContentFilters.changes(),
            ::Pair,
        ),
        sourceMetadata,
    ) { languagesWithSources, enabledLanguages, disabledSources, categoryPreferences, metadata ->
        val (classificationFilter, enabledContentFilters) = categoryPreferences
        val (contentFilterOptions, effectiveClassifications) = metadata
        val filteredItems = TreeMap<String, List<Source>>(languagesWithSources.comparator())
        languagesWithSources.forEach { (language, sources) ->
            val visibleSources = sources.filter { source ->
                classificationFilter.isEmpty() ||
                    (effectiveClassifications[source.id] ?: SourceClassification.MIXED) in classificationFilter
            }
            if (visibleSources.isNotEmpty()) filteredItems[language] = visibleSources
        }
        State.Success(
            items = filteredItems,
            enabledLanguages = enabledLanguages,
            disabledSources = disabledSources,
            classifications = effectiveClassifications,
            classificationFilter = classificationFilter,
            contentFilterOptions = contentFilterOptions,
            enabledContentFilters = enabledContentFilters,
        )
    }
        .catch<State> { throwable -> emit(State.Error(throwable = throwable)) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), State.Loading)

    fun toggleSource(source: Source) {
        toggleSource.await(source)
    }

    fun toggleLanguage(language: String) {
        toggleLanguage.await(language)
    }

    fun setClassification(sourceId: Long, classification: SourceClassification) {
        preferences.setSourceClassification(sourceId, classification)
    }

    fun toggleClassificationFilter(classification: SourceClassification) {
        preferences.sourceClassificationFilter.getAndSet { current ->
            if (classification in current) current - classification else current + classification
        }
    }

    fun toggleContentFilter(sourceId: Long, filterId: String) {
        val entry = SourceContentFilterEntry(sourceId, filterId)
        preferences.setContentFilterEnabled(
            sourceId = sourceId,
            filterId = filterId,
            enabled = entry !in preferences.enabledSourceContentFilters.get(),
        )
    }

    sealed interface State {

        @Immutable
        data object Loading : State

        @Immutable
        data class Error(
            val throwable: Throwable,
        ) : State

        @Immutable
        data class Success(
            val items: SortedMap<String, List<Source>>,
            val enabledLanguages: Set<String>,
            val disabledSources: Set<String>,
            val classifications: Map<Long, SourceClassification>,
            val classificationFilter: Set<SourceClassification>,
            val contentFilterOptions: Map<Long, List<eu.kanade.tachiyomi.source.SourceContentFilterOption>>,
            val enabledContentFilters: Set<SourceContentFilterEntry>,
        ) : State {

            val isEmpty: Boolean
                get() = items.isEmpty()
        }
    }
}

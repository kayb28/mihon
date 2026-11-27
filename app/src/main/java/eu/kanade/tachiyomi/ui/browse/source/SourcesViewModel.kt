package eu.kanade.tachiyomi.ui.browse.source

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.domain.source.interactor.GetEnabledSources
import eu.kanade.domain.source.interactor.ToggleSource
import eu.kanade.domain.source.interactor.ToggleSourcePin
import eu.kanade.presentation.browse.SourceUiModel
import eu.kanade.tachiyomi.data.source.SourceHealthRecord
import eu.kanade.tachiyomi.data.source.SourceHealthStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.source.model.Pin
import tachiyomi.domain.source.model.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import tachiyomi.domain.source.service.SourceManager
import java.util.TreeMap
import kotlin.time.Duration.Companion.seconds

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class SourcesViewModel(
    private val getEnabledSources: GetEnabledSources,
    private val toggleSource: ToggleSource,
    private val toggleSourcePin: ToggleSourcePin,
    private val sourceManager: SourceManager,
    private val sourceHealthStore: SourceHealthStore,
    // D6 (PROMPT 8): storage of the per-source domain override rules
    private val domainOverrideStore: eu.kanade.tachiyomi.network.DomainOverrideStore,
) : ViewModel() {

    /** D6: the active rule for a host, formatted for display (from -> to) or null. */
    fun activeDomainRule(host: String): String? =
        domainOverrideStore.targetFor(host)?.let { to -> "$host -> $to" }

    /** D6: save (create/edit) the rule for [fromHost]. */
    fun saveDomainRule(fromHost: String, toHost: String) {
        domainOverrideStore.upsert(
            eu.kanade.tachiyomi.network.DomainOverrideRule(fromHost = fromHost, toHost = toHost),
        )
    }

    /** D6: delete the rule for [fromHost] if present. */
    fun deleteDomainRule(fromHost: String) {
        domainOverrideStore.delete(fromHost)
    }

    private val _events = Channel<Event>(Int.MAX_VALUE)
    val events = _events.receiveAsFlow()

    private val dialog = MutableStateFlow<Dialog?>(null)

    private val enabledSources = getEnabledSources.subscribe()
        .catch {
            logcat(LogPriority.ERROR, it)
            _events.send(Event.FailedFetchingSources)
        }
        .map(::toSourceUiModels)

    val state: StateFlow<State> = combine(
        enabledSources,
        dialog,
        sourceHealthStore.records.changes(),
    ) { items, dialog, healthRecords ->
        State(dialog = dialog, isLoading = false, items = items, healthRecords = healthRecords)
    }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), State())

    private fun toSourceUiModels(sources: List<Source>): List<SourceUiModel> {
        val map = TreeMap<String, MutableList<Source>> { d1, d2 ->
            // Sources without a lang defined will be placed at the end
            when {
                d1 == LAST_USED_KEY && d2 != LAST_USED_KEY -> -1
                d2 == LAST_USED_KEY && d1 != LAST_USED_KEY -> 1
                d1 == PINNED_KEY && d2 != PINNED_KEY -> -1
                d2 == PINNED_KEY && d1 != PINNED_KEY -> 1
                d1 == "" && d2 != "" -> 1
                d2 == "" && d1 != "" -> -1
                else -> d1.compareTo(d2)
            }
        }
        val byLang = sources.groupByTo(map) {
            when {
                it.isUsedLast -> LAST_USED_KEY
                Pin.Actual in it.pin -> PINNED_KEY
                else -> it.lang
            }
        }

        return byLang.flatMap {
            listOf(
                SourceUiModel.Header(it.key),
                *it.value.map { source ->
                    SourceUiModel.Item(source)
                }.toTypedArray(),
            )
        }
    }

    fun toggleSource(source: Source) {
        toggleSource.await(source)
    }

    fun togglePin(source: Source) {
        toggleSourcePin.await(source)
    }

    fun showSourceDialog(source: Source) {
        dialog.update { Dialog(source) }
    }

    fun closeDialog() {
        dialog.update { null }
    }

    // D6 (PROMPT 8): the "Editar dominio" dialog state for a source whose site moved.
    // The request host comes from the installed source's baseUrl (HttpSource), resolved
    // asynchronously because SourceManager.get is a suspend call.
    data class EditDomainState(val source: Source, val host: String?)

    private val editDomain = MutableStateFlow<EditDomainState?>(null)
    val editDomainState: StateFlow<EditDomainState?> = editDomain.asStateFlow()

    fun showEditDomainDialog(source: Source) {
        dialog.update { null }
        editDomain.update { EditDomainState(source, host = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val installed = sourceManager.get(source.id)
            val host = (installed as? HttpSource)?.baseUrl?.toHttpUrlOrNull()?.host
            editDomain.update { it?.copy(host = host) }
        }
    }

    fun closeEditDomainDialog() {
        editDomain.update { null }
    }

    fun checkSource(source: Source) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val installed = sourceManager.get(source.id)
                if (installed == null) {
                    sourceHealthStore.recordNeedsVerification(source.id)
                } else if (installed.supportsLatest) {
                    installed.getLatestUpdates(1)
                    sourceHealthStore.recordSuccess(source.id)
                } else {
                    installed.getPopularManga(1)
                    sourceHealthStore.recordSuccess(source.id)
                }
                _events.send(Event.SourceCheckCompleted)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                sourceHealthStore.recordFailure(source.id, e)
                _events.send(Event.SourceCheckFailed)
            }
        }
    }

    sealed interface Event {
        data object FailedFetchingSources : Event
        data object SourceCheckCompleted : Event
        data object SourceCheckFailed : Event
    }

    data class Dialog(val source: Source)

    @Immutable
    data class State(
        val dialog: Dialog? = null,
        val isLoading: Boolean = true,
        val items: List<SourceUiModel> = listOf(),
        val healthRecords: Set<SourceHealthRecord> = emptySet(),
    ) {
        val isEmpty = items.isEmpty()
    }

    companion object {
        const val PINNED_KEY = "pinned"
        const val LAST_USED_KEY = "last_used"
    }
}

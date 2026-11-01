package mihon.feature.migration.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cafe.adriel.voyager.core.screen.Screen
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.domain.manga.model.hasCustomCover
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.source.getNameForMangaInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.domain.migration.models.MigrationFlag
import mihon.domain.migration.usecases.MigrateMangaUseCase
import mihon.feature.common.utils.getLabel
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
internal fun Screen.MigrateMangaDialog(
    current: Manga,
    target: Manga,
    onClickTitle: () -> Unit,
    onDismissRequest: () -> Unit,
    onComplete: () -> Unit = onDismissRequest,
) {
    val scope = rememberCoroutineScope()

    val viewModel = metroViewModel<MigrateDialogViewModel>()
    LaunchedEffect(current, target) {
        viewModel.init(current, target)
    }
    val state by viewModel.state.collectAsState()

    if (state.isMigrated) return

    if (state.isMigrating) {
        LoadingScreen(
            modifier = Modifier.background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f)),
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = stringResource(MR.strings.migration_dialog_what_to_include))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                // Confirm-at-a-glance header: the dialog used to jump straight into the
                // "what to include" checkboxes, so the only way to tell where the migration was
                // going was to remember what the user tapped. Showing both titles (and the
                // sources they live on) makes a wrong target obvious before Migrate is hit.
                MigrationDialogHeader(state)
                state.applicableFlags.fastForEach { flag ->
                    LabeledCheckbox(
                        label = stringResource(flag.getLabel()),
                        checked = flag in state.selectedFlags,
                        onCheckedChange = { viewModel.toggleSelection(flag) },
                    )
                }
                if (state.migrationFailed) {
                    Text(
                        text = stringResource(MR.strings.migration_error_generic),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = MaterialTheme.padding.small),
                    )
                }
            }
        },
        confirmButton = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
            ) {
                TextButton(
                    onClick = {
                        onDismissRequest()
                        onClickTitle()
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_show_manga))
                }

                Spacer(modifier = Modifier.weight(1f))

                TextButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            val migrated = viewModel.migrateManga(replace = false)
                            if (migrated) withContext(Dispatchers.Main) { onComplete() }
                        }
                    },
                ) {
                    Text(text = stringResource(MR.strings.copy))
                }
                TextButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            val migrated = viewModel.migrateManga(replace = true)
                            if (migrated) withContext(Dispatchers.Main) { onComplete() }
                        }
                    },
                ) {
                    Text(text = stringResource(MR.strings.migrate))
                }
            }
        },
    )
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class MigrateDialogViewModel(
    private val sourcePreference: SourcePreferences,
    private val coverCache: CoverCache,
    private val downloadManager: DownloadManager,
    private val migrateManga: MigrateMangaUseCase,
    private val sourceManager: SourceManager,
) : ViewModel() {

    val state: StateFlow<MigrateDialogViewModel.State>
        field = MutableStateFlow<MigrateDialogViewModel.State>(State())

    fun init(current: Manga, target: Manga) {
        val applicableFlags = buildList {
            MigrationFlag.entries.forEach {
                val applicable = when (it) {
                    MigrationFlag.CHAPTER -> true
                    MigrationFlag.CATEGORY -> true
                    MigrationFlag.CUSTOM_COVER -> current.hasCustomCover(coverCache)
                    MigrationFlag.NOTES -> current.notes.isNotBlank()
                    MigrationFlag.REMOVE_DOWNLOAD -> downloadManager.getDownloadCount(current) > 0
                }
                if (applicable) add(it)
            }
        }
        val selectedFlags = sourcePreference.migrationFlags.get()
        // Source names are resolved off the main thread (SourceManager.getOrStub is suspend).
        // Publish the manga + flags synchronously so the dialog renders immediately; the names
        // arrive a beat later and fill in the "From / To" header.
        state.update {
            State(
                current = current,
                target = target,
                applicableFlags = applicableFlags,
                selectedFlags = selectedFlags,
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            val currentSource = sourceManager.getOrStub(current.source).getNameForMangaInfo()
            val targetSource = sourceManager.getOrStub(target.source).getNameForMangaInfo()
            state.update { it.copy(currentSource = currentSource, targetSource = targetSource) }
        }
    }

    fun toggleSelection(flag: MigrationFlag) {
        state.update {
            val selectedFlags = it.selectedFlags.toMutableSet()
                .apply { if (contains(flag)) remove(flag) else add(flag) }
                .toSet()
            it.copy(selectedFlags = selectedFlags)
        }
    }

    suspend fun migrateManga(replace: Boolean): Boolean {
        val currentState = state.value
        val current = currentState.current ?: return false
        val target = currentState.target ?: return false
        sourcePreference.migrationFlags.set(currentState.selectedFlags)
        state.update { it.copy(isMigrating = true, migrationFailed = false) }
        return try {
            migrateManga(current, target, replace)
            state.update { it.copy(isMigrating = false, isMigrated = true) }
            true
        } catch (e: CancellationException) {
            state.update { it.copy(isMigrating = false) }
            throw e
        } catch (_: Exception) {
            // MigrateMangaUseCase now propagates failures — show them in-dialog instead of
            // crashing the calling scope or pretending the migration succeeded.
            state.update { it.copy(isMigrating = false, migrationFailed = true) }
            false
        }
    }

    data class State(
        val current: Manga? = null,
        val target: Manga? = null,
        val currentSource: String = "",
        val targetSource: String = "",
        val applicableFlags: List<MigrationFlag> = emptyList(),
        val selectedFlags: Set<MigrationFlag> = emptySet(),
        val isMigrating: Boolean = false,
        val isMigrated: Boolean = false,
        val migrationFailed: Boolean = false,
    )
}

@Composable
private fun MigrationDialogHeader(state: MigrateDialogViewModel.State) {
    val current = state.current
    val target = state.target
    if (current == null && target == null) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = MaterialTheme.padding.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
    ) {
        if (current != null) {
            MigrationDialogRow(
                label = stringResource(MR.strings.migration_dialog_from_label),
                title = current.title,
                source = state.currentSource,
            )
        }
        if (target != null) {
            MigrationDialogRow(
                label = stringResource(MR.strings.migration_dialog_to_label),
                title = target.title,
                source = state.targetSource,
                highlight = true,
            )
        }
    }
}

@Composable
private fun MigrationDialogRow(
    label: String,
    title: String,
    source: String,
    highlight: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.widthIn(max = 56.dp),
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (source.isNotBlank()) {
                Text(
                    text = source,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

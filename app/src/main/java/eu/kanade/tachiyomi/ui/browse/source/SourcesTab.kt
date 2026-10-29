package eu.kanade.tachiyomi.ui.browse.source

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.browse.SourceOptionsDialog
import eu.kanade.presentation.browse.SourcesScreen
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import eu.kanade.tachiyomi.data.library.LibraryUpdateWorker
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.FilterList
import mihon.icons.materialsymbols.rounded.TravelExplore
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun Screen.sourcesTab(): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val viewModel = metroViewModel<SourcesViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updatingLibraryText = stringResource(MR.strings.updating_library)
    val updateAlreadyRunningText = stringResource(MR.strings.update_already_running)
    val sourceCheckCompleteText = stringResource(MR.strings.source_health_check_complete)
    val sourceCheckFailedText = stringResource(MR.strings.source_health_check_failed)

    return TabContent(
        titleRes = MR.strings.label_sources,
        actions = listOf(
            AppBar.Action(
                title = stringResource(MR.strings.action_global_search),
                icon = MaterialSymbols.Rounded.TravelExplore,
                onClick = { navigator.push(GlobalSearchScreen()) },
            ),
            AppBar.Action(
                title = stringResource(MR.strings.action_filter),
                icon = MaterialSymbols.Rounded.FilterList,
                onClick = { navigator.push(SourcesFilterScreen()) },
            ),
        ),
        content = { contentPadding, snackbarHostState ->
            SourcesScreen(
                state = state,
                contentPadding = contentPadding,
                onClickItem = { source, listing ->
                    navigator.push(BrowseSourceScreen(source.id, listing.query))
                },
                onClickPin = viewModel::togglePin,
                onLongClickItem = viewModel::showSourceDialog,
            )

            state.dialog?.let { dialog ->
                val source = dialog.source
                SourceOptionsDialog(
                    source = source,
                    healthRecord = state.healthRecords.firstOrNull { it.sourceId == source.id },
                    onClickPin = {
                        viewModel.togglePin(source)
                        viewModel.closeDialog()
                    },
                    onClickDisable = {
                        viewModel.toggleSource(source)
                        viewModel.closeDialog()
                    },
                    onClickCheckHealth = { viewModel.checkSource(source) },
                    onClickUpdateLibrary = {
                        scope.launch {
                            val started = LibraryUpdateWorker.startNow(context.workManager, sourceId = source.id)
                            snackbarHostState.showSnackbar(
                                if (started) updatingLibraryText else updateAlreadyRunningText,
                            )
                        }
                        viewModel.closeDialog()
                    },
                    onDismiss = viewModel::closeDialog,
                )
            }

            val internalErrString = stringResource(MR.strings.internal_error)
            LaunchedEffect(Unit) {
                viewModel.events.collectLatest { event ->
                    when (event) {
                        SourcesViewModel.Event.FailedFetchingSources -> {
                            launch { snackbarHostState.showSnackbar(internalErrString) }
                        }
                        SourcesViewModel.Event.SourceCheckCompleted -> {
                            launch { snackbarHostState.showSnackbar(sourceCheckCompleteText) }
                        }
                        SourcesViewModel.Event.SourceCheckFailed -> {
                            launch { snackbarHostState.showSnackbar(sourceCheckFailedText) }
                        }
                    }
                }
            }
        },
    )
}

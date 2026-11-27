package eu.kanade.tachiyomi.ui.browse.extension.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.browse.ExtensionDetailsScreen
import eu.kanade.presentation.util.Screen
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

data class ExtensionDetailsScreen(
    private val pkgName: String,
) : Screen() {

    @Composable
    override fun Content() {
        val viewModel =
            assistedMetroViewModel<ExtensionDetailsViewModel, ExtensionDetailsViewModel.Factory> {
                create(pkgName = pkgName)
            }
        val state by viewModel.state.collectAsStateWithLifecycle()

        val navigator = LocalNavigator.currentOrThrow

        when (val state = state) {
            ExtensionDetailsViewModel.State.Loading -> LoadingScreen()
            ExtensionDetailsViewModel.State.Uninstalled -> {
                LaunchedEffect(Unit) { navigator.pop() }
                EmptyScreen(MR.strings.empty_screen)
            }
            is ExtensionDetailsViewModel.State.Success -> {
                // Fallo G (T-G, G1): the section's rows, from the SAME store as the
                // sources tab's "Editar dominio" entry (G2). Re-computed per state
                // emission; re-entering the screen refreshes the listed rules.
                val domainRows = remember(state) { viewModel.domainRows(state.sources) }
                ExtensionDetailsScreen(
                    navigateUp = navigator::pop,
                    state = state,
                    onClickSourcePreferences = { navigator.push(SourcePreferencesScreen(it)) },
                    onClickEnableAll = { viewModel.toggleSources(true) },
                    onClickDisableAll = { viewModel.toggleSources(false) },
                    onClickClearCookies = viewModel::clearCookies,
                    onClickUninstall = viewModel::uninstallExtension,
                    onClickSource = viewModel::toggleSource,
                    onClickIncognito = viewModel::toggleIncognito,
                    domainRows = domainRows,
                    onClickEditDomain = viewModel::showEditDomainDialog,
                )
            }
        }

        // Fallo G (T-G, G1): the SAME "Editar dominio" dialog the sources tab uses
        // (SourceEditDomainDialog), backed by the same store (G2: no duplicate impl).
        val editDomainState by viewModel.editDomainState.collectAsStateWithLifecycle()
        editDomainState?.host?.let { host ->
            eu.kanade.presentation.browse.SourceEditDomainDialog(
                currentHost = host,
                activeRule = viewModel.activeDomainRule(host),
                onDismissRequest = viewModel::closeEditDomainDialog,
                onSave = { toHost ->
                    viewModel.saveDomainRule(host, toHost)
                    viewModel.closeEditDomainDialog()
                },
                onDelete = {
                    viewModel.deleteDomainRule(host)
                    viewModel.closeEditDomainDialog()
                },
            )
        }
    }
}

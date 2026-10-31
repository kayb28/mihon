package mihon.feature.migration.repair

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.manga.components.BaseMangaListItem
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.migration.search.MigrateSearchScreen
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Check
import mihon.icons.materialsymbols.rounded.DoneAll
import mihon.icons.materialsymbols.rounded.Refresh
import mihon.icons.materialsymbols.rounded.Search
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

class RepairLinksScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = metroViewModel<RepairLinksViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()

        if (state.isLoading) {
            LoadingScreen()
            return
        }

        Scaffold(
            topBar = {
                AppBar(
                    title = stringResource(MR.strings.repair_links_title),
                    navigateUp = navigator::pop,
                )
            },
        ) { paddingValues ->
            if (state.items.isEmpty() && !state.isSimulating) {
                EmptyScreen(
                    message = stringResource(MR.strings.update_errors_empty),
                    modifier = Modifier.padding(paddingValues),
                )
                return@Scaffold
            }

            FastScrollLazyColumn(
                contentPadding = paddingValues,
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(MaterialTheme.padding.medium),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(MaterialTheme.padding.medium),
                            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                        ) {
                            Text(
                                text = stringResource(MR.strings.repair_links_simulation_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = stringResource(MR.strings.repair_links_simulation_desc),
                                style = MaterialTheme.typography.bodySmall,
                            )

                            if (state.isSimulating || state.isMigrating) {
                                val progress = if (state.progressTotal > 0) {
                                    state.progressCurrent.toFloat() / state.progressTotal.toFloat()
                                } else {
                                    0f
                                }
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                )
                                Text(
                                    text = stringResource(
                                        MR.strings.repair_links_running,
                                        state.progressCurrent,
                                        state.progressTotal,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                Text(
                                    text = stringResource(
                                        MR.strings.repair_links_summary,
                                        state.repairedCount,
                                        state.pendingReviewCount,
                                        state.noMatchCount,
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                            ) {
                                if (state.matchFoundCount > 0 && !state.isMigrating && !state.isSimulating) {
                                    FilledTonalButton(
                                        onClick = { viewModel.executeRepair(limit = 10) },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(text = stringResource(MR.strings.repair_links_action_repair_10))
                                    }
                                    Button(
                                        onClick = { viewModel.executeRepair(limit = null) },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Icon(
                                            imageVector = MaterialSymbols.Rounded.DoneAll,
                                            contentDescription = null,
                                            modifier = Modifier.padding(end = 4.dp),
                                        )
                                        Text(text = stringResource(MR.strings.repair_links_action_repair_all))
                                    }
                                } else if (!state.isSimulating && !state.isMigrating) {
                                    OutlinedButton(
                                        onClick = { viewModel.startSimulation() },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Icon(
                                            imageVector = MaterialSymbols.Rounded.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.padding(end = 4.dp),
                                        )
                                        Text(text = stringResource(MR.strings.repair_links_action_simulate))
                                    }
                                }
                            }
                        }
                    }
                }

                items(
                    items = state.items,
                    key = { "repair_${it.currentManga.id}" },
                ) { item ->
                    BaseMangaListItem(
                        manga = item.currentManga,
                        content = {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = MaterialTheme.padding.medium),
                            ) {
                                Text(
                                    text = item.currentManga.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                when (item.status) {
                                    ProposedRepair.Status.SIMULATING -> {
                                        Text(
                                            text = "...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.outline,
                                        )
                                    }
                                    ProposedRepair.Status.MATCH_FOUND -> {
                                        Text(
                                            text = "→ ${item.proposedManga?.url ?: ""}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    ProposedRepair.Status.REPAIRED -> {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = MaterialSymbols.Rounded.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(end = 4.dp),
                                            )
                                            Text(
                                                text = stringResource(MR.strings.repair_links_status_repaired),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                    ProposedRepair.Status.NEEDS_REVIEW -> {
                                        Text(
                                            text = "${stringResource(MR.strings.repair_links_status_pending)}${item.reason?.let { " ($it)" } ?: ""}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    ProposedRepair.Status.NO_MATCH -> {
                                        Text(
                                            text = stringResource(MR.strings.repair_links_status_no_match),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.outline,
                                        )
                                    }
                                    ProposedRepair.Status.FAILED -> {
                                        Text(
                                            text = "Migration failed",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        },
                        actions = {
                            if (item.status == ProposedRepair.Status.NEEDS_REVIEW || item.status == ProposedRepair.Status.NO_MATCH) {
                                OutlinedButton(
                                    onClick = {
                                        navigator.push(MigrateSearchScreen(item.currentManga.id))
                                    },
                                    modifier = Modifier.padding(start = MaterialTheme.padding.small),
                                ) {
                                    Icon(
                                        imageVector = MaterialSymbols.Rounded.Search,
                                        contentDescription = null,
                                        modifier = Modifier.padding(end = 4.dp),
                                    )
                                    Text(
                                        text = stringResource(MR.strings.repair_links_action_manual),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

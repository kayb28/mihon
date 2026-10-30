package mihon.feature.library.errors

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.util.system.createFileInCacheDir
import eu.kanade.tachiyomi.util.storage.getUriCompat
import mihon.feature.migration.repair.RepairLinksScreen
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.DoneAll
import mihon.icons.materialsymbols.rounded.Info
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import java.io.File

class UpdateErrorsScreen : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = metroViewModel<UpdateErrorsViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()

        if (state.isLoading) {
            LoadingScreen()
            return
        }

        Scaffold(
            topBar = {
                AppBar(
                    title = stringResource(MR.strings.update_errors_title),
                    navigateUp = navigator::pop,
                    actions = {
                        IconButton(
                            onClick = {
                                val errorFile = File(context.cacheDir, "mihon_update_errors.txt")
                                if (errorFile.exists()) {
                                    try {
                                        val uri = errorFile.getUriCompat(context)
                                        NotificationReceiver.openErrorLogPendingActivity(context, uri).send()
                                    } catch (_: Exception) {
                                    }
                                }
                            },
                        ) {
                            Icon(
                                imageVector = MaterialSymbols.Rounded.Info,
                                contentDescription = stringResource(MR.strings.update_errors_view_log),
                            )
                        }
                    },
                )
            },
        ) { paddingValues ->
            if (state.groups.isEmpty()) {
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
                if (state.totalBrokenLinks > 0) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(MaterialTheme.padding.medium),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(MaterialTheme.padding.medium),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(
                                            MR.strings.update_errors_broken_links,
                                            state.totalBrokenLinks,
                                        ),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = stringResource(MR.strings.repair_links_simulation_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                Button(
                                    onClick = {
                                        navigator.push(RepairLinksScreen())
                                    },
                                    modifier = Modifier.padding(start = MaterialTheme.padding.small),
                                ) {
                                    Icon(
                                        imageVector = MaterialSymbols.Rounded.DoneAll,
                                        contentDescription = null,
                                        modifier = Modifier.padding(end = 4.dp),
                                    )
                                    Text(text = stringResource(MR.strings.update_errors_action_repair))
                                }
                            }
                        }
                    }
                }

                state.groups.forEach { group ->
                    item(key = "header_${group.sourceName}") {
                        Text(
                            text = group.sourceName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(
                                start = MaterialTheme.padding.medium,
                                end = MaterialTheme.padding.medium,
                                top = MaterialTheme.padding.medium,
                                bottom = MaterialTheme.padding.small,
                            ),
                        )
                    }

                    if (group.brokenLinkErrors.isNotEmpty()) {
                        item(key = "broken_header_${group.sourceName}") {
                            Text(
                                text = stringResource(
                                    MR.strings.update_errors_broken_links,
                                    group.brokenLinkErrors.size,
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(
                                    horizontal = MaterialTheme.padding.medium,
                                    vertical = MaterialTheme.padding.extraSmall,
                                ),
                            )
                        }

                        items(
                            items = group.brokenLinkErrors,
                            key = { "broken_${it.manga.id}" },
                        ) { errorItem ->
                            BaseMangaListItem(
                                manga = errorItem.manga,
                                onClickItem = {
                                    navigator.push(MangaScreen(errorItem.manga.id))
                                },
                                content = {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(start = MaterialTheme.padding.medium),
                                    ) {
                                        Text(
                                            text = errorItem.manga.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = errorItem.error.message ?: "HTTP ${errorItem.error.httpCode ?: 404}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                },
                            )
                        }
                    }

                    if (group.temporaryErrors.isNotEmpty()) {
                        item(key = "temp_header_${group.sourceName}") {
                            Text(
                                text = stringResource(
                                    MR.strings.update_errors_temporary,
                                    group.temporaryErrors.size,
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(
                                    horizontal = MaterialTheme.padding.medium,
                                    vertical = MaterialTheme.padding.extraSmall,
                                ),
                            )
                        }

                        items(
                            items = group.temporaryErrors,
                            key = { "temp_${it.manga.id}" },
                        ) { errorItem ->
                            BaseMangaListItem(
                                manga = errorItem.manga,
                                onClickItem = {
                                    navigator.push(MangaScreen(errorItem.manga.id))
                                },
                                content = {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(start = MaterialTheme.padding.medium),
                                    ) {
                                        Text(
                                            text = errorItem.manga.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = errorItem.error.message ?: "Error",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.outline,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

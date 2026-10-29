package eu.kanade.presentation.browse

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.browse.components.BaseSourceItem
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.settings.widget.SwitchPreferenceWidget
import eu.kanade.tachiyomi.ui.browse.source.SourcesFilterViewModel
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.domain.source.model.Source
import tachiyomi.domain.source.model.SourceClassification
import tachiyomi.domain.source.model.SourceContentFilterEntry
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen

@Composable
fun SourcesFilterScreen(
    navigateUp: () -> Unit,
    state: SourcesFilterViewModel.State.Success,
    onClickLanguage: (String) -> Unit,
    onClickSource: (Source) -> Unit,
    onClickClassificationFilter: (SourceClassification) -> Unit,
    onSetClassification: (Long, SourceClassification) -> Unit,
    onToggleContentFilter: (Long, String) -> Unit,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(MR.strings.label_sources),
                navigateUp = navigateUp,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        if (state.isEmpty && state.classificationFilter.isEmpty()) {
            EmptyScreen(
                stringRes = if (state.classificationFilter.isEmpty()) {
                    MR.strings.source_filter_empty_screen
                } else {
                    MR.strings.source_classification_filter_empty
                },
                modifier = Modifier.padding(contentPadding),
            )
            return@Scaffold
        }
        SourcesFilterContent(
            contentPadding = contentPadding,
            state = state,
            onClickLanguage = onClickLanguage,
            onClickSource = onClickSource,
            onClickClassificationFilter = onClickClassificationFilter,
            onSetClassification = onSetClassification,
            onToggleContentFilter = onToggleContentFilter,
        )
    }
}

@Composable
private fun SourcesFilterContent(
    contentPadding: PaddingValues,
    state: SourcesFilterViewModel.State.Success,
    onClickLanguage: (String) -> Unit,
    onClickSource: (Source) -> Unit,
    onClickClassificationFilter: (SourceClassification) -> Unit,
    onSetClassification: (Long, SourceClassification) -> Unit,
    onToggleContentFilter: (Long, String) -> Unit,
) {
    FastScrollLazyColumn(
        contentPadding = contentPadding,
    ) {
        item(key = "source-classification-filter", contentType = "source-classification-filter") {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(stringResource(MR.strings.source_classification_filter_title))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SourceClassification.entries.forEach { classification ->
                        FilterChip(
                            selected = classification in state.classificationFilter,
                            onClick = { onClickClassificationFilter(classification) },
                            label = { Text(stringResource(classification.labelResource())) },
                        )
                    }
                }
                Text(
                    text = stringResource(MR.strings.source_classification_warning),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = stringResource(MR.strings.source_content_filter_notice),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
                if (state.isEmpty && state.classificationFilter.isNotEmpty()) {
                    Text(stringResource(MR.strings.source_classification_filter_empty))
                }
            }
        }
        state.items.forEach { (language, sources) ->
            val enabled = language in state.enabledLanguages
            item(
                key = language,
                contentType = "source-filter-header",
            ) {
                SourcesFilterHeader(
                    modifier = Modifier.animateItem(),
                    language = language,
                    enabled = enabled,
                    onClickItem = onClickLanguage,
                )
            }
            items(
                items = sources,
                key = { "source-filter-${it.key()}" },
                contentType = { "source-filter-item" },
            ) { source ->
                SourcesFilterItem(
                    modifier = Modifier.animateItem(),
                    source = source,
                    enabled = "${source.id}" !in state.disabledSources,
                    classification = state.classifications[source.id] ?: SourceClassification.MIXED,
                    onClickItem = onClickSource,
                    onSetClassification = onSetClassification,
                    contentFilterOptions = state.contentFilterOptions[source.id].orEmpty(),
                    enabledContentFilters = state.enabledContentFilters,
                    onToggleContentFilter = onToggleContentFilter,
                )
            }
        }
    }
}

@Composable
private fun SourcesFilterHeader(
    language: String,
    enabled: Boolean,
    onClickItem: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SwitchPreferenceWidget(
        modifier = modifier,
        title = LocaleHelper.getSourceDisplayName(language, LocalContext.current),
        checked = enabled,
        onCheckedChanged = { onClickItem(language) },
    )
}

@Composable
private fun SourcesFilterItem(
    source: Source,
    enabled: Boolean,
    classification: SourceClassification,
    onClickItem: (Source) -> Unit,
    onSetClassification: (Long, SourceClassification) -> Unit,
    contentFilterOptions: List<eu.kanade.tachiyomi.source.SourceContentFilterOption>,
    enabledContentFilters: Set<SourceContentFilterEntry>,
    onToggleContentFilter: (Long, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember(source.id) { mutableStateOf(false) }
    var contentFiltersExpanded by remember(source.id) { mutableStateOf(false) }
    BaseSourceItem(
        modifier = modifier,
        source = source,
        showLanguageInContent = false,
        onClickItem = { onClickItem(source) },
        action = {
            TextButton(onClick = { expanded = true }) {
                Text(stringResource(classification.labelResource()))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                SourceClassification.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(option.labelResource())) },
                        onClick = {
                            onSetClassification(source.id, option)
                            expanded = false
                        },
                    )
                }
            }
            if (contentFilterOptions.isNotEmpty()) {
                TextButton(onClick = { contentFiltersExpanded = true }) {
                    Text(stringResource(MR.strings.source_content_filter_title))
                }
                DropdownMenu(
                    expanded = contentFiltersExpanded,
                    onDismissRequest = { contentFiltersExpanded = false },
                ) {
                    contentFilterOptions.forEach { option ->
                        val selected = SourceContentFilterEntry(source.id, option.id) in enabledContentFilters
                        DropdownMenuItem(
                            text = {
                                Row(modifier = Modifier.widthIn(max = 280.dp)) {
                                    Checkbox(checked = selected, onCheckedChange = null)
                                    Column {
                                        Text(option.label)
                                        option.description?.let {
                                            Text(
                                                it,
                                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                            )
                                        }
                                    }
                                }
                            },
                            onClick = { onToggleContentFilter(source.id, option.id) },
                        )
                    }
                }
            } else {
                Text(
                    text = stringResource(MR.strings.source_content_filter_none),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
            }
            Checkbox(checked = enabled, onCheckedChange = null)
        },
    )
}

private fun SourceClassification.labelResource() = when (this) {
    SourceClassification.GENERAL -> MR.strings.source_classification_general
    SourceClassification.ADULT -> MR.strings.source_classification_adult
    SourceClassification.MIXED -> MR.strings.source_classification_mixed
}

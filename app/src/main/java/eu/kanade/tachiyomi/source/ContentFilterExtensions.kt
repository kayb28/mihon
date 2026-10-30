package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.SManga

/** Applies only IDs declared by the source; an empty or unsupported selection is a no-op. */
fun Source.applyEnabledContentFilters(
    mangas: List<SManga>,
    selectedFilterIds: Set<String>,
): List<SManga> {
    val filterable = this as? ContentFilterableSource ?: return mangas
    if (selectedFilterIds.isEmpty()) return mangas

    val declaredIds = filterable.getContentFilterOptions()
        .asSequence()
        .map { it.id }
        .filter { it.isNotBlank() && '|' !in it }
        .toSet()
    val enabledIds = selectedFilterIds intersect declaredIds
    if (enabledIds.isEmpty()) return mangas
    return mangas.filter { filterable.includeInSearch(it, enabledIds) }
}

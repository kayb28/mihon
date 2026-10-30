package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.SManga

/**
 * Optional capability for sources that can reliably apply their own content filters.
 * Existing extensions are unaffected and must not be treated as supporting filtering unless they
 * explicitly implement this interface.
 */
interface ContentFilterableSource {
    /** Stable, source-specific IDs; labels should be localized by the source implementation. */
    fun getContentFilterOptions(): List<SourceContentFilterOption>

    /**
     * Returns whether [manga] should remain visible when the selected, declared filter IDs are active.
     * This is called only when the selected set is non-empty and intersected with declared options.
     * Implementations should use source-verified metadata and keep titles matching all active filters.
     */
    fun includeInSearch(manga: SManga, enabledFilterIds: Set<String>): Boolean
}

/** A user-visible, source-declared filter. IDs must remain stable across extension updates. */
data class SourceContentFilterOption(
    val id: String,
    val label: String,
    val description: String? = null,
)

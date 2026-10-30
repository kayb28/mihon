package tachiyomi.domain.library.model

/**
 * Library tab grouping mode.
 * Grouping is applied in memory over the loaded library list (no SQL changes).
 */
object LibraryGroup {
    const val BY_DEFAULT = 0
    const val BY_SOURCE = 1
}

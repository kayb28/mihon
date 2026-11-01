package tachiyomi.domain.source.model

import mihon.domain.extension.model.ContentWarning

/** Personal, user-assigned source category. It is not an extension content warning. */
enum class SourceClassification {
    GENERAL,
    ADULT,
    MIXED,
}

fun sourceClassificationFor(
    sourceId: Long,
    assignments: Set<SourceClassificationEntry>,
): SourceClassification = assignments.firstOrNull { it.sourceId == sourceId }?.classification
    ?: SourceClassification.MIXED

/**
 * Resolves a source's classification by preferring the user's explicit assignment and falling back
 * to the [contentWarning] declared by the source's extension — so a source shipped by an NSFW
 * extension reads as [SourceClassification.ADULT] even before the user tags it manually, instead of
 * every untagged source collapsing to [SourceClassification.MIXED] (which is what the prior default
 * did, and what made the filter useless on a fresh install: every extension showed "Mixed" no matter
 * what its content warning was).
 *
 * [contentWarning] being null (source's extension not loaded / unknown) still falls back to MIXED,
 * matching the historical behavior for stub sources.
 */
fun sourceClassificationFor(
    sourceId: Long,
    assignments: Set<SourceClassificationEntry>,
    contentWarning: ContentWarning?,
): SourceClassification = assignments.firstOrNull { it.sourceId == sourceId }?.classification
    ?: contentWarning.toDefaultClassification()

fun filterSourceIdsByClassification(
    sourceIds: List<Long>,
    assignments: Set<SourceClassificationEntry>,
    allowed: Set<SourceClassification>,
): List<Long> = sourceIds.filter { sourceClassificationFor(it, assignments) in allowed }

/**
 * Maps the extension-declared [ContentWarning] to the default [SourceClassification] used when the
 * user hasn't assigned one themselves: NSFW → ADULT, MIXED → MIXED, SAFE → GENERAL.
 */
fun ContentWarning?.toDefaultClassification(): SourceClassification = when (this) {
    ContentWarning.NSFW -> SourceClassification.ADULT
    ContentWarning.MIXED -> SourceClassification.MIXED
    ContentWarning.SAFE, null -> SourceClassification.GENERAL
}

/** Stable preference record keyed by source ID rather than its mutable display name. */
data class SourceClassificationEntry(
    val sourceId: Long,
    val classification: SourceClassification,
) {
    fun serialize(): String = "$sourceId|${classification.name}"

    companion object {
        /** Returns null for malformed or unknown preference values so corrupted entries are ignored. */
        fun deserialize(value: String): SourceClassificationEntry? {
            val separator = value.lastIndexOf('|')
            if (separator <= 0 || separator == value.lastIndex) return null
            val sourceId = value.substring(0, separator).toLongOrNull() ?: return null
            val classification = runCatching {
                SourceClassification.valueOf(value.substring(separator + 1))
            }.getOrNull() ?: return null
            return SourceClassificationEntry(sourceId, classification)
        }
    }
}

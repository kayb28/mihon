package tachiyomi.domain.source.model

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

fun filterSourceIdsByClassification(
    sourceIds: List<Long>,
    assignments: Set<SourceClassificationEntry>,
    allowed: Set<SourceClassification>,
): List<Long> = sourceIds.filter { sourceClassificationFor(it, assignments) in allowed }

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

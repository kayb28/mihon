package tachiyomi.domain.source.model

/** Enabled source-declared content filter, keyed by stable source ID and stable filter ID. */
data class SourceContentFilterEntry(
    val sourceId: Long,
    val filterId: String,
) {
    fun serialize(): String = "$sourceId|$filterId"

    companion object {
        fun deserialize(value: String): SourceContentFilterEntry? {
            val separator = value.indexOf('|')
            if (separator <= 0 || separator == value.lastIndex) return null
            val sourceId = value.substring(0, separator).toLongOrNull() ?: return null
            val filterId = value.substring(separator + 1)
            if (filterId.isBlank() || '|' in filterId) return null
            return SourceContentFilterEntry(sourceId, filterId)
        }
    }
}

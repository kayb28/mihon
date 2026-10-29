package eu.kanade.tachiyomi.data.source

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import mihon.domain.extension.model.ContentWarning
import tachiyomi.domain.source.model.SourceClassification

/** Resolves Mihon's extension content warning to a source default; explicit user choices always win. */
@Inject
@SingleIn(AppScope::class)
class SourceClassificationProvider(
    private val extensionManager: ExtensionManager,
    private val preferences: SourcePreferences,
) {
    val classifications: Flow<Map<Long, SourceClassification>> = combine(
        extensionManager.loadedExtensionsFlow,
        preferences.sourceClassifications.changes(),
    ) { extensions, assignments ->
        buildMap {
            extensions.forEach { extension ->
                val classification = extension.contentWarning.toSourceClassification()
                extension.sources.forEach { source -> put(source.id, classification) }
            }
            assignments.forEach { assignment -> put(assignment.sourceId, assignment.classification) }
        }
    }

    suspend fun currentClassifications(): Map<Long, SourceClassification> = classifications.first()

    suspend fun eligibleMigrationSourceIds(sourceIds: List<Long>): List<Long> =
        preferences.eligibleMigrationSourceIds(sourceIds, currentClassifications())

    private fun ContentWarning.toSourceClassification(): SourceClassification = when (this) {
        ContentWarning.SAFE -> SourceClassification.GENERAL
        ContentWarning.MIXED -> SourceClassification.MIXED
        ContentWarning.NSFW -> SourceClassification.ADULT
    }
}

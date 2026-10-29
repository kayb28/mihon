package eu.kanade.domain.source.service

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.source.interactor.SetMigrateSorting
import eu.kanade.tachiyomi.util.system.LocaleHelper
import mihon.domain.extension.model.ContentWarning
import mihon.domain.migration.models.MigrationFlag
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import tachiyomi.core.common.preference.getEnumSet
import tachiyomi.core.common.preference.getLongArray
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.source.model.SourceClassification
import tachiyomi.domain.source.model.SourceClassificationEntry
import tachiyomi.domain.source.model.SourceContentFilterEntry
import tachiyomi.domain.source.model.sourceClassificationFor
import tachiyomi.domain.source.model.filterSourceIdsByClassification
import tachiyomi.core.common.preference.getAndSet

@Inject
@SingleIn(AppScope::class)
class SourcePreferences(
    preferenceStore: PreferenceStore,
) {

    val sourceDisplayMode: Preference<LibraryDisplayMode> = preferenceStore.getObjectFromString(
        "pref_display_mode_catalogue",
        LibraryDisplayMode.default,
        LibraryDisplayMode.Serializer::serialize,
        LibraryDisplayMode.Serializer::deserialize,
    )

    val enabledLanguages: Preference<Set<String>> = preferenceStore.getStringSet(
        "source_languages",
        LocaleHelper.getDefaultEnabledLanguages(),
    )

    val disabledSources: Preference<Set<String>> = preferenceStore.getStringSet("hidden_catalogues", emptySet())

    val incognitoExtensions: Preference<Set<String>> = preferenceStore.getStringSet("incognito_extensions", emptySet())

    val pinnedSources: Preference<Set<String>> = preferenceStore.getStringSet("pinned_catalogues", emptySet())

    val lastUsedSource: Preference<Long> = preferenceStore.getLong(
        Preference.appStateKey("last_catalogue_source"),
        -1,
    )

    val enabledContentWarnings: Preference<Set<ContentWarning>> = preferenceStore.getEnumSet(
        "enabled_content_warnings",
        setOf(ContentWarning.SAFE, ContentWarning.MIXED, ContentWarning.NSFW),
    )

    val applyContentWarningsToInstalled: Preference<Boolean> = preferenceStore.getBoolean(
        "apply_content_warnings_to_installed",
        true,
    )

    val migrationSortingMode: Preference<SetMigrateSorting.Mode> = preferenceStore.getEnum(
        "pref_migration_sorting",
        SetMigrateSorting.Mode.ALPHABETICAL,
    )

    val migrationSortingDirection: Preference<SetMigrateSorting.Direction> = preferenceStore.getEnum(
        "pref_migration_direction",
        SetMigrateSorting.Direction.ASCENDING,
    )

    val hideInLibraryItems: Preference<Boolean> = preferenceStore.getBoolean("browse_hide_in_library_items", false)

    val extensionRepos: Preference<Set<String>> = preferenceStore.getStringSet("extension_repos", emptySet())

    val extensionUpdatesCount: Preference<Int> = preferenceStore.getInt("ext_updates_count", 0)

    val trustedExtensions: Preference<Set<String>> = preferenceStore.getStringSet(
        Preference.appStateKey("trusted_extensions"),
        emptySet(),
    )

    val globalSearchFilterState: Preference<Boolean> = preferenceStore.getBoolean(
        Preference.appStateKey("has_filters_toggle_state"),
        false,
    )

    val migrationSources: Preference<List<Long>> = preferenceStore.getLongArray("migration_sources", emptyList())

    /** Missing entries default to MIXED: unknown sources are never implicitly classified as GENERAL. */
    val sourceClassifications: Preference<Set<SourceClassificationEntry>> = preferenceStore.getObjectSetFromStringSet(
        key = "source_classifications_by_id",
        defaultValue = emptySet(),
        serializer = SourceClassificationEntry::serialize,
        deserializer = SourceClassificationEntry::deserialize,
    )

    /** Empty means no category filter in the source catalog. */
    val sourceClassificationFilter: Preference<Set<SourceClassification>> = preferenceStore.getEnumSet(
        "source_classification_filter",
        emptySet(),
    )

    val enabledSourceContentFilters: Preference<Set<SourceContentFilterEntry>> =
        preferenceStore.getObjectSetFromStringSet(
            key = "enabled_source_content_filters",
            defaultValue = emptySet(),
            serializer = SourceContentFilterEntry::serialize,
            deserializer = SourceContentFilterEntry::deserialize,
        )

    /** All enabled by default to preserve migration behavior for existing users. */
    val migrationAllowedClassifications: Preference<Set<SourceClassification>> = preferenceStore.getEnumSet(
        "migration_allowed_source_classifications",
        SourceClassification.entries.toSet(),
    )

    val migrationFlags: Preference<Set<MigrationFlag>> = preferenceStore.getObjectFromInt(
        key = "migration_flags",
        defaultValue = MigrationFlag.entries.toSet(),
        serializer = { MigrationFlag.toBit(it) },
        deserializer = { value: Int -> MigrationFlag.fromBit(value) },
    )

    val migrationDeepSearchMode: Preference<Boolean> = preferenceStore.getBoolean("migration_deep_search", false)

    val migrationPrioritizeByChapters: Preference<Boolean> = preferenceStore.getBoolean(
        "migration_prioritize_by_chapters",
        false,
    )

    val migrationHideUnmatched: Preference<Boolean> = preferenceStore.getBoolean("migration_hide_unmatched", false)

    val migrationHideWithoutUpdates: Preference<Boolean> = preferenceStore.getBoolean(
        "migration_hide_without_updates",
        false,
    )

    fun sourceClassification(sourceId: Long): SourceClassification =
        sourceClassificationFor(sourceId, sourceClassifications.get())

    fun setSourceClassification(sourceId: Long, classification: SourceClassification) {
        sourceClassifications.getAndSet { current ->
            current.filterNot { it.sourceId == sourceId } + SourceClassificationEntry(sourceId, classification)
        }
    }

    fun isMigrationClassificationAllowed(sourceId: Long): Boolean =
        sourceClassification(sourceId) in migrationAllowedClassifications.get()

    fun eligibleMigrationSourceIds(sourceIds: List<Long>): List<Long> {
        val allowed = migrationAllowedClassifications.get()
        return filterSourceIdsByClassification(sourceIds, sourceClassifications.get(), allowed)
    }

    fun enabledContentFilterIds(sourceId: Long): Set<String> =
        enabledSourceContentFilters.get().filter { it.sourceId == sourceId }.mapTo(mutableSetOf()) { it.filterId }

    fun setContentFilterEnabled(sourceId: Long, filterId: String, enabled: Boolean) {
        if (filterId.isBlank() || '|' in filterId) return
        enabledSourceContentFilters.getAndSet { current ->
            val entry = SourceContentFilterEntry(sourceId, filterId)
            if (enabled) current + entry else current - entry
        }
    }
}

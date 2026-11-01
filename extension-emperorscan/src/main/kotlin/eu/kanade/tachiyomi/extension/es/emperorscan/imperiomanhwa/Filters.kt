package eu.kanade.tachiyomi.extension.es.emperorscan.imperiomanhwa

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.serialization.json.JsonElement

/**
 * Filter list builder.
 *
 * The site accepts these query params on `/manga/`:
 *   - `orden`   — reciente/nuevas/vistas/valoradas/seguidas/alfabetico
 *   - `genero`  — server-provided genre ids (fetched from the home payload)
 *   - `tipo`    — manga/manhwa/manhua/webtoon
 *   - `estado`  — ongoing/completed/hiatus/cancelled
 *   - `seccion` — server-provided section ids (optional)
 *
 * Values come from the cached [FilterData] JSON extracted from the home
 * page Inertia payload.
 */
object Filters {

    val SORT_KEYS = listOf("reciente", "nuevas", "vistas", "valoradas", "seguidas", "alfabetico")
    val TYPE_KEYS = listOf("manga", "manhwa", "manhua", "webtoon")
    val STATUS_KEYS = listOf("ongoing", "completed", "hiatus", "cancelled")

    /** `/manga/` defaults to "most viewed" — matches the upstream APK. */
    const val DEFAULT_SORT_INDEX = 2 // vistas

    class SelectFilter(
        displayName: String,
        /** Pair(displayName, value) for each option. */
        val options: List<Pair<String, String>>,
        /** URL query parameter this filter maps to. */
        val queryParam: String,
        defaultIndex: Int = 0,
    ) : Filter.Select<String>(displayName, options.map { it.first }.toTypedArray(), defaultIndex) {

        val selectedValue: String?
            get() = options.getOrNull(state)?.second?.takeUnless { it.isEmpty() }
    }

    fun build(filterData: JsonElement?, i18n: I18nBundle): FilterList {
        val parsed = filterData?.let {
            runCatching {
                ImperioManhwa.json.decodeFromJsonElement(FilterData.serializer(), it)
            }.getOrNull()
        }

        val all = i18n.get("all") to ""

        val sortFilter = SelectFilter(
            displayName = i18n.get("sort_title"),
            options = SORT_KEYS.map { key -> i18n.get("sort_$key") to key },
            queryParam = "orden",
            defaultIndex = DEFAULT_SORT_INDEX,
        )

        val genreFilter = parsed?.genres?.takeIf { it.isNotEmpty() }?.let { genres ->
            SelectFilter(
                displayName = i18n.get("genre_title"),
                options = listOf(all) + genres.map { it.name to it.slug },
                queryParam = "genero",
            )
        }

        val typeFilter = SelectFilter(
            displayName = i18n.get("type_title"),
            options = listOf(all) + TYPE_KEYS.map { key ->
                key.replaceFirstChar { it.titlecase() } to key
            },
            queryParam = "tipo",
        )

        val statusFilter = SelectFilter(
            displayName = i18n.get("status_title"),
            options = listOf(all) + STATUS_KEYS.map { key -> i18n.get("status_$key") to key },
            queryParam = "estado",
        )

        val sectionFilter = parsed?.sections?.takeIf { it.isNotEmpty() }?.let { sections ->
            SelectFilter(
                displayName = i18n.get("section_title"),
                options = listOf(all) + sections.map { it.label to it.code },
                queryParam = "seccion",
            )
        }

        return FilterList(
            listOfNotNull(sortFilter, genreFilter, typeFilter, statusFilter, sectionFilter),
        )
    }

    /** Converts the active filters to `(queryParam, value)` URL pairs. */
    fun collectQueryPairs(filters: FilterList): List<Pair<String, String>> =
        filters.filterIsInstance<SelectFilter>()
            .mapNotNull { f -> f.selectedValue?.let { f.queryParam to it } }
}

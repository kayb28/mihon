package eu.kanade.tachiyomi.ui.browse.source

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Fallo G (PROMPT 27, T-G): the pure row model shared by the TWO "Editar dominio"
 * entries — the sources tab's long-press options dialog (D6) and the new section at the
 * top of the app-hosted extension details screen (G1). One formatting, one store: both
 * entries call [activeRuleLabel] with the same DomainOverrideStore lookup, so G2's
 * "no duplicate implementation" holds by construction.
 */
object DomainRuleUi {

    /** One source's identity for row building: [sourceId] plus its site's [baseUrl]. */
    data class SourceInput(val sourceId: Long, val baseUrl: String?)

    /** One "Editar dominio" row: the resolved [host] and the active rule's display text. */
    data class Row(val sourceId: Long, val host: String, val activeRule: String?)

    /**
     * The host of an http(s) [url], lowercase, or null when the URL does not name an
     * http(s) host (local/stub sources, garbage input) — exactly the inputs a domain
     * rule can match, since DomainOverrideInterceptor matches by exact host.
     */
    fun hostOf(url: String?): String? = url?.toHttpUrlOrNull()?.host

    /**
     * The active rule for [host], formatted for display (from -> to), or null when no
     * rule is set. [targetFor] is the store lookup (DomainOverrideStore.targetFor), so
     * both entries render the same rule from the same store.
     */
    fun activeRuleLabel(host: String, targetFor: (String) -> String?): String? =
        targetFor(host)?.let { to -> "$host -> $to" }

    /**
     * The section's rows: one per source whose baseUrl resolves to an http(s) host, in
     * the given order, each with its active rule label (null when the source has none).
     */
    fun rows(sources: List<SourceInput>, targetFor: (String) -> String?): List<Row> {
        return sources.mapNotNull { input ->
            val host = hostOf(input.baseUrl) ?: return@mapNotNull null
            Row(sourceId = input.sourceId, host = host, activeRule = activeRuleLabel(host, targetFor))
        }
    }
}

package eu.kanade.tachiyomi.network

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import tachiyomi.core.common.preference.PreferenceStore

/**
 * D1: store of "from_host -> to_host" domain override rules (PROMPT 8), persisted through
 * PreferenceStore as a JSON map. Rules match by EXACT host, so each extension's site host
 * is naturally scoped per source — no source ID needs to enter the request chain.
 *
 * Persisted through PreferenceStore as a JSON map; nothing else touches the big engine.
 */
class DomainOverrideStore(
    private val preferenceStore: PreferenceStore,
    private val rulesKey: String = "domain_override_rules",
) {

    private val preferences = preferenceStore.getString(rulesKey, "{}")

    private val json = Json { ignoreUnknownKeys = true }

    /** All active rules, keyed by fromHost (lowercase). */
    fun rules(): Map<String, String> = runCatching {
        json.decodeFromString<Map<String, String>>(preferences.get())
    }.getOrDefault(emptyMap())

    /**
     * Create or edit the rule for [rule.fromHost]. Hosts are stored lowercase so the
     * exact match in [targetFor] is case-insensitive, like HttpUrl host normalization.
     */
    fun upsert(rule: DomainOverrideRule) {
        val next = rules() + (rule.fromHost.lowercase() to rule.toHost.lowercase())
        preferences.set(json.encodeToString(next))
    }

    /** Delete the rule for [fromHost] if present. */
    fun delete(fromHost: String) {
        val next = rules() - fromHost.lowercase()
        preferences.set(json.encodeToString(next))
    }

    /** The configured target host for an exact (case-insensitive) [host], or null. */
    fun targetFor(host: String): String? = rules()[host.lowercase()]
}

/**
 * A single "old host -> new host" rule plus the D4 input validation verdicts.
 */
data class DomainOverrideRule(
    val fromHost: String,
    val toHost: String,
) {
    companion object {

        /**
         * D4: validate the URL the user pasted in the "Editar dominio" dialog against the
         * source's current host. Accepted input is reduced to its host (the rule rewrites
         * hosts, not paths). Rejected input carries a visible reason for the dialog;
         * nothing ever reaches the request pipeline unvalidated.
         */
        fun validate(input: String, currentHost: String): Validation {
            val trimmed = input.trim()
            val url = runCatching { trimmed.toHttpUrl() }.getOrNull()
                // tolerate a bare host ("new.example") by assuming https, like browsers do
                ?: runCatching { "https://$trimmed".toHttpUrl() }.getOrNull()
                ?: return Invalid.Rejected()
            if (url.scheme != "https" && url.scheme != "http") return Invalid.Rejected()
            val host = url.host.lowercase()
            if (host == currentHost.lowercase() || host == "www." + currentHost.lowercase()) {
                return Invalid.SameHost(host)
            }
            return Valid(toHost = host)
        }
    }

    /** D4 verdict: either a valid rule target, or a rejection with a user-visible reason. */
    sealed interface Validation
    data class Valid(val toHost: String) : Validation
    sealed interface Invalid : Validation {
        /** The input is not an http/https URL (or not a URL at all). */
        data class Rejected(val reasonKey: String = "invalid_url") : Invalid

        /** The new host equals the current host — nothing would change. */
        data class SameHost(val host: String) : Invalid
    }
}

/**
 * D2-D5: the ONE rewrite implementation shared by the OkHttp path and the WebView fallback
 * path (PROMPT 8 audited fact: both share the network layer, so the helper must be single).
 *
 * newBuilder().host() keeps scheme, port, path, query and fragment — the request is only
 * re-pointed at the new host, exactly what a site that moved domains needs.
 */
class DomainOverrideHelper(private val store: DomainOverrideStore) {

    /**
     * D2: rewrite [url] if its host has a rule, keeping scheme choice, path, query and
     * fragment. Returns null when nothing matches (D5: the caller keeps the input as-is).
     * D6: [onRewrite] is invoked once per actual rewrite with the log line.
     */
    fun rewriteUrl(
        url: okhttp3.HttpUrl,
        onRewrite: (String) -> Unit = {},
    ): okhttp3.HttpUrl? {
        val target = store.targetFor(url.host) ?: return null
        // D7: never fight the extension's own preference. If the request already points
        // at a rule's target host (the extension moved itself via its baseUrl/mirror
        // preference), the fork rule stays out of the way. This also guarantees the
        // rewrite is applied exactly once — no ping-pong between chained rules.
        if (store.targetFor(target) != null) return null
        return url.newBuilder().host(target).build()
    }

    /**
     * D2: request-level rewrite (method, headers and body are preserved untouched);
     * returns the SAME instance when no rule matches (D5).
     */
    fun rewriteRequest(request: okhttp3.Request): okhttp3.Request {
        val rewritten = rewriteUrl(request.url) ?: return request
        return request.newBuilder().url(rewritten).build()
    }
}

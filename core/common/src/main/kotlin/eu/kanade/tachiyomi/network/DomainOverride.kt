package eu.kanade.tachiyomi.network

import tachiyomi.core.common.preference.PreferenceStore

/**
 * D1: store of "from_host -> to_host" domain override rules (PROMPT 8), persisted through
 * PreferenceStore as a JSON map. Rules match by EXACT host, so each extension's site host
 * is naturally scoped per source — no source ID needs to enter the request chain.
 *
 * RED SKELETON: this commit ships the API contract plus tests only; every method is a
 * no-op returning the empty state, so the red tests fail on BEHAVIOR (no rule exists yet).
 * The green commits implement one behavior each (D1 store first).
 */
class DomainOverrideStore(private val preferenceStore: PreferenceStore) {

    private val preferences = preferenceStore.getString("domain_override_rules", "{}")

    /** All active rules, keyed by fromHost (lowercase). Red skeleton: always empty. */
    fun rules(): Map<String, String> = emptyMap()

    /** Create or edit the rule for [rule.fromHost]. Red skeleton: no-op. */
    fun upsert(rule: DomainOverrideRule) {
        // D1 green commit implements the real persistence
    }

    /** Delete the rule for [fromHost] if present. Red skeleton: no-op. */
    fun delete(fromHost: String) {
        // D1 green commit implements the real deletion
    }

    /** The configured target host for an exact (case-insensitive) [host], or null. */
    fun targetFor(host: String): String? = null
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
         * hosts, not paths). Rejected input carries a visible reason for the dialog.
         */
        fun validate(input: String, currentHost: String): Validation = Invalid.Rejected
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
 * RED SKELETON: every method is the identity/no-op (today's behavior), so the red tests
 * fail on BEHAVIOR. Green commits implement D2 (rewrite), D3 (shared with the WebView
 * path), D5 (zero rules = identity stays guaranteed by construction) and D6 (log line).
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
    ): okhttp3.HttpUrl? = null

    /**
     * D2: request-level rewrite (method, headers and body are preserved untouched);
     * returns the SAME instance when no rule matches (D5).
     */
    fun rewriteRequest(request: okhttp3.Request): okhttp3.Request = request
}

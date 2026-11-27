package eu.kanade.tachiyomi.network.interceptor

import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Pure (Android-free) decision layer of the WebView fetch fallback, extracted verbatim from
 * WebViewFetchFallbackInterceptor so its trigger rules and guards are unit-testable on the
 * JVM (same pattern as FetchSession.kt). The big OkHttp engine stays untouched: this class
 * only decides WHEN a WebView attempt may happen and keeps the per-host cooldown bookkeeping.
 *
 * Behavior preserved at extraction time (the audited state, PROMPT 7 baseline):
 *  - fallback triggered only by the HTTP-code whitelist {403, 503} + method whitelist GET/POST
 *  - network exceptions never trigger the fallback (they propagate in the interceptor)
 *  - one WebView attempt per request (structural, in the interceptor), per-host lock,
 *    2-minute warm pool (interceptor-side), 10-minute per-host cooldown after a failure
 *
 * PROMPT 7 target behaviors (W1-W5) land as green commits behind the tests in
 * WebViewFallbackPolicyTest:
 *  W1 any HTTP error code triggers; W2 the exception branch triggers; W3 no-connectivity
 *  exemption; W4 configurable cooldown + no-amplification guards; W5 per-host usage report.
 */
class WebViewFallbackPolicy(
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** Per-host cooldown applied after a failed WebView attempt. */
    @Volatile
    var cooldownMs: Long = DEFAULT_COOLDOWN_MS

    private val cooldowns = ConcurrentHashMap<String, Long>()

    // W5: per-host "fallback used / result" record feeding the problem-source reports.
    // Declared here from the start (API contract of the red commit); it records no-ops
    // until its green commit.
    private val tracker = FallbackUsageTracker()

    /**
     * Whether a failed request (an HTTP error status code or a network exception) is allowed
     * to attempt the WebView fallback for [host] right now.
     */
    fun shouldAttempt(
        host: String,
        method: String,
        statusCode: Int? = null,
        cause: Throwable? = null,
        hasConnectivity: Boolean = true,
    ): Boolean {
        if (method !in SUPPORTED_METHODS) return false
        // W3 (technical exemption, user-vetoable until the merge gate): with no connectivity
        // at all (airplane mode / no network) a WebView cannot load anything either, so the
        // attempt is skipped to save battery. If the user vetoes W3 this check goes away and
        // every failure triggers again.
        if (!hasConnectivity) return false
        // W2: a network failure (IOException and its subtypes) is as much a "first try
        // failed" as an HTTP error code — it gets the WebView second try too. Anything that
        // is not an HTTP error and not a network exception (2xx/3xx codes, non-I/O bugs)
        // never triggers.
        val failure = (statusCode != null && statusCode in FALLBACK_CODES) || cause is IOException
        if (!failure) return false
        val lastFailure = cooldowns[host] ?: return true
        return clock() - lastFailure > cooldownMs
    }

    /**
     * Records the outcome of a WebView attempt: failures start the per-host cooldown.
     */
    fun recordResult(
        host: String,
        success: Boolean,
        statusCode: Int? = null,
        cause: Throwable? = null,
    ) {
        if (!success) cooldowns[host] = clock()
        tracker.record(host, success, statusCode, cause, usedAt = clock())
    }

    /** W5: aggregated per-host view of "fallback used / result", newest state last. */
    fun usageReport(): List<HostFallbackSummary> = tracker.summary()

    companion object {
        // W1: every 4xx/5xx triggers the WebView attempt (was {403, 503})
        val FALLBACK_CODES: IntRange = 400..599
        val SUPPORTED_METHODS = setOf("GET", "POST")
        const val DEFAULT_COOLDOWN_MS = 10L * 60_000L
    }
}

/** W5: one aggregated row per host for the problem-source reports. */
data class HostFallbackSummary(
    val host: String,
    val attempts: Int,
    val successes: Int,
    val failures: Int,
    val lastUsedAt: Long,
    val lastSuccess: Boolean?,
    val lastStatusCode: Int?,
    val lastErrorKind: String?,
)

/**
 * W5: in-memory per-host registry of fallback usages. Pure Kotlin, unit-testable; the
 * interceptor feeds it after every WebView attempt so the existing problem-source reports
 * can state whether the fallback was tried for a host and how it ended.
 */
class FallbackUsageTracker {
    private data class Entry(
        var attempts: Int = 0,
        var successes: Int = 0,
        var failures: Int = 0,
        var lastUsedAt: Long = 0L,
        var lastSuccess: Boolean? = null,
        var lastStatusCode: Int? = null,
        var lastErrorKind: String? = null,
    )

    private val entries = ConcurrentHashMap<String, Entry>()

    fun record(
        host: String,
        success: Boolean,
        statusCode: Int?,
        cause: Throwable?,
        usedAt: Long = System.currentTimeMillis(),
    ) {
        val entry = entries.computeIfAbsent(host) { Entry() }
        synchronized(entry) {
            entry.attempts++
            if (success) entry.successes++ else entry.failures++
            entry.lastUsedAt = usedAt
            entry.lastSuccess = success
            entry.lastStatusCode = statusCode
            entry.lastErrorKind = cause?.let { it::class.java.simpleName }
        }
    }

    fun summary(): List<HostFallbackSummary> = entries.map { (host, entry) ->
        synchronized(entry) {
            HostFallbackSummary(
                host = host,
                attempts = entry.attempts,
                successes = entry.successes,
                failures = entry.failures,
                lastUsedAt = entry.lastUsedAt,
                lastSuccess = entry.lastSuccess,
                lastStatusCode = entry.lastStatusCode,
                lastErrorKind = entry.lastErrorKind,
            )
        }
    }.sortedBy { it.host }
}

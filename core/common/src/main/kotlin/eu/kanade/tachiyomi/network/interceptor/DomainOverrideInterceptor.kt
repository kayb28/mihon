package eu.kanade.tachiyomi.network.interceptor

import eu.kanade.tachiyomi.network.DomainOverrideHelper
import logcat.LogPriority
import okhttp3.Interceptor
import okhttp3.Response
import tachiyomi.core.common.util.system.logcat

/**
 * D3 (PROMPT 8): the OkHttp side of the domain override. Every request whose host has a
 * "from_host -> to_host" rule is re-pointed at the new host BEFORE the rest of the chain
 * runs, so Cloudflare handling, the WebView fallback and every caller follow the move.
 *
 * This interceptor and the WebView fallback share the ONE [DomainOverrideHelper] instance
 * (audited fact: both paths share the network layer — single implementation, two callers).
 * With zero rules configured [DomainOverrideHelper.rewriteRequest] returns the same
 * Request instance and this interceptor is a pure pass-through (D5).
 *
 * D6: each actual rewrite emits one log line ("domain override a.example -> b.example")
 * feeding the problem-source reports.
 *
 * P1-7 (T-10.12, verificación V4): this interceptor is registered at APP level
 * (`NetworkHelper.addInterceptor`), so it runs exactly ONCE per call — a rewritten
 * request's redirects are followed INSIDE the chain call and never re-enter here.
 * One rewrite per call; no ping-pong, no depth guard needed.
 */
class DomainOverrideInterceptor(
    private val helper: DomainOverrideHelper,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val rewritten = helper.rewriteRequest(original)
        if (rewritten !== original) {
            logcat(LogPriority.INFO) {
                "domain override ${original.url.host} -> ${rewritten.url.host}"
            }
        }
        return chain.proceed(rewritten)
    }
}

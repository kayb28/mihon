package eu.kanade.tachiyomi.network.interceptor

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import eu.kanade.tachiyomi.network.AndroidCookieJar
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.util.system.WebViewUtil
import eu.kanade.tachiyomi.util.system.isOutdated
import eu.kanade.tachiyomi.util.system.setDefaultSettings
import eu.kanade.tachiyomi.util.system.setUserAgent
import eu.kanade.tachiyomi.util.system.toWebViewHeaders
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import logcat.LogPriority
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.write

/**
 * A "second web engine" fallback: when a source keeps blocking Mihon's plain OkHttp requests
 * (HTTP 403/503, including Cloudflare challenges that were solved but whose clearance gets
 * rejected again because OkHttp's TLS fingerprint differs from a real browser), the failed
 * request is retried through the system WebView's Chromium network stack and the result is
 * served back to the caller as a normal OkHttp response.
 *
 * How it works:
 *  1. A headless WebView is created with the original request's User-Agent.
 *  2. The URL is loaded once, which lets any JS anti-bot challenge auto-solve in a real
 *     browser context (cf_clearance lands in the shared CookieManager).
 *  3. An injected `fetch()` re-issues the original request from inside the page, using the
 *     browser's own TLS fingerprint, headers and cookies. The response is streamed back in
 *     base64 chunks through a WebMessage bridge and reassembled into an OkHttp Response.
 *
 * Level 2: if a host needed this fallback more than once recently, the used WebView is kept
 * warm in a small pool (2 minutes idle TTL) so subsequent requests for that origin skip the
 * page load entirely.
 *
 * Failures are non-destructive: the original blocked response is returned untouched and the
 * host enters a 10 minute cooldown to avoid hammering sites that can't be helped this way.
 */
class WebViewFetchFallbackInterceptor(
    private val context: Context,
    private val cookieManager: AndroidCookieJar,
    private val appScope: CoroutineScope,
    private val userAgentProvider: () -> String,
    private val preferences: NetworkPreferences,
) : WebViewInterceptor(context, appScope, userAgentProvider) {

    private val mainHandler = Handler(Looper.getMainLooper())

    // Requests to a host share its lock while the fallback runs, so concurrent requests to the
    // same host wait for one WebView instead of each opening their own
    private val hostLocks = ConcurrentHashMap<String, ReentrantReadWriteLock>()

    // Hosts where the fallback recently failed; they get a cooldown before trying again
    private val cooldowns = ConcurrentHashMap<String, Long>()

    // Fallback activations per host, used to decide when to keep a warm WebView (level 2)
    private val activations = ConcurrentHashMap<String, ArrayDeque<Long>>()

    // Warm WebView pool keyed by origin (level 2)
    private val pool = ConcurrentHashMap<String, Engine>()
    private val poolMutex = Any()

    private val firstSuccessShown = AtomicBoolean(false)

    init {
        appScope.launch(Dispatchers.Main) {
            while (isActive) {
                delay(POOL_SWEEP_INTERVAL_MS)
                evictIdleEngines()
            }
        }
    }

    override fun shouldIntercept(response: Response): Boolean {
        if (!preferences.webviewFallbackEnabled.get()) return false
        if (response.code !in FALLBACK_CODES) return false
        val request = response.request
        if (request.method !in SUPPORTED_METHODS) return false
        val lastFailure = cooldowns[request.url.host] ?: return true
        return System.currentTimeMillis() - lastFailure > COOLDOWN_MS
    }

    override fun intercept(chain: Interceptor.Chain, request: Request, response: Response): Response {
        val headersJson = request.toFetchHeadersJson()
        val bodyJson = request.toFetchBodyJson()

        if (bodyJson == null) return response

        val host = request.url.host
        val lock = hostLocks.computeIfAbsent(host) { ReentrantReadWriteLock() }
        var fallback: Response? = null

        lock.write {
            fallback = runCatching { fetchViaWebView(request, headersJson, bodyJson) }
                .onFailure {
                    logcat(LogPriority.WARN, it) { "WebView fallback failed for $host" }
                }
                .getOrNull()

            if (fallback != null) {
                onFallbackSuccess()
            } else {
                cooldowns[host] = System.currentTimeMillis()
            }
        }

        val result = fallback
        return if (result != null) {
            response.close()
            result
        } else {
            response
        }
    }

    private fun onFallbackSuccess() {
        if (firstSuccessShown.compareAndSet(false, true)) {
            mainHandler.post {
                runCatching { context.toast(MR.strings.information_webview_fallback_active, Toast.LENGTH_SHORT) }
            }
        }
    }

    // region WebView fetch

    @SuppressLint("SetJavaScriptEnabled")
    private fun fetchViaWebView(
        request: Request,
        headersJson: JSONObject,
        bodyJson: JSONObject,
    ): Response? {
        val host = request.url.host
        val origin = request.url.origin()
        val session = FetchSession()
        val prevClearance = clearance(request.url)
        // A WebView can only load a page with GET; for other methods the clearance covers the
        // whole site, so it's earned on the site's root page instead (same trick as
        // CloudflareInterceptor).
        val challengeUrl = if (request.method == "GET") {
            request.url.toString()
        } else {
            request.url.newBuilder().encodedPath("/").query(null).fragment(null).build().toString()
        }
        val loadHeaders = request.headers.toWebViewHeaders()
        val userAgent = request.header("User-Agent") ?: userAgentProvider()

        var engine: Engine? = null
        var challengeFound = false

        fun startFetch() {
            if (session.fetchStarted || session.failed != null) return
            val e = engine ?: return
            session.fetchStarted = true
            e.sessionRef.set(session)
            try {
                e.webview.evaluateJavascript(buildFetchScript(request, headersJson, bodyJson), null)
            } catch (t: Throwable) {
                session.fail("evaluate failed: ${t.message}")
            }
        }

        fun installClient(e: Engine) {
            var listenerInjected = false
            e.webview.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    e.loadedOrigin = origin
                    if (!e.useIsolatedWorld && !listenerInjected && url == challengeUrl) {
                        listenerInjected = true
                        view.evaluateJavascript(challengeListenerScript, null)
                    }
                    // Fetch right away when the page loaded normally, wait for the clearance
                    // cookie when the loaded page was an auto-solving challenge.
                    if (isCleared(request.url, prevClearance) || !challengeFound) {
                        startFetch()
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?,
                ) {
                    if (request?.isForMainFrame == true &&
                        errorResponse?.responseHeaders?.get("cf-mitigated") == "challenge"
                    ) {
                        challengeFound = true
                    }
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame && !session.fetchStarted) {
                        session.fail("load error: ${error.description}")
                    }
                }

                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    // Not handling it kills the app, failing the fallback is enough
                    logcat(LogPriority.ERROR) {
                        "WebView renderer gone during webview fallback: url=${request.url}"
                    }
                    session.fail("webview renderer gone")
                    return true
                }
            }
        }

        mainHandler.post {
            try {
                val e = obtainEngine(origin, userAgent) ?: run {
                    session.fail("no webview available")
                    return@post
                }
                engine = e
                e.inUse.set(true)
                e.lastUsed = System.currentTimeMillis()
                e.sessionRef.set(session)

                if (e.loadedOrigin != origin) {
                    installClient(e)
                    e.webview.loadUrl(challengeUrl, loadHeaders)
                    // If no clearance ever shows up (non-Cloudflare WAF) or the challenge takes
                    // too long, run the fetch anyway; its result tells the truth.
                    mainHandler.postDelayed({ startFetch() }, NAV_MAX_WAIT_MS)
                } else {
                    // Warm pooled WebView on the same origin: skip the page load entirely
                    startFetch()
                }
            } catch (t: Throwable) {
                logcat(LogPriority.WARN, t) { "WebView fallback setup failed for $host" }
                session.fail(t.message ?: "webview error")
            }
        }

        session.latch.await(OP_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        val result = assembleResponse(request, session)

        engine?.let { e ->
            e.sessionRef.set(null)
            e.inUse.set(false)
            mainHandler.post {
                if (result != null) {
                    if (recordActivation(host) || e.pooled) {
                        maybePool(origin, e)
                    } else {
                        destroyEngine(e, checkOutdated = false)
                    }
                } else {
                    if (e.pooled) {
                        synchronized(poolMutex) { if (pool[origin] === e) pool.remove(origin) }
                    }
                    destroyEngine(e, checkOutdated = true)
                }
            }
        }

        return result
    }

    private fun assembleResponse(request: Request, session: FetchSession): Response? {
        val failed = session.failed
        if (failed != null) {
            logcat(LogPriority.INFO) { "WebView fallback did not complete: $failed" }
            return null
        }
        if (!session.done || session.status == 0) return null
        // If the browser itself still gets a challenge page, this host can't be helped here
        if (session.headers["cf-mitigated"] == "challenge") {
            logcat(LogPriority.INFO) { "WebView fallback still blocked by challenge" }
            return null
        }

        val contentType = session.headers["content-type"]
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { it.toMediaTypeOrNull() }.getOrNull() }
        val bytes = synchronized(session.buffer) { session.buffer.toByteArray() }

        val headers = Headers.Builder()
        for ((name, value) in session.headers) {
            if (name in STRIPPED_RESPONSE_HEADERS) continue
            runCatching { headers.add(name, value) }
        }
        headers.add(FALLBACK_HEADER, "1")

        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(session.status)
            .message(session.statusText.take(64))
            .headers(headers.build())
            .body(bytes.toResponseBody(contentType))
            .build()
    }

    private fun buildFetchScript(request: Request, headersJson: JSONObject, bodyJson: JSONObject): String {
        val params = JSONObject()
        params.put("url", request.url.toString())
        params.put("method", request.method)
        params.put("headers", headersJson)
        for (key in bodyJson.keys()) params.put(key, bodyJson.get(key))
        return fetchScriptTemplate
            .replace("__PARAMS__", params.toString())
            .replace("__MAX_BYTES__", MAX_RESPONSE_BYTES.toString())
    }

    // endregion

    // region Engine pool

    private fun obtainEngine(origin: String, userAgent: String): Engine? {
        synchronized(poolMutex) {
            val pooled = pool[origin]
            if (
                pooled != null &&
                pooled.userAgent == userAgent &&
                !pooled.inUse.get() &&
                System.currentTimeMillis() - pooled.lastUsed < POOL_TTL_MS
            ) {
                return pooled
            }
            if (pooled != null && !pooled.inUse.get()) {
                pool.remove(origin)
                pooled.destroy()
            }
        }
        return createEngine(origin, userAgent)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createEngine(origin: String, userAgent: String): Engine? {
        if (!WebViewUtil.supportsWebView(context)) return null

        val sessionRef = AtomicReference<FetchSession?>(null)
        val useIsolatedWorld = WebViewFeature.isFeatureSupported(WebViewFeature.JS_INJECTION_IN_FRAME_AND_WORLD)

        return try {
            val webview = WebView(context).apply {
                setDefaultSettings()
                setUserAgent(userAgent)
            }
            val engine = Engine(
                webview = webview,
                sessionRef = sessionRef,
                useIsolatedWorld = useIsolatedWorld,
                listenerScriptHandler = null,
                userAgent = userAgent,
            )

            if (useIsolatedWorld) {
                // An isolated world keeps the bridge out of the page's reach
                val world = WebViewCompat.getExecutionWorld(webview, BRIDGE_NAME)
                val origins = setOf(origin)
                WebViewCompat.addWebMessageListener(
                    webview,
                    BRIDGE_NAME,
                    origins,
                    world,
                ) { _, message, _, isMainFrame, _ ->
                    if (isMainFrame) dispatchMessage(engine, message.data)
                }
                engine.listenerScriptHandler = WebViewCompat.addJavaScriptOnEvent(
                    webview,
                    challengeListenerScript,
                    WebViewCompat.INJECTION_EVENT_DOCUMENT_START,
                    origins,
                    world,
                )
            } else {
                webview.addJavascriptInterface(
                    object {
                        @Suppress("unused")
                        @JavascriptInterface
                        fun postMessage(event: String) = dispatchMessage(engine, event)
                    },
                    BRIDGE_NAME,
                )
            }

            engine
        } catch (t: Throwable) {
            logcat(LogPriority.WARN, t) { "Failed to create fallback WebView" }
            null
        }
    }

    private fun dispatchMessage(engine: Engine, data: String?) {
        if (data.isNullOrBlank()) return
        val session = engine.sessionRef.get() ?: return
        if (data == "interactiveBegin" || data == "fail") {
            session.onChallengeAbort(data)
        } else {
            session.onMessage(data)
        }
    }

    private fun maybePool(origin: String, engine: Engine) {
        val evicted = mutableListOf<Engine>()
        synchronized(poolMutex) {
            if (engine.pooled) {
                engine.lastUsed = System.currentTimeMillis()
                return
            }
            pool[origin]?.let { existing ->
                if (!existing.inUse.get()) {
                    pool.remove(origin)
                    evicted.add(existing)
                }
            }
            while (pool.size >= POOL_MAX) {
                val lru = pool.entries.minByOrNull { it.value.lastUsed } ?: break
                if (lru.value.inUse.get()) break
                pool.remove(lru.key)
                evicted.add(lru.value)
            }
            if (pool.size < POOL_MAX) {
                engine.pooled = true
                engine.lastUsed = System.currentTimeMillis()
                pool[origin] = engine
            }
        }
        evicted.forEach { it.destroy() }
    }

    private fun evictIdleEngines() {
        val now = System.currentTimeMillis()
        val toDestroy = mutableListOf<Engine>()
        synchronized(poolMutex) {
            pool.entries.removeIf { (_, engine) ->
                if (!engine.inUse.get() && now - engine.lastUsed > POOL_TTL_MS) {
                    toDestroy.add(engine)
                    true
                } else {
                    false
                }
            }
        }
        toDestroy.forEach { it.destroy() }
    }

    private fun destroyEngine(engine: Engine, checkOutdated: Boolean) {
        if (checkOutdated && runCatching { engine.webview.isOutdated() }.getOrDefault(false)) {
            runCatching { context.toast(MR.strings.information_webview_outdated, Toast.LENGTH_LONG) }
        }
        engine.destroy()
    }

    private fun recordActivation(host: String): Boolean {
        val now = System.currentTimeMillis()
        val queue = activations.computeIfAbsent(host) { ArrayDeque() }
        synchronized(queue) {
            while (queue.isNotEmpty() && now - queue.first() > WARM_WINDOW_MS) queue.removeFirst()
            queue.addLast(now)
            return queue.size >= WARM_THRESHOLD
        }
    }

    // endregion

    // region Request serialization

    private fun Request.toFetchHeadersJson(): JSONObject {
        val json = JSONObject()
        for (name in headers.names()) {
            if (name.lowercase() in FORBIDDEN_FETCH_HEADERS) continue
            json.put(name, headers[name])
        }
        val body = body
        if (body is FormBody) {
            if (!json.has("Content-Type")) json.put("Content-Type", "application/x-www-form-urlencoded")
        } else if (body != null && !json.has("Content-Type")) {
            body.contentType()?.let { json.put("Content-Type", it.toString()) }
        }
        return json
    }

    /** Returns null when the request body can't be replayed through fetch. */
    private fun Request.toFetchBodyJson(): JSONObject? {
        val params = JSONObject()
        val body = body ?: return params

        return when (body) {
            is MultipartBody -> null
            is FormBody -> {
                val encoded = buildString {
                    for (i in 0 until body.size) {
                        if (isNotEmpty()) append('&')
                        append(body.encodedName(i))
                        append('=')
                        append(body.encodedValue(i))
                    }
                }
                params.put("body", encoded)
                params
            }
            else -> {
                if (body.isOneShot() || body.isDuplex()) return null
                val length = body.contentLength()
                if (length <= 0 || length > MAX_REQUEST_BODY_BYTES) return null
                try {
                    val buffer = Buffer()
                    body.writeTo(buffer)
                    params.put("bodyB64", Base64.encodeToString(buffer.readByteArray(), Base64.NO_WRAP))
                    params
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    // endregion

    // region Cloudflare helpers (same mechanics as CloudflareInterceptor)

    private fun clearance(url: HttpUrl): String? {
        return cookieManager.get(url).firstOrNull { it.name == "cf_clearance" }?.value
    }

    private fun isCleared(url: HttpUrl, previousClearance: String?): Boolean {
        val clearance = clearance(url)
        return !clearance.isNullOrBlank() && clearance != previousClearance
    }

    // An origin rule without a port only matches the scheme's default one
    private fun HttpUrl.origin(): String {
        return if (port == HttpUrl.defaultPort(scheme)) "$scheme://$host" else "$scheme://$host:$port"
    }

    // endregion

    private class FetchSession {
        val latch = CountDownLatch(1)
        val headers = ConcurrentHashMap<String, String>()
        val buffer = ByteArrayOutputStream()

        @Volatile var failed: String? = null

        @Volatile var status: Int = 0

        @Volatile var statusText: String = ""

        @Volatile var fetchStarted: Boolean = false

        @Volatile var done: Boolean = false

        fun fail(reason: String) {
            if (failed == null) {
                failed = reason
                latch.countDown()
            }
        }

        fun onChallengeAbort(event: String) {
            if (!fetchStarted) fail("challenge $event")
        }

        fun onMessage(raw: String) {
            val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return
            when (obj.optString("t")) {
                "head" -> {
                    status = obj.optInt("s")
                    statusText = obj.optString("m")
                    val h = obj.optJSONObject("h")
                    if (h != null) {
                        for (key in h.keys()) headers[key.lowercase()] = h.optString(key)
                    }
                }
                "data" -> runCatching {
                    val bytes = Base64.decode(obj.optString("b"), Base64.NO_WRAP)
                    synchronized(buffer) { buffer.write(bytes) }
                }.onFailure { fail("chunk decode failed") }
                "end" -> {
                    done = true
                    latch.countDown()
                }
                "err" -> fail(obj.optString("m").ifBlank { "fetch error" })
            }
        }
    }

    private class Engine(
        val webview: WebView,
        val sessionRef: AtomicReference<FetchSession?>,
        val useIsolatedWorld: Boolean,
        var listenerScriptHandler: ScriptHandler?,
        val userAgent: String,
        @Volatile var loadedOrigin: String? = null,
        @Volatile var pooled: Boolean = false,
        @Volatile var lastUsed: Long = 0L,
    ) {
        val inUse = AtomicBoolean(false)

        fun destroy() {
            try {
                webview.stopLoading()
            } catch (_: Throwable) {}
            try {
                if (useIsolatedWorld) {
                    WebViewCompat.removeWebMessageListener(
                        webview,
                        WebViewCompat.getExecutionWorld(webview, BRIDGE_NAME),
                        BRIDGE_NAME,
                    )
                } else {
                    webview.removeJavascriptInterface(BRIDGE_NAME)
                }
            } catch (_: Throwable) {}
            try {
                listenerScriptHandler?.remove()
            } catch (_: Throwable) {}
            try {
                webview.destroy()
            } catch (_: Throwable) {}
        }
    }
}

private const val BRIDGE_NAME = "mihonfix"
private val FALLBACK_CODES = setOf(403, 503)
private val SUPPORTED_METHODS = setOf("GET", "POST")
private const val FALLBACK_HEADER = "X-MihonFix-Webview-Fallback"
private const val MAX_RESPONSE_BYTES = 25L * 1024 * 1024
private const val MAX_REQUEST_BODY_BYTES = 2L * 1024 * 1024
private const val OP_TIMEOUT_SECONDS = 60L

// Max time waiting for a challenge to auto-solve before running the fetch anyway
private const val NAV_MAX_WAIT_MS = 20_000L

private const val COOLDOWN_MS = 10L * 60_000L
private const val WARM_WINDOW_MS = 10L * 60_000L
private const val WARM_THRESHOLD = 2
private const val POOL_TTL_MS = 2L * 60_000L
private const val POOL_MAX = 2
private const val POOL_SWEEP_INTERVAL_MS = 30_000L

// Hop-by-hop / transport-level headers that don't apply to the reassembled response
private val STRIPPED_RESPONSE_HEADERS = setOf(
    "content-encoding",
    "content-length",
    "transfer-encoding",
    "connection",
    "keep-alive",
    "set-cookie",
)

// Headers the fetch API forbids or that would contradict the browser's own identity
private val FORBIDDEN_FETCH_HEADERS = setOf(
    "user-agent", "cookie", "cookie2", "host", "content-length", "origin", "referer",
    "accept-encoding", "connection", "keep-alive", "transfer-encoding", "upgrade",
    "expect", "te", "trailer", "via", "date", "set-cookie", "proxy-connection",
)

private val challengeListenerScript = """
    addEventListener("message", ({data}) => {
        if (data?.source === "cloudflare-challenge") {
            mihonfix?.postMessage(data.event);
        }
    })
""".trimIndent()

private val fetchScriptTemplate = """
    (function() {
        var P = __PARAMS__;
        (async function() {
            try {
                var init = {
                    method: P.method,
                    credentials: "include",
                    redirect: "follow",
                    headers: P.headers || {}
                };
                if (P.bodyB64) {
                    init.body = Uint8Array.from(atob(P.bodyB64));
                } else if (P.body !== null && P.body !== undefined) {
                    init.body = P.body;
                }
                var resp = await fetch(P.url, init);
                var h = {};
                resp.headers.forEach(function(v, k) { h[k] = v; });
                mihonfix.postMessage(JSON.stringify({ t: "head", s: resp.status, m: resp.statusText || "", u: resp.url, h: h }));
                if (!resp.body) {
                    mihonfix.postMessage(JSON.stringify({ t: "end" }));
                    return;
                }
                var reader = resp.body.getReader();
                var total = 0;
                for (;;) {
                    var chunk = await reader.read();
                    if (chunk.done) break;
                    total += chunk.value.byteLength;
                    if (total > __MAX_BYTES__) {
                        try { reader.cancel(); } catch (e) {}
                        mihonfix.postMessage(JSON.stringify({ t: "err", m: "response too large" }));
                        return;
                    }
                    var bin = "";
                    var CH = 0x8000;
                    for (var i = 0; i < chunk.value.length; i += CH) {
                        bin += String.fromCharCode.apply(null, chunk.value.subarray(i, Math.min(i + CH, chunk.value.length)));
                    }
                    mihonfix.postMessage(JSON.stringify({ t: "data", b: btoa(bin) }));
                }
                mihonfix.postMessage(JSON.stringify({ t: "end" }));
            } catch (e) {
                mihonfix.postMessage(JSON.stringify({ t: "err", m: String(e && e.message ? e.message : e) }));
            }
        })();
    })();
""".trimIndent()

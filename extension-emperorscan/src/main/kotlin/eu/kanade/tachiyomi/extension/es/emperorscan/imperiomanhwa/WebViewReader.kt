package eu.kanade.tachiyomi.extension.es.emperorscan.imperiomanhwa

import android.annotation.SuppressLint
import android.app.Application
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

/**
 * Headless-WebView executor for `assets/reader.js`.
 *
 * The site's reader pages (`/manga/<slug>/<chapterId>/`) ship a Cloudflare
 * Turnstile-gated image manifest. The site's own reader module (loaded from
 * `/build/assets/reader.<hash>.js` via `<script type=module>`) knows how to
 * decrypt it, but expects a browser context with `window.turnstile`
 * available.
 *
 * Execution model (mirrors the upstream APK):
 *   1. Load an empty HTML document in a headless WebView whose base URL is
 *      the site, so the dynamic `import()` of the reader module resolves.
 *   2. `window.turnstile` is shimmed inside reader.js: with a cached token
 *      the challenge auto-solves; without one the sitekey is surfaced back
 *      to Kotlin through the [ReaderResult].
 *   3. `await import(entryUrl)` → `openReader(...)` → `fetchManifest(...)`.
 *   4. The result is postMessage'd back over a
 *      [WebViewCompat.addWebMessageListener] bridge (the modern replacement
 *      for the reflection-based boundary interface the upstream APK uses).
 *
 * Upstream wraps failures in IOException; `execute` does the same so the
 * parse pipeline reports meaningful errors to the user.
 */
class WebViewReader(private val baseUrl: String) {

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun execute(input: ReaderInput, timeoutSeconds: Long = 30L): ReaderResult =
        withContext(Dispatchers.Main) {
            val response = CompletableDeferred<ReaderResult>()

            // Random-ish bridge name so a site-defined global can't collide
            // with it after the dynamic import lands.
            val bridgeName = "r" + input.hashCode().toString(36)
            val bootstrap = buildBootstrapScript(input, bridgeName)

            val app = Injekt.get<Application>()
            val webView = WebView(app).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.blockImages = true
                settings.useWideViewPort = false
                settings.loadWithOverviewMode = false
            }

            val listener = WebViewCompat.WebMessageListener { _, message, _, _, _ ->
                val payload = message.data
                val result = runCatching {
                    ImperioManhwa.json.decodeFromString(ReaderResult.serializer(), payload)
                }.getOrElse {
                    ReaderResult(error = "Reader reply was not valid: ${payload.take(200)}")
                }
                response.complete(result)
            }

            if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                WebViewCompat.addWebMessageListener(
                    webView,
                    bridgeName,
                    setOf(baseUrl),
                    listener,
                )
            } else {
                // Legacy fallback: addJavascriptInterface exposes a global
                // `bridgeName` with one method per @JavascriptInterface —
                // the bootstrap already calls `bridgeName.postMessage(...)`.
                @SuppressLint("AddJavascriptInterface")
                webView.addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun postMessage(payload: String) {
                            val result = runCatching {
                                ImperioManhwa.json.decodeFromString(ReaderResult.serializer(), payload)
                            }.getOrElse {
                                ReaderResult(error = "Reader reply was not valid: ${payload.take(200)}")
                            }
                            response.complete(result)
                        }
                    },
                    bridgeName,
                )
            }

            try {
                webView.loadDataWithBaseURL(baseUrl, bootstrap, "text/html", "UTF-8", null)
                withTimeout(timeoutSeconds.seconds) { response.await() }
            } catch (e: TimeoutCancellationException) {
                ReaderResult(error = "Reader WebView timed out after $timeoutSeconds s")
            } finally {
                runCatching {
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                        WebViewCompat.removeWebMessageListener(webView, bridgeName)
                    }
                }
                webView.destroy()
            }
        }

    /**
     * Builds the bootstrap HTML document. Inside a `<script type=module>`:
     *
     *   1. `reader.js` (an async arrow function taking the input) is read
     *      from assets and invoked with the serialized [ReaderInput].
     *   2. On success the pages array is posted back as
     *      `{ pages: [...] }`; on failure as
     *      `{ error, sitekey, action, cdata }` (matching the upstream
     *      reader.js postMessage contract).
     */
    private fun buildBootstrapScript(input: ReaderInput, bridgeName: String): String {
        val readerJs = javaClass.classLoader!!
            .getResourceAsStream("assets/reader.js")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: throw IOException("Missing asset: assets/reader.js")

        val inputJson = ImperioManhwa.json.encodeToString(ReaderInput.serializer(), input)
        val token = input.turnstileToken?.let { "\"$it\"" } ?: "null"

        val invocation = """
            (async () => {
                try {
                    const fn = $readerJs;
                    const input = $inputJson;
                    input.token = $token;
                    const pages = await fn(input);
                    ${bridgeName}.postMessage(JSON.stringify({ pages }));
                } catch (e) {
                    ${bridgeName}.postMessage(JSON.stringify({
                        error: String(e),
                        sitekey: e?.sitekey ?? null,
                        action: e?.action ?? null,
                        cdata: e?.cdata ?? null,
                    }));
                }
            })();
        """.trimIndent()

        return """
            <!doctype html>
            <html lang="es">
            <head><meta charset="utf-8"><title>Reader</title></head>
            <body>
                <script type="module">
                    $invocation
                </script>
            </body>
            </html>
        """.trimIndent()
    }
}

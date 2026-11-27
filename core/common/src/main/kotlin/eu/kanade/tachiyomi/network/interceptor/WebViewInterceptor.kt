package eu.kanade.tachiyomi.network.interceptor

import android.content.Context
import android.os.Build
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.WebViewUtil
import eu.kanade.tachiyomi.util.system.setDefaultSettings
import eu.kanade.tachiyomi.util.system.setUserAgent
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import tachiyomi.i18n.MR
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

abstract class WebViewInterceptor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val defaultUserAgentProvider: () -> String,
) : Interceptor {

    /**
     * When this is called, it initializes the WebView if it wasn't already. We use this to avoid
     * blocking the main thread too much. If used too often we could consider moving it to the
     * Application class.
     */
    private val initWebView by lazy {
        // Crashes on some devices. We skip this in some cases since the only impact is slower
        // WebView init in those rare cases.
        // See https://bugs.chromium.org/p/chromium/issues/detail?id=1279562
        if (DeviceUtil.isMiui || (Build.VERSION.SDK_INT == Build.VERSION_CODES.S && DeviceUtil.isSamsung)) {
            return@lazy
        }

        try {
            WebSettings.getDefaultUserAgent(context)
        } catch (_: Exception) {
            // Avoid some crashes like when Chrome/WebView is being updated.
        }
    }

    abstract fun shouldIntercept(response: Response): Boolean

    abstract fun intercept(chain: Interceptor.Chain, request: Request, response: Response): Response

    /**
     * W2 hook: decide whether a network exception thrown by the first OkHttp attempt should
     * get a WebView second try. The base class never triggers on exceptions (historical
     * behavior); the WebView fetch fallback overrides it to widen the trigger to "all
     * failures". If the hook says no (or the WebView attempt yields nothing) the ORIGINAL
     * exception is rethrown — errors are never swallowed.
     */
    protected open fun shouldInterceptException(request: Request, cause: IOException): Boolean = false

    /**
     * W2 hook: run the WebView attempt for a request whose first try threw. Returns null when
     * the fallback cannot produce a response (the original exception then propagates).
     */
    protected open fun interceptException(chain: Interceptor.Chain, request: Request, cause: IOException): Response? = null

    final override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = try {
            chain.proceed(request)
        } catch (e: IOException) {
            // W2: network exceptions may get a WebView second try before propagating
            if (!shouldInterceptException(request, e)) throw e
            if (!prepareWebView()) throw e
            interceptException(chain, request, e) ?: throw e
        }
        if (!shouldIntercept(response)) {
            return response
        }

        if (!prepareWebView()) return response

        return intercept(chain, request, response)
    }

    /**
     * Returns whether a WebView can be used, initializing it if so.
     */
    protected fun prepareWebView(): Boolean {
        if (!WebViewUtil.supportsWebView(context)) {
            scope.launch(Dispatchers.Main) {
                context.toast(MR.strings.information_webview_required, Toast.LENGTH_LONG)
            }
            return false
        }
        initWebView
        return true
    }

    fun CountDownLatch.awaitFor30Seconds() {
        await(30, TimeUnit.SECONDS)
    }

    fun createWebView(request: Request): WebView {
        return WebView(context).apply {
            setDefaultSettings()
            // Avoid sending empty User-Agent, Chromium WebView will reset to default if empty
            setUserAgent(request.header("User-Agent") ?: defaultUserAgentProvider())
        }
    }
}

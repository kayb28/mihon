package eu.kanade.tachiyomi.network.interceptor

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Behavior tests for the WebView fallback decision layer (PROMPT 7, behaviors W1-W5).
 * Red state: they run against the audited baseline (whitelist {403, 503}, exceptions never
 * trigger, connectivity ignored, cooldown fixed at 10 minutes, no usage report) and fail on
 * BEHAVIOR, one test group per behavior ID.
 *
 * These are pure JVM tests: WebView/OkHttp stay out of the picture, only the policy's
 * decisions are exercised (the interceptor wiring is device-validated per the merge gate).
 */
class WebViewFallbackPolicyTest {

    private class ControlledClock {
        var now = 1_000_000L
        fun tick(ms: Long) {
            now += ms
        }
    }

    private fun policy(clock: ControlledClock) = WebViewFallbackPolicy(clock = { clock.now })

    // region W1 — any HTTP error code triggers the WebView attempt

    @Test
    fun `W1 any 4xx code triggers the fallback`() {
        val c = ControlledClock()
        val p = policy(c)
        for (code in listOf(400, 401, 403, 404, 410, 418, 429)) {
            p.shouldAttempt("host.example", "GET", statusCode = code) shouldBe true
        }
    }

    @Test
    fun `W1 any 5xx code triggers the fallback`() {
        val c = ControlledClock()
        val p = policy(c)
        for (code in listOf(500, 502, 503, 520, 524)) {
            p.shouldAttempt("host.example", "GET", statusCode = code) shouldBe true
        }
    }

    @Test
    fun `W1 success and redirect codes do not trigger`() {
        val c = ControlledClock()
        val p = policy(c)
        for (code in listOf(200, 204, 301, 304)) {
            p.shouldAttempt("host.example", "GET", statusCode = code) shouldBe false
        }
    }

    @Test
    fun `W1 codes that already triggered keep triggering`() {
        val c = ControlledClock()
        val p = policy(c)
        for (code in listOf(403, 503)) {
            p.shouldAttempt("host.example", "GET", statusCode = code) shouldBe true
        }
    }

    // endregion

    // region W2 — the exception branch triggers; failures propagate

    @Test
    fun `W2 a network exception triggers the fallback`() {
        val c = ControlledClock()
        val p = policy(c)
        p.shouldAttempt("host.example", "GET", cause = IOException("connection reset")) shouldBe true
    }

    @Test
    fun `W2 a socket timeout triggers the fallback`() {
        val c = ControlledClock()
        val p = policy(c)
        p.shouldAttempt("host.example", "GET", cause = SocketTimeoutException("read timed out")) shouldBe true
    }

    @Test
    fun `W2 non-network exceptions do not trigger the fallback`() {
        val c = ControlledClock()
        val p = policy(c)
        p.shouldAttempt("host.example", "GET", cause = IllegalStateException("bug")) shouldBe false
    }

    // endregion

    // region W3 — smart skip: with no connectivity the WebView attempt never happens

    @Test
    fun `W3 an HTTP error without connectivity does not trigger`() {
        val c = ControlledClock()
        val p = policy(c)
        p.shouldAttempt("host.example", "GET", statusCode = 403, hasConnectivity = false) shouldBe false
    }

    @Test
    fun `W3 a network exception without connectivity does not trigger`() {
        val c = ControlledClock()
        val p = policy(c)
        p.shouldAttempt(
            "host.example",
            "GET",
            cause = IOException("airplane mode"),
            hasConnectivity = false,
        ) shouldBe false
    }

    @Test
    fun `W3 with connectivity the attempt is still allowed`() {
        val c = ControlledClock()
        val p = policy(c)
        p.shouldAttempt("host.example", "GET", statusCode = 403, hasConnectivity = true) shouldBe true
    }

    // endregion

    // region W4 — guards intact and configurable: 1 attempt per request (structural),
    // per-host cooldown active, no WebView parade on a dead host

    @Test
    fun `W4 cooldown is active by default and conservative`() {
        val p = WebViewFallbackPolicy()
        p.cooldownMs shouldBe WebViewFallbackPolicy.DEFAULT_COOLDOWN_MS
        WebViewFallbackPolicy.DEFAULT_COOLDOWN_MS shouldBe 10L * 60_000L
    }

    @Test
    fun `W4 N failing requests to a dead host do not parade WebViews`() {
        val c = ControlledClock()
        val p = policy(c)
        var attempts = 0
        // N requests fail with a non-whitelisted code (the W1 case); each one may attempt the
        // WebView once, but after a failed attempt the host must be on cooldown
        repeat(5) {
            if (p.shouldAttempt("dead.example", "GET", statusCode = 500)) {
                attempts++
                p.recordResult("dead.example", success = false, statusCode = 500)
            }
        }
        attempts shouldBe 1
        // still on cooldown right after the failure
        p.shouldAttempt("dead.example", "GET", statusCode = 500) shouldBe false
        // and after the cooldown elapses, exactly one more attempt is allowed
        c.tick(p.cooldownMs + 1)
        p.shouldAttempt("dead.example", "GET", statusCode = 500) shouldBe true
    }

    @Test
    fun `W4 the cooldown is configurable`() {
        val c = ControlledClock()
        val p = policy(c)
        p.cooldownMs = 500L
        p.recordResult("dead.example", success = false, statusCode = 500)
        p.shouldAttempt("dead.example", "GET", statusCode = 500) shouldBe false
        c.tick(501L)
        p.shouldAttempt("dead.example", "GET", statusCode = 500) shouldBe true
    }

    @Test
    fun `W4 a successful attempt does not start the cooldown`() {
        val c = ControlledClock()
        val p = policy(c)
        p.recordResult("ok.example", success = true, statusCode = 200)
        p.shouldAttempt("ok.example", "GET", statusCode = 503) shouldBe true
    }

    @Test
    fun `W4 unsupported methods never trigger`() {
        val c = ControlledClock()
        val p = policy(c)
        p.shouldAttempt("host.example", "DELETE", statusCode = 404) shouldBe false
        p.shouldAttempt("host.example", "HEAD", statusCode = 404) shouldBe false
        p.shouldAttempt("host.example", "PUT", statusCode = 404) shouldBe false
    }

    @Test
    fun `W4 distinct hosts do not share the cooldown`() {
        val c = ControlledClock()
        val p = policy(c)
        p.recordResult("dead.example", success = false, statusCode = 500)
        p.shouldAttempt("other.example", "GET", statusCode = 500) shouldBe true
    }

    // endregion

    // region W5 — per-host "fallback used / result" report for the problem-source reports

    @Test
    fun `W5 the aggregator summarizes attempts per host`() {
        val p = WebViewFallbackPolicy()
        p.recordResult("a.example", success = true, statusCode = 403)
        p.recordResult("a.example", success = true, statusCode = 403)
        p.recordResult("a.example", success = false, statusCode = 500, cause = IOException("reset"))
        p.recordResult("b.example", success = false, statusCode = null, cause = SocketTimeoutException("t/o"))

        val report = p.usageReport()
        report.shouldHaveSize(2)

        val a = report.first { it.host == "a.example" }
        a.attempts shouldBe 3
        a.successes shouldBe 2
        a.failures shouldBe 1
        a.lastSuccess shouldBe false
        a.lastStatusCode shouldBe 500
        a.lastErrorKind.shouldBeInstanceOf<String>()

        val b = report.first { it.host == "b.example" }
        b.attempts shouldBe 1
        b.successes shouldBe 0
        b.failures shouldBe 1
        b.lastSuccess shouldBe false
        b.lastStatusCode shouldBe null
    }

    @Test
    fun `W5 a host without attempts does not appear in the report`() {
        val p = WebViewFallbackPolicy()
        p.shouldAttempt("never-tried.example", "GET", statusCode = 403)
        p.usageReport().shouldHaveSize(0)
    }

    // endregion
}

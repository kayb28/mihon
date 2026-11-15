package eu.kanade.tachiyomi.network.interceptor

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import java.util.Base64

/**
 * Regression tests for the WebView fetch fallback session protocol (the Kotlin side of the
 * page-world bridge). These mirror the acceptance criteria of the audit report section 3.3:
 * out-of-sequence, duplicated or post-close messages are rejected, an invalid sequence from
 * the session's own key (including a status outside 100–599 or a non-numeric one) fails the
 * session immediately (releasing the latch) instead of stalling the caller until the
 * operation timeout; the response size is capped on the Kotlin side; malformed messages
 * can't corrupt the session.
 *
 * The fetch script side (conversion of base64 POST bodies, message keying) is exercised by
 * scripts/test_webview_fetch_script.js, which runs the real template extracted from the
 * interceptor source.
 */
class FetchSessionProtocolTest {

    private fun session() = FetchSession(maxBytes = 1024, keyGenerator = { "test-key" })

    private fun head(status: Int = 200, headers: Map<String, String> = emptyMap(), key: String = "test-key") =
        """{"t":"head","k":"$key","s":$status,"m":"OK","h":${headers.entries.joinToString(",", "{", "}") { """"${it.key}":"${it.value}"""" }}}"""

    private fun data(bytes: ByteArray, key: String = "test-key") =
        """{"t":"data","k":"$key","b":"${Base64.getEncoder().encodeToString(bytes)}"}"""

    private fun end(key: String = "test-key") = """{"t":"end","k":"$key"}"""

    private fun err(message: String, key: String = "test-key") = """{"t":"err","k":"$key","m":"$message"}"""

    @Test
    fun `head data end round trip assembles the body`() {
        val s = session()
        val first = "hola ".toByteArray()
        // Non-ASCII byte sequence: UTF-8 é (0xC3 0xA9), a raw 0xFF and a NUL — exercises the
        // exact byte-preservation that the atob/charCodeAt fix in the script guarantees
        val second = byteArrayOf(0xC3.toByte(), 0xA9.toByte(), 0xFF.toByte(), 0x00.toByte())
        s.onMessage(head(201, mapOf("Content-Type" to "application/json"))) shouldBe true
        s.onMessage(data(first)) shouldBe true
        s.onMessage(data(second)) shouldBe true
        s.onMessage(end()) shouldBe true
        s.status shouldBe 201
        s.statusText shouldBe "OK"
        s.headers["content-type"] shouldBe "application/json"
        s.done shouldBe true
        s.failed shouldBe null
        s.body() shouldBe first + second
        s.latch.count shouldBe 0L // normal completion releases the caller
    }

    @Test
    fun `message with a foreign session key is rejected`() {
        val s = session()
        s.onMessage(head(key = "another-key")) shouldBe false
        s.onMessage(data("x".toByteArray(), key = "another-key")) shouldBe false
        s.status shouldBe 0
        s.done shouldBe false
        s.failed shouldBe null
    }

    @Test
    fun `messages after end are rejected`() {
        val s = session()
        s.onMessage(head()) shouldBe true
        s.onMessage(end()) shouldBe true
        s.onMessage(data("late".toByteArray())) shouldBe false
        s.done shouldBe true
        s.body() shouldBe ByteArray(0)
    }

    @Test
    fun `messages after failure are rejected`() {
        val s = session()
        s.onMessage(head()) shouldBe true
        s.onMessage(err("boom")) shouldBe true
        s.failed shouldBe "boom"
        s.onMessage(data("late".toByteArray())) shouldBe false
        s.onMessage(end()) shouldBe false
    }

    @Test
    fun `duplicated head fails the session without overwriting the first status`() {
        val s = session()
        s.onMessage(head(200)) shouldBe true
        s.onMessage(head(500)) shouldBe false
        s.status shouldBe 200
        s.failed shouldBe "duplicate head"
        s.latch.count shouldBe 0L // no silent rejection that would stall the caller
    }

    @Test
    fun `head without status fails the session`() {
        val s = session()
        // A head with no "s" field must not leave status at 0 (which would let a later
        // head overwrite the metadata of a session that believes it has none)
        s.onMessage("""{"t":"head","k":"test-key","m":"OK","h":{}}""") shouldBe false
        s.failed shouldBe "head without status"
        s.latch.count shouldBe 0L
    }

    @Test
    fun `head status outside 100-599 fails the session`() {
        for (bad in intArrayOf(99, 600, -1, 9999)) {
            val s = session()
            s.onMessage(head(bad)) shouldBe false
            s.failed shouldBe "head with invalid status"
            s.status shouldBe 0
            s.done shouldBe false
            s.latch.count shouldBe 0L
        }
    }

    @Test
    fun `head with non-numeric status fails the session`() {
        val s = session()
        // optInt() maps a non-numeric "s" to 0, so it takes the same "no status" path
        s.onMessage("""{"t":"head","k":"test-key","s":"abc","m":"OK"}""") shouldBe false
        s.failed shouldBe "head without status"
        s.latch.count shouldBe 0L
    }

    @Test
    fun `data before head fails the session immediately`() {
        val s = session()
        s.onMessage(data("early".toByteArray())) shouldBe false
        s.failed shouldBe "data before head"
        s.latch.count shouldBe 0L // caller wakes up with a controlled error, not a timeout
        // the failed session accepts nothing else
        s.onMessage(head()) shouldBe false
        s.status shouldBe 0
        s.done shouldBe false
    }

    @Test
    fun `end before head fails the session immediately`() {
        val s = session()
        s.onMessage(end()) shouldBe false
        s.failed shouldBe "end before head"
        s.latch.count shouldBe 0L
        s.done shouldBe false
        s.body() shouldBe null
    }

    @Test
    fun `oversized response is capped on the Kotlin side`() {
        val s = session()
        s.onMessage(head()) shouldBe true
        // maxBytes = 1024; a single 2KB chunk must be refused and fail the session
        s.onMessage(data(ByteArray(2048))) shouldBe false
        s.failed shouldBe "response too large"
        s.body() shouldBe null
    }

    @Test
    fun `malformed json does not corrupt the session`() {
        val s = session()
        s.onMessage("no-es-json{{") shouldBe false
        s.onMessage(null) shouldBe false
        s.failed shouldBe null
        s.onMessage(head()) shouldBe true
    }

    @Test
    fun `invalid base64 chunk fails the session in a controlled way`() {
        val s = session()
        s.onMessage(head()) shouldBe true
        s.onMessage("""{"t":"data","k":"test-key","b":"!!!not-base64!!!"}""") shouldBe false
        s.failed shouldBe "chunk decode failed"
    }

    @Test
    fun `challenge abort before fetch start fails the session`() {
        val s = session()
        s.onChallengeAbort("interactiveBegin")
        s.failed shouldBe "challenge interactiveBegin"
        s.onChallengeAbort("fail") // second abort doesn't overwrite
        s.failed shouldBe "challenge interactiveBegin"
    }

    @Test
    fun `challenge abort after fetch start is ignored`() {
        val s = session()
        s.fetchStarted = true
        s.onChallengeAbort("interactiveBegin")
        s.failed shouldBe null
    }

    @Test
    fun `empty error message falls back to a generic reason`() {
        val s = session()
        s.onMessage(err("")) shouldBe true
        s.failed shouldBe "fetch error"
    }

    @Test
    fun `each session gets a distinct key`() {
        val a = FetchSession(maxBytes = 1024)
        val b = FetchSession(maxBytes = 1024)
        a.key shouldNotBe b.key
    }
}

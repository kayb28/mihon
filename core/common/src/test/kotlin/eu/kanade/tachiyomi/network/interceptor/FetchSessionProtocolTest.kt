package eu.kanade.tachiyomi.network.interceptor

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import java.util.Base64

/**
 * Regression tests for the WebView fetch fallback session protocol (the Kotlin side of the
 * page-world bridge). These mirror the acceptance criteria of the audit report section 3.3:
 * out-of-sequence, duplicated or post-close messages are rejected; the response size is
 * capped on the Kotlin side; malformed messages can't corrupt the session.
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
        s.onMessage(head(201, mapOf("Content-Type" to "application/json"))) shouldBe true
        s.onMessage(data("hola ".toByteArray())) shouldBe true
        s.onMessage(data(byteArrayOf(0xC3.toByte(), 0xA9.toByte(), 0xFF.toByte(), 0x00.toByte()))) shouldBe true
        s.onMessage(end()) shouldBe true
        s.status shouldBe 201
        s.statusText shouldBe "OK"
        s.headers["content-type"] shouldBe "application/json"
        s.done shouldBe true
        s.failed shouldBe null
        s.body() shouldBe "hola é\u00FF\u0000".toByteArray(Charsets.ISO_8859_1)
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
    fun `duplicated head does not overwrite the first status`() {
        val s = session()
        s.onMessage(head(200)) shouldBe true
        s.onMessage(head(500)) shouldBe false
        s.status shouldBe 200
    }

    @Test
    fun `data before head is buffered but blocks any later head`() {
        val s = session()
        s.onMessage(data("early".toByteArray())) shouldBe true
        // A stray data chunk (no head yet) is kept, but it permanently blocks the head
        // slot: a late head must not overwrite the session state anymore
        s.onMessage(head()) shouldBe false
        s.status shouldBe 0
    }

    @Test
    fun `end without head is rejected`() {
        val s = session()
        s.onMessage(end()) shouldBe false
        s.done shouldBe false
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

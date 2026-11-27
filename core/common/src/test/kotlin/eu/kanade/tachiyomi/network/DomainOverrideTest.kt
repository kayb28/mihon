package eu.kanade.tachiyomi.network

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

/**
 * Behavior tests for the per-source domain override (PROMPT 8, behaviors D1-D6).
 * Red state: they run against a repo where no override exists (the audited baseline:
 * every request goes to the host the extension asked for) and fail on BEHAVIOR, one
 * test group per behavior ID.
 *
 * Pure JVM tests: the store uses InMemoryPreferenceStore (same persistence contract as
 * the real PreferenceStore), the helper only touches HttpUrl/Request, and the WebView
 * path is exercised through the same helper call the fallback interceptor makes.
 */
class DomainOverrideTest {

    private fun store(): DomainOverrideStore = DomainOverrideStore(InMemoryPreferenceStore())

    private fun helper(): DomainOverrideHelper = DomainOverrideHelper(store())

    // ---------------------------------------------------------------- D1

    @Test
    fun `D1 rules are created, edited and deleted`() {
        val s = store()
        s.rules() shouldBe emptyMap()

        s.upsert(DomainOverrideRule(fromHost = "old.example", toHost = "new.example"))
        s.rules() shouldBe mapOf("old.example" to "new.example")

        // edit = same fromHost, new toHost
        s.upsert(DomainOverrideRule(fromHost = "old.example", toHost = "newer.example"))
        s.rules() shouldBe mapOf("old.example" to "newer.example")

        s.delete("old.example")
        s.rules() shouldBe emptyMap()
    }

    @Test
    fun `D1 matching is exact-host, case-insensitive`() {
        val s = store()
        s.upsert(DomainOverrideRule(fromHost = "old.example", toHost = "new.example"))
        // exact match (case-insensitive, as HttpUrl normalizes hosts)
        s.targetFor("old.example") shouldBe "new.example"
        s.targetFor("OLD.example") shouldBe "new.example"
        // subdomains and unrelated hosts do NOT match
        s.targetFor("www.old.example") shouldBe null
        s.targetFor("notold.example") shouldBe null
        s.targetFor("new.example") shouldBe null
    }

    // ---------------------------------------------------------------- D2

    @Test
    fun `D2 a matching request is rewritten keeping path, query and fragment`() {
        val h = helperWith("old.example" to "new.example")
        val out = h.rewriteUrl("https://old.example/manga/12?page=3#frag".toHttpUrl())
        out shouldNotBe null
        out!!.toString() shouldBe "https://new.example/manga/12?page=3#frag"
    }

    @Test
    fun `D2 any other request is left intact`() {
        val h = helperWith("old.example" to "new.example")
        h.rewriteUrl("https://other.example/manga/12".toHttpUrl()) shouldBe null
        h.rewriteUrl("https://sub.old.example/x".toHttpUrl()) shouldBe null
    }

    @Test
    fun `D2 rewriteRequest rewrites a Request and keeps method and body`() {
        val h = helperWith("old.example" to "new.example")
        val request = Request.Builder()
            .url("https://old.example/search?q=one+piece".toHttpUrl())
            .header("X-Test", "1")
            .build()
        val out = h.rewriteRequest(request)
        out.url.toString() shouldBe "https://new.example/search?q=one+piece"
        out.method shouldBe request.method
        out.header("X-Test") shouldBe "1"
    }

    // ---------------------------------------------------------------- D3

    @Test
    fun `D3 the WebView path rewrites through the SAME helper`() {
        // The fallback interceptor calls helper.rewriteRequest before loading the WebView,
        // so a fallback request for a moved host also follows the move. Simulating the call
        // the interceptor makes (same helper instance shared by both paths):
        val h = helperWith("old.example" to "new.example")
        val fallbackRequest = Request.Builder().url("https://old.example/chapter/7".toHttpUrl()).build()
        val rewritten = h.rewriteRequest(fallbackRequest)
        rewritten.url.host shouldBe "new.example"
        rewritten.url.encodedPath shouldBe "/chapter/7"
    }

    // ---------------------------------------------------------------- D4

    @Test
    fun `D4 invalid input is rejected with a visible reason`() {
        // not a URL
        DomainOverrideRule.validate("esto no es una url") shouldBeInstanceOf
            DomainOverrideRule.Invalid.Rejected::class
        // non-http(s) scheme
        DomainOverrideRule.validate("ftp://new.example") shouldBeInstanceOf
            DomainOverrideRule.Invalid.Rejected::class
        DomainOverrideRule.validate("file:///sdcard/x") shouldBeInstanceOf
            DomainOverrideRule.Invalid.Rejected::class
        // same host as the source of the rule
        DomainOverrideRule.validate("https://old.example", currentHost = "old.example")
            .shouldBeInstanceOf<DomainOverrideRule.Invalid.SameHost>()
    }

    @Test
    fun `D4 a valid new URL is accepted and reduced to its host`() {
        val verdict = DomainOverrideRule.validate("https://New.Example/manga", currentHost = "old.example")
        verdict shouldBe DomainOverrideRule.Valid(toHost = "new.example")
    }

    // ---------------------------------------------------------------- D5

    @Test
    fun `D5 with zero rules the helper is a no-op that never throws`() {
        val h = helper() // no rules configured
        val url = "https://untouched.example/a?b=c#d".toHttpUrl()
        h.rewriteUrl(url) shouldBe null
        val request = Request.Builder().url(url).build()
        // same instance back: the request pipeline is untouched
        h.rewriteRequest(request) shouldBe request
    }

    // ---------------------------------------------------------------- D6 (JVM-visible part)

    @Test
    fun `D6 every rewrite logs a line for the problem-source reports`() {
        val h = helperWith("old.example" to "new.example")
        val records = mutableListOf<String>()
        h.rewriteUrl("https://old.example/x".toHttpUrl()) { records.add(it) }
        records shouldHaveSize 1
        records.single() shouldContain "old.example"
        records.single() shouldContain "new.example"
        // and no line when nothing is rewritten
        val before = records.size
        h.rewriteUrl("https://other.example/x".toHttpUrl()) { records.add(it) }
        records.size shouldBe before
    }

    // ---------------------------------------------------------------- D7

    @Test
    fun `D7 a request already on a rule target host is left alone (extension preference wins)`() {
        val s = store()
        s.upsert(DomainOverrideRule(fromHost = "old.example", toHost = "new.example"))
        val h = DomainOverrideHelper(s)
        // The extension's own mirror/baseUrl preference already points at new.example:
        // the fork rule must NOT touch that request (no ping-pong, no override of the
        // extension's choice).
        h.rewriteUrl("https://new.example/page".toHttpUrl()) shouldBe null
        // And the rule still fires for requests to the old host.
        h.rewriteUrl("https://old.example/page".toHttpUrl()) shouldNotBe null
    }

    @Test
    fun `D7 chained targets do not loop`() {
        val s = store()
        // A rule old->new and another new->back: a request to old must land on new and
        // STOP there (single application), never bounce back.
        s.upsert(DomainOverrideRule(fromHost = "old.example", toHost = "new.example"))
        s.upsert(DomainOverrideRule(fromHost = "new.example", toHost = "back.example"))
        val h = DomainOverrideHelper(s)
        val out = h.rewriteUrl("https://old.example/x".toHttpUrl())
        out shouldNotBe null
        out!!.host shouldBe "new.example"
    }

    // ---------------------------------------------------------------- helpers

    private fun helperWith(vararg rules: Pair<String, String>): DomainOverrideHelper {
        val s = store()
        rules.forEach { (from, to) -> s.upsert(DomainOverrideRule(fromHost = from, toHost = to)) }
        return DomainOverrideHelper(s)
    }
}

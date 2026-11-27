package eu.kanade.tachiyomi.ui.browse.source

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Fallo G (PROMPT 27, T-G): the "Editar dominio" entry (D6) is wired only behind the
 * sources tab's long-press options dialog, which users cannot find. G1 adds a second,
 * discoverable entry: a "Editar dominio" section at the top of the app-hosted extension
 * details screen. The pure row model both entries share lives in [DomainRuleUi]: it
 * resolves each installed source's host from its baseUrl and formats the active rule
 * (from -> to) for display. Sources whose URL does not resolve to an http(s) host
 * produce no row — the section only lists sources a domain rule can actually apply to.
 */
class DomainRuleUiTest {

    // G1: the host resolution both entries rely on.
    @Test
    fun `G1 hostOf resolves the host of an http or https baseUrl`() {
        DomainRuleUi.hostOf("https://mangasite.com") shouldBe "mangasite.com"
        DomainRuleUi.hostOf("http://mangasite.com/path/page") shouldBe "mangasite.com"
        DomainRuleUi.hostOf("HTTPS://MANGASITE.COM") shouldBe "mangasite.com"
    }

    @Test
    fun `G1 hostOf rejects input without an http or https host`() {
        DomainRuleUi.hostOf(null).shouldBeNull()
        DomainRuleUi.hostOf("").shouldBeNull()
        DomainRuleUi.hostOf("not a url").shouldBeNull()
        DomainRuleUi.hostOf("ftp://mangasite.com").shouldBeNull()
    }

    // G1: the section's rows: one per online source with a resolvable host.
    @Test
    fun `G1 rows lists only sources whose baseUrl resolves to a host`() {
        val rows = DomainRuleUi.rows(
            sources = listOf(
                DomainRuleUi.SourceInput(sourceId = 1L, baseUrl = "https://mangasite.com"),
                DomainRuleUi.SourceInput(sourceId = 2L, baseUrl = "not a url"),
                DomainRuleUi.SourceInput(sourceId = 3L, baseUrl = null),
            ),
            targetFor = { null },
        )
        rows.map { it.sourceId } shouldBe listOf(1L)
        rows.single().host shouldBe "mangasite.com"
        rows.single().activeRule.shouldBeNull()
    }

    @Test
    fun `G1 rows lists the active rule formatted as from - to`() {
        val rows = DomainRuleUi.rows(
            sources = listOf(
                DomainRuleUi.SourceInput(sourceId = 1L, baseUrl = "https://old.example"),
            ),
            targetFor = { host -> if (host == "old.example") "new.example" else null },
        )
        rows.single().activeRule shouldBe "old.example -> new.example"
    }

    @Test
    fun `G1 rows keeps the source order`() {
        val rows = DomainRuleUi.rows(
            sources = listOf(
                DomainRuleUi.SourceInput(sourceId = 7L, baseUrl = "https://a.example"),
                DomainRuleUi.SourceInput(sourceId = 3L, baseUrl = "https://b.example"),
            ),
            targetFor = { null },
        )
        rows.map { it.sourceId } shouldBe listOf(7L, 3L)
    }

    // G2: the rule label is ONE formatting used by both entries (the sources tab's
    // options dialog and the extension details section) — no duplicate implementation.
    @Test
    fun `G2 activeRuleLabel is the shared formatting for both entries`() {
        DomainRuleUi.activeRuleLabel("old.example") { "new.example" } shouldBe "old.example -> new.example"
        DomainRuleUi.activeRuleLabel("old.example") { null }.shouldBeNull()
    }
}

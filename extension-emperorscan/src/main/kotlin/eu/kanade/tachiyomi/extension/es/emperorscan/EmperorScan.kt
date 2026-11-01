package eu.kanade.tachiyomi.extension.es.emperorscan

import eu.kanade.tachiyomi.extension.es.emperorscan.imperiomanhwa.ImperioManhwa

/**
 * Emperor Scan (https://imperiomanhwa.com) — Spanish manhwa site, NSFW.
 *
 * The site is a custom Astro + Inertia build (not WordPress/MangaThemesia):
 * every page ships a `<script data-page=app>` JSON payload, and the reader
 * decrypts its image manifest through the site's own JS module behind a
 * Cloudflare Turnstile gate (see [ImperioManhwa] / WebViewReader).
 *
 * Reconstructed from the previously shipped upstream APK
 * (Tachiyomi: Emperor Scan v1.6.74, keiyoushi `Generated` class) so the
 * configuration is 1:1 with upstream:
 *
 *   - baseUrl  = https://imperiomanhwa.com
 *   - lang     = "es"
 *   - name     = "Emperor Scan"
 *   - searchPath = "buscar"          → search URL is /buscar/?q=<query>
 *   - sourceId = 4562754350961964148L
 *
 * [sourceId] is what the upstream APK reported; keeping it means the user's
 * library entries, categories and read state survive the upgrade untouched.
 * A different id would look like a brand-new source and orphan everything.
 */
class EmperorScan : ImperioManhwa(
    baseUrl = "https://imperiomanhwa.com",
    lang = "es",
    name = "Emperor Scan",
    searchPath = "buscar",
    sourceId = 4562754350961964148L,
)

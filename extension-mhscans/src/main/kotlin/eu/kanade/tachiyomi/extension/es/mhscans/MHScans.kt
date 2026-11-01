package eu.kanade.tachiyomi.extension.es.mhscans

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SManga.Companion.CANCELLED
import eu.kanade.tachiyomi.source.model.SManga.Companion.COMPLETED
import eu.kanade.tachiyomi.source.model.SManga.Companion.ON_HIATUS
import eu.kanade.tachiyomi.source.model.SManga.Companion.ONGOING
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import okhttp3.HttpUrl
import okhttp3.Request
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * MHScans (https://mhscans.com) — Spanish scanlator site.
 *
 * The site is a custom Astro + Tailwind build (not WordPress/MangaThemesia), so this source
 * uses hand-picked selectors instead of any standard theme helper. Reconstructed from the
 * previously shipped APK (Tachiyomi: MHScans v1.6.71) by decompilation, then re-verified
 * against the live site so the same selectors still resolve.
 *
 * [versionId] is 2 — that's what the v1.6.71 APK used, and generateId hashes "mhscans/es/2"
 * into source id 9008444898589746680. Keeping it means the user's library entries, categories
 * and read state survive the upgrade untouched; a different id would look like a brand-new
 * source and orphan everything.
 *
 * URL layout:
 *  - Library / search:  /library?q=<query>&sort=<latest|updated|views|rating|title>&type=<t>&status=<s>&genre=<g>&page=<n>
 *  - Manga detail:      /comics/<slug>
 *  - Chapter reader:    /leer/<shortId>
 *
 * Selectors (verified against live HTML):
 *  - Manga card in library:  div.lib-grid > a[href^=/comics/]
 *  - Card title / cover:     p (first) / img[src]
 *  - Detail title:           .comic-hero h1
 *  - Detail cover:           .comic-hero__cover img
 *  - Detail description:     #comic-description
 *  - Genres:                 .comic-chips li
 *  - Status:                 .comic-meta__status
 *  - Chapter link:           a[href^=/leer/][data-chapter-num]
 *  - Chapter title / date:   [data-cl-title] / [data-cl-title] + div > span
 *  - Reader page:            img.page-img (with abs:data-src fallback for lazy-loading)
 */
class MHScans : HttpSource() {

    override val name = "MHScans"

    override val baseUrl = "https://mhscans.com"

    override val lang = "es"

    override val supportsLatest = true

    // v1.6.71's id: generateId(name, lang, versionId) on "mhscans/es/2".
    override val versionId: Int = 2

    // The host app's client (network.client) already carries the Cloudflare/soup interceptors
    // and cookie jar; only the Referer needs adding per request via headersBuilder.

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    /**
     * Stored urls are path ids ("<slug>" for manga, "<shortId>" for chapters), NOT paths —
     * the base HttpSource's default requests would build "https://mhscans.com<slug>" and fail.
     * Both the new getMangaUrl/getChapterUrl entry points and the legacy *Request overrides
     * are wired here so every flow (browse, details, updates, migration) hits real URLs.
     */
    override fun getMangaUrl(manga: SManga): String = "$baseUrl/comics/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/leer/${chapter.url}"

    private fun get(url: HttpUrl): Request = Request.Builder()
        .url(url)
        .headers(headers)
        .build()

    override fun mangaDetailsRequest(manga: SManga): Request =
        get(getMangaUrl(manga).toHttpUrl())

    override fun chapterListRequest(manga: SManga): Request =
        get(getMangaUrl(manga).toHttpUrl())

    override fun pageListRequest(chapter: SChapter): Request =
        get(getChapterUrl(chapter).toHttpUrl())

    // ---- Popular / Latest ----

    override fun popularMangaRequest(page: Int): Request =
        searchMangaRequest(page, "", FilterList(SortFilter("views")))

    override fun popularMangaParse(response: Response): MangasPage =
        searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int): Request =
        searchMangaRequest(page, "", FilterList(SortFilter("updated")))

    override fun latestUpdatesParse(response: Response): MangasPage =
        searchMangaParse(response)

    // ---- Search ----

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/library".toHttpUrl().newBuilder()
            .apply {
                if (query.isNotBlank()) addQueryParameter("q", query)
                filters.filterIsInstance<SortFilter>().firstOrNull()?.let { f ->
                    val sort = f.values[f.state].second
                    if (sort.isNotEmpty()) addQueryParameter("sort", sort)
                }
                filters.filterIsInstance<TypeFilter>().firstOrNull()?.let { f ->
                    val type = f.values[f.state].second
                    if (type.isNotEmpty()) addQueryParameter("type", type)
                }
                filters.filterIsInstance<StatusFilter>().firstOrNull()?.let { f ->
                    val status = f.values[f.state].second
                    if (status.isNotEmpty()) addQueryParameter("status", status)
                }
                filters.filterIsInstance<GenreFilter>().firstOrNull()?.let { f ->
                    val genre = f.values[f.state].second
                    if (genre.isNotEmpty()) addQueryParameter("genre", genre)
                }
                addQueryParameter("page", page.toString())
            }
            .build()
        return Request.Builder()
            .url(url)
            .headers(headers)
            .build()
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val cards = document.select("div.lib-grid > a[href^=/comics/]")
            .filterNot { card ->
                // Skip novel entries — the site mixes novels and comics in the same grid.
                card.selectFirst("div.comic-cover ~ span")?.text() == "Novel"
            }
        val mangas = cards.map { card ->
            SManga.create().apply {
                val absHref = card.absUrl("href")
                url = absHref.toHttpUrl().pathSegments[1]
                title = card.selectFirst("p")?.text()
                    ?: card.selectFirst("[data-cl-title]")?.text()
                    ?: card.text().lineSequence().firstOrNull().orEmpty()
                thumbnail_url = card.selectFirst("img")?.absUrl("src")
            }
        }
        val hasNext = document.selectFirst("nav.lib-pagination a[aria-label=Siguiente]") != null
        return MangasPage(mangas, hasNext)
    }

    // ---- Manga details ----

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            url = response.request.url.pathSegments[1]
            title = document.selectFirst(".comic-hero h1")?.text().orEmpty()
            thumbnail_url = document.selectFirst(".comic-hero__cover img")?.absUrl("src")
            description = document.selectFirst("#comic-description")?.text()
            genre = document.select(".comic-chips li").joinToString(", ") { it.text() }
            status = document.selectFirst(".comic-meta__status")?.text()
                ?.trim()
                ?.lowercase(Locale.ROOT)
                ?.let(::parseStatus)
                ?: SManga.UNKNOWN
        }
    }

    private fun parseStatus(text: String): Int = when (text) {
        "en emisión" -> ONGOING
        "completado" -> COMPLETED
        "en hiato" -> ON_HIATUS
        "cancelado", "cancelled" -> CANCELLED
        else -> SManga.UNKNOWN
    }

    // ---- Chapter list ----

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("a[href^=/leer/][data-chapter-num]").map { element ->
            SChapter.create().apply {
                url = element.absUrl("href").toHttpUrl().pathSegments[1]
                name = element.selectFirst("[data-cl-title]")?.text().orEmpty()
                chapter_number = element.attr("data-chapter-num").toFloatOrNull() ?: -1f
                date_upload = element.selectFirst("[data-cl-title] + div > span")
                    ?.text()
                    ?.let(::parseRelativeDate)
                    ?: 0L
            }
        }
    }

    private val relativeDateRegex = Regex("hace (\\d+) (\\p{L}+)")

    /**
     * Parses Spanish relative dates produced by the site ("hace 2 sem", "hace 3 días", ...).
     * Anchored to the current time, so the upload timestamp drifts slightly on each refresh —
     * acceptable for a scanlator source where the chapter's relative age is what the site shows.
     */
    private fun parseRelativeDate(text: String): Long {
        val match = relativeDateRegex.find(text.lowercase(Locale.ROOT)) ?: return 0L
        val amount = match.groupValues[1].toLong()
        val unit = match.groupValues[2]
        val chronoUnit = when {
            unit.startsWith("min") -> ChronoUnit.MINUTES
            unit.startsWith("h") -> ChronoUnit.HOURS
            unit.startsWith("d") -> ChronoUnit.DAYS
            unit.startsWith("sem") -> ChronoUnit.WEEKS
            unit.startsWith("mes") -> ChronoUnit.MONTHS
            unit.startsWith("a") -> ChronoUnit.YEARS
            else -> return 0L
        }
        return ZonedDateTime.now().minus(amount, chronoUnit).toInstant().toEpochMilli()
    }

    // ---- Pages ----

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("img.page-img").mapIndexed { index, img ->
            val src = img.attr("abs:data-src").takeIf { it.isNotBlank() }
                ?: img.absUrl("src")
            Page(index, imageUrl = src)
        }
    }

    override fun imageRequest(page: Page): Request =
        super.imageRequest(page).newBuilder()
            .header("Referer", "$baseUrl/")
            .build()

    override fun imageUrlParse(response: Response): String = ""

    // ---- Filters ----

    /**
     * Sort filter matching the site's `/library?sort=` parameter. The "latest" option
     * corresponds to the site's "Más reciente" default; "views" drives Popular and "updated"
     * drives Latest.
     */
    private class SortFilter(defaultSelection: String) : Filter.Select<Pair<String, String>>(
        "Ordenar por",
        arrayOf(
            "Más reciente" to "latest",
            "Actualizado" to "updated",
            "Más visto" to "views",
            "Mejor valorado" to "rating",
            "A-Z" to "title",
        ),
        arrayOf("latest", "updated", "views", "rating", "title").indexOf(defaultSelection).coerceAtLeast(0),
    )

    /**
     * Type filter — the site splits the catalog by Manga / Manhwa / Manhua / Novel / Doujinshi /
     * Other. Driven by the `?type=` query parameter, distinct from `?genre=` (a thematic tag
     * set). Not exposed by the v1.6.71 APK; added because the site supports it.
     */
    private class TypeFilter : Filter.Select<Pair<String, String>>(
        "Tipo",
        arrayOf(
            "Cualquiera" to "",
            "Manga" to "Manga",
            "Manhwa" to "Manhwa",
            "Manhua" to "Manhua",
            "Novel" to "Novel",
            "Doujinshi" to "Doujinshi",
            "Otro" to "Other",
        ),
    )

    /**
     * Status filter — mirrors the `?status=` query parameter the site exposes on /library.
     * Also not exposed by the v1.6.71 APK.
     */
    private class StatusFilter : Filter.Select<Pair<String, String>>(
        "Estado",
        arrayOf(
            "Cualquiera" to "",
            "En curso" to "Ongoing",
            "Completado" to "Completed",
            "En hiato" to "Hiatus",
            "Cancelado" to "Cancelled",
        ),
    )

    /**
     * Genre dropdown. The site exposes the available genres as `dd-opt` chips on /library; the
     * extension ships the fixed list observed on the live catalog so the filter is available
     * before the first network call (the v1.6.71 APK scraped it at runtime instead).
     */
    private class GenreFilter : Filter.Select<Pair<String, String>>(
        "Género",
        arrayOf(
            "Cualquiera" to "",
            "Acción" to "Acción",
            "Aventura" to "Aventura",
            "Artes Marciales" to "Artes Marciales",
            "Ciencia ficción" to "Ciencia ficción",
            "Comedia" to "Comedia",
            "Drama" to "Drama",
            "Fantasía" to "Fantasía",
            "Manhua" to "Manhua",
            "Manhwa" to "Manhwa",
            "Meian" to "Meian",
            "Recomendado" to "Recomendado",
            "Reencarnación" to "Reencarnación",
            "Shounen" to "Shounen",
            "Sistema" to "Sistema",
        ),
    )

    override fun getFilterList(): FilterList = FilterList(
        SortFilter("latest"),
        TypeFilter(),
        StatusFilter(),
        GenreFilter(),
    )
}

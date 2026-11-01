@file:Suppress("DEPRECATION") // Request/Parse API — matches :extension-mhscans and this fork's HttpSource
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package eu.kanade.tachiyomi.extension.es.emperorscan.imperiomanhwa

import android.content.Context
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneOffset
import java.util.Locale

/**
 * ImperioManhwa multisrc — Astro + Inertia-style site with a Cloudflare
 * Turnstile-gated reader.
 *
 * Every page is a thin HTML shell with a single
 * `<script data-page=app type="application/json">` payload. The payload is
 * an [InertiaPage] whose `props` vary by `component`:
 *
 * | component       | props        | used for                              |
 * |-----------------|--------------|---------------------------------------|
 * | `Home/…`        | HomeProps    | filter catalogs                       |
 * | `Manga/…`       | ListingProps | `/manga/`, `/buscar/?q=` listings     |
 * | `Series/…`      | SeriesProps  | `/manga/<slug>/` details + chapters   |
 * | `Reader/…`      | ReaderProps  | reader page (via WebView)             |
 * | `Reader/Locked` | (any)        | premium chapter (no pages)            |
 *
 * Series with more than one chapter page expose on-demand
 * `<mangaPath>/<chaptersPath>-N.json` endpoints (Astro pagination).
 *
 * Page lists are decrypted by the site's own reader module executed inside a
 * headless WebView ([WebViewReader]) with a `window.turnstile` shim.
 */
abstract class ImperioManhwa(
    final override val baseUrl: String,
    final override val lang: String,
    final override val name: String,
    /** Search path segment (Emperor Scan uses `"buscar"`). */
    private val searchPath: String,
    /**
     * Stable source identifier. MUST equal the id reported by the previously
     * installed APK (Emperor Scan: 4562754350961964148L) or Mihon treats the
     * install as a new source and orphans every library entry.
     */
    private val sourceId: Long,
) : HttpSource(), ConfigurableSource {

    override val id: Long get() = sourceId

    override val supportsLatest: Boolean = true

    // ──────────────────────────────────────────────────────────────────────
    // Headers / client
    // ──────────────────────────────────────────────────────────────────────

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")
        .set("Accept-Language", "es,en;q=0.8")

    // ──────────────────────────────────────────────────────────────────────
    // i18n + user preferences
    // ──────────────────────────────────────────────────────────────────────

    private val i18n by lazy { I18nBundle(javaClass.classLoader!!, lang) }

    /**
     * Source-scoped preferences, obtained via Injekt instead of the
     * `sourcePreferences()` helper: that top-level function compiles into the
     * host's `ConfigurableSourceKt` file-facade, which R8 is free to strip or
     * merge away in release builds (it did — NoClassDefFoundError on the
     * user's installed app). The file name matches the one the host's source
     * settings screen binds ("source_$id"), so the toggle set from Settings
     * and this getter share the same state.
     */
    private val preferences by lazy {
        Injekt.get<Context>().getSharedPreferences("source_$id", Context.MODE_PRIVATE)
    }

    private val hideLockedChapters: Boolean
        get() = preferences.getBoolean(KEY_HIDE_LOCKED, true)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = KEY_HIDE_LOCKED
            title = i18n.get("hide_locked_title")
            summary = i18n.get("hide_locked_summary")
            setDefaultValue(true)
            screen.addPreference(this)
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Request helpers
    // ──────────────────────────────────────────────────────────────────────

    private fun get(url: HttpUrl): Request = Request.Builder()
        .url(url)
        .headers(headers)
        .build()

    private fun get(url: String): Request = Request.Builder()
        .url(url)
        .headers(headers)
        .build()

    private fun baseUrlHttp(): HttpUrl = baseUrl.toHttpUrlOrNull()
        ?: throw IOException("Invalid baseUrl: $baseUrl")

    /** `/manga/` or `/manga/page/N/` plus the given filter query params. */
    private fun mangaListUrl(page: Int, queryParams: List<Pair<String, String>>): HttpUrl =
        baseUrlHttp().newBuilder()
            .addPathSegment("manga")
            .apply { if (page > 1) { addPathSegment("page"); addPathSegment(page.toString()) } }
            .addPathSegment("") // trailing slash — Astro expects it
            .apply { queryParams.forEach { (k, v) -> addQueryParameter(k, v) } }
            .build()

    /** `/buscar/?q=<query>&page=<N>`. */
    private fun searchUrl(page: Int, query: String): HttpUrl =
        baseUrlHttp().newBuilder()
            .addPathSegment(searchPath)
            .addPathSegment("") // trailing slash
            .addQueryParameter("q", query)
            .apply { if (page > 1) addQueryParameter("page", page.toString()) }
            .build()

    // ──────────────────────────────────────────────────────────────────────
    // Popular / Latest / Search — Request/Parse pairs
    // ──────────────────────────────────────────────────────────────────────

    override fun popularMangaRequest(page: Int): Request =
        get(mangaListUrl(page, listOf("orden" to "vistas")))

    override fun popularMangaParse(response: Response): MangasPage = parseListing(response)

    override fun latestUpdatesRequest(page: Int): Request =
        get(mangaListUrl(page, emptyList()))

    override fun latestUpdatesParse(response: Response): MangasPage = parseListing(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val trimmed = query.trim()

        // Pasted series URL → fetch it directly; searchMangaParse detects the
        // Series component and returns a single initialized entry.
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return get(trimmed)
        }

        return if (trimmed.isNotEmpty()) {
            get(searchUrl(page, trimmed))
        } else {
            get(mangaListUrl(page, Filters.collectQueryPairs(filters)))
        }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val (component, props) = parseInertiaPage(response)
        return when {
            component.startsWith("Series/") -> {
                val series = json.decodeFromJsonElement(SeriesProps.serializer(), props)
                MangasPage(listOf(series.series.toSManga(i18n)), false)
            }
            else -> parseListingProps(props)
        }
    }

    private fun parseListing(response: Response): MangasPage {
        val (_, props) = parseInertiaPage(response)
        return parseListingProps(props)
    }

    private fun parseListingProps(props: JsonElement): MangasPage {
        val listing = json.decodeFromJsonElement(ListingProps.serializer(), props)
        val mangas = listing.series.map { dto ->
            SManga.create().apply {
                url = dto.url
                title = dto.title
                thumbnail_url = dto.cover
            }
        }
        return MangasPage(mangas, listing.pagination.next != null)
    }

    // ──────────────────────────────────────────────────────────────────────
    // Details / Chapters
    // ──────────────────────────────────────────────────────────────────────

    override fun mangaDetailsRequest(manga: SManga): Request = get(baseUrl + manga.url)

    override fun mangaDetailsParse(response: Response): SManga =
        parseSeriesProps(response).series.toSManga(i18n)

    override fun chapterListRequest(manga: SManga): Request = get(baseUrl + manga.url)

    override fun chapterListParse(response: Response): List<SChapter> {
        val props = parseSeriesProps(response)
        val allChapters = if (props.pagination.last > 1) {
            props.chapters + fetchRemainingChapters(props)
        } else {
            props.chapters
        }
        val filtered = if (hideLockedChapters) allChapters.filter { !it.locked } else allChapters

        // Track the most recent ISO date seen so far — anchors the year for
        // chapters that only carry an altDate like "10 ene.".
        var reference: LocalDate? = null
        return filtered.map { dto ->
            dto.isoDate?.let {
                runCatching {
                    LocalDate.ofEpochDay(Instant.parse(it).toEpochMilli() / 86_400_000)
                }.getOrNull()?.let { reference = it }
            }
            dto.toSChapter(props.series.url, reference)
        }
    }

    /** Fetches chapter pages 2..N synchronously (runs on a background thread). */
    private fun fetchRemainingChapters(props: SeriesProps): List<ChapterDto> {
        val base = baseUrlHttp()
        val mangaPathUrl = base.resolve(props.series.url)
            ?: throw IOException("Cannot resolve mangaPath: ${props.series.url}")

        val out = ArrayList<ChapterDto>()
        for (n in 2..props.pagination.last) {
            val pageUrl = mangaPathUrl.resolve("${props.paths.chapters}-$n.json")
                ?: throw IOException("Cannot resolve chapter page URL for n=$n")
            client.newCall(get(pageUrl)).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code} fetching $pageUrl")
                val body = response.body.string()
                out += json.decodeFromString(ChapterListDto.serializer(), body).items
            }
        }
        return out
    }

    private fun parseSeriesProps(response: Response): SeriesProps {
        val (_, props) = parseInertiaPage(response)
        return json.decodeFromJsonElement(SeriesProps.serializer(), props)
    }

    private fun SeriesDto.toSManga(i18n: I18nBundle): SManga = SManga.create().apply {
        url = this@toSManga.url
        title = this@toSManga.title
        thumbnail_url = cover
        author = authors.takeIf { it.isNotEmpty() }?.joinToString(", ")
        artist = artists.takeIf { it.isNotEmpty() }?.joinToString(", ")
        description = buildString {
            this@toSManga.synopsis?.let { append(it) }
            if (altNames.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append(i18n.get("alt_names")).append(": ").append(altNames.joinToString(", "))
            }
        }.takeIf { it.isNotEmpty() }
        genre = buildList {
            type?.replaceFirstChar { it.titlecase() }?.let { add(it) }
            addAll(genres.map { it.name })
            addAll(tags.map { it.name })
        }.distinct().joinToString(", ")
        status = when (this@toSManga.status) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        initialized = true
    }

    private fun ChapterDto.toSChapter(mangaPath: String, reference: LocalDate?): SChapter =
        SChapter.create().apply {
            url = "$mangaPath$slug/"
            name = if (locked) "🔒 $label" else label
            date_upload = parseDate(reference)
        }

    /**
     * Chapter date resolver:
     *  - `isoDate` (ISO 8601) preferred.
     *  - else `altDate` like `"10 ene."` matched with
     *    `(\d{1,2}) (\p{L}{3})\p{L}*\.?` and resolved against the bilingual
     *    month table, picking the candidate year in `[refYear+1..refYear-1]`
     *    that is NOT more than 62 days after the reference date.
     */
    private fun ChapterDto.parseDate(reference: LocalDate?): Long {
        isoDate?.let { iso ->
            return runCatching { Instant.parse(iso).toEpochMilli() }.getOrElse { 0L }
        }
        val alt = altDate ?: return 0L
        val match = DATE_REGEX.matchEntire(alt) ?: return 0L
        val (dayStr, monthAbbr) = match.destructured
        val day = dayStr.toIntOrNull() ?: return 0L
        val month = MONTHS[monthAbbr.lowercase(Locale.ROOT)] ?: return 0L

        val ref = reference ?: LocalDate.now(ZoneOffset.UTC)
        val cutoff = ref.plusDays(62)
        val candidate = (ref.year + 1 downTo ref.year - 1)
            .map { MonthDay.of(month, day).atYear(it) }
            .firstOrNull { !it.isAfter(cutoff) }
            ?: return 0L
        return candidate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }

    // ──────────────────────────────────────────────────────────────────────
    // Pages — decrypted by the site's reader module in a headless WebView.
    // ──────────────────────────────────────────────────────────────────────

    override fun pageListRequest(chapter: SChapter): Request = get(chapterUrl(chapter))

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val script = document.selectFirst("script[data-page=app]")
            ?: return emptyList()
        val (component, propsJson) = parseInertia(script.data())
        if (component == "Reader/Locked") return emptyList()

        val props = json.decodeFromJsonElement(ReaderProps.serializer(), propsJson)
        val entryUrl = document
            .selectFirst("script[type=module][src*=build/assets]")
            ?.absUrl("src")
            ?.takeIf { it.isNotEmpty() }
            ?: throw IOException("Missing reader entry script on ${response.request.url}")

        val input = ReaderInput(
            entry = entryUrl,
            reader = props.reader,
            chapterId = props.chapter.id,
            seriesSlug = props.series.slug,
        )

        // runBlocking on the calling (background) thread; the WebView itself
        // dispatches to Main internally. withContext(IO) guards against a
        // main-thread caller deadlocking on Dispatchers.Main.
        val result = withBlockingIo { WebViewReader(baseUrl).execute(input) }

        result.error?.let { err ->
            val challenge = result.sitekey?.let { sk ->
                " (Turnstile sitekey=$sk, action=${result.action}, cdata=${result.cdata})"
            }.orEmpty()
            throw IOException("Reader error: $err$challenge")
        }

        val pages = result.pages ?: return emptyList()
        val siteHost = baseUrlHttp().host
        return pages.mapIndexed { index, dto ->
            val src = dto.src
                ?: dto.srcset.split(", ").firstOrNull()?.substringBefore(' ')
                ?: throw IOException("Page ${dto.n} has no resolvable URL")
            val resolved = src.toHttpUrlOrNull()
                ?: throw IOException("Page ${dto.n} URL is invalid: $src")
            if (resolved.host != siteHost && !resolved.host.endsWith(".$siteHost")) {
                throw IOException("Unexpected image host: ${resolved.host}")
            }
            Page(index, imageUrl = resolved.toString())
        }
    }

    private fun chapterUrl(chapter: SChapter): String {
        val raw = chapter.url
        return if (raw.startsWith("http")) raw else baseUrl + raw
    }

    override fun imageRequest(page: Page): Request =
        super.imageRequest(page).newBuilder()
            .header("Referer", "$baseUrl/")
            .build()

    // ──────────────────────────────────────────────────────────────────────
    // Inertia payload parsing
    // ──────────────────────────────────────────────────────────────────────

    private fun parseInertiaPage(response: Response): Pair<String, JsonElement> {
        val data = response.asJsoup()
            .selectFirst("script[data-page=app]")
            ?.data()
            ?: throw IOException("Missing <script data-page=app> on ${response.request.url}")
        return parseInertia(data)
    }

    private fun parseInertia(data: String): Pair<String, JsonElement> {
        val page = json.decodeFromString(
            InertiaPage.serializer(JsonElement.serializer()),
            data,
        )
        return page.component to page.props
    }

    // ──────────────────────────────────────────────────────────────────────
    // Filter list — cached from the home page's Inertia payload.
    // ──────────────────────────────────────────────────────────────────────

    @Volatile
    private var cachedFilterData: JsonElement? = null

    override fun getFilterList(): FilterList = Filters.build(loadFilterData(), i18n)

    private fun loadFilterData(): JsonElement? {
        cachedFilterData?.let { return it }

        // File cache: avoids refetching the home page on every app start.
        val cacheFile = filterCacheFile()
        if (cacheFile != null && cacheFile.isFile) {
            runCatching {
                json.decodeFromString(JsonElement.serializer(), cacheFile.readText())
            }.getOrNull()?.let {
                cachedFilterData = it
                return it
            }
        }

        val fetched = runCatching {
            val homeUrl = baseUrlHttp().newBuilder().addPathSegment("").build()
            client.newCall(get(homeUrl)).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code} fetching $homeUrl")
                val (component, props) = parseInertiaPage(response)
                if (!component.startsWith("Home/")) {
                    throw IOException("Unexpected home component: $component")
                }
                val home = json.decodeFromJsonElement(HomeProps.serializer(), props)
                json.encodeToJsonElement(
                    FilterData.serializer(),
                    FilterData(genres = home.genres, sections = home.content.sections),
                )
            }
        }.getOrNull() ?: return null

        cachedFilterData = fetched
        cacheFile?.let { file ->
            runCatching {
                file.parentFile?.mkdirs()
                file.writeText(json.encodeToString(JsonElement.serializer(), fetched))
            }
        }
        return fetched
    }

    private fun filterCacheFile(): File? = runCatching {
        File(Injekt.get<Context>().cacheDir, "source_$id").resolve("filters.json")
    }.getOrNull()

    // ──────────────────────────────────────────────────────────────────────
    // Companion
    // ──────────────────────────────────────────────────────────────────────

    companion object {
        const val KEY_HIDE_LOCKED = "removePremiumChapters"

        /** Tolerates the Astro payload's loose typing (nullable fields, extras). */
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
            coerceInputValues = true
        }

        /** Matches `"10 ene."`, `"3 jan"`, `"25 dic"`… */
        val DATE_REGEX = Regex("(\\d{1,2}) (\\p{L}{3})\\p{L}*\\.?")

        /** Bilingual (EN/ES) month abbreviation → 1-indexed month. */
        val MONTHS: Map<String, Int> = buildMap {
            val en = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
            val es = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
            en.forEachIndexed { i, abbr -> put(abbr, i + 1) }
            es.forEachIndexed { i, abbr -> put(abbr, i + 1) }
        }
    }
}

/** Runs [block] on the IO dispatcher without deadlocking a main-thread caller. */
private inline fun <T> withBlockingIo(crossinline block: suspend () -> T): T =
    runBlocking { block() }

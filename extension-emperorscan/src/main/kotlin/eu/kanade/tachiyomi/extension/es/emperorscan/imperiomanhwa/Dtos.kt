package eu.kanade.tachiyomi.extension.es.emperorscan.imperiomanhwa

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonElement

/**
 * Serializable DTOs matching the wire format of the Astro
 * `<script data-page=app>` Inertia payloads served by imperiomanhwa.com.
 *
 * Class/field names mirror the descriptor names embedded in the decompiled
 * upstream APK (`eu.kanade.tachiyomi.multisrc.imperiomanhwa.*`), so the JSON
 * contract is byte-for-byte identical to what the previously shipped v1.6.74
 * APK consumed.
 */

/** Inertia-page envelope. `component` identifies the Astro route (e.g. "Series/Show", "Reader/Locked"). */
@Serializable
data class InertiaPage<T>(
    val component: String,
    val props: T,
)

/** Manga card on a listing page. */
@Serializable
data class MangaDto(
    val url: String,
    val title: String,
    val cover: String? = null,
)

/** Named entity (genre) — `id` goes into the `genero`/`seccion` query param. */
@Serializable
data class NamedDto(
    val id: String,
    val name: String,
)

/** Same shape as [NamedDto] but a distinct descriptor (sections list). */
@Serializable
data class SectionDto(
    val id: String,
    val name: String,
)

/** Listing page props — `/manga/` and `/buscar/?q=` share this shape. */
@Serializable
data class ListingProps(
    val listing: List<MangaDto> = emptyList(),
    val pagination: PaginationDto = PaginationDto(),
)

/** Home page props — carries the filter catalogs. */
@Serializable
data class HomeProps(
    val genres: List<NamedDto> = emptyList(),
    val sections: List<SectionDto> = emptyList(),
)

@Serializable
data class PaginationDto(
    val next: String? = null,
)

/** Chapter-list pagination: how many `chapters-N.json` files exist (page 1 inline). */
@Serializable
data class ChaptersPaginationDto(
    val totalPages: Int = 1,
)

/** Relative path fragment used to build the on-demand `…-N.json` chapter URLs. */
@Serializable
data class PathsDto(
    val chapters: String,
)

@Serializable
data class FilterData(
    val genres: List<NamedDto>,
    val sections: List<SectionDto>,
)

@Serializable
data class ChapterDto(
    val id: String,
    val name: String,
    val isoDate: String? = null,
    val altDate: String? = null,
    val locked: Boolean = false,
)

/** Payload of the on-demand `chapters-N.json` endpoints. */
@Serializable
data class ChapterListDto(
    val chapters: List<ChapterDto> = emptyList(),
)

@Serializable
data class SeriesDto(
    val path: String,
    val title: String,
    val type: String? = null,
    val status: String? = null,
    val cover: String? = null,
    val altNames: List<String> = emptyList(),
    val description: String? = null,
    val genres: List<NamedDto> = emptyList(),
    val sections: List<NamedDto> = emptyList(),
    val authors: List<String> = emptyList(),
    val artists: List<String> = emptyList(),
)

@Serializable
data class SeriesProps(
    val series: SeriesDto,
    val chapters: List<ChapterDto> = emptyList(),
    val pagination: ChaptersPaginationDto = ChaptersPaginationDto(),
    val paths: PathsDto,
    val nextCursor: String? = null,
)

/** One decrypted page entry returned by the site's reader module. */
@Serializable
data class PageDto(
    val n: Int,
    val src: String? = null,
    val srcset: String,
)

@Serializable
data class ReaderSeriesDto(
    val id: Int,
)

@Serializable
data class ReaderChapterDto(
    val slug: String,
)

/** Reader page payload (`component = "Reader/Show"`). */
@Serializable
data class ReaderProps(
    val series: ReaderSeriesDto,
    val chapter: ReaderChapterDto,
    val initialPages: List<PageDto> = emptyList(),
    val remainingPages: List<JsonElement> = emptyList(),
    val challenge: JsonElement,
)

/**
 * Result of the WebView reader.js execution.
 *
 * - `pages != null` → success (decrypted image refs).
 * - `error != null` → the reader threw; if `sitekey` is also present the
 *   user must solve a Cloudflare Turnstile challenge externally.
 */
@Serializable
data class ReaderResult(
    val pages: List<PageDto>? = null,
    val error: String? = null,
    val sitekey: String? = null,
    val action: String? = null,
    val cdata: String? = null,
)

/** Input handed to `assets/reader.js` through the WebView bridge. */
@Serializable
data class ReaderInput(
    val entry: String,
    val challenge: JsonElement,
    val chapterId: Int,
    val seriesSlug: String,
) {
    // Not serialized: patched into the bootstrap script separately so the
    // JSON field order of the wire format stays identical to upstream.
    @Transient
    var turnstileToken: String? = null
}

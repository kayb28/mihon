package eu.kanade.tachiyomi.extension.es.emperorscan.imperiomanhwa

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Serializable DTOs matching the wire format of the Astro
 * `<script data-page=app>` Inertia payloads served by imperiomanhwa.com.
 *
 * Field names mirror the descriptor names embedded in the decompiled
 * upstream APK v1.6.74 (`eu.kanade.tachiyomi.multisrc.imperiomanhwa.*`),
 * so the JSON contract is byte-for-byte identical to what the previously
 * shipped APK consumed.
 *
 * Correctness was re-verified by extracting every serializer descriptor
 * (PluginGeneratedSerialDescriptor.addElement calls) from the dex — the
 * earlier reconstruction had several wire names wrong (e.g. `listing`
 * instead of `series`, `sections` instead of `content`, `totalPages`
 * instead of `last`), which made the listing parser silently return
 * 0 results because [Json] ignored the unknown keys.
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

/** Named entity (genre). `slug` goes into the `genero` query param. */
@Serializable
data class NamedDto(
    val slug: String,
    val name: String,
)

/** Section entry. `code` goes into the `seccion` query param. */
@Serializable
data class SectionDto(
    val code: String,
    val label: String,
)

/** Listing page props — `/manga/` and `/buscar/?q=` share this shape. */
@Serializable
data class ListingProps(
    val series: List<MangaDto> = emptyList(),
    val pagination: PaginationDto = PaginationDto(),
)

/** Home page props — carries the filter catalogs wrapped in `content`. */
@Serializable
data class HomeProps(
    val genres: List<NamedDto> = emptyList(),
    val content: ContentDto = ContentDto(),
)

/** Wrapper around the home page's `sections` list. */
@Serializable
data class ContentDto(
    val sections: List<SectionDto> = emptyList(),
)

@Serializable
data class PaginationDto(
    val next: String? = null,
)

/** Chapter-list pagination: `last` is the total number of chapter pages (page 1 is inline). */
@Serializable
data class ChaptersPaginationDto(
    val last: Int = 1,
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
    val slug: String,
    val label: String,
    @SerialName("published_at") val isoDate: String? = null,
    @SerialName("published_label") val altDate: String? = null,
    val locked: Boolean = false,
)

/** Payload of the on-demand `chapters-N.json` endpoints. */
@Serializable
data class ChapterListDto(
    val items: List<ChapterDto> = emptyList(),
)

@Serializable
data class SeriesDto(
    val url: String,
    val title: String,
    val type: String? = null,
    val status: String? = null,
    @SerialName("cover_canonical") val cover: String? = null,
    @SerialName("alt_titles") val altNames: List<String> = emptyList(),
    val synopsis: String? = null,
    val genres: List<NamedDto> = emptyList(),
    val tags: List<NamedDto> = emptyList(),
    val authors: List<String> = emptyList(),
    val artists: List<String> = emptyList(),
)

@Serializable
data class SeriesProps(
    val series: SeriesDto,
    val chapters: List<ChapterDto> = emptyList(),
    @SerialName("chapters_pagination") val pagination: ChaptersPaginationDto = ChaptersPaginationDto(),
    val paths: PathsDto,
    @SerialName("tags_label") val tagsLabel: String? = null,
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
    val slug: String,
)

@Serializable
data class ReaderChapterDto(
    val id: Int,
)

/** Reader page payload (`component = "Reader/Show"`). */
@Serializable
data class ReaderProps(
    val chapter: ReaderChapterDto,
    val series: ReaderSeriesDto,
    val pages: List<PageDto> = emptyList(),
    val dims: JsonElement,
    val reader: JsonElement,
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

/**
 * Input handed to `assets/reader.js` through the WebView bridge.
 *
 * Wire field order matches the upstream reader.js contract verbatim:
 * `entry` (reader module URL), `reader` (per-page config blob), `chapterId`,
 * `seriesSlug`, optional `token` (Cloudflare Turnstile solution).
 */
@Serializable
data class ReaderInput(
    val entry: String,
    val reader: JsonElement,
    val chapterId: Int,
    val seriesSlug: String,
    val token: String? = null,
)

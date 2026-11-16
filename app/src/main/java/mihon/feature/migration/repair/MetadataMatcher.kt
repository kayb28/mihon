package mihon.feature.migration.repair

import com.aallam.similarity.NormalizedLevenshtein
import eu.kanade.tachiyomi.source.model.SManga
import java.text.Normalizer
import java.util.regex.Pattern

/**
 * Pure, JVM-testable normalization of manga metadata (status, authors, genres, description)
 * used by the migration candidate validation. No network, no Android, no database: every
 * function takes plain values and returns plain values so the rules can be unit tested.
 *
 * Text rules mirror [TitleMatcher.normalize] (lowercase, NFD, strip accents) so both sides of
 * a comparison — the local manga and the remote candidate — are folded the same way.
 */
object MetadataMatcher {

    private val DIACRITICS_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+")

    // Same Unicode-aware letter/digit class (and kana voicing mark exception) as TitleMatcher:
    // \p{Alnum} would be ASCII-only and would strip CJK/Cyrillic text entirely.
    private val NON_WORD_PATTERN = Pattern.compile("[^\\p{IsAlphabetic}\\p{IsDigit}\\u3099\\u309A\\s]")
    private val MULTI_SPACE_PATTERN = Pattern.compile("\\s+")
    private val AUTHOR_SPLIT_PATTERN = Pattern.compile("[/,&]|\\s+y\\s+|\\s+and\\s+")

    /**
     * Coarse publication state used for comparisons: raw strings from sources vary
     * ("Completed", "En curso", "Hiatus", …) and SManga's numeric constants carry two values
     * that both read as finished (COMPLETED, PUBLISHING_FINISHED).
     */
    enum class StatusCategory {
        COMPLETED,
        ONGOING,
        PAUSED,
        CANCELLED,
        UNKNOWN,
    }

    private val COMPLETED_STATUSES = setOf(
        "completed",
        "complete",
        "finished",
        "finalizado",
        "finalizada",
        "completado",
        "completada",
        "completo",
        "publishing finished",
        "publicacion finalizada",
    )
    private val ONGOING_STATUSES = setOf(
        "ongoing",
        "on going",
        "en curso",
        "publicandose",
        "publishing",
        "releasing",
        "en emision",
        "emision",
        "serializando",
    )
    private val PAUSED_STATUSES = setOf(
        "hiatus",
        "on hiatus",
        "pausado",
        "pausada",
        "en pausa",
        "pausa",
    )
    private val CANCELLED_STATUSES = setOf(
        "cancelled",
        "canceled",
        "cancelado",
        "cancelada",
    )

    /**
     * Spanish/English equivalences for the most common genres, normalized keys → English
     * canonical form, so "Acción" from a Spanish source and "Action" from an English one
     * compare equal. Genres not listed here are kept as-is (lowercased, de-accented,
     * hyphens folded to spaces) rather than dropped.
     */
    private val GENRE_EQUIVALENCES = mapOf(
        "accion" to "action",
        "aventura" to "adventure",
        "fantasia" to "fantasy",
        "comedia" to "comedy",
        "terror" to "horror",
        "misterio" to "mystery",
        "sobrenatural" to "supernatural",
        "suspenso" to "thriller",
        "deporte" to "sports",
        "deportes" to "sports",
        "ciencia ficcion" to "sci fi",
        "ficcion cientifica" to "sci fi",
        "recuentos de la vida" to "slice of life",
        "psicologico" to "psychological",
        "escolar" to "school",
        "chicas magicas" to "magical girls",
        "artes marciales" to "martial arts",
        "shounen" to "shonen",
        "shoujo" to "shojo",
    )

    /**
     * Maps a free-text status (source sites mix English and Spanish, and case/punctuation
     * vary) onto [StatusCategory]. Blank or unrecognized text → [StatusCategory.UNKNOWN].
     */
    fun normalizeStatus(raw: String?): StatusCategory {
        val normalized = normalizeFreeText(raw)
        return when {
            normalized in COMPLETED_STATUSES -> StatusCategory.COMPLETED
            normalized in ONGOING_STATUSES -> StatusCategory.ONGOING
            normalized in PAUSED_STATUSES -> StatusCategory.PAUSED
            normalized in CANCELLED_STATUSES -> StatusCategory.CANCELLED
            else -> StatusCategory.UNKNOWN
        }
    }

    /**
     * Maps SManga's numeric status constants onto [StatusCategory]. LICENSED says nothing
     * about publication state, so it lands on [StatusCategory.UNKNOWN] like UNKNOWN itself.
     */
    fun normalizeStatus(status: Int): StatusCategory = when (status) {
        SManga.ONGOING -> StatusCategory.ONGOING
        SManga.COMPLETED, SManga.PUBLISHING_FINISHED -> StatusCategory.COMPLETED
        SManga.CANCELLED -> StatusCategory.CANCELLED
        SManga.ON_HIATUS -> StatusCategory.PAUSED
        else -> StatusCategory.UNKNOWN
    }

    /**
     * Splits an author string into normalized author names. Separators: "/", ",", "&",
     * " y " and " and " (the latter two matching TitleMatcher's author handling). Blank or
     * missing authors yield an empty set.
     */
    fun normalizeAuthors(author: String?): Set<String> {
        if (author.isNullOrBlank()) return emptySet()
        return AUTHOR_SPLIT_PATTERN.split(author)
            .map { TitleMatcher.normalize(it) }
            .filter { it.isNotBlank() }
            .toSet()
    }

    /**
     * Normalizes a genre list into a comparable set: lowercase, no accents, hyphens folded
     * to spaces, es/en equivalences applied. Unmapped genres are kept as-is.
     */
    fun normalizeGenres(genres: List<String>?): Set<String> {
        return genres.orEmpty()
            .map { normalizeGenre(it) }
            .filter { it.isNotBlank() }
            .toSet()
    }

    /**
     * Lowercases, strips accents and punctuation, and collapses whitespace, so descriptions
     * from different sources compare on words alone ("¡Hola, Mundo!" and "hola mundo" match).
     * Unlike [TitleMatcher.normalize] symbols become spaces instead of being removed: a
     * removed separator would glue sentence-boundary words together ("end.Start" → "endstart").
     */
    fun normalizeDescription(description: String?): String = normalizeFreeText(description)

    /**
     * Normalized-Levenshtein similarity (0..1) of the two descriptions after
     * [normalizeDescription] — the same metric the search engine uses on titles. A blank
     * side yields 0.0; presence is checked by the caller before trusting the score.
     */
    fun descriptionSimilarity(a: String?, b: String?): Double {
        val left = normalizeDescription(a)
        val right = normalizeDescription(b)
        if (left.isEmpty() || right.isEmpty()) return 0.0
        return NormalizedLevenshtein().similarity(left, right)
    }

    private fun normalizeGenre(genre: String): String {
        val lowered = genre.lowercase().trim()
        val nfd = Normalizer.normalize(lowered, Normalizer.Form.NFD)
        val withoutAccents = DIACRITICS_PATTERN.matcher(nfd).replaceAll("")
        val hyphensAsSpaces = withoutAccents.replace('-', ' ')
        val cleaned = MULTI_SPACE_PATTERN.matcher(hyphensAsSpaces).replaceAll(" ").trim()
        return GENRE_EQUIVALENCES[cleaned] ?: cleaned
    }

    private fun normalizeFreeText(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val nfd = Normalizer.normalize(raw.lowercase().trim(), Normalizer.Form.NFD)
        val withoutAccents = DIACRITICS_PATTERN.matcher(nfd).replaceAll("")
        val withoutSymbols = NON_WORD_PATTERN.matcher(withoutAccents).replaceAll(" ")
        return MULTI_SPACE_PATTERN.matcher(withoutSymbols).replaceAll(" ").trim()
    }
}

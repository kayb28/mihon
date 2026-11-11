package mihon.feature.migration.repair

import eu.kanade.tachiyomi.source.model.SManga
import java.text.Normalizer
import java.util.regex.Pattern

object TitleMatcher {

    private val DIACRITICS_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+")
    private val SYMBOLS_PATTERN = Pattern.compile("[^\\p{Alnum}\\s]")
    private val MULTI_SPACE_PATTERN = Pattern.compile("\\s+")
    private val AUTHOR_SPLIT_PATTERN = Pattern.compile("[,&]|\\s+y\\s+|\\s+and\\s+")

    /**
     * Normalizes a title:
     * 1. Lowercase and trim
     * 2. Canonical decomposition (NFD)
     * 3. Remove all diacritical marks (tildes, accents)
     * 4. Remove all non-alphanumeric characters (symbols/punctuation)
     * 5. Collapse multiple spaces into a single space and trim
     */
    fun normalize(title: String): String {
        val nfd = Normalizer.normalize(title.lowercase().trim(), Normalizer.Form.NFD)
        val withoutAccents = DIACRITICS_PATTERN.matcher(nfd).replaceAll("")
        val withoutSymbols = SYMBOLS_PATTERN.matcher(withoutAccents).replaceAll("")
        return MULTI_SPACE_PATTERN.matcher(withoutSymbols).replaceAll(" ").trim()
    }

    /**
     * Splits an author string into normalized author names ("A, B & C" / "A y B").
     * Blank or missing authors yield an empty set.
     */
    private fun normalizeAuthors(author: String?): Set<String> {
        if (author.isNullOrBlank()) return emptySet()
        return AUTHOR_SPLIT_PATTERN.split(author)
            .map { normalize(it) }
            .filter { it.isNotBlank() }
            .toSet()
    }

    private fun normalizeGenres(genres: List<String>?): Set<String> {
        return genres.orEmpty()
            .map { normalize(it) }
            .filter { it.isNotBlank() }
            .toSet()
    }

    /**
     * Compatible when either side is unknown (blank); otherwise at least one name must overlap.
     * Same-title different-work entries (remakes, editions) usually share no author.
     */
    private fun isAuthorCompatible(current: Set<String>, candidate: Set<String>): Boolean {
        if (current.isEmpty() || candidate.isEmpty()) return true
        return current.intersect(candidate).isNotEmpty()
    }

    /**
     * Compatible when either side is unknown; otherwise at least one genre must overlap.
     */
    private fun isGenreCompatible(current: Set<String>, candidate: Set<String>): Boolean {
        if (current.isEmpty() || candidate.isEmpty()) return true
        return current.intersect(candidate).isNotEmpty()
    }

    sealed interface MatchResult {
        /**
         * Exactly one candidate has an identical normalized title and a different URL,
         * with no author/genre conflict against the current manga.
         */
        data class AutoAccept(val candidate: SManga) : MatchResult

        /**
         * Needs manual review (e.g. 0 matches, multiple identical matches, same URL as current,
         * or a secondary-signal conflict like author/genres).
         */
        data class NeedsReview(
            val reason: Reason,
            val candidates: List<SManga> = emptyList(),
        ) : MatchResult {
            enum class Reason {
                NO_MATCH,
                MULTIPLE_EXACT_MATCHES,
                SAME_URL,
                AUTHOR_MISMATCH,
                GENRE_MISMATCH,
            }
        }
    }

    fun findMatch(
        currentTitle: String,
        currentUrl: String,
        currentAuthor: String?,
        currentGenres: List<String>?,
        candidates: List<SManga>,
    ): MatchResult {
        if (candidates.isEmpty()) {
            return MatchResult.NeedsReview(MatchResult.NeedsReview.Reason.NO_MATCH)
        }

        val normalizedCurrent = normalize(currentTitle)
        if (normalizedCurrent.isEmpty()) {
            return MatchResult.NeedsReview(MatchResult.NeedsReview.Reason.NO_MATCH, candidates)
        }

        val currentAuthors = normalizeAuthors(currentAuthor)
        val currentGenreSet = normalizeGenres(currentGenres)

        fun candidateAuthorCompatible(candidate: SManga): Boolean =
            isAuthorCompatible(currentAuthors, normalizeAuthors(candidate.author))

        fun candidateGenreCompatible(candidate: SManga): Boolean =
            isGenreCompatible(currentGenreSet, normalizeGenres(candidate.getGenres()))

        val exactMatches = candidates.filter { candidate ->
            normalize(candidate.title) == normalizedCurrent
        }
        if (exactMatches.isEmpty()) {
            return MatchResult.NeedsReview(MatchResult.NeedsReview.Reason.NO_MATCH, candidates)
        }

        // Secondary signals first: use author/genres to disambiguate multiple exact titles
        val compatibleMatches = exactMatches.filter { candidate ->
            candidateAuthorCompatible(candidate) && candidateGenreCompatible(candidate)
        }
        val pool = compatibleMatches.ifEmpty { exactMatches }

        return when {
            pool.size == 1 -> {
                val candidate = pool.first()
                when {
                    candidate.url == currentUrl ->
                        MatchResult.NeedsReview(MatchResult.NeedsReview.Reason.SAME_URL, pool)
                    !candidateAuthorCompatible(candidate) ->
                        MatchResult.NeedsReview(MatchResult.NeedsReview.Reason.AUTHOR_MISMATCH, pool)
                    !candidateGenreCompatible(candidate) ->
                        MatchResult.NeedsReview(MatchResult.NeedsReview.Reason.GENRE_MISMATCH, pool)
                    else -> MatchResult.AutoAccept(candidate)
                }
            }
            pool.size > 1 -> {
                MatchResult.NeedsReview(MatchResult.NeedsReview.Reason.MULTIPLE_EXACT_MATCHES, pool)
            }
            else -> {
                MatchResult.NeedsReview(MatchResult.NeedsReview.Reason.NO_MATCH, candidates)
            }
        }
    }
}

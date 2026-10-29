package mihon.feature.migration.repair

import eu.kanade.tachiyomi.source.model.SManga
import java.text.Normalizer
import java.util.regex.Pattern

object TitleMatcher {

    private val DIACRITICS_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+")
    private val SYMBOLS_PATTERN = Pattern.compile("[^\\p{Alnum}\\s]")
    private val MULTI_SPACE_PATTERN = Pattern.compile("\\s+")

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
        val withoutSymbols = SYMBOLS_PATTERN.matcher(withoutAccents).replaceAll(" ")
        return MULTI_SPACE_PATTERN.matcher(withoutSymbols).replaceAll(" ").trim()
    }

    sealed interface MatchResult {
        /**
         * Exactly one candidate has an identical normalized title and a different URL.
         */
        data class AutoAccept(val candidate: SManga) : MatchResult

        /**
         * Needs manual review (e.g. 0 matches, multiple identical matches, or same URL as current).
         */
        data class NeedsReview(
            val reason: Reason,
            val candidates: List<SManga> = emptyList(),
        ) : MatchResult {
            enum class Reason {
                NO_MATCH,
                MULTIPLE_EXACT_MATCHES,
                SAME_URL,
            }
        }
    }

    fun findMatch(currentTitle: String, currentUrl: String, candidates: List<SManga>): MatchResult {
        if (candidates.isEmpty()) {
            return MatchResult.NeedsReview(MatchResult.NeedsReview.Reason.NO_MATCH)
        }

        val normalizedCurrent = normalize(currentTitle)
        if (normalizedCurrent.isEmpty()) {
            return MatchResult.NeedsReview(MatchResult.NeedsReview.Reason.NO_MATCH, candidates)
        }

        val exactMatches = candidates.filter { candidate ->
            normalize(candidate.title) == normalizedCurrent
        }

        return when {
            exactMatches.size == 1 -> {
                val candidate = exactMatches.first()
                if (candidate.url == currentUrl) {
                    MatchResult.NeedsReview(MatchResult.NeedsReview.Reason.SAME_URL, exactMatches)
                } else {
                    MatchResult.AutoAccept(candidate)
                }
            }
            exactMatches.size > 1 -> {
                MatchResult.NeedsReview(MatchResult.NeedsReview.Reason.MULTIPLE_EXACT_MATCHES, exactMatches)
            }
            else -> {
                MatchResult.NeedsReview(MatchResult.NeedsReview.Reason.NO_MATCH, candidates)
            }
        }
    }
}

package mihon.feature.migration.list.search

import com.aallam.similarity.NormalizedLevenshtein
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import java.util.Locale

typealias SearchAction<T> = suspend (String) -> List<T>

abstract class BaseSmartSearchEngine<T>(
    private val extraSearchParams: String? = null,
    private val eligibleThreshold: Double = MIN_ELIGIBLE_THRESHOLD,
) {
    protected val normalizedLevenshtein = NormalizedLevenshtein()

    protected abstract fun getTitle(result: T): String

    /**
     * Fetches candidates for every query and ranks them by [calculateDistance], dropping entries
     * below [eligibleThreshold]. A single query with a single candidate keeps the legacy
     * auto-accept behavior (distance 1.0) instead of being scored.
     *
     * Exposed so subclasses can post-process the ranked candidates — e.g. the hybrid exact-match
     * pass in [SmartSourceSearchEngine] — without re-fetching anything.
     */
    protected suspend fun rankedCandidates(
        searchAction: SearchAction<T>,
        queries: List<String>,
        calculateDistance: (T) -> Double,
    ): List<SearchEntry<T>> {
        return supervisorScope {
            queries.map { query ->
                async(Dispatchers.IO) {
                    val builtQuery = if (!extraSearchParams.isNullOrBlank()) {
                        "$query $extraSearchParams"
                    } else {
                        query
                    }

                    val candidates = searchAction(builtQuery)
                    // Single query returning a single candidate: the legacy auto-accept keeps it
                    // eligible without scoring, but the flag records WHY it got distance 1.0 so the
                    // UI can say "only result" instead of claiming a title match it never checked.
                    val autoAccept = queries.size <= 1 && candidates.size <= 1
                    candidates
                        .map {
                            val distance = if (!autoAccept) calculateDistance(it) else 1.0
                            SearchEntry(it, distance, autoAccepted = autoAccept)
                        }
                        .filter { it.distance >= eligibleThreshold }
                }
            }
                .flatMap { it.await() }
        }
    }

    protected fun cleanDeepSearchTitle(title: String): String {
        val preTitle = title.lowercase(Locale.getDefault())

        // Remove text in brackets
        var cleanedTitle = removeTextInBrackets(preTitle, true)
        if (cleanedTitle.length <= 5) { // Title is suspiciously short, try parsing it backwards
            cleanedTitle = removeTextInBrackets(preTitle, false)
        }

        // Strip chapter reference RU
        cleanedTitle = cleanedTitle.replace(chapterRefCyrillicRegexp, " ").trim()

        // Strip non-special characters
        val cleanedTitleEng = cleanedTitle.replace(titleRegex, " ")

        // Do not strip foreign language letters if cleanedTitle is too short
        cleanedTitle = if (cleanedTitleEng.length <= 5) {
            cleanedTitle.replace(titleCyrillicRegex, " ")
        } else {
            cleanedTitleEng
        }

        // Strip splitters and consecutive spaces
        cleanedTitle = cleanedTitle.trim().replace(" - ", " ").replace(consecutiveSpacesRegex, " ").trim()

        return cleanedTitle
    }

    private fun removeTextInBrackets(text: String, readForward: Boolean): String {
        val openingChars = if (readForward) "([<{" else ")]}>"
        val closingChars = if (readForward) ")]}>" else "([<{"
        var depth = 0

        return buildString {
            for (char in (if (readForward) text else text.reversed())) {
                when (char) {
                    in openingChars -> depth++
                    in closingChars -> if (depth > 0) depth-- // Avoid depth going negative on mismatched closing
                    else -> if (depth == 0) {
                        // If reading backward, the result is reversed, so prepend
                        if (readForward) append(char) else insert(0, char)
                    }
                }
            }
        }
    }

    protected fun getDeepSearchQueries(cleanedTitle: String): List<String> {
        val splitCleanedTitle = cleanedTitle.split(" ")
        val splitSortedByLargest = splitCleanedTitle.sortedByDescending { it.length }

        if (splitCleanedTitle.isEmpty()) {
            return emptyList()
        }

        // Search cleaned title
        // Search two largest words
        // Search largest word
        // Search first two words
        // Search first word
        val searchQueries = listOf(
            listOf(cleanedTitle),
            splitSortedByLargest.take(2),
            splitSortedByLargest.take(1),
            splitCleanedTitle.take(2),
            splitCleanedTitle.take(1),
        )

        return searchQueries
            .map { it.joinToString(" ").trim() }
            .distinct()
    }

    companion object {
        const val MIN_ELIGIBLE_THRESHOLD = 0.4

        // Unicode-aware, like TitleMatcher now is: the old [a-zA-Z0-9- ] strip dropped whole
        // CJK/Cyrillic tails, so "Tokyo Revengers 特別編" and "Tokyo Revengers 番外編" both cleaned
        // to "tokyo revengers" and scored as identical fuzzy candidates. The short-title fallback
        // below becomes a no-op for those titles, which is fine — nothing needs recovering.
        private val titleRegex = Regex("[^\\p{L}\\p{Nd}\\- ]")
        private val titleCyrillicRegex = Regex("[^\\p{L}0-9- ]")
        private val consecutiveSpacesRegex = Regex(" +")
        private val chapterRefCyrillicRegexp = Regex("""((- часть|- глава) \d*)""")
    }
}

data class SearchEntry<T>(
    val entry: T,
    val distance: Double,
    /**
     * True when the legacy single-query/single-candidate auto-accept assigned distance 1.0
     * without comparing titles — the score means "only result", not "same title".
     */
    val autoAccepted: Boolean = false,
)

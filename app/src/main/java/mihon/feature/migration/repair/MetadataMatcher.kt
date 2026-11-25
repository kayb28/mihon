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

    /**
     * Which metadata signals a candidate was validated against. Each signal is only
     * *evaluated* when both sides have data: a field missing on either side counts neither
     * for nor against the candidate.
     */
    enum class Signal { AUTHOR, STATUS, DESCRIPTION, GENRES }

    /**
     * Outcome of [validate]. [NoData] is distinct from [Rejected] with zero matches: "the
     * source gave us nothing to compare" is not "the source contradicted the manga".
     */
    sealed interface MetadataVerdict {
        /** Title passed and at least [MIN_MATCHED_SIGNALS] signals agree. */
        data class Accepted(
            val matchedSignals: Set<Signal>,
            /** Signals that had data on both sides and were weighed (M3 veto reads this). */
            val evaluatedSignals: Set<Signal> = emptySet(),
        ) : MetadataVerdict

        /** None of the four fields could be compared; the caller decides the fallback. */
        data object NoData : MetadataVerdict

        /** Data was present but fewer than [MIN_MATCHED_SIGNALS] signals agree, or the author veto fired. */
        data class Rejected(
            val matchedSignals: Set<Signal>,
            val evaluatedSignals: Set<Signal>,
        ) : MetadataVerdict
    }

    /**
     * Minimum agreement for a candidate to be accepted once the title criterion passed:
     * at least 2 of the 4 metadata signals must match.
     */
    const val MIN_MATCHED_SIGNALS = 2

    /**
     * Initial description-similarity threshold (adjustable via preferences at the call
     * site): normalized descriptions must score at least this much for the signal to match.
     */
    const val DEFAULT_DESCRIPTION_THRESHOLD = 0.6

    /**
     * Author agreement, STRICT (M2): a shared surname token is not evidence that two names
     * are the same person — "Park" is contained in "Park Joo" and in "Park Ji-Ho", and with
     * status that pair used to push a fuzzy-titled different work over the 2-of-4 gate.
     * Agreement is:
     *
     * - Full normalized-name intersection ("Kim Doe" on both sides) — the exact case.
     * - Full token-set containment where the SHORTER side carries >= 2 tokens
     *   ("Kim Jae-Hwan" vs "Jae Hwan Kim": romanization spacing, punctuation and
     *   surname/given-name order flip constantly between sources; "Kim Doe" vs
     *   "Kim Doe (Redice Studio)" for two-token names). A one-token name is never the
     *   shorter side: "Park" alone matches nothing, and a studio credit attached to a
     *   single-token name ("Chugong" vs "Chugong (Redice Studio)") no longer counts.
     *
     * Anything else is a real mismatch for this pair — but callers treat AUTHOR as ONE signal
     * among four (no veto in manual review; the M3 veto only guards the high-impact path).
     */
    private fun authorsMatch(current: Set<String>, candidate: Set<String>): Boolean {
        if (current.intersect(candidate).isNotEmpty()) return true
        val currentTokens = current.map { it.split(" ").filter { t -> t.length >= 2 }.toSet() }
        val candidateTokens = candidate.map { it.split(" ").filter { t -> t.length >= 2 }.toSet() }
        // De-spaced full-name equality: normalize folds hyphens OUT ("Kim Jae-Hwan" ->
        // "kimjaehwan"), so the same name spaced differently on the other side ("Kim Jae Hwan")
        // is one token there. Equality on the de-spaced form is a FULL-sequence check, not a
        // containment: "park" vs "park joo" still never matches.
        val currentDeSpaced = current.map { it.replace(" ", "") }.toSet()
        val candidateDeSpaced = candidate.map { it.replace(" ", "") }.toSet()
        if (currentDeSpaced.intersect(candidateDeSpaced).isNotEmpty()) return true
        val tokenPairs = sequence {
            currentTokens.forEach { a -> candidateTokens.forEach { b -> yield(a to b) } }
        }
        return tokenPairs.any { (a, b) ->
            (a.size >= 2 && a.size <= b.size && b.containsAll(a)) ||
                (b.size >= 2 && b.size <= a.size && a.containsAll(b))
        }
    }

    /**
     * Words that carry no evidence of the same work: filler, structural markup and the
     * vocabulary every description in the genre shares ("world", "story", "power").
     */
    private val GENERIC_WORDS = setOf(
        "a", "an", "the", "of", "and", "or", "to", "in", "on", "at", "for", "with", "by", "from",
        "his", "her", "their", "its", "he", "she", "they", "it", "who", "that", "this", "these",
        "those", "is", "are", "was", "were", "be", "been", "being", "has", "have", "had",
        "will", "would", "can", "could", "must", "may", "one", "man", "woman", "girl", "boy",
        "young", "life", "world", "story", "tale", "new", "old", "great", "best", "top",
        "power", "powers", "strong", "strongest", "weak", "hero", "villain", "god", "gods",
        "demon", "demons", "sword", "king", "lords", "war", "battle", "school", "student",
        "de", "la", "el", "los", "las", "un", "una", "del", "al", "que", "y", "o", "en",
        "por", "para", "con", "su", "sus", "se", "es", "son", "fue", "mas", "mundo", "vida",
        "historia", "poder", "poderes", "fuerte", "debil", "heroe", "villano", "dios",
        "demonio", "demonios", "espada", "rey", "guerra", "batalla", "escuela", "estudiante",
        "un", "joven", "chica", "chico",
    )

    /**
     * Sub-genre vocabulary added after the real-device false positives of phase 3: two
     * works of the SAME sub-genre share these on every synopsis, so they are not evidence
     * of the same work. "Regresor" (regression fantasy) and "El regreso de mi ex" (romantic
     * second-chance) both counted "regresa/universidad/oportunidad/destino" as narrative
     * anchors — the vocabulary of the second-chance sub-genre, not of either work. Same
     * root for the tower/volleyball pair: tower-climb and campus-romance boilerplate.
     */
    private val SUBGENRE_WORDS = setOf(
        // Regression / time-travel fantasy boilerplate
        "regresa", "regreso", "regresion", "regresor", "regresar", "regresan", "volver",
        "vuelve", "volvio", "pasado", "segunda", "oportunidad", "cambia", "cambiar",
        "cambio", "cambió", "elige", "elegir", "corregir", "destino", "futuro", "arrepentido",
        "regret", "returns", "returned", "back", "past", "second", "chance", "again",
        // Campus romance / rom-com boilerplate
        "universidad", "colegio", "instituto", "campus", "romance", "romantica", "amor",
        "amiga", "amigo", "novia", "novio", "ex", "expareja", "exesposa", "exnovia",
        "college", "university", "love", "loves", "crush", "girlfriend", "boyfriend",
        "date", "dating", "kiss", "popular", "guapo", "guapa", "hermosa", "atractivo",
        // Tower-climb / hunter boilerplate
        "torre", "piso", "planta", "nivel", "niveles", "escala", "escalar", "escalando",
        "ascenso", "desafiar", "desafio", "portero", "cazador", "cazadores", "monstruos",
        "puertas", "gate", "gates", "tower", "floor", "climb", "hunter", "hunters",
        "monsters", "challenge", "clearing",
        // Volleyball / sports rom-com boilerplate (Ookii's own sub-genre)
        "equipo", "team", "entrenamiento", "partido", "voleibol", "volleyball", "sport",
        "sports", "coach", "capitan", "captain",
    )

    /**
     * Korean particle suffixes in their romanized form: when Korean glue does not use
     * spaces ("bremen을" → "bremeneul"), the fused token ends in one of these. Anchors may
     * match such a fused token by stripping the suffix, which is how a hangul description
     * anchors on the same proper noun as a Latin one ("bremeneul" ↔ "bremen") — without
     * opening the prefix loophole that let "regresa" match "regresar".
     */
    private val KOREAN_PARTICLE_SUFFIXES = listOf(
        "eul", "reul", "eun", "neun", "ega", "ga", "eseo", "ese", "ro", "ro", "wa", "gwa",
        "man", "boda", "cheoreom", "hago", "hago",
    )

    /**
     * How many distinct narrative anchors (non-generic specific words: character names,
     * occupations, places, powers) [b]'s description shares with [a]'s. An anchor is
     * DISTINCTIVE: at least [MIN_ANCHOR_LENGTH] letters, outside the genre boilerplate
     * ([GENERIC_WORDS]) AND the sub-genre boilerplate ([SUBGENRE_WORDS]). Korean glue is
     * unblocked by stripping a romanized particle suffix ("bremeneul" → "bremen"), never
     * by raw prefix matching: prefixes are how sub-genre vocabulary ("regresa" matching
     * "regresar") masqueraded as evidence in the phase-3 false positives.
     */
    fun narrativeOverlap(a: String?, b: String?): Int {
        val tokensA = anchorTokens(a)
        val tokensB = anchorTokens(b)
        if (tokensA.isEmpty() || tokensB.isEmpty()) return 0
        return tokensB.count { token -> tokensA.any { other -> anchorsMatch(other, token) } }
    }

    /**
     * Minimum length a word must reach to count as a narrative anchor at all: below this
     * the word is too easy to collide across unrelated synopses ("torre" vs "torture").
     */
    const val MIN_ANCHOR_LENGTH = 4

    private fun anchorsMatch(a: String, b: String): Boolean {
        if (a == b) return true
        if (a.length < MIN_ANCHOR_LENGTH || b.length < MIN_ANCHOR_LENGTH) return false
        // Korean glue only: one side must be the other side plus a romanized particle
        // suffix — "bremeneul" == "bremen" + "eul". A bare prefix (regresa/regresar) is
        // deliberately NOT an anchor match anymore.
        return KOREAN_PARTICLE_SUFFIXES.any { suffix ->
            (a.endsWith(suffix) && a.removeSuffix(suffix) == b) ||
                (b.endsWith(suffix) && b.removeSuffix(suffix) == a)
        }
    }

    private fun anchorTokens(description: String?): Set<String> {
        // Romanize BEFORE normalizing: normalizeDescription's NFD decomposition would break
        // hangul into scattered jamo (차준직 → 차준직은) that Romanizer's precomposed-syllable
        // arithmetic cannot read. Romanizing first keeps syllables whole, then normalize folds
        // case/accents/punctuation on the Latin result.
        if (description.isNullOrBlank()) return emptySet()
        val romanized = Romanizer.romanize(description)
        val normalized = normalizeDescription(romanized)
        if (normalized.isEmpty()) return emptySet()
        return normalized.split(" ")
            .filter { it.length >= MIN_ANCHOR_LENGTH && it !in GENERIC_WORDS && it !in SUBGENRE_WORDS }
            .toSet()
    }

    /**
     * Minimum number of shared narrative anchors for the description signal to match when
     * the overall Levenshtein similarity is below [DEFAULT_DESCRIPTION_THRESHOLD]: two
     * descriptions that name the same people doing the same things are the same work's
     * synopsis even when they were written independently (translations, paraphrases).
     */
    const val MIN_NARRATIVE_ANCHORS = 4

    /**
     * Levels of title agreement, in decreasing strength. The search flow uses them to decide
     * how much metadata evidence a candidate needs: an EXACT normalized title is strong
     * evidence on its own, a ROMANIZED match (hangul ↔ Latin form of the same title) is
     * nearly as strong, a FUZZY near-match needs the full metadata gate.
     */
    enum class TitleLevel {
        /** Identical after [TitleMatcher.normalize] (case, accents, punctuation folded). */
        EXACT,

        /** Identical after romanizing both sides: "전지적 독자 시점" == "jeonjijeok dokja sijeom". */
        ROMANIZED,

        /** High normalized-Levenshtein similarity — needs the full metadata gate. */
        FUZZY,

        /** Below the fuzzy threshold: not the same title as far as this comparison goes. */
        NONE,
    }

    /**
     * Fuzzy titles at or above this similarity count as [TitleLevel.FUZZY] (the engine's own
     * eligible threshold mirrors this: candidates below it never even get picked).
     */
    const val TITLE_FUZZY_THRESHOLD = 0.6

    /**
     * Title levels strong enough to carry a match with LESS metadata agreement than the
     * 2-of-4 rule: an EXACT normalized title or a ROMANIZED form of the same title is
     * evidence the candidate IS the work searched for — the metadata gate then only needs
     * no outright contradiction (one agreeing signal, or none evaluated at all).
     */
    val STRONG_TITLE_LEVELS = setOf(TitleLevel.EXACT, TitleLevel.ROMANIZED)

    /**
     * Classifies how two titles compare after [TitleMatcher.normalize]: EXACT (identical
     * keys), ROMANIZED (identical once the non-Latin script is transliterated — Korean vs
     * its romanized form, or a Japanese kana title vs its romaji), FUZZY (similarity ≥
     * [TITLE_FUZZY_THRESHOLD]) or NONE. Used by the migration gate to weigh title evidence.
     */
    fun titleLevel(currentTitle: String, candidateTitle: String): TitleLevel {
        val a = TitleMatcher.normalize(currentTitle)
        val b = TitleMatcher.normalize(candidateTitle)
        if (a.isEmpty() || b.isEmpty()) return TitleLevel.NONE
        if (a == b) return TitleLevel.EXACT
        val romanizedA = TitleMatcher.normalize(Romanizer.romanize(currentTitle))
        val romanizedB = TitleMatcher.normalize(Romanizer.romanize(candidateTitle))
        if (romanizedA.isNotEmpty() && romanizedA == romanizedB) return TitleLevel.ROMANIZED
        if (descriptionSimilarity(a, b) >= TITLE_FUZZY_THRESHOLD) return TitleLevel.FUZZY
        return TitleLevel.NONE
    }

    /**
     * Compares the source manga's metadata against a candidate's and returns the verdict.
     * The title criterion is NOT re-checked here — the caller applies its own title levels
     * and only asks this whether the metadata agrees:
     *
     * 1. Author: normalized name sets intersect → [Signal.AUTHOR] matches. Both sides carry
     *    authors and none intersects → the signal does not match (NOT a veto: romanization
     *    variants, reversed name order or studio credits attached to the name keep this from
     *    being decisive on its own). Missing author on either side → not evaluated.
     * 2. Status: same [StatusCategory], and not [StatusCategory.UNKNOWN] on either side.
     *    Deliberately weak: sources mislabel publication state (works marked completed keep
     *    receiving chapters), so it counts as one more signal, never as a veto.
     * 3. Description: similarity ≥ [descriptionThreshold] (normalized Levenshtein) OR the
     *    two share enough narrative anchors ([narrativeOverlap]).
     * 4. Genres: intersection ≥ 50% of the smaller genre set.
     *
     * A signal is evaluated only when both sides have data for it; if no signal can be
     * evaluated the verdict is [MetadataVerdict.NoData] — the caller then applies its
     * configured fallback (the old title-only criterion) instead of rejecting blindly.
     */
    fun validate(
        currentAuthor: String?,
        currentStatus: Int,
        currentDescription: String?,
        currentGenres: List<String>?,
        candidateAuthor: String?,
        candidateStatus: Int,
        candidateDescription: String?,
        candidateGenres: List<String>?,
        descriptionThreshold: Double = DEFAULT_DESCRIPTION_THRESHOLD,
    ): MetadataVerdict {
        val evaluated = mutableSetOf<Signal>()
        val matched = mutableSetOf<Signal>()

        val currentAuthors = normalizeAuthors(currentAuthor)
        val candidateAuthors = normalizeAuthors(candidateAuthor)
        if (currentAuthors.isNotEmpty() && candidateAuthors.isNotEmpty()) {
            evaluated += Signal.AUTHOR
            if (authorsMatch(currentAuthors, candidateAuthors)) matched += Signal.AUTHOR
        }

        val currentStatusCategory = normalizeStatus(currentStatus)
        val candidateStatusCategory = normalizeStatus(candidateStatus)
        if (currentStatusCategory != StatusCategory.UNKNOWN && candidateStatusCategory != StatusCategory.UNKNOWN) {
            evaluated += Signal.STATUS
            if (currentStatusCategory == candidateStatusCategory) matched += Signal.STATUS
        }

        val currentDescriptionNormalized = normalizeDescription(currentDescription)
        val candidateDescriptionNormalized = normalizeDescription(candidateDescription)
        if (currentDescriptionNormalized.isNotEmpty() && candidateDescriptionNormalized.isNotEmpty()) {
            evaluated += Signal.DESCRIPTION
            if (descriptionSimilarity(currentDescription, candidateDescription) >= descriptionThreshold ||
                narrativeOverlap(currentDescription, candidateDescription) >= MIN_NARRATIVE_ANCHORS
            ) {
                matched += Signal.DESCRIPTION
            }
        }

        val currentGenreSet = normalizeGenres(currentGenres)
        val candidateGenreSet = normalizeGenres(candidateGenres)
        if (currentGenreSet.isNotEmpty() && candidateGenreSet.isNotEmpty()) {
            evaluated += Signal.GENRES
            val smallerSize = minOf(currentGenreSet.size, candidateGenreSet.size)
            if (currentGenreSet.intersect(candidateGenreSet).size * 2 >= smallerSize) {
                matched += Signal.GENRES
            }
        }

        return when {
            evaluated.isEmpty() -> MetadataVerdict.NoData
            matched.size >= MIN_MATCHED_SIGNALS ->
                MetadataVerdict.Accepted(matched.toSet(), evaluated.toSet())
            else -> MetadataVerdict.Rejected(matched.toSet(), evaluated.toSet())
        }
    }

    /**
     * Outcome of [validateChapters], the chapter-count gate applied before the metadata gate.
     * A candidate whose source reports a chapter list much shorter than the library copy is a
     * stale mirror or a different work sharing the title, regardless of what any metadata
     * signal would say.
     */
    sealed interface ChapterVerdict {
        /** Either side reports no chapter count (0 or unknown): this gate does not judge. */
        data object Skip : ChapterVerdict

        /** candidate/original is under [CHAPTER_REJECT_RATIO]: rejected outright. */
        data object Rejected : ChapterVerdict

        /**
         * Identity of the work may well be right, but the source is NOT USEFUL to keep reading
         * (phase 3): the candidate passes the ratio floor yet trails by an absolute gap no
         * reader would migrate into — 93/211 chapters is "correct work, wrong mirror", not an
         * alternative comparable to one that is one or two chapters behind. The work is not
         * offered as a migration destination, but this is a VIABILITY verdict, not an identity
         * one: the same source may still be perfect for other titles.
         */
        data object NotViable : ChapterVerdict

        /**
         * Accepted; [chaptersBehind] is positive when the candidate trails the library copy by
         * more than [CHAPTER_BEHIND_TOLERANCE] chapters (shown as a warning line), zero when the
         * counts are close enough or the candidate has more chapters than the library copy.
         */
        data class Accepted(val chaptersBehind: Int) : ChapterVerdict
    }

    /**
     * A candidate reporting fewer than this fraction of the library copy's chapters is rejected.
     */
    const val CHAPTER_REJECT_RATIO = 0.5

    /**
     * A candidate within [0.5, 1.0] of the library copy is accepted, but when it trails by more
     * than this many chapters the list flags it («Va N caps atrás») instead of showing nothing.
     */
    const val CHAPTER_BEHIND_TOLERANCE = 5

    /**
     * Absolute gap past which a ratio-passing candidate stops being a useful migration
     * destination altogether ([ChapterVerdict.NotViable]). Calibrated on the real-device
     * phase-3 report: the two observed false-positive migrations carried 93 and 62 chapters
     * of delay while the healthy matches reported 1-2; a weekly serial amasses ~25 chapters
     * per quarter, so a source 26+ chapters behind is months stale. Gaps in
     * (CHAPTER_BEHIND_TOLERANCE, CHAPTER_NOT_VIABLE_GAP] stay accepted with the «Va N caps
     * atrás» warning so borderline mirrors remain visible choices.
     */
    const val CHAPTER_NOT_VIABLE_GAP = 25

    /**
     * Compares the library manga's chapter count against the candidate's. Unknown counts on
     * either side (0, missing, failed fetch) never reject: the gate skips and the metadata
     * gate (with its configured fallback) decides.
     *
     * Viability policy (phase 3): ratio under [CHAPTER_REJECT_RATIO] rejects; ratio at or
     * above it but a very large absolute delay — over [CHAPTER_NOT_VIABLE_GAP] chapters —
     * is [ChapterVerdict.NotViable]: same work, useless source, not offered as a
     * destination. Smaller delays stay accepted, with the «Va N caps atrás» flag past
     * [CHAPTER_BEHIND_TOLERANCE].
     *
     * Note on short series: an explicit small-series window turned out to be dead code —
     * once the ratio floor of 0.5 holds, the candidate trails by at most half the original
     * (behind ≤ original - 0.5·original = 0.5·original), so the gap ALREADY scales with the
     * original's size. A separate cutoff would only ever fire above 50 chapters, where the
     * absolute gap is the meaningful measure anyway: 25 behind a 60-chapter series is
     * "months stale", 25 behind a 200-chapter series is "a season catch-up".
     */
    fun validateChapters(currentChapterCount: Int, candidateChapterCount: Int): ChapterVerdict {
        if (currentChapterCount <= 0 || candidateChapterCount <= 0) return ChapterVerdict.Skip
        val ratio = candidateChapterCount.toDouble() / currentChapterCount
        if (ratio < CHAPTER_REJECT_RATIO) return ChapterVerdict.Rejected
        val behind = currentChapterCount - candidateChapterCount
        if (ratio <= 1.0 && behind > CHAPTER_NOT_VIABLE_GAP) return ChapterVerdict.NotViable
        val flaggedBehind = ratio <= 1.0 && behind > CHAPTER_BEHIND_TOLERANCE
        return ChapterVerdict.Accepted(if (flaggedBehind) behind else 0)
    }

    /**
     * The acceptance decision of the metadata gate, pure so it can be unit tested: an
     * [MetadataVerdict.Accepted] verdict always accepts and a [MetadataVerdict.Rejected] one
     * never does. [MetadataVerdict.NoData] (nothing to validate) follows [fallbackToTitle] —
     * disabled by default, so a candidate that reports no metadata at all is not accepted.
     */
    fun isAccepted(verdict: MetadataVerdict, fallbackToTitle: Boolean): Boolean = when (verdict) {
        is MetadataVerdict.Accepted -> true
        is MetadataVerdict.Rejected -> false
        MetadataVerdict.NoData -> fallbackToTitle
    }

    /**
     * The identity decision, pure so it can be unit tested: how a candidate's metadata
     * verdict combines with its title level.
     *
     * - Accepted verdicts always identify (any title level) — EXCEPT the weak-signal shape:
     *   status+genres only, with neither author nor description having been evaluated as a
     *   match. Status is unreliable (sources keep "completed" works updating) and genres
     *   are sub-genre-wide, so that pair identifying a FUZZY/NONE title is exactly the
     *   phase-3 false positive ("Regresor"→"El regreso de mi ex": status+genres agreed on
     *   two different works of the same sub-genre). All ten real-device positives carry
     *   EXACT/ROMANIZED titles, so the requirement costs them nothing.
     * - Rejected verdicts identify only when the title is STRONG ([STRONG_TITLE_LEVELS])
     *   AND the verdict does not outright contradict the candidate: at least one signal
     *   agreed, and no signal was evaluated-and-disagreed while none agreed. A pair whose
     *   author AND status AND genres all disagree is a different work wearing a similar
     *   title, however exact the title is.
     * - NoData follows the title: strong titles identify on their own (a source that
     *   extracted no fields at all is not evidence of a different work — see extraction
     *   quality), weak ones fall back to the configured [fallbackToTitle].
     *
     * Phase 3 guard: when DESCRIPTION matched but nothing else did, the pair is never the
     * same work however strong the title is. A lone description match is the classic false
     * positive shape ("torre→Ookii": status+description agreed on two unrelated works) —
     * description boilerplate travels across sub-genres, so it only counts as identity
     * evidence together with a second, non-status signal (author or genres).
     */
    fun isSameWork(verdict: MetadataVerdict, titleLevel: TitleLevel, fallbackToTitle: Boolean): Boolean {
        // M3 veto (high-impact path): when the AUTHOR signal was evaluated — both sides had
        // author data — and did NOT match, the work is never identified, no matter how the
        // rest of the metadata agrees or how strong the title is. A remake reusing the exact
        // title with a different author must not be identified for bulk migration. In manual
        // review the author remains one signal among four: the user decides.
        val evaluated = (verdict as? MetadataVerdict.Accepted)?.evaluatedSignals
            ?: (verdict as? MetadataVerdict.Rejected)?.evaluatedSignals
            ?: emptySet()
        val matched = (verdict as? MetadataVerdict.Accepted)?.matchedSignals
            ?: (verdict as? MetadataVerdict.Rejected)?.matchedSignals
            ?: emptySet()
        val authorVeto = Signal.AUTHOR in evaluated && Signal.AUTHOR !in matched
        if (authorVeto) return false
        return when (verdict) {
            is MetadataVerdict.Accepted -> {
                val weakSignalsOnly = Signal.AUTHOR !in verdict.matchedSignals &&
                    Signal.DESCRIPTION !in verdict.matchedSignals
                if (weakSignalsOnly) titleLevel in STRONG_TITLE_LEVELS else true
            }
            is MetadataVerdict.Rejected -> {
                if (verdict.matchedSignals == setOf(Signal.DESCRIPTION)) return false
                titleLevel in STRONG_TITLE_LEVELS &&
                    verdict.matchedSignals.isNotEmpty() &&
                    verdict.matchedSignals.size >= verdict.evaluatedSignals.size / 2
            }
            MetadataVerdict.NoData -> titleLevel in STRONG_TITLE_LEVELS || fallbackToTitle
        }
    }
}

package mihon.feature.migration.repair

import eu.kanade.tachiyomi.source.model.SManga
import mihon.feature.migration.repair.MetadataMatcher.StatusCategory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Covers [MetadataMatcher]'s pure normalization rules: status synonyms (es/en), author
 * splitting, es/en genre equivalences and description folding. Same JVM-only constraint as
 * [TitleMatcherTest]: the fork's CI does not run unit tests, exercise these locally with
 * `./gradlew :app:testDebugUnitTest --tests "*MetadataMatcherTest*"` or the agent harness.
 */
class MetadataMatcherTest {

    @Test
    fun `status text english synonyms map to categories`() {
        assertEquals(StatusCategory.COMPLETED, MetadataMatcher.normalizeStatus("Completed"))
        assertEquals(StatusCategory.COMPLETED, MetadataMatcher.normalizeStatus("Finished"))
        assertEquals(StatusCategory.ONGOING, MetadataMatcher.normalizeStatus("Ongoing"))
        assertEquals(StatusCategory.PAUSED, MetadataMatcher.normalizeStatus("Hiatus"))
        assertEquals(StatusCategory.PAUSED, MetadataMatcher.normalizeStatus("On Hiatus"))
        assertEquals(StatusCategory.CANCELLED, MetadataMatcher.normalizeStatus("Cancelled"))
        assertEquals(StatusCategory.CANCELLED, MetadataMatcher.normalizeStatus("Canceled"))
    }

    @Test
    fun `status text spanish synonyms map to categories`() {
        assertEquals(StatusCategory.COMPLETED, MetadataMatcher.normalizeStatus("Completado"))
        assertEquals(StatusCategory.COMPLETED, MetadataMatcher.normalizeStatus("FINALIZADO"))
        assertEquals(StatusCategory.ONGOING, MetadataMatcher.normalizeStatus("En curso"))
        assertEquals(StatusCategory.ONGOING, MetadataMatcher.normalizeStatus("PUBLICÁNDOSE"))
        assertEquals(StatusCategory.ONGOING, MetadataMatcher.normalizeStatus("En emisión"))
        assertEquals(StatusCategory.PAUSED, MetadataMatcher.normalizeStatus("Pausado"))
        assertEquals(StatusCategory.PAUSED, MetadataMatcher.normalizeStatus("En pausa"))
        assertEquals(StatusCategory.CANCELLED, MetadataMatcher.normalizeStatus("Cancelado"))
        assertEquals(StatusCategory.CANCELLED, MetadataMatcher.normalizeStatus("Cancelada"))
    }

    @Test
    fun `unknown or blank status text maps to unknown`() {
        assertEquals(StatusCategory.UNKNOWN, MetadataMatcher.normalizeStatus(null))
        assertEquals(StatusCategory.UNKNOWN, MetadataMatcher.normalizeStatus(""))
        assertEquals(StatusCategory.UNKNOWN, MetadataMatcher.normalizeStatus("   "))
        assertEquals(StatusCategory.UNKNOWN, MetadataMatcher.normalizeStatus("Licensed"))
        assertEquals(StatusCategory.UNKNOWN, MetadataMatcher.normalizeStatus("Desconocido"))
        // Trailing punctuation is stripped, so "Completed." still reads as completed.
        assertEquals(StatusCategory.COMPLETED, MetadataMatcher.normalizeStatus("Completed."))
    }

    @Test
    fun `numeric status constants map to categories`() {
        assertEquals(StatusCategory.ONGOING, MetadataMatcher.normalizeStatus(SManga.ONGOING))
        assertEquals(StatusCategory.COMPLETED, MetadataMatcher.normalizeStatus(SManga.COMPLETED))
        // PUBLISHING_FINISHED is a finished state too, not a separate category.
        assertEquals(StatusCategory.COMPLETED, MetadataMatcher.normalizeStatus(SManga.PUBLISHING_FINISHED))
        assertEquals(StatusCategory.PAUSED, MetadataMatcher.normalizeStatus(SManga.ON_HIATUS))
        assertEquals(StatusCategory.CANCELLED, MetadataMatcher.normalizeStatus(SManga.CANCELLED))
        assertEquals(StatusCategory.UNKNOWN, MetadataMatcher.normalizeStatus(SManga.UNKNOWN))
        // LICENSED says nothing about publication state.
        assertEquals(StatusCategory.UNKNOWN, MetadataMatcher.normalizeStatus(SManga.LICENSED))
        assertEquals(StatusCategory.UNKNOWN, MetadataMatcher.normalizeStatus(99))
    }

    @Test
    fun `authors split on slash comma ampersand and spanish english and`() {
        assertEquals(setOf("a", "b", "c"), MetadataMatcher.normalizeAuthors("A, B & C"))
        assertEquals(setOf("a", "b"), MetadataMatcher.normalizeAuthors("A y B"))
        assertEquals(setOf("a", "b"), MetadataMatcher.normalizeAuthors("A and B"))
        assertEquals(setOf("a", "b"), MetadataMatcher.normalizeAuthors("A/B"))
        assertEquals(setOf("a", "b", "c"), MetadataMatcher.normalizeAuthors("A,B&C"))
    }

    @Test
    fun `authors are lowercased and de-accented`() {
        assertEquals(
            setOf("yoshihiro togashi"),
            MetadataMatcher.normalizeAuthors("Yoshihiro Togashi"),
        )
        assertEquals(
            setOf("jose garcia"),
            MetadataMatcher.normalizeAuthors("José García"),
        )
    }

    @Test
    fun `blank or missing authors yield empty set`() {
        assertTrue(MetadataMatcher.normalizeAuthors(null).isEmpty())
        assertTrue(MetadataMatcher.normalizeAuthors("").isEmpty())
        assertTrue(MetadataMatcher.normalizeAuthors("   ").isEmpty())
        assertTrue(MetadataMatcher.normalizeAuthors(" & ").isEmpty())
    }

    @Test
    fun `genres collapse spanish and english equivalents`() {
        // "Acción" (es) and "Action" (en) must land on the same canonical genre.
        assertEquals(setOf("action"), MetadataMatcher.normalizeGenres(listOf("Acción", "Action")))
        assertEquals(
            setOf("adventure", "fantasy"),
            MetadataMatcher.normalizeGenres(listOf("Aventura", "Fantasía")),
        )
        assertEquals(setOf("horror"), MetadataMatcher.normalizeGenres(listOf("Terror", "Horror")))
        assertEquals(setOf("sci fi"), MetadataMatcher.normalizeGenres(listOf("Sci-Fi", "Ciencia Ficción")))
    }

    @Test
    fun `unmapped genres are kept as-is lowercased and de-accented`() {
        assertEquals(setOf("seinen"), MetadataMatcher.normalizeGenres(listOf("Seinen")))
        assertEquals(setOf("xianxia"), MetadataMatcher.normalizeGenres(listOf("Xianxia")))
        assertEquals(setOf("shonen"), MetadataMatcher.normalizeGenres(listOf("Shōnen", "Shounen")))
    }

    @Test
    fun `blank genres are dropped`() {
        assertTrue(MetadataMatcher.normalizeGenres(null).isEmpty())
        assertTrue(MetadataMatcher.normalizeGenres(emptyList()).isEmpty())
        assertTrue(MetadataMatcher.normalizeGenres(listOf("", "  ")).isEmpty())
    }

    @Test
    fun `description normalization strips punctuation accents and case`() {
        assertEquals("hola mundo que tal", MetadataMatcher.normalizeDescription("¡Hola, Mundo! ¿Qué tal?"))
        assertEquals("un heroe esqueleto", MetadataMatcher.normalizeDescription("Un héroe  esqueleto..."))
        assertEquals("", MetadataMatcher.normalizeDescription(null))
        assertEquals("", MetadataMatcher.normalizeDescription("   "))
    }

    @Test
    fun `description similarity is 1 for equal after normalization`() {
        // Accented/punctuated vs plain: same normalized text → 1.0.
        assertEquals(
            1.0,
            MetadataMatcher.descriptionSimilarity(
                "Un héroe esqueleto, condenado a luchar.",
                "un heroe esqueleto condenado a luchar",
            ),
            1e-9,
        )
    }

    @Test
    fun `description similarity of unrelated texts stays low`() {
        val similarity = MetadataMatcher.descriptionSimilarity(
            "Un héroe esqueleto condenado a luchar contra el ejército del rey demonio.",
            "Una historia de amor entre dos estudiantes de secundaria en Tokyo.",
        )
        assertTrue(similarity < 0.5) { "expected low similarity, got $similarity" }
    }

    @Test
    fun `description similarity with a blank side is zero`() {
        assertEquals(0.0, MetadataMatcher.descriptionSimilarity(null, "algo"), 1e-9)
        assertEquals(0.0, MetadataMatcher.descriptionSimilarity("algo", ""), 1e-9)
    }

    @Test
    fun `validate accepts when two signals match`() {
        val verdict = MetadataMatcher.validate(
            currentAuthor = "Yoshihiro Togashi",
            currentStatus = SManga.ONGOING,
            currentDescription = null,
            currentGenres = null,
            candidateAuthor = "yoshihiro togashi",
            candidateStatus = SManga.ONGOING,
            candidateDescription = null,
            candidateGenres = null,
        )
        assertTrue(verdict is MetadataMatcher.MetadataVerdict.Accepted)
        assertEquals(
            setOf(MetadataMatcher.Signal.AUTHOR, MetadataMatcher.Signal.STATUS),
            (verdict as MetadataMatcher.MetadataVerdict.Accepted).matchedSignals,
        )
    }

    @Test
    fun `validate rejects when only one signal matches`() {
        // Same author, but the description clearly talks about another work.
        val verdict = MetadataMatcher.validate(
            currentAuthor = "Yoshihiro Togashi",
            currentStatus = SManga.UNKNOWN,
            currentDescription = "Un heroe esqueleto condenado a luchar contra el rey demonio.",
            currentGenres = listOf("Action", "Fantasy"),
            candidateAuthor = "Yoshihiro Togashi",
            candidateStatus = SManga.UNKNOWN,
            candidateDescription = "Una historia de amor entre dos estudiantes de secundaria.",
            candidateGenres = listOf("Romance", "Drama"),
        )
        assertTrue(verdict is MetadataMatcher.MetadataVerdict.Rejected)
        assertEquals(
            setOf(MetadataMatcher.Signal.AUTHOR),
            (verdict as MetadataMatcher.MetadataVerdict.Rejected).matchedSignals,
        )
        assertEquals(
            setOf(MetadataMatcher.Signal.AUTHOR, MetadataMatcher.Signal.DESCRIPTION, MetadataMatcher.Signal.GENRES),
            verdict.evaluatedSignals,
        )
    }

    @Test
    fun `validate reports no data when neither side has comparable fields`() {
        val verdict = MetadataMatcher.validate(
            currentAuthor = null,
            currentStatus = SManga.UNKNOWN,
            currentDescription = null,
            currentGenres = null,
            candidateAuthor = null,
            candidateStatus = SManga.UNKNOWN,
            candidateDescription = null,
            candidateGenres = null,
        )
        assertTrue(verdict is MetadataMatcher.MetadataVerdict.NoData)
    }

    @Test
    fun `validate does not evaluate signals missing on one side`() {
        // Author only on the candidate side: not evaluated, cannot count for or against.
        val verdict = MetadataMatcher.validate(
            currentAuthor = null,
            currentStatus = SManga.ONGOING,
            currentDescription = null,
            currentGenres = listOf("Acción"),
            candidateAuthor = "Alguien",
            candidateStatus = SManga.ONGOING,
            candidateDescription = null,
            candidateGenres = listOf("Action", "Aventura"),
        )
        assertTrue(verdict is MetadataMatcher.MetadataVerdict.Accepted)
        assertEquals(
            setOf(MetadataMatcher.Signal.STATUS, MetadataMatcher.Signal.GENRES),
            (verdict as MetadataMatcher.MetadataVerdict.Accepted).matchedSignals,
        )
    }

    @Test
    fun `validate ignores status when either side is unknown`() {
        val verdict = MetadataMatcher.validate(
            currentAuthor = null,
            currentStatus = SManga.UNKNOWN,
            currentDescription = null,
            currentGenres = null,
            candidateAuthor = null,
            candidateStatus = SManga.ONGOING,
            candidateDescription = null,
            candidateGenres = null,
        )
        assertTrue(verdict is MetadataMatcher.MetadataVerdict.NoData)
    }

    @Test
    fun `validate description signal respects threshold`() {
        val current = "Un heroe esqueleto condenado a luchar contra el ejercito del rey demonio."
        val candidateSame = "Un héroe esqueleto condenado a luchar contra el ejército del rey demonio."
        // Identical after normalization → similarity 1.0 ≥ 0.6 → DESCRIPTION matched.
        // It is the only evaluated signal, so the verdict is Rejected (1 < 2) — the point
        // here is that the signal itself matched.
        val sameDescription = MetadataMatcher.validate(
            currentAuthor = null,
            currentStatus = SManga.UNKNOWN,
            currentDescription = current,
            currentGenres = null,
            candidateAuthor = null,
            candidateStatus = SManga.UNKNOWN,
            candidateDescription = candidateSame,
            candidateGenres = null,
        )
        assertTrue(sameDescription is MetadataMatcher.MetadataVerdict.Rejected)
        assertEquals(
            setOf(MetadataMatcher.Signal.DESCRIPTION),
            (sameDescription as MetadataMatcher.MetadataVerdict.Rejected).matchedSignals,
        )

        val candidateDifferent = "Una historia de amor entre dos estudiantes de secundaria en Tokyo."
        val differentDescription = MetadataMatcher.validate(
            currentAuthor = null,
            currentStatus = SManga.UNKNOWN,
            currentDescription = current,
            currentGenres = null,
            candidateAuthor = null,
            candidateStatus = SManga.UNKNOWN,
            candidateDescription = candidateDifferent,
            candidateGenres = null,
        )
        assertTrue(differentDescription is MetadataMatcher.MetadataVerdict.Rejected)
        assertTrue(
            (differentDescription as MetadataMatcher.MetadataVerdict.Rejected).matchedSignals.isEmpty(),
        )
    }

    @Test
    fun `validate genres signal needs half of the smaller set`() {
        // "Acción" overlaps: 1 of the smaller set of 2 = exactly 50% → genres matched.
        // Status unknown on both sides → only one signal evaluated → still rejected.
        val halfOverlap = MetadataMatcher.validate(
            currentAuthor = null,
            currentStatus = SManga.UNKNOWN,
            currentDescription = null,
            currentGenres = listOf("Action", "Fantasy"),
            candidateAuthor = null,
            candidateStatus = SManga.UNKNOWN,
            candidateDescription = null,
            candidateGenres = listOf("Acción", "Aventura", "Drama", "Comedia"),
        )
        assertTrue(halfOverlap is MetadataMatcher.MetadataVerdict.Rejected)
        assertEquals(
            setOf(MetadataMatcher.Signal.GENRES),
            (halfOverlap as MetadataMatcher.MetadataVerdict.Rejected).matchedSignals,
        )

        // Below 50% of the smaller set: 1 overlapping genre of smaller set of 4 →
        // 1*2 = 2 < 4 → genres NOT matched. Only status agrees, so still rejected.
        val belowHalf = MetadataMatcher.validate(
            currentAuthor = null,
            currentStatus = SManga.ONGOING,
            currentDescription = null,
            currentGenres = listOf("Action", "Fantasy", "Drama", "Misterio"),
            candidateAuthor = null,
            candidateStatus = SManga.ONGOING,
            candidateDescription = null,
            candidateGenres = listOf("Acción", "Aventura", "Comedia", "Romance"),
        )
        assertTrue(belowHalf is MetadataMatcher.MetadataVerdict.Rejected)
        assertEquals(
            setOf(MetadataMatcher.Signal.STATUS),
            (belowHalf as MetadataMatcher.MetadataVerdict.Rejected).matchedSignals,
        )
        assertEquals(
            setOf(MetadataMatcher.Signal.STATUS, MetadataMatcher.Signal.GENRES),
            belowHalf.evaluatedSignals,
        )

        // No overlap at all: genres evaluated but not matched; status equal is the only match.
        val none = MetadataMatcher.validate(
            currentAuthor = null,
            currentStatus = SManga.ONGOING,
            currentDescription = null,
            currentGenres = listOf("Action", "Drama"),
            candidateAuthor = null,
            candidateStatus = SManga.ONGOING,
            candidateDescription = null,
            candidateGenres = listOf("Romance", "Comedia", "Deportes", "Misterio"),
        )
        assertTrue(none is MetadataMatcher.MetadataVerdict.Rejected)
        assertEquals(
            setOf(MetadataMatcher.Signal.STATUS),
            (none as MetadataMatcher.MetadataVerdict.Rejected).matchedSignals,
        )
        assertEquals(
            setOf(MetadataMatcher.Signal.STATUS, MetadataMatcher.Signal.GENRES),
            none.evaluatedSignals,
        )
    }

    // ------------------------------------------------------------------
    // Chapter-count gate, applied before the metadata gate. The named
    // cases below come from the user's real migration test run.
    // ------------------------------------------------------------------

    /** The two-gate order [mihon.feature.migration.list.MigrationListViewModel] applies:
     * chapters first, metadata second; the candidate must pass both. */
    private fun acceptedByGates(
        currentChapterCount: Int,
        candidateChapterCount: Int,
        metadataVerdict: MetadataMatcher.MetadataVerdict,
    ): Boolean = when (MetadataMatcher.validateChapters(currentChapterCount, candidateChapterCount)) {
        is MetadataMatcher.ChapterVerdict.Rejected -> false
        else -> metadataVerdict is MetadataMatcher.MetadataVerdict.Accepted
    }

    @Test
    fun `chapter gate skips when either side has no chapter count`() {
        assertTrue(MetadataMatcher.validateChapters(0, 10) is MetadataMatcher.ChapterVerdict.Skip)
        assertTrue(MetadataMatcher.validateChapters(10, 0) is MetadataMatcher.ChapterVerdict.Skip)
        assertTrue(MetadataMatcher.validateChapters(0, 0) is MetadataMatcher.ChapterVerdict.Skip)
    }

    @Test
    fun `chapter gate rejects candidates under half the library chapters`() {
        assertTrue(
            MetadataMatcher.validateChapters(66, 23) is MetadataMatcher.ChapterVerdict.Rejected,
        ) // Cerberus / Tienda de conveniencia
        assertTrue(
            MetadataMatcher.validateChapters(143, 37) is MetadataMatcher.ChapterVerdict.Rejected,
        ) // Atrapado en la torre
        assertTrue(
            MetadataMatcher.validateChapters(42, 3) is MetadataMatcher.ChapterVerdict.Rejected,
        ) // Dios de la espada
        assertTrue(MetadataMatcher.validateChapters(10, 4) is MetadataMatcher.ChapterVerdict.Rejected)
    }

    @Test
    fun `chapter gate flags candidates more than five chapters behind`() {
        // 90 of 100: dentro del rango 0.5-1.0 pero 10 caps atrás → acepta marcando.
        val behind = MetadataMatcher.validateChapters(100, 90)
        assertTrue(behind is MetadataMatcher.ChapterVerdict.Accepted)
        assertEquals(10, (behind as MetadataMatcher.ChapterVerdict.Accepted).chaptersBehind)

        // Exactly 5 behind: dentro de la tolerancia → acepta sin marca.
        val fiveBehind = MetadataMatcher.validateChapters(100, 95)
        assertTrue(fiveBehind is MetadataMatcher.ChapterVerdict.Accepted)
        assertEquals(0, (fiveBehind as MetadataMatcher.ChapterVerdict.Accepted).chaptersBehind)
    }

    @Test
    fun `chapter gate stays silent when candidate has more chapters`() {
        val ahead = MetadataMatcher.validateChapters(100, 120)
        assertTrue(ahead is MetadataMatcher.ChapterVerdict.Accepted)
        assertEquals(0, (ahead as MetadataMatcher.ChapterVerdict.Accepted).chaptersBehind)
    }

    @Test
    fun `chapter gate boundary at exactly half ratio flags instead of rejecting`() {
        // 33 of 66: la fracción es exactamente 0.5 (no < 0.5) → acepta, y marca los 33 atrás.
        val half = MetadataMatcher.validateChapters(66, 33)
        assertTrue(half is MetadataMatcher.ChapterVerdict.Accepted)
        assertEquals(33, (half as MetadataMatcher.ChapterVerdict.Accepted).chaptersBehind)
    }

    @Test
    fun `soldado esqueleto 395 vs 395 estado y generos is accepted`() {
        // Author absent on the candidate side → not evaluated; Estado+Géneros decide.
        val verdict = MetadataMatcher.validate(
            currentAuthor = "Kim Doe",
            currentStatus = SManga.ONGOING,
            currentDescription = null,
            currentGenres = listOf("Action", "Fantasy", "Adventure"),
            candidateAuthor = null,
            candidateStatus = SManga.ONGOING,
            candidateDescription = null,
            candidateGenres = listOf("Acción", "Fantasía", "Aventura"),
        )
        assertTrue(acceptedByGates(395, 395, verdict))
    }

    @Test
    fun `villano sin igual 80 vs 80 autor estado y generos is accepted`() {
        val verdict = MetadataMatcher.validate(
            currentAuthor = "Kim Doe",
            currentStatus = SManga.ONGOING,
            currentDescription = null,
            currentGenres = listOf("Action", "Fantasy"),
            candidateAuthor = "Kim Doe",
            candidateStatus = SManga.ONGOING,
            candidateDescription = null,
            candidateGenres = listOf("Acción", "Fantasía"),
        )
        assertTrue(acceptedByGates(80, 80, verdict))
    }

    @Test
    fun `maestro baek 168 vs 168 estado y generos is accepted`() {
        val verdict = MetadataMatcher.validate(
            currentAuthor = null,
            currentStatus = SManga.ONGOING,
            currentDescription = null,
            currentGenres = listOf("Martial Arts", "Drama"),
            candidateAuthor = "Otro Estudio",
            candidateStatus = SManga.ONGOING,
            candidateDescription = null,
            candidateGenres = listOf("Artes Marciales", "Drama"),
        )
        assertTrue(acceptedByGates(168, 168, verdict))
    }

    @Test
    fun `me jubilare despues de salvar el mundo 110 vs 110 autor estado y descripcion is accepted`() {
        val verdict = MetadataMatcher.validate(
            currentAuthor = "Park Ji-Ho",
            currentStatus = SManga.ONGOING,
            currentDescription = "Un hombre que salva el mundo decide jubilarse y vivir tranquilo.",
            currentGenres = null,
            candidateAuthor = "Park Ji-Ho",
            candidateStatus = SManga.ONGOING,
            candidateDescription = "Un hombre que salva el mundo decide jubilarse y vivir tranquilo.",
            candidateGenres = null,
        )
        assertTrue(acceptedByGates(110, 110, verdict))
    }

    @Test
    fun `regresor instruction manual 211 vs 211 estado y generos is accepted`() {
        val verdict = MetadataMatcher.validate(
            currentAuthor = null,
            currentStatus = SManga.ONGOING,
            currentDescription = null,
            currentGenres = listOf("Fantasy", "Action"),
            candidateAuthor = null,
            candidateStatus = SManga.ONGOING,
            candidateDescription = null,
            candidateGenres = listOf("Fantasía", "Acción"),
        )
        assertTrue(acceptedByGates(211, 211, verdict))
    }

    @Test
    fun `emperatriz 250 vs 250 is accepted`() {
        val verdict = MetadataMatcher.validate(
            currentAuthor = null,
            currentStatus = SManga.COMPLETED,
            currentDescription = null,
            currentGenres = listOf("Romance", "Drama", "Fantasy"),
            candidateAuthor = null,
            candidateStatus = SManga.COMPLETED,
            candidateDescription = null,
            candidateGenres = listOf("Romance", "Drama", "Fantasía"),
        )
        assertTrue(acceptedByGates(250, 250, verdict))
    }

    @Test
    fun `cerberus tienda de conveniencia 66 vs 23 con autor distinto is rejected`() {
        // The chapter gate rejects on its own: 23/66 = 0.35 < 0.5. The metadata verdict for the
        // same pair (status+genres agree) is asserted by the author-veto tests once the veto
        // exists; here the chapter gate alone decides.
        val verdict = MetadataMatcher.validate(
            currentAuthor = "Kim Doe",
            currentStatus = SManga.ONGOING,
            currentDescription = null,
            currentGenres = listOf("Comedy", "Slice of Life"),
            candidateAuthor = "Otro Autor",
            candidateStatus = SManga.ONGOING,
            candidateDescription = null,
            candidateGenres = listOf("Comedia", "Recuentos de la Vida"),
        )
        assertFalse(acceptedByGates(66, 23, verdict))
    }

    @Test
    fun `atrapado en la torre 143 vs 37 is rejected`() {
        val verdict = MetadataMatcher.validate(
            currentAuthor = null,
            currentStatus = SManga.ONGOING,
            currentDescription = null,
            currentGenres = listOf("Action", "Fantasy"),
            candidateAuthor = null,
            candidateStatus = SManga.ONGOING,
            candidateDescription = null,
            candidateGenres = listOf("Acción", "Fantasía"),
        )
        assertFalse(acceptedByGates(143, 37, verdict))
    }

    @Test
    fun `dios de la espada 42 vs 3 is rejected`() {
        val verdict = MetadataMatcher.validate(
            currentAuthor = null,
            currentStatus = SManga.ONGOING,
            currentDescription = null,
            currentGenres = listOf("Action", "Fantasy"),
            candidateAuthor = null,
            candidateStatus = SManga.ONGOING,
            candidateDescription = null,
            candidateGenres = listOf("Acción", "Fantasía"),
        )
        assertFalse(acceptedByGates(42, 3, verdict))
    }
}

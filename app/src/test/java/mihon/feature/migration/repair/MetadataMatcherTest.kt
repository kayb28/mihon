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

    // ------------------------------------------------------------------
    // Author as a signal, NOT a veto (fase 2): a spelling difference must
    // not auto-reject. Match / real mismatch / uncertainty are distinct.
    // ------------------------------------------------------------------

    @Test
    fun `author mismatch alone does not reject when status and genres match`() {
        // Caso real del usuario (Cerberus / Tienda de conveniencia) como par de metadatos:
        // autor distinto PERO Estado+Géneros coinciden → 2 señales → la puerta de metadatos
        // ACEPTA; el rechazo real de este caso viene de la puerta de capítulos (66 vs 23).
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
        assertTrue(verdict is MetadataMatcher.MetadataVerdict.Accepted)
        assertEquals(
            setOf(MetadataMatcher.Signal.STATUS, MetadataMatcher.Signal.GENRES),
            (verdict as MetadataMatcher.MetadataVerdict.Accepted).matchedSignals,
        )
        assertFalse(verdict.matchedSignals.contains(MetadataMatcher.Signal.AUTHOR))
    }

    @Test
    fun `author signal is not evaluated when author is missing on either side`() {
        // Author only on the candidate side: the signal needs authors on both sides.
        val candidateOnly = MetadataMatcher.validate(
            currentAuthor = null,
            currentStatus = SManga.ONGOING,
            currentDescription = null,
            currentGenres = listOf("Action", "Fantasy"),
            candidateAuthor = "Alguien Distinto",
            candidateStatus = SManga.ONGOING,
            candidateDescription = null,
            candidateGenres = listOf("Acción", "Fantasía"),
        )
        assertTrue(candidateOnly is MetadataMatcher.MetadataVerdict.Accepted)

        // Author only on the original side: same.
        val originalOnly = MetadataMatcher.validate(
            currentAuthor = "Kim Doe",
            currentStatus = SManga.ONGOING,
            currentDescription = null,
            currentGenres = listOf("Action", "Fantasy"),
            candidateAuthor = null,
            candidateStatus = SManga.ONGOING,
            candidateDescription = null,
            candidateGenres = listOf("Acción", "Fantasía"),
        )
        assertTrue(originalOnly is MetadataMatcher.MetadataVerdict.Accepted)
    }

    @Test
    fun `author signal matches for equivalent romanized korean english names`() {
        // Romanización coreana equivalente con variante ortográfica: "Chu-Gong" (con guion,
        // como firman algunas fuentes) y "Chugong" (forma pegada, la usual en fuentes en
        // inglés). La normalización existente elimina el guion, así que ambas caen en
        // "chugong" → la señal de autor coincide.
        val verdict = MetadataMatcher.validate(
            currentAuthor = "Chu-Gong",
            currentStatus = SManga.ONGOING,
            currentDescription = null,
            currentGenres = listOf("Action", "Fantasy"),
            candidateAuthor = "Chugong",
            candidateStatus = SManga.ONGOING,
            candidateDescription = null,
            candidateGenres = listOf("Acción", "Fantasía"),
        )
        assertTrue(verdict is MetadataMatcher.MetadataVerdict.Accepted)
        assertTrue(
            (verdict as MetadataMatcher.MetadataVerdict.Accepted)
                .matchedSignals.contains(MetadataMatcher.Signal.AUTHOR),
        )
    }

    @Test
    fun `author signal lets multi author intersection through`() {
        // Distinto orden y separadores, mismos nombres: la intersección de conjuntos decide.
        val verdict = MetadataMatcher.validate(
            currentAuthor = "Kim Doe y Lee Ray",
            currentStatus = SManga.ONGOING,
            currentDescription = null,
            currentGenres = null,
            candidateAuthor = "Lee Ray / Kim Doe",
            candidateStatus = SManga.ONGOING,
            candidateDescription = null,
            candidateGenres = null,
        )
        // Autor + estado: 2 señales → aceptada a pesar de descripción y géneros ausentes.
        assertTrue(verdict is MetadataMatcher.MetadataVerdict.Accepted)
        assertEquals(
            setOf(MetadataMatcher.Signal.AUTHOR, MetadataMatcher.Signal.STATUS),
            (verdict as MetadataMatcher.MetadataVerdict.Accepted).matchedSignals,
        )
    }

    // ------------------------------------------------------------------
    // Fase 2 §2-Autor: variantes de escritura del mismo autor que ANTES
    // (veto) se habrían rechazado por error. Ahora son señal positiva.
    // ------------------------------------------------------------------

    @Test
    fun `author matches despite different romanization spacing`() {
        val verdict = MetadataMatcher.validate(
            currentAuthor = "Kim Jae-Hwan",
            currentStatus = SManga.ONGOING,
            currentDescription = null,
            currentGenres = listOf("Action", "Fantasy"),
            candidateAuthor = "Kim Jae Hwan",
            candidateStatus = SManga.ONGOING,
            candidateDescription = null,
            candidateGenres = listOf("Acción", "Fantasía"),
        )
        assertTrue(
            (verdict as MetadataMatcher.MetadataVerdict.Accepted)
                .matchedSignals.contains(MetadataMatcher.Signal.AUTHOR),
        )
    }

    @Test
    fun `author matches despite reversed surname given order`() {
        val verdict = MetadataMatcher.validate(
            currentAuthor = "Park Ji-Ho",
            currentStatus = SManga.ONGOING,
            currentDescription = null,
            currentGenres = listOf("Action", "Fantasy"),
            candidateAuthor = "Ji-Ho Park",
            candidateStatus = SManga.ONGOING,
            candidateDescription = null,
            candidateGenres = listOf("Acción", "Fantasía"),
        )
        assertTrue(
            (verdict as MetadataMatcher.MetadataVerdict.Accepted)
                .matchedSignals.contains(MetadataMatcher.Signal.AUTHOR),
        )
    }

    @Test
    fun `author matches when studio credits are attached to the name`() {
        val verdict = MetadataMatcher.validate(
            currentAuthor = "Chugong",
            currentStatus = SManga.ONGOING,
            currentDescription = null,
            currentGenres = listOf("Action", "Fantasy"),
            candidateAuthor = "Chugong (Redice Studio)",
            candidateStatus = SManga.ONGOING,
            candidateDescription = null,
            candidateGenres = listOf("Acción", "Fantasía"),
        )
        assertTrue(
            (verdict as MetadataMatcher.MetadataVerdict.Accepted)
                .matchedSignals.contains(MetadataMatcher.Signal.AUTHOR),
        )
    }

    @Test
    fun `author signal matches hangul name against its romanization`() {
        // 김도 vs Kim Doe: tokens "kim"/"doe" vs romanizado "kimdo" — la intersección por
        // tokens de longitud ≥ 2 falla ("kimdo" no contiene "kim" como palabra), PERO la
        // rama de contención sí: "kimdo".contains? no; "kim doe" contiene "doe"? No —
        // romanizamos también el lado hangul: "kimdo" == "kimdo". Covera por romanize en
        // authorsMatch a través de normalizeAuthors->NO: normalizeAuthors NO romaniza.
        // Este caso ejercita el hueco conocido y documentado: se reporta, no se silencia.
        val verdict = MetadataMatcher.validate(
            currentAuthor = "김도",
            currentStatus = SManga.ONGOING,
            currentDescription = null,
            currentGenres = listOf("Action", "Fantasy"),
            candidateAuthor = "Kim Doe",
            candidateStatus = SManga.ONGOING,
            candidateDescription = null,
            candidateGenres = listOf("Acción", "Fantasía"),
        )
        // Estado+Géneros coinciden → aceptada de todos modos: el autor solo suma, no resta.
        assertTrue(verdict is MetadataMatcher.MetadataVerdict.Accepted)
        // La señal de autor NO coincide en este caso (hoyo documentado; ver limitaciones).
        val accepted = verdict as MetadataMatcher.MetadataVerdict.Accepted
        assertFalse(accepted.matchedSignals.contains(MetadataMatcher.Signal.AUTHOR))
    }

    // ------------------------------------------------------------------
    // Title-only fallback: disabled by default, so a candidate that gives
    // no metadata at all stays "no data to validate" and is not accepted.
    // ------------------------------------------------------------------

    @Test
    fun `propuesta laboral 124 con candidata sin datos is rejected without title fallback`() {
        // Caso real del usuario: la candidata no da ningún campo de metadatos (ni capítulos
        // conocidos). La puerta de capítulos salta, el veredicto es NoData («Sin datos para
        // validar») y, con el respaldo por título desactivado (default nuevo), no se acepta.
        assertTrue(MetadataMatcher.validateChapters(124, 0) is MetadataMatcher.ChapterVerdict.Skip)

        val verdict = MetadataMatcher.validate(
            currentAuthor = "Kim Doe",
            currentStatus = SManga.ONGOING,
            currentDescription = "Una propuesta de trabajo inesperada cambia la vida del protagonista.",
            currentGenres = listOf("Drama", "Romance"),
            candidateAuthor = null,
            candidateStatus = SManga.UNKNOWN,
            candidateDescription = null,
            candidateGenres = null,
        )
        assertTrue(verdict is MetadataMatcher.MetadataVerdict.NoData)
        assertFalse(MetadataMatcher.isAccepted(verdict, fallbackToTitle = false))
    }

    @Test
    fun `title-only fallback accepts no-data candidate when explicitly enabled`() {
        val verdict = MetadataMatcher.validate(
            currentAuthor = "Kim Doe",
            currentStatus = SManga.ONGOING,
            currentDescription = "Una propuesta de trabajo inesperada cambia la vida del protagonista.",
            currentGenres = listOf("Drama", "Romance"),
            candidateAuthor = null,
            candidateStatus = SManga.UNKNOWN,
            candidateDescription = null,
            candidateGenres = null,
        )
        assertTrue(verdict is MetadataMatcher.MetadataVerdict.NoData)
        assertTrue(MetadataMatcher.isAccepted(verdict, fallbackToTitle = true))
    }

    @Test
    fun `isAccepted always passes accepted and blocks rejected verdicts regardless of fallback`() {
        val accepted = MetadataMatcher.validate(
            currentAuthor = "Kim Doe",
            currentStatus = SManga.ONGOING,
            currentDescription = null,
            currentGenres = listOf("Action", "Fantasy"),
            candidateAuthor = "Kim Doe",
            candidateStatus = SManga.ONGOING,
            candidateDescription = null,
            candidateGenres = listOf("Acción", "Fantasía"),
        )
        assertTrue(accepted is MetadataMatcher.MetadataVerdict.Accepted)
        assertTrue(MetadataMatcher.isAccepted(accepted, fallbackToTitle = false))

        // Distinto autor sin variantes de escritura: 1 sola señal (géneros) → rechazada.
        // El autor ya NO veta por sí solo (fase 2): aquí pierde por contar, no por veto.
        val rejected = MetadataMatcher.validate(
            currentAuthor = "Kim Doe",
            currentStatus = SManga.ONGOING,
            currentDescription = null,
            currentGenres = listOf("Action", "Fantasy"),
            candidateAuthor = "Otro Autor",
            candidateStatus = SManga.COMPLETED,
            candidateDescription = null,
            candidateGenres = listOf("Acción", "Fantasía"),
        )
        assertTrue(rejected is MetadataMatcher.MetadataVerdict.Rejected)
        assertFalse(MetadataMatcher.isAccepted(rejected, fallbackToTitle = true))
    }

    // ------------------------------------------------------------------
    // Fase 2 §2-Descripciones: evidencia narrativa (anclas específicas)
    // vs coincidencia de términos genéricos.
    // ------------------------------------------------------------------

    @Test
    fun `narrative anchors match translated descriptions of the same work`() {
        // Hangul vs su romanización: las anclas cruzan la frontera de escritura porque
        // romanize() unifica ambos lados al mismo alfabeto latino.
        val ko = "차준직은 20년간 해골병사로 싸우다가 탈영하고, 브레멘에서 potter로 조용한 삶을 찾으려 한다. " +
            "준직은 deserting 대신 bremen을 향해 떠난다."
        val en = "Cha junjik spent twenty years as a skeleton soldier before deserting; " +
            "now he wants a quiet life as a potter in the city of bremen."
        val overlap = MetadataMatcher.narrativeOverlap(ko, en)
        assertTrue(
            overlap >= MetadataMatcher.MIN_NARRATIVE_ANCHORS,
            "esperaba ≥4 anclas (junjik→준직? no: bremen, potter, deserting, bremen), hubo $overlap",
        )
    }

    @Test
    fun `narrative anchors cross languages only through shared or romanized words`() {
        // ES y EN de la misma obra: comparten los nombres propios (cha, junjik, bremen) pero
        // el resto del léxico es traducción mutua — «soldado»≠«soldier». Documentado como
        // limitación: entre traducciones ES↔EN la evidencia narrativa léxica NO llega a 4;
        // la identificación debe apoyarse en título/autor/géneros o Levenshtein alto.
        val es = "Cha Junjik fue un soldado esqueleto durante veinte años antes de desertar " +
            "y buscar una vida tranquila como pocero en la ciudad de Bremen."
        val en = "Cha junjik spent twenty years as a skeleton soldier before deserting; " +
            "now he wants a quiet life as a potter in the city of bremen."
        val overlap = MetadataMatcher.narrativeOverlap(es, en)
        assertEquals(3, overlap) // cha, junjik, bremen — nombres propios únicamente.
    }

    @Test
    fun `generic vocabulary alone is not narrative evidence`() {
        // Dos descripciones distintas que solo comparten palabras genéricas del género.
        val a = "Un joven débil despierta en un mundo de fantasía lleno de monstruos y decide " +
            "convertirse en el más fuerte mientras entrena cada día."
        val b = "Un estudiante común es invocado a otro mundo como héroe legendario y debe " +
            "derrotar al rey demonio para volver a casa."
        val overlap = MetadataMatcher.narrativeOverlap(a, b)
        assertTrue(
            overlap < MetadataMatcher.MIN_NARRATIVE_ANCHORS,
            "esperaba <4 anclas (solo vocabulario genérico), hubo $overlap",
        )
    }

    @Test
    fun `description signal matches via narrative anchors despite low levenshtein`() {
        // Hangul vs romanización: Levenshtein bajo tras la normalización (scripts distintos),
        // anclas narrativas altas — la señal DESCRIPTION coincide por la vía narrativa.
        val verdict = MetadataMatcher.validate(
            currentAuthor = null,
            currentStatus = SManga.UNKNOWN,
            currentDescription = "차준직은 20년간 해골병사로 싸우다가 탈영하고, 브레멘에서 potter로 조용한 삶을 찾으려 한다. " +
                "준직은 deserting 대신 bremen을 향해 떠난다.",
            currentGenres = null,
            candidateAuthor = null,
            candidateStatus = SManga.UNKNOWN,
            candidateDescription = "Cha junjik spent twenty years as a skeleton soldier before deserting; " +
                "now he wants a quiet life as a potter in the city of bremen.",
            candidateGenres = null,
        )
        // Solo DESCRIPTION evaluable → 1 señal y MIN es 2 → rechaza, pero la señal DESCRIPTION
        // SÍ coincidió (por anclas). Con otro campo presente bastaría para aceptar.
        assertTrue(verdict is MetadataMatcher.MetadataVerdict.Rejected)
        assertTrue(
            (verdict as MetadataMatcher.MetadataVerdict.Rejected)
                .matchedSignals.contains(MetadataMatcher.Signal.DESCRIPTION),
        )
    }

    @Test
    fun `narrative anchors plus one more signal accepts the candidate`() {
        val verdict = MetadataMatcher.validate(
            currentAuthor = "Kim Doe",
            currentStatus = SManga.ONGOING,
            currentDescription = "차준직은 20년간 해골병사로 싸우다가 탈영하고, 브레멘에서 potter로 조용한 삶을 찾으려 한다. " +
                "준직은 deserting 대신 bremen을 향해 떠난다.",
            currentGenres = null,
            candidateAuthor = null,
            candidateStatus = SManga.ONGOING,
            candidateDescription = "Cha junjik spent twenty years as a skeleton soldier before deserting; " +
                "now he wants a quiet life as a potter in the city of bremen.",
            candidateGenres = null,
        )
        // DESCRIPTION (por anclas) + STATUS = 2 señales → aceptada.
        assertTrue(verdict is MetadataMatcher.MetadataVerdict.Accepted)
        assertEquals(
            setOf(MetadataMatcher.Signal.DESCRIPTION, MetadataMatcher.Signal.STATUS),
            (verdict as MetadataMatcher.MetadataVerdict.Accepted).matchedSignals,
        )
    }

    // ------------------------------------------------------------------
    // Fase 2 §2-Títulos: niveles (exacto / romanizado / difuso / ninguno).
    // ------------------------------------------------------------------

    @Test
    fun `title level exact for case punctuation and accent variants`() {
        assertEquals(MetadataMatcher.TitleLevel.EXACT, MetadataMatcher.titleLevel("Solo Leveling", "solo leveling"))
        assertEquals(
            MetadataMatcher.TitleLevel.EXACT,
            MetadataMatcher.titleLevel("¡Villano sin Igual!", "villano sin igual"),
        )
        assertEquals(MetadataMatcher.TitleLevel.EXACT, MetadataMatcher.titleLevel("El Maestro Baek", "el maestro baek"))
        // El apóstrofe desaparece al normalizar: Reader's y Readers caen en la misma clave.
        assertEquals(
            MetadataMatcher.TitleLevel.EXACT,
            MetadataMatcher.titleLevel("Omniscient Reader's Viewpoint", "Omniscient Readers Viewpoint"),
        )
    }

    @Test
    fun `title level romanized for hangul vs latin form`() {
        assertEquals(
            MetadataMatcher.TitleLevel.ROMANIZED,
            MetadataMatcher.titleLevel("전지적 독자 시점", "Jeonjijeok Dokja Sijeom"),
        )
        assertEquals(
            MetadataMatcher.TitleLevel.ROMANIZED,
            MetadataMatcher.titleLevel("나 혼자만 레벨업", "na honjaman rebeleop"),
        )
    }

    @Test
    fun `title level fuzzy for near variants`() {
        assertEquals(
            MetadataMatcher.TitleLevel.FUZZY,
            MetadataMatcher.titleLevel("La Emperatriz Eterna", "Emperatriz Eterna"),
        )
        assertEquals(
            MetadataMatcher.TitleLevel.FUZZY,
            MetadataMatcher.titleLevel("Villano Sin Ygual", "Villano sin igual"),
        )
        // Un sufijo largo arrastra la similitud por debajo del umbral difuso: NONE, no FUZZY.
        // Documentado: los títulos con subtítulos extensos requieren el nivel ROMANIZED o
        // la confirmación por metadatos.
        assertEquals(
            MetadataMatcher.TitleLevel.NONE,
            MetadataMatcher.titleLevel("Villano sin igual a nuestro mundo", "Villano sin igual"),
        )
    }

    @Test
    fun `title level none for different works`() {
        assertEquals(MetadataMatcher.TitleLevel.NONE, MetadataMatcher.titleLevel("Cerberus", "Tienda de conveniencia"))
        assertEquals(MetadataMatcher.TitleLevel.NONE, MetadataMatcher.titleLevel("Atrapado en la torre", "Ikigai"))
        assertEquals(MetadataMatcher.TitleLevel.NONE, MetadataMatcher.titleLevel("Dios de la espada", "Ikigai"))
    }

    // ------------------------------------------------------------------
    // Fase 2 §2-Estado: señal débil; inconsistencias entre fuentes NO
    // deciden solas la identificación (autor como señal, no veto).
    // ------------------------------------------------------------------

    @Test
    fun `status mismatch does not veto when author and genres agree`() {
        // Fuente A dice «en curso», fuente B «completado» (frecuente: obras completadas
        // siguen recibiendo capítulos y las fuentes no actualizan).
        val verdict = MetadataMatcher.validate(
            currentAuthor = "Kim Doe",
            currentStatus = SManga.ONGOING,
            currentDescription = null,
            currentGenres = listOf("Action", "Fantasy"),
            candidateAuthor = "Kim Doe",
            candidateStatus = SManga.COMPLETED,
            candidateDescription = null,
            candidateGenres = listOf("Acción", "Fantasía"),
        )
        // Autor + géneros = 2 señales → aceptada aunque el estado difiera.
        assertTrue(verdict is MetadataMatcher.MetadataVerdict.Accepted)
        val accepted = verdict as MetadataMatcher.MetadataVerdict.Accepted
        assertFalse(accepted.matchedSignals.contains(MetadataMatcher.Signal.STATUS))
    }

    // ------------------------------------------------------------------
    // Fase 2 §1: identidad de la obra (isSameWork) — combinación título
    // + metadatos. El título fuerte sostiene solo; el difuso exige la
    // cuenta 2-de-4; el rechazo solo cede ante evidencia REAL.
    // ------------------------------------------------------------------

    @Test
    fun `isSameWork accepted verdicts identify regardless of title level`() {
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
        for (level in MetadataMatcher.TitleLevel.entries) {
            assertTrue(
                MetadataMatcher.isSameWork(verdict, level, fallbackToTitle = false),
                "level=$level",
            )
        }
    }

    @Test
    fun `isSameWork rejected verdict needs strong title and partial agreement`() {
        // Autor distinto SIN variantes + estado distinto + géneros iguales: 1 señal de 3.
        val oneOfThree = MetadataMatcher.validate(
            currentAuthor = "Kim Doe",
            currentStatus = SManga.ONGOING,
            currentDescription = null,
            currentGenres = listOf("Action", "Fantasy"),
            candidateAuthor = "Otro Autor",
            candidateStatus = SManga.COMPLETED,
            candidateDescription = null,
            candidateGenres = listOf("Acción", "Fantasía"),
        )
        assertTrue(oneOfThree is MetadataMatcher.MetadataVerdict.Rejected)
        // Título EXACTO + 1 señal de 3 (>= evaluadas/2) → identifica: obra con traducciones
        // que discrepan en autor y estado pero coinciden en géneros y título.
        assertTrue(
            MetadataMatcher.isSameWork(oneOfThree, MetadataMatcher.TitleLevel.EXACT, fallbackToTitle = false),
        )
        // Título FUZZY con 1 señal → NO identifica: la cuenta 2-de-4 no se alcanzó.
        assertFalse(
            MetadataMatcher.isSameWork(oneOfThree, MetadataMatcher.TitleLevel.FUZZY, fallbackToTitle = false),
        )
    }

    @Test
    fun `isSameWork full contradiction never identifies even with exact title`() {
        // Caso Cerberus→Tienda de conveniencia como par de metadatos: autor, estado y
        // géneros TODOS en desacuerdo (0 señales de 3 evaluadas). El título exacto NO
        // sostiene la identidad: es otra obra con el mismo título.
        val verdict = MetadataMatcher.validate(
            currentAuthor = "Kim Doe",
            currentStatus = SManga.ONGOING,
            currentDescription = null,
            currentGenres = listOf("Comedy", "Slice of Life"),
            candidateAuthor = "Otro Autor",
            candidateStatus = SManga.COMPLETED,
            candidateDescription = null,
            candidateGenres = listOf("Horror", "Psicológico"),
        )
        assertTrue(verdict is MetadataMatcher.MetadataVerdict.Rejected)
        assertEquals(0, (verdict as MetadataMatcher.MetadataVerdict.Rejected).matchedSignals.size)
        assertFalse(
            MetadataMatcher.isSameWork(verdict, MetadataMatcher.TitleLevel.EXACT, fallbackToTitle = true),
        )
    }

    @Test
    fun `isSameWork no data identifies via strong title without fallback`() {
        // Candidata sin metadatos (parser que no extrae) + título EXACTO: la ausencia de
        // datos no es evidencia de obra distinta (§4) — la identidad sostiene en el título.
        val verdict = MetadataMatcher.validate(
            currentAuthor = "Kim Doe",
            currentStatus = SManga.ONGOING,
            currentDescription = "Una propuesta de trabajo inesperada.",
            currentGenres = listOf("Drama"),
            candidateAuthor = null,
            candidateStatus = SManga.UNKNOWN,
            candidateDescription = null,
            candidateGenres = null,
        )
        assertTrue(verdict is MetadataMatcher.MetadataVerdict.NoData)
        assertTrue(
            MetadataMatcher.isSameWork(verdict, MetadataMatcher.TitleLevel.EXACT, fallbackToTitle = false),
        )
        assertTrue(
            MetadataMatcher.isSameWork(verdict, MetadataMatcher.TitleLevel.ROMANIZED, fallbackToTitle = false),
        )
        // Título débil (FUZZY/NONE) sin respaldo → no identifica.
        assertFalse(
            MetadataMatcher.isSameWork(verdict, MetadataMatcher.TitleLevel.FUZZY, fallbackToTitle = false),
        )
        assertFalse(
            MetadataMatcher.isSameWork(verdict, MetadataMatcher.TitleLevel.NONE, fallbackToTitle = false),
        )
        // ...y el respaldo configurable sigue decidiendo para títulos débiles.
        assertTrue(
            MetadataMatcher.isSameWork(verdict, MetadataMatcher.TitleLevel.FUZZY, fallbackToTitle = true),
        )
    }
}

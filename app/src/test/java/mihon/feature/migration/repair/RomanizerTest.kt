package mihon.feature.migration.repair

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Covers [Romanizer]: hangul (Revised Romanization with anglicized family names) and kana
 * (Hepburn) transliteration for titles and author names. Same JVM-only constraint as the
 * other matcher tests: CI (`testDebugUnitTest`) is the authority.
 */
class RomanizerTest {

    @Test
    fun `hangul syllables follow revised romanization`() {
        // ㄱ final reads "k": 한국어 -> han-guk-eo.
        assertEquals("hangukeo", Romanizer.romanize("한국어"))
        assertEquals("seoul", Romanizer.romanize("서울"))
        // ㅂ final reads "p" and ㅇ final reads "ng": 망가 -> mang-ga.
        assertEquals("mangga", Romanizer.romanize("망가"))
    }

    @Test
    fun `common family names keep anglicized spellings`() {
        assertEquals("kim", Romanizer.romanize("김"))
        assertEquals("park", Romanizer.romanize("박"))
        assertEquals("choi", Romanizer.romanize("최"))
        assertEquals("lee", Romanizer.romanize("이"))
        // Two-syllable name: the override applies to the surname syllable only, the given
        // name follows the general tables (도 -> "do").
        assertEquals("kimdo", Romanizer.romanize("김도"))
        assertEquals("chugong", Romanizer.romanize("추공"))
    }

    @Test
    fun `mixed script romanizes only the hangul part`() {
        // The Latin part passes through untouched — including its original case; callers
        // lowercase through TitleMatcher.normalize when they need a canonical key.
        assertEquals("na honjaman Level Up", Romanizer.romanize("나 혼자만 Level Up"))
        assertEquals("sword of demon hunter", Romanizer.romanize("sword of demon hunter"))
    }

    @Test
    fun `kana follows hepburn with sokuon and long vowels`() {
        // Kanji in a kana sentence stay as-is (no small deterministic mapping exists); only
        // the kana around them transliterate. The comparison pipelines normalize both sides
        // the same way, so the kanji residue does not affect matching.
        assertEquals("kono素晴rashii世界ni祝福o", Romanizer.romanize("この素晴らしい世界に祝福を"))
        assertEquals("sapporo", Romanizer.romanize("さっぽろ"))
        assertEquals("koohii", Romanizer.romanize("コーヒー"))
        // キョ is a yōon contraction: ki + small yo -> "kyo".
        assertEquals("toukyou", Romanizer.romanize("トウキョウ"))
    }

    @Test
    fun `romanized forms of known titles stay recognizable`() {
        // "Omniscient Reader's Viewpoint" — Korean title vs its romanized form.
        assertEquals("jeonjijeok dokja sijeom", Romanizer.romanize("전지적 독자 시점"))
        // 레벨 transliterates as "rebel" (RR) — the mechanical form, not the English loanword
        // spelling "level"; matching compares romanized forms of both sides, so consistency
        // matters more than the commercial spelling.
        assertEquals("na honjaman rebeleop", Romanizer.romanize("나 혼자만 레벨업"))
    }

    @Test
    fun `isRomanizable distinguishes scripts that need work`() {
        assertTrue(Romanizer.isRomanizable("전지적 독자 시점"))
        assertTrue(Romanizer.isRomanizable("さっぽろ"))
        assertFalse(Romanizer.isRomanizable("Solo Leveling"))
        assertFalse(Romanizer.isRomanizable("Level Up 3"))
    }

    @Test
    fun `kanji and non kana non hangul text passes through`() {
        // Kanji is intentionally untouched — no small deterministic mapping exists.
        assertEquals("剣神", Romanizer.romanize("剣神"))
        assertEquals("villain to kill", Romanizer.romanize("villain to kill"))
    }

    @Test
    fun `standalone particle syllables romanize`() {
        // Particles like 의 (ui) must not be dropped, and ㅇ-initial syllables keep their vowel.
        assertEquals("ui", Romanizer.romanize("의"))
        assertEquals("eunhye", Romanizer.romanize("은혜"))
    }
}

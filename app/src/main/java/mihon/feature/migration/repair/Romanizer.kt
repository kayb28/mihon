package mihon.feature.migration.repair

/**
 * Pure JVM transliteration of Korean hangul and Japanese kana into Latin script, used to
 * compare titles and author names that the same source lists in either script: scan sources
 * split between the original title ("전지적 독자 시점") and its romanized form
 * ("Jeonjijeok Dokja Sijeom"), and the same author appears as "김도" or "Kim Doe".
 *
 * Hangul uses Revised Romanization with one practical override: the most common family
 * names keep their conventional anglicized spellings (김→kim, 박→park, 최→choi…) because
 * sources write them that way far more often than the strict RR forms (gim, bak, choe).
 * Kana uses Hepburn, with the sokuon (っ/ッ) doubling the next consonant and the long
 * vowel mark (ー) repeating the previous vowel. Everything else passes through unchanged,
 * so a mixed string like "나 혼자만 Level Up" only transliterates its hangul part.
 */
object Romanizer {

    // Hangul syllable arithmetic: S = 0xAC00 + ((L * 21) + V) * 28 + T
    private const val HANGUL_BASE = 0xAC00
    private const val HANGUL_END = 0xD7A3

    private val LEADS = arrayOf(
        "g", "kk", "n", "d", "tt", "r", "m", "b", "pp", "s", "ss", "", "j", "jj", "ch", "k", "t", "p", "h",
    )
    private val VOWELS = arrayOf(
        "a", "ae", "ya", "yae", "eo", "e", "yeo", "ye", "o", "wa", "wae", "oe", "yo",
        "u", "wo", "we", "wi", "yu", "eu", "ui", "i",
    )

    // Final consonants (trails) in jamo order 0..27: ∅ ㄱ ㄲ ㄳ ㄴ ㄵ ㄶ ㄷ ㄹ ㄺ ㄻ ㄼ ㄽ ㄾ ㄿ
    // ㅀ ㅁ ㅂ ㅄ ㅅ ㅆ ㅇ ㅈ ㅊ ㅋ ㅌ ㅍ ㅎ → RR values. The trailing-jamo index is the syllable
    // code mod 28, so slot 8 is ㄹ ("l"); a misaligned table turned 추공 into "chugot" and
    // 레벨업 into "rebeleol".
    private val TRAILS = arrayOf(
        "", "k", "kk", "ks", "n", "n", "n", "t", "l", "k", "m", "l", "l", "l", "p", "l",
        "m", "p", "p", "t", "t", "ng", "t", "t", "k", "t", "p", "n",
    )

    /**
     * Family-name overrides applied per syllable before the general RR tables: the spellings
     * sources actually use (Kim/Park/Choi…) instead of strict RR (gim/bak/choe).
     */
    private val SURNAME_SYLLABLES = mapOf(
        '김' to "kim",
        '박' to "park",
        '이' to "lee",
        '최' to "choi",
        '정' to "jung",
        '강' to "kang",
        '조' to "jo",
        '장' to "jang",
        '임' to "lim",
        '한' to "han",
        '오' to "oh",
        '서' to "seo",
        '신' to "shin",
        '권' to "kwon",
        '황' to "hwang",
        '안' to "ahn",
        '송' to "song",
        '유' to "yoo",
        '홍' to "hong",
        '전' to "jeon",
    )

    // Hiragana and katakana share Hepburn values; compound yōon (きゃ etc.) decompose into
    // the き row + a small ゃ/ゅ/ょ, which the character map below already transliterates.
    private val KANA = mapOf(
        'あ' to "a", 'い' to "i", 'う' to "u", 'え' to "e", 'お' to "o",
        'か' to "ka", 'き' to "ki", 'く' to "ku", 'け' to "ke", 'こ' to "ko",
        'さ' to "sa", 'し' to "shi", 'す' to "su", 'せ' to "se", 'そ' to "so",
        'た' to "ta", 'ち' to "chi", 'つ' to "tsu", 'て' to "te", 'と' to "to",
        'な' to "na", 'に' to "ni", 'ぬ' to "nu", 'ね' to "ne", 'の' to "no",
        'は' to "ha", 'ひ' to "hi", 'ふ' to "fu", 'へ' to "he", 'ほ' to "ho",
        'ま' to "ma", 'み' to "mi", 'む' to "mu", 'め' to "me", 'も' to "mo",
        'や' to "ya", 'ゆ' to "yu", 'よ' to "yo",
        'ら' to "ra", 'り' to "ri", 'る' to "ru", 'れ' to "re", 'ろ' to "ro",
        'わ' to "wa", 'ゐ' to "i", 'ゑ' to "e", 'を' to "o",
        'ん' to "n",
        'ぁ' to "a", 'ぃ' to "i", 'ぅ' to "u", 'ぇ' to "e", 'ぉ' to "o",
        'ゃ' to "ya", 'ゅ' to "yu", 'ょ' to "yo",
        'っ' to "",
        'ゔ' to "vu",
        // Voiced and semi-voiced hiragana: precomposed characters with dakuten/handakuten
        // (が ぎ ぐ … ぽ); sources write them directly, so they need their own entries.
        'が' to "ga", 'ぎ' to "gi", 'ぐ' to "gu", 'げ' to "ge", 'ご' to "go",
        'ざ' to "za", 'じ' to "ji", 'ず' to "zu", 'ぜ' to "ze", 'ぞ' to "zo",
        'だ' to "da", 'ぢ' to "ji", 'づ' to "zu", 'で' to "de", 'ど' to "do",
        'ば' to "ba", 'び' to "bi", 'ぶ' to "bu", 'べ' to "be", 'ぼ' to "bo",
        'ぱ' to "pa", 'ぴ' to "pi", 'ぷ' to "pu", 'ぺ' to "pe", 'ぽ' to "po",
        'ア' to "a", 'イ' to "i", 'ウ' to "u", 'エ' to "e", 'オ' to "o",
        'カ' to "ka", 'キ' to "ki", 'ク' to "ku", 'ケ' to "ke", 'コ' to "ko",
        'サ' to "sa", 'シ' to "shi", 'ス' to "su", 'セ' to "se", 'ソ' to "so",
        'タ' to "ta", 'チ' to "chi", 'ツ' to "tsu", 'テ' to "te", 'ト' to "to",
        'ナ' to "na", 'ニ' to "ni", 'ヌ' to "nu", 'ネ' to "ne", 'ノ' to "no",
        'ハ' to "ha", 'ヒ' to "hi", 'フ' to "fu", 'ヘ' to "he", 'ホ' to "ho",
        'マ' to "ma", 'ミ' to "mi", 'ム' to "mu", 'メ' to "me", 'モ' to "mo",
        'ヤ' to "ya", 'ユ' to "yu", 'ヨ' to "yo",
        'ラ' to "ra", 'リ' to "ri", 'ル' to "ru", 'レ' to "re", 'ロ' to "ro",
        'ワ' to "wa", 'ヰ' to "i", 'ヱ' to "e", 'ヲ' to "o",
        'ン' to "n",
        'ァ' to "a", 'ィ' to "i", 'ゥ' to "u", 'ェ' to "e", 'ォ' to "o",
        'ャ' to "ya", 'ュ' to "yu", 'ョ' to "yo",
        'ッ' to "",
        'ヴ' to "vu",
        // Katakana with dakuten/handakuten: sources write loanwords like レベル and ポイント in
        // katakana, so the voiced rows need their own entries for a faithful Hepburn output.
        'ガ' to "ga", 'ギ' to "gi", 'グ' to "gu", 'ゲ' to "ge", 'ゴ' to "go",
        'ザ' to "za", 'ジ' to "ji", 'ズ' to "zu", 'ゼ' to "ze", 'ゾ' to "zo",
        'ダ' to "da", 'ヂ' to "ji", 'ヅ' to "zu", 'デ' to "de", 'ド' to "do",
        'バ' to "ba", 'ビ' to "bi", 'ブ' to "bu", 'ベ' to "be", 'ボ' to "bo",
        'パ' to "pa", 'ピ' to "pi", 'プ' to "pu", 'ペ' to "pe", 'ポ' to "po",
    )

    private val SOKUON = setOf('っ', 'ッ')
    private val LONG_VOWEL = 'ー'
    private val SMALL_Y_KANA = setOf('ゃ', 'ゅ', 'ょ', 'ャ', 'ュ', 'ョ')

    /**
     * Transliterates every hangul syllable and kana character to Latin and leaves the rest
     * (Latin, digits, punctuation, spaces, kanji/hanzi) untouched. Kanji are NOT converted:
     * there is no small deterministic mapping, and author/title pairs that differ only in
     * kanji spelling still fold through [MetadataMatcher]'s token comparison.
     */
    fun romanize(text: String): String {
        if (text.isEmpty()) return text
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                isHangulSyllable(c) -> {
                    val idx = c.code - HANGUL_BASE
                    val lead = LEADS[idx / (21 * 28)]
                    val vowel = VOWELS[(idx % (21 * 28)) / 28]
                    val trail = TRAILS[idx % 28]
                    out.append(SURNAME_SYLLABLES[c] ?: (lead + vowel + trail))
                }
                c in SOKUON && i + 1 < text.length -> {
                    // っ doubles the next consonant: さっぽろ -> sapporo. The next character is
                    // kana in practice; if it is not, the sokuon is simply dropped.
                    val next = KANA[text[i + 1]] ?: ""
                    val consonant = next.takeWhile { it !in "aiueoy" }
                    out.append(consonant)
                }
                c in KANA -> {
                    val mapped = KANA.getValue(c)
                    // Yōon contraction: き + ょ should read "kyo", not "kiyeo" — when a kana ending
                    // in "i" is followed by a small y-kana, the "i" is dropped.
                    val nextIsSmallY = i + 1 < text.length && text[i + 1] in SMALL_Y_KANA
                    if (nextIsSmallY && mapped.length == 2 && mapped.endsWith("i")) {
                        out.append(mapped[0])
                    } else {
                        out.append(mapped)
                    }
                }
                c == LONG_VOWEL && out.isNotEmpty() -> {
                    // ー repeats the previous vowel: コーヒー -> koohii
                    val prev = out[out.length - 1]
                    if (prev in "aiueo") out.append(prev) else out.append('-')
                }
                else -> out.append(c)
            }
            i++
        }
        return out.toString()
    }

    /**
     * True when [romanize] would change anything — callers use it to avoid issuing a search
     * query that is identical to the original title.
     */
    fun isRomanizable(text: String): Boolean = romanize(text) != text

    private fun isHangulSyllable(c: Char): Boolean = c.code in HANGUL_BASE..HANGUL_END
}

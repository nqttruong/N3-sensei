package com.n3sensei.app.domain.translate

/**
 * The likely language of a selected text.
 *
 * @property language the best guess; null when it cannot be told (no letters, or a script that
 *   is none of the supported languages): the learner chooses.
 * @property confidence a calibrated probability when the detector has one; null for rule-based
 *   detection, which has no such number (none is made up).
 * @property isAmbiguous true when another language is plausible too: the learner should check.
 * @property candidates plausible languages, most likely first (empty: none of the supported ones).
 * @property mixed several scripts of similar weight (e.g. Japanese with Vietnamese): [language] is the larger part.
 */
data class LanguageDetection(
    val language: Language?,
    val confidence: Double?,
    val isAmbiguous: Boolean,
    val candidates: List<Language>,
    val mixed: Boolean = false,
)

/** Tells the language of a selected text. Replaceable (e.g. by an on-device model) without touching callers. */
fun interface LanguageDetector {
    fun detect(text: String): LanguageDetection
}

/**
 * Rule-based detection from the scripts and letters of the text, offline and instant.
 *
 * - Hiragana or Katakana → Japanese.
 * - Kanji alone → Japanese only as a guess (Chinese uses the same characters): ambiguous,
 *   unless a mark of Japanese writing is there (々, 〆, ヶ); characters only simplified Chinese
 *   uses → not a supported language.
 * - Latin letters with a mark only Vietnamese uses (đ, ơ, ư, ă, ạ, ả, ấ…) → Vietnamese.
 *   Other accents (à, é, ô…) are shared with French and others → Vietnamese, ambiguous.
 * - Latin letters without accents → English, unless every word is also a possible Vietnamese
 *   syllable written without accents ("kinh nghiem", "to", "an") → ambiguous.
 * - Several scripts of similar weight → the largest one, ambiguous.
 * - Digits, punctuation, emoji and spaces are ignored; nothing left → unknown.
 */
class ScriptLanguageDetector : LanguageDetector {

    override fun detect(text: String): LanguageDetection {
        var kana = 0
        var han = 0
        var latin = 0
        var other = 0
        var japaneseMark = false
        var chineseOnly = false
        var vietnameseOnly = false
        var sharedAccent = false
        text.codePoints().forEach { cp ->
            when {
                isKana(cp) -> kana++
                cp in JAPANESE_MARKS -> {
                    japaneseMark = true
                    han++
                }
                Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN -> {
                    han++
                    if (cp in SIMPLIFIED_CHINESE_ONLY) chineseOnly = true
                }
                Character.UnicodeScript.of(cp) == Character.UnicodeScript.LATIN && Character.isLetter(cp) -> {
                    latin++
                    when {
                        isVietnameseOnly(cp) -> vietnameseOnly = true
                        cp > 0x7F -> sharedAccent = true
                    }
                }
                Character.isLetter(cp) -> other++
            }
        }
        val letters = kana + han + latin + other
        if (letters == 0) return UNKNOWN

        // One guess per script group, then weigh the groups.
        val japanese = kana + han
        val groups = buildList {
            if (japanese > 0) add(Group(japaneseGuess(kana, japaneseMark, chineseOnly), japanese))
            if (latin > 0) add(Group(latinGuess(text, vietnameseOnly, sharedAccent), latin))
            if (other > 0) add(Group(Guess(null, ambiguous = true, candidates = emptyList()), other))
        }.sortedByDescending { it.weight }
        val main = groups.first()
        val mixed = groups.size > 1 && groups[1].weight * MIXED_SHARE >= letters
        val candidates = groups.flatMap { it.guess.candidates }.distinct()
        return LanguageDetection(
            language = main.guess.language,
            confidence = null,
            isAmbiguous = main.guess.ambiguous || mixed,
            candidates = candidates,
            mixed = mixed,
        )
    }

    private data class Guess(val language: Language?, val ambiguous: Boolean, val candidates: List<Language>)
    private data class Group(val guess: Guess, val weight: Int)

    private fun japaneseGuess(kana: Int, japaneseMark: Boolean, chineseOnly: Boolean): Guess = when {
        kana > 0 || japaneseMark -> Guess(Language.JAPANESE, ambiguous = false, candidates = listOf(Language.JAPANESE))
        chineseOnly -> Guess(null, ambiguous = true, candidates = emptyList())
        else -> Guess(Language.JAPANESE, ambiguous = true, candidates = listOf(Language.JAPANESE))
    }

    private fun latinGuess(text: String, vietnameseOnly: Boolean, sharedAccent: Boolean): Guess = when {
        vietnameseOnly -> Guess(Language.VIETNAMESE, ambiguous = false, candidates = listOf(Language.VIETNAMESE))
        sharedAccent -> Guess(Language.VIETNAMESE, ambiguous = true, candidates = listOf(Language.VIETNAMESE, Language.ENGLISH))
        else -> {
            val words = WORD.findAll(text.lowercase()).map { it.value }.toList()
            if (words.isNotEmpty() && words.all { VIETNAMESE_SYLLABLE.matches(it) }) {
                // Every word could be unaccented Vietnamese: lean on Vietnamese-only spellings.
                val vietnamese = words.any { VIETNAMESE_SPELLING.containsMatchIn(it) }
                if (vietnamese) {
                    Guess(Language.VIETNAMESE, ambiguous = true, candidates = listOf(Language.VIETNAMESE, Language.ENGLISH))
                } else {
                    Guess(Language.ENGLISH, ambiguous = true, candidates = listOf(Language.ENGLISH, Language.VIETNAMESE))
                }
            } else {
                Guess(Language.ENGLISH, ambiguous = false, candidates = listOf(Language.ENGLISH))
            }
        }
    }

    private fun isKana(cp: Int): Boolean =
        cp in 0x3041..0x309F || cp in 0x30A0..0x30FF || cp in 0x31F0..0x31FF || cp in 0xFF66..0xFF9F

    /** Letters of Vietnamese not used by other common Latin-script languages. */
    private fun isVietnameseOnly(cp: Int): Boolean =
        cp in 0x1EA0..0x1EF9 || cp in VIETNAMESE_LETTERS

    private companion object {
        val UNKNOWN = LanguageDetection(null, null, isAmbiguous = true, candidates = emptyList())

        /** A second script counts as "mixed" when it has at least 1 / [MIXED_SHARE] of the letters. */
        const val MIXED_SHARE = 4

        val JAPANESE_MARKS = setOf('々'.code, '〆'.code, 'ヶ'.code)

        /** đ Đ ă Ă ơ Ơ ư Ư (with or without tone marks, the toned forms are in U+1EA0–1EF9) and ĩ Ĩ ũ Ũ. */
        val VIETNAMESE_LETTERS = "đĐăĂơƠưƯĩĨũŨ".map { it.code }.toSet()

        /** Very common characters written only in simplified Chinese (Japanese uses other forms or none). */
        val SIMPLIFIED_CHINESE_ONLY = "们这说么吗个对时会过还没请谢问爱乐书车东门见长马鸟语读写买卖们觉认让给话边头听开关"
            .codePoints().toArray().toSet()

        val WORD = Regex("[a-z]+")

        /** An unaccented Vietnamese syllable: optional onset, 1–3 vowels, optional final. */
        val VIETNAMESE_SYLLABLE = Regex(
            "^(ngh|ng|nh|ch|gh|gi|kh|ph|qu|th|tr|[bcdghklmnpqrstvx])?[aeiouy]{1,3}(ch|ng|nh|[cmnpt])?$",
        )

        /** Spellings English words (almost) never have: ng-/ngh-/nh-/kh- onsets, -nh endings, uo. */
        val VIETNAMESE_SPELLING = Regex("^(ngh|ng|nh|kh)|nh$|uo")
    }
}

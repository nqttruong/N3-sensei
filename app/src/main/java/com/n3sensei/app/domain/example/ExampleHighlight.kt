package com.n3sensei.app.domain.example

/**
 * Where an example shows what it illustrates, as ranges of its Japanese text: the word being
 * learned, the kanji, or the parts of a grammar pattern. Used to emphasize them on screen; the
 * text itself is never changed. Nothing found → no range (the example is shown plainly).
 */
object ExampleHighlight {

    /**
     * The word in [sentence]: its spelling (any of the forms of `代わる・替わる`), or its stem when
     * it is conjugated (働く → 働いた, 寂しい → 寂しかった, keeping every kanji of the word), or
     * the word written in kana. Same rules as the build check in tools/content/examples.
     */
    fun ofVocabulary(sentence: String, expression: String, reading: String): List<IntRange> {
        val forms = expression.split('・').map { it.trim() }.filter { it.isNotEmpty() }
        val readings = reading.split('・').map { it.trim() }
        forms.forEachIndexed { i, form ->
            val r = if (readings.size == forms.size) readings[i] else readings.first()
            wordIn(sentence, form, r)?.let { return listOf(it) }
        }
        return emptyList()
    }

    /** Every place of [character] in [text]. */
    fun ofKanji(text: String, character: String): List<IntRange> {
        if (character.isEmpty()) return emptyList()
        val out = mutableListOf<IntRange>()
        var pos = text.indexOf(character)
        while (pos >= 0) {
            out += pos until pos + character.length
            pos = text.indexOf(character, pos + character.length)
        }
        return out
    }

    /**
     * The fixed parts of [pattern] in [sentence]: `〜といえば／〜というと` → といえば, というと;
     * optional parts and labels in brackets are left out (`〜ため（に）` → ため, `〜そうだ（伝聞）`
     * → そうだ). A part that ends in an inflected word also matches its stem (そうだ → そうです),
     * and て/で, た/だ count as the same sound (〜てもいい → 休んでもいい).
     */
    fun ofGrammar(sentence: String, pattern: String): List<IntRange> {
        val fragments = pattern.split('／', '/')
            .flatMap { alternative ->
                alternative.replace(BRACKETS, "").split('〜', '～', '…').map { it.trim() }.filter { it.isNotEmpty() }
            }
            .distinct()
            .sortedByDescending { it.length }
        val text = sentence.voiceless()
        val ranges = mutableListOf<IntRange>()
        fun mark(part: String): Boolean {
            var found = false
            var pos = text.indexOf(part)
            while (pos >= 0) {
                val range = pos until pos + part.length
                if (ranges.none { it.first <= range.last && range.first <= it.last }) {
                    ranges += range
                    found = true
                }
                pos = text.indexOf(part, pos + part.length)
            }
            return found
        }
        // Whole parts (and their conjugated forms) first, for every alternative; only then the
        // shortened ones, so といったら → とい does not take the place of といえば.
        val pending = fragments.map { it.voiceless() }.filterNot { f -> (listOf(f) + inflected(f)).any(::mark) }
        for (f in pending) {
            val minLength = if (f.any(::isKanji)) 1 else minOf(2, f.length)
            val shortest = maxOf(minLength, f.length - if (f.length >= 5) 3 else 2)
            (f.length - 1 downTo shortest).firstOrNull { mark(f.substring(0, it)) }
        }
        return ranges.sortedBy { it.first }
    }

    /** [fragment] with its last verb ending conjugated: がる → がっ (欲しがっている), にする → にし (にします). */
    private fun inflected(fragment: String): List<String> {
        if (fragment.length < 2) return emptyList()
        if (fragment.endsWith("する")) return listOf(fragment.dropLast(2) + "し")
        val stem = fragment.dropLast(1)
        return ENDINGS[fragment.last()].orEmpty().map { stem + it }
    }

    private fun wordIn(sentence: String, form: String, reading: String): IntRange? {
        val lastKanji = form.indexOfLast(::isKanji)
        val shortest = if (lastKanji >= 0) lastKanji + 1 else maxOf(if (form.length == 2) 1 else 2, form.length - 1)
        val candidates = buildList {
            add(form to true)
            for (n in form.length - 1 downTo shortest) add(form.substring(0, n) to false)
            if (lastKanji >= 0 && reading.isNotEmpty()) {
                add(reading to true)
                for (n in reading.length - 1 downTo maxOf(2, reading.length - 2)) add(reading.substring(0, n) to false)
            }
        }
        for ((text, whole) in candidates) {
            val pos = find(sentence, text, whole)
            if (pos >= 0) return pos until pos + text.length
        }
        return null
    }

    /** A stem (not [whole]) only where no kanji follows it: 写る matches 写っている, not 写真. */
    private fun find(sentence: String, text: String, whole: Boolean): Int {
        var pos = sentence.indexOf(text)
        while (pos >= 0 && !whole) {
            val end = pos + text.length
            if (end >= sentence.length || !isKanji(sentence[end])) return pos
            pos = sentence.indexOf(text, pos + 1)
        }
        return pos
    }

    private fun isKanji(c: Char): Boolean = c in '一'..'鿿' || c in '㐀'..'䶿' || c == '々' || c == '〆'

    /** て/で and た/だ as one sound; same length, so positions carry over to the original text. */
    private fun String.voiceless(): String = map {
        when (it) {
            'で' -> 'て'
            'だ' -> 'た'
            else -> it
        }
    }.joinToString("")

    /** Other forms of a verb's last kana (voiceless, as the text is searched). */
    private val ENDINGS = mapOf(
        'る' to "っりられ", 'う' to "っいわ", 'く' to "いきかけ", 'す' to "しさせ",
        'む' to "んみまめ", 'ぶ' to "んびばべ", 'つ' to "っちたて", 'ぐ' to "いきかけ",
    ).mapValues { (_, forms) -> forms.map { it.toString() } }

    private val BRACKETS = Regex("（[^）]*）|\\([^)]*\\)")
}

package com.n3sensei.app.domain.example

import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleHighlightTest {

    private fun String.marked(ranges: List<IntRange>): String {
        val sb = StringBuilder(this)
        ranges.sortedByDescending { it.first }.forEach { sb.insert(it.last + 1, ']'); sb.insert(it.first, '[') }
        return sb.toString()
    }

    private fun word(sentence: String, expression: String, reading: String) =
        sentence.marked(ExampleHighlight.ofVocabulary(sentence, expression, reading))

    private fun grammar(sentence: String, pattern: String) = sentence.marked(ExampleHighlight.ofGrammar(sentence, pattern))

    @Test
    fun aWord_asWritten_orByItsStemWhenConjugated() {
        assertEquals("明日は友達と映画を見る[予定]です。", word("明日は友達と映画を見る予定です。", "予定", "よてい"))
        assertEquals("[働]いた経験がある。", word("働いた経験がある。", "働く", "はたらく"))
        assertEquals("一人で[寂し]かった。", word("一人で寂しかった。", "寂しい", "さびしい"))
        assertEquals("[静かな]町だ。", word("静かな町だ。", "静かな", "しずかな"))
        assertEquals("子どもがボールを[け]って遊んでいる。", word("子どもがボールをけって遊んでいる。", "ける", "ける"))
    }

    @Test
    fun aStem_isNotTakenFromAnotherWord() {
        // 写る must not match the 写 of 写真.
        assertEquals("この写真には富士山がきれいに[写]っている。", word("この写真には富士山がきれいに写っている。", "写る", "うつる"))
    }

    @Test
    fun anyOfSeveralForms_orTheWordInKana() {
        assertEquals("部屋の空気を入れ[替え]た。", word("部屋の空気を入れ替えた。", "代える・替える・換える", "かえる"))
        assertEquals("子どもが[わがまま]を言う。", word("子どもがわがままを言う。", "我儘な", "わがままな"))
        assertEquals("全然関係ない。", word("全然関係ない。", "予定", "よてい"))
    }

    @Test
    fun aKanji_everywhereItIs() {
        assertEquals(listOf(0..0, 2..2), ExampleHighlight.ofKanji("坂の坂道", "坂"))
        assertEquals("上り[坂]", "上り坂".marked(ExampleHighlight.ofKanji("上り坂", "坂")))
        assertEquals(emptyList<IntRange>(), ExampleHighlight.ofKanji("上り", "坂"))
    }

    @Test
    fun aGrammarPattern_byItsFixedParts() {
        assertEquals("大雨の[ため]、電車が止まっています。", grammar("大雨のため、電車が止まっています。", "〜ため（に）"))
        assertEquals("日本の夏[といえば]、花火大会ですね。", grammar("日本の夏といえば、花火大会ですね。", "〜といえば／〜というと／〜といったら"))
        assertEquals("休みの日は、掃除[とか]洗濯[とか]で忙しい。", grammar("休みの日は、掃除とか洗濯とかで忙しい。", "〜とか〜とか"))
        assertEquals("父は[めったに]怒ら[ない]。", grammar("父はめったに怒らない。", "めったに〜ない"))
        // Labels in brackets are left out; an inflected ending matches its stem.
        assertEquals("山田さんの話では、有名だ[そう]です。", grammar("山田さんの話では、有名だそうです。", "〜そうだ（伝聞）"))
        // て/で are one sound for the match; the text itself is unchanged.
        assertEquals("疲れたら、少し休ん[でもいい]ですよ。", grammar("疲れたら、少し休んでもいいですよ。", "〜てもいい"))
        assertEquals("夏休みの[間]、ずっと祖母の家にいた。", grammar("夏休みの間、ずっと祖母の家にいた。", "〜間（は）"))
        assertEquals("何もない。", grammar("何もない。", "〜ことにする"))
        // A verb ending is matched in its other forms.
        assertEquals("子どもがおもちゃを欲し[がっ]ている。", grammar("子どもがおもちゃを欲しがっている。", "〜がる"))
        assertEquals("飲み物はコーヒー[にし]ます。", grammar("飲み物はコーヒーにします。", "〜にする"))
        assertEquals("ビザを取ら[なければな]りません。", grammar("ビザを取らなければなりません。", "〜なければならない"))
    }
}

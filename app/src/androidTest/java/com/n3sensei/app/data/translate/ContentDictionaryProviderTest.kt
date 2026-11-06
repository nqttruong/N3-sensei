package com.n3sensei.app.data.translate

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.data.importing.AndroidContentImportRepository
import com.n3sensei.app.data.importing.ContentPackImporter
import com.n3sensei.app.data.local.N3Database
import com.n3sensei.app.domain.importing.ImportOutcome
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** N3 Sensei's content as the "Dịch nhanh" dictionary, with the real Mimikara and kanji packs in Room: exact matches only. */
@RunWith(AndroidJUnit4::class)
class ContentDictionaryProviderTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "content-dictionary-test.db"
    private lateinit var db: N3Database
    private lateinit var dictionary: ContentDictionaryProvider

    private fun asset(path: String) = context.assets.open(path).use { it.readBytes().toString(Charsets.UTF_8) }

    @Before
    fun setUp() = runBlocking {
        context.deleteDatabase(name)
        db = N3Database.create(context, name)
        val importer = ContentPackImporter(db.contentPackDao(), Clock.fixed(Instant.parse("2026-11-06T00:00:00Z"), ZoneOffset.UTC))
        assertEquals(ImportOutcome.IMPORTED, importer.import(asset(AndroidContentImportRepository.MIMIKARA_PACK_ASSET)).outcome)
        assertEquals(ImportOutcome.IMPORTED, importer.import(asset(AndroidContentImportRepository.N3_KANJI_PACK_ASSET)).outcome)
        dictionary = ContentDictionaryProvider(db.contentDao())
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(name)
    }

    @Test
    fun japaneseWord_byItsSpellingOrReading_withMeaningReadingAndExample() = runBlocking {
        val byWord = dictionary.lookupJapanese("作者").single()
        assertEquals(listOf("作者", "さくしゃ", "tác giả"), listOf(byWord.japanese, byWord.reading, byWord.meaningVi))
        assertEquals("この小説の作者は、まだ二十歳だそうだ。", byWord.examples.single().japanese)
        assertTrue(byWord.sourceName!!.contains("Mimikara"))
        assertEquals("作者", dictionary.lookupJapanese("さくしゃ").single().japanese)
    }

    @Test
    fun onlyExactMatches_neverPartOfAWord() = runBlocking {
        assertTrue(dictionary.lookupJapanese("作者は").isEmpty())
        assertTrue(dictionary.lookupJapanese("さくし").isEmpty())
        assertTrue(dictionary.lookupVietnamese("tác").isEmpty())
        assertTrue(dictionary.lookupVietnamese("tac gia").isEmpty()) // no diacritics: not the same word
    }

    @Test
    fun oneOfSeveralForms_hasItsReading() = runBlocking {
        val kawaru = dictionary.lookupJapanese("替わる").single()
        assertEquals("代わる・替わる・換わる", kawaru.japanese)
        assertEquals("かわる", kawaru.reading)
    }

    @Test
    fun aWordWithTwoSenses_givesBoth() = runBlocking {
        val shikkari = dictionary.lookupJapanese("しっかり").map { it.meaningVi }
        assertEquals(listOf("toàn bộ", "kiên quyết, chắc chắn, đáng tin cậy"), shikkari)
    }

    @Test
    fun kanjiAndTheKanjisWords() = runBlocking {
        val kanji = dictionary.lookupJapanese("意").first()
        assertEquals("意", kanji.japanese)
        assertTrue(kanji.notes.any { it.startsWith("On: ") })
        assertEquals(listOf("注意", "意味", "決意"), kanji.examples.map { it.japanese })
        val word = dictionary.lookupJapanese("注意")
        assertTrue(word.any { it.reading == "ちゅうい" && it.meaningVi == "Chú ý" })
    }

    @Test
    fun vietnameseMeaning_oneWholeSense_caseAside() = runBlocking {
        assertEquals(listOf("作者"), dictionary.lookupVietnamese("Tác giả").map { it.japanese })
        // "khủng khiếp, rất nhiều": each sense finds 大変に.
        assertTrue(dictionary.lookupVietnamese("rất nhiều").any { it.japanese == "大変に" })
        // A kanji word by its meaning.
        assertTrue(dictionary.lookupVietnamese("chú ý").any { it.japanese == "注意" && it.reading == "ちゅうい" })
    }

    @Test
    fun noEnglish_andTheVersionFollowsTheContent() = runBlocking {
        assertTrue(dictionary.lookupEnglish("author").isEmpty())
        val before = dictionary.version()
        db.openHelper.writableDatabase.execSQL("UPDATE content_packs SET version = version + 1 WHERE id = 'n3-kanji'")
        assertNotEquals(before, dictionary.version())
    }
}

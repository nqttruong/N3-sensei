package com.n3sensei.app.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.n3sensei.app.data.local.N3Database
import com.n3sensei.app.domain.repository.ContentRepository
import com.n3sensei.app.testing.SeedData
import com.n3sensei.app.testing.inMemoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineContentRepositoryTest {

    private lateinit var db: N3Database
    private lateinit var repository: ContentRepository

    @Before
    fun setUp() = runBlocking {
        db = inMemoryDatabase()
        db.contentPackDao().replacePack(SeedData.bundle())
        repository = OfflineContentRepository(db.contentDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun lessons_haveItemCounts_includingEmptyLesson() = runTest {
        val lessons = repository.observeLessons().first()
        assertEquals(listOf(1, 2), lessons.map { it.number })
        with(lessons[0]) {
            assertEquals(4, vocabularyCount)
            assertEquals(2, kanjiCount)
            assertEquals(1, grammarCount)
        }
        with(lessons[1]) { assertEquals(0, vocabularyCount + kanjiCount + grammarCount) }
    }

    @Test
    fun lessonsFlow_emitsEmptyListOnEmptyDatabase() = runTest {
        db.contentPackDao().deletePack(SeedData.PACK_ID)
        assertTrue(repository.observeLessons().first().isEmpty())
    }

    @Test
    fun itemsByLesson_areInSortOrder_andEmptyForEmptyOrUnknownLesson() = runTest {
        assertEquals(
            listOf("経験", "増える", "届ける", "しっかり"),
            repository.observeVocabulary(SeedData.LESSON_1).first().map { it.expression },
        )
        assertEquals(listOf("経", "増"), repository.observeKanji(SeedData.LESSON_1).first().map { it.character })
        assertEquals(1, repository.observeGrammar(SeedData.LESSON_1).first().size)
        assertTrue(repository.observeVocabulary(SeedData.LESSON_EMPTY).first().isEmpty())
        assertTrue(repository.observeKanji("unknown").first().isEmpty())
    }

    @Test
    fun vocabularyDetail_includesExamplesTagsAndSource() = runTest {
        val detail = repository.getVocabularyDetail(SeedData.V_KEIKEN)!!
        with(detail.vocabulary) {
            assertEquals("けいけん", reading)
            assertEquals("{経験|けいけん}", furigana)
            assertEquals("kinh nghiệm", meaningVi)
            assertEquals("danh từ, động từ する", partOfSpeech)
        }
        assertEquals(listOf("日本で働いた経験があります。"), detail.examples.map { it.japanese })
        assertEquals("Tôi có kinh nghiệm làm việc ở Nhật.", detail.examples.single().translationVi)
        assertEquals(listOf("Công việc"), detail.tags.map { it.name })
        assertEquals(SeedData.SOURCE_ID, detail.source.id)
        assertFalse(detail.source.licenseVerified)
    }

    @Test
    fun kanaOnlyVocabulary_hasNoFurigana() = runTest {
        val detail = repository.getVocabularyDetail(SeedData.V_SHIKKARI)!!
        assertNull(detail.vocabulary.furigana)
        assertTrue(detail.examples.isEmpty())
        assertTrue(detail.tags.isEmpty())
    }

    @Test
    fun kanjiAndGrammarDetails_shareExampleWithVocabulary() = runTest {
        val kanji = repository.getKanjiDetail(SeedData.K_KEI)!!
        assertEquals(listOf("ケイ", "キョウ"), kanji.kanji.onReadings)
        assertEquals("KINH", kanji.kanji.hanViet)
        assertEquals("seed-ex01", kanji.examples.single().id)

        val grammar = repository.getGrammarDetail(SeedData.G_TABAKARI)!!
        assertEquals("V-た + ばかり", grammar.grammar.structure)
        assertTrue(grammar.grammar.cautionNote!!.contains("〜たところ"))
        assertEquals("日本に来たばかりです。", grammar.examples.single().japanese)
    }

    @Test
    fun missingItems_returnNull() = runTest {
        assertNull(repository.getVocabularyDetail("missing"))
        assertNull(repository.getKanjiDetail("missing"))
        assertNull(repository.getGrammarDetail("missing"))
    }

    @Test
    fun searchVocabulary_matchesExpressionReadingAndVietnameseMeaning() = runTest {
        assertEquals(listOf(SeedData.V_KEIKEN), repository.searchVocabulary("経験").map { it.id })
        assertEquals(listOf(SeedData.V_FUERU), repository.searchVocabulary("ふえる").map { it.id })
        assertEquals(listOf(SeedData.V_TODOKERU), repository.searchVocabulary("chuyển đến").map { it.id })
        assertEquals(listOf(SeedData.V_KEIKEN), repository.searchVocabulary("  経験  ").map { it.id })
    }

    @Test
    fun search_exactMatchRanksFirst() = runTest {
        // "増" is contained in 増える; exact kanji match must come first.
        assertEquals(SeedData.K_ZOU, repository.searchKanji("増").first().id)
    }

    @Test
    fun searchKanji_matchesHanVietAndReadings() = runTest {
        assertEquals(listOf(SeedData.K_ZOU), repository.searchKanji("TĂNG").map { it.id })
        assertEquals(listOf(SeedData.K_KEI), repository.searchKanji("キョウ").map { it.id })
        assertEquals(listOf(SeedData.K_ZOU), repository.searchKanji("ふ.や").map { it.id })
    }

    @Test
    fun searchGrammar_matchesPatternAndMeaning() = runTest {
        assertEquals(1, repository.searchGrammar("ばかり").size)
        assertEquals(1, repository.searchGrammar("vừa mới").size)
    }

    @Test
    fun search_blankOrNoMatch_returnsEmpty() = runTest {
        assertTrue(repository.searchVocabulary("").isEmpty())
        assertTrue(repository.searchVocabulary("   ").isEmpty())
        assertTrue(repository.searchVocabulary("không-tồn-tại").isEmpty())
    }

    @Test
    fun search_likeWildcardsAreLiteral() = runTest {
        assertTrue(repository.searchVocabulary("%").isEmpty())
        assertTrue(repository.searchVocabulary("_").isEmpty())
    }

    @Test
    fun search_pagesWithLimitAndOffset() = runTest {
        // "n" appears in several Vietnamese meanings.
        val all = repository.searchVocabulary("n", limit = 100)
        assertTrue(all.size >= 3)
        val page1 = repository.searchVocabulary("n", limit = 2, offset = 0)
        val page2 = repository.searchVocabulary("n", limit = 2, offset = 2)
        assertEquals(all.take(2), page1)
        assertEquals(all.drop(2).take(2), page2)
        assertEquals(1, repository.searchVocabulary("n", limit = 0).size) // clamped to 1
        assertEquals(all, repository.searchVocabulary("n", limit = 100, offset = -5)) // clamped to 0
    }
}

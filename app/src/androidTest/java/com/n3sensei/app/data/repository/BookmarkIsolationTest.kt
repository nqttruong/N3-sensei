package com.n3sensei.app.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.data.importing.AndroidContentImportRepository
import com.n3sensei.app.data.importing.ContentPackImporter
import com.n3sensei.app.data.local.N3Database
import com.n3sensei.app.data.local.entity.ItemMarkEntity
import com.n3sensei.app.domain.edit.EditableField
import com.n3sensei.app.domain.edit.FieldError
import com.n3sensei.app.domain.edit.SaveResult
import com.n3sensei.app.domain.edit.UserWords
import com.n3sensei.app.domain.importing.ImportOutcome
import com.n3sensei.app.domain.model.ItemQuery
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.reminder.ReminderLessonCatalog
import com.n3sensei.app.domain.translate.BookmarkEntry
import com.n3sensei.app.domain.translate.BookmarkResult
import com.n3sensei.app.domain.translate.BookmarkTranslation
import com.n3sensei.app.domain.translate.JapaneseText
import com.n3sensei.app.domain.translate.Language
import com.n3sensei.app.domain.translate.TranslationExample
import com.n3sensei.app.domain.widget.WidgetContentPicker
import com.n3sensei.app.domain.widget.WidgetMode
import com.n3sensei.app.domain.widget.WidgetSettings
import com.n3sensei.app.testing.countRows
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * "Dịch nhanh" bookmarks in a real Room database with the real Mimikara pack: one entry per
 * bookmark, in "Từ của tôi", and in **no** automatic study flow (new cards, counts, statistics,
 * reminders, widget, practice), while words studied before keep their behaviour.
 */
@RunWith(AndroidJUnit4::class)
class BookmarkIsolationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "bookmark-isolation-test.db"
    private val clock = Clock.fixed(Instant.parse("2026-11-06T00:00:00Z"), ZoneOffset.UTC)
    private lateinit var db: N3Database
    private lateinit var words: OfflineUserContentRepository
    private lateinit var content: OfflineContentRepository
    private lateinit var progress: OfflineProgressRepository

    private lateinit var manualId: String

    @Before
    fun setUp() = runBlocking {
        context.deleteDatabase(name)
        db = N3Database.create(context, name)
        val json = context.assets.open(AndroidContentImportRepository.MIMIKARA_PACK_ASSET).use { it.readBytes().toString(Charsets.UTF_8) }
        assertEquals(ImportOutcome.IMPORTED, ContentPackImporter(db.contentPackDao(), clock).import(json).outcome)
        words = OfflineUserContentRepository(db.userContentDao(), clock)
        content = OfflineContentRepository(db.contentDao(), db.contentPackDao())
        progress = OfflineProgressRepository(db.progressDao(), clock)
        // A word added by hand: studied, as before.
        val added = words.addWord(mapOf(EditableField.WORD_EXPRESSION to "経由", EditableField.WORD_READING to "けいゆ", EditableField.WORD_MEANING to "đi qua"))
        manualId = (added as SaveResult.Saved).id
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(name)
    }

    private val kinhNghiem = BookmarkEntry(
        sourceLanguage = Language.VIETNAMESE, sourceText = "kinh nghiệm", key = "kinh nghiệm",
        japanese = JapaneseText("経験", "けいけん"), meaningVi = "kinh nghiệm",
        translations = listOf(BookmarkTranslation(Language.ENGLISH, "experience", null), BookmarkTranslation(Language.JAPANESE, "経験", "けいけん")),
        example = TranslationExample("日本で働いた経験があります。", null, "Tôi có kinh nghiệm làm việc ở Nhật."),
    )
    private val bank = BookmarkEntry(Language.ENGLISH, "bank", "bank", null, "ngân hàng", emptyList(), null)

    private suspend fun save(entry: BookmarkEntry) = (words.saveBookmark(entry) as BookmarkResult.Saved).vocabularyId

    private fun count(sql: String) = db.openHelper.readableDatabase.query(sql).use { it.moveToFirst(); it.getInt(0) }

    private suspend fun myWords(includeSaved: Boolean) =
        content.observeVocabularyItems(ItemQuery(lessonId = UserWords.LESSON_ID, userWordsOnly = true, includeSaved = includeSaved)).first()

    private suspend fun newRefs() = db.progressDao().getNewRefs(100_000).filter { it.itemType == ItemType.VOCABULARY }.map { it.itemId }.toSet()

    private fun studyRows() = listOf("learning_progress", "item_marks", "review_records", "reminder_history").associateWith { db.countRows(it) }

    @Test
    fun vietnameseWithEnglishAndJapanese_isOneSavedEntry_withItsTranslationsAndExample() = runBlocking {
        val id = save(kinhNghiem)
        val detail = content.getVocabularyDetail(id)!!
        assertEquals(listOf("経験", "けいけん", "kinh nghiệm"), with(detail.vocabulary) { listOf(expression, reading, meaningVi) })
        assertTrue(detail.saved)
        assertEquals(listOf("en:experience", "ja:経験"), detail.translations.map { "${it.language}:${it.text}" })
        assertEquals("日本で働いた経験があります。", detail.examples.single().japanese)
        // Unknown JLPT level: empty, never a made-up N3.
        assertEquals("", detail.vocabulary.jlptLevel)
        assertEquals(1, count("SELECT COUNT(*) FROM user_words WHERE study_state = 'SAVED'"))
    }

    @Test
    fun english_hasNoKana_andIsFoundByItsTranslations() = runBlocking {
        val id = save(bank)
        val word = content.getVocabularyDetail(id)!!.vocabulary
        assertEquals("bank", word.expression)
        assertEquals("", word.reading)
        val kinh = save(kinhNghiem)
        // "Từ của tôi" search finds a bookmark by its English translation too.
        val found = content.observeVocabularyItems(ItemQuery(text = "experience", lessonId = UserWords.LESSON_ID, userWordsOnly = true, includeSaved = true)).first()
        assertEquals(listOf(kinh), found.map { it.item.id })
        assertTrue(found.single().isSaved)
    }

    @Test
    fun aMimikaraWord_canBeBookmarked_withoutTouchingThePackWord() = runBlocking {
        val packWord = db.contentDao().getVocabulary("mimikara-n3-v090")!!
        val id = save(BookmarkEntry(Language.JAPANESE, "作者", "作者|さくしゃ", JapaneseText("作者", "さくしゃ"), "tác giả", emptyList(), null))
        assertTrue(id.startsWith(UserWords.ID_PREFIX))
        assertEquals(packWord, db.contentDao().getVocabulary("mimikara-n3-v090"))
        assertEquals("さくしゃ", content.getVocabularyDetail(id)!!.vocabulary.reading)
    }

    @Test
    fun savingAgain_neverDuplicates_andAddsOnlyWhatIsMissing() = runBlocking {
        val first = words.saveBookmark(bank.copy(meaningVi = null)) as BookmarkResult.Saved
        assertTrue(first.created)
        val again = words.saveBookmark(bank.copy(translations = listOf(BookmarkTranslation(Language.JAPANESE, "銀行", "ぎんこう")))) as BookmarkResult.Saved
        assertFalse(again.created)
        assertEquals(first.vocabularyId, again.vocabularyId)
        assertEquals(1, count("SELECT COUNT(*) FROM user_words WHERE source_key = 'bank'"))
        val detail = content.getVocabularyDetail(first.vocabularyId)!!
        assertEquals("ngân hàng", detail.vocabulary.meaningVi) // a missing meaning is filled in
        assertEquals(listOf("銀行"), detail.translations.map { it.text })
        // Same again: nothing new.
        words.saveBookmark(bank.copy(translations = listOf(BookmarkTranslation(Language.JAPANESE, "銀行", "ぎんこう"))))
        assertEquals(1, db.countRows("user_word_translations"))
        // The learner's own meaning is never overwritten by a later bookmark.
        db.openHelper.writableDatabase.execSQL("UPDATE vocabulary SET meaning_vi = 'nhà băng' WHERE id = '${first.vocabularyId}'")
        words.saveBookmark(bank)
        assertEquals("nhà băng", content.getVocabularyDetail(first.vocabularyId)!!.vocabulary.meaningVi)
        // The live bookmark state follows the database.
        assertEquals(first.vocabularyId, words.observeBookmark(Language.ENGLISH, "bank").first())
        assertNull(words.observeBookmark(Language.VIETNAMESE, "bank").first())
    }

    @Test
    fun aFailureInTheMiddle_rollsEverythingBack() = runBlocking {
        val before = listOf("vocabulary", "user_words", "user_word_translations", "example_sentences").associateWith { db.countRows(it) }
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_translation BEFORE INSERT ON user_word_translations BEGIN SELECT RAISE(ABORT, 'disk full (test)'); END",
        )
        assertEquals(BookmarkResult.Failed, words.saveBookmark(kinhNghiem))
        assertEquals(before, listOf("vocabulary", "user_words", "user_word_translations", "example_sentences").associateWith { db.countRows(it) })
        assertNull(words.observeBookmark(Language.VIETNAMESE, "kinh nghiệm").first())
    }

    @Test
    fun savedWords_areInMyWords_butInNoStudyFlow() = runBlocking {
        val studyBefore = studyRows()
        val newBefore = newRefs()
        val availableBefore = db.progressDao().observeNewByType().first().sumOf { it.count }
        val statusBefore = db.analyticsDao().observeStatusByType().first().single { it.itemType == ItemType.VOCABULARY }
        val saved = setOf(save(kinhNghiem), save(bank))

        // In "Từ của tôi" (the list the learner browses), marked as saved.
        val listed = myWords(includeSaved = true)
        assertTrue(listed.map { it.item.id }.containsAll(saved + manualId))
        assertTrue(listed.filter { it.item.id in saved }.all { it.isSaved })
        assertFalse(listed.single { it.item.id == manualId }.isSaved)

        // New cards of a review session, the "new" count and the statistics: unchanged.
        assertEquals(newBefore, newRefs())
        assertEquals(availableBefore, db.progressDao().observeNewByType().first().sumOf { it.count })
        assertEquals(statusBefore, db.analyticsDao().observeStatusByType().first().single { it.itemType == ItemType.VOCABULARY })
        assertTrue(progress.getNewItems(100_000).none { it.id in saved })
        // Practice / flashcards of "Từ của tôi": the studied word only.
        assertEquals(listOf(manualId), myWords(includeSaved = false).map { it.item.id })
        // Reminders: neither a lesson choice nor a new item.
        val catalog = ReminderLessonCatalog.observe(content).first()
        assertTrue(catalog.newItems[ItemType.VOCABULARY].orEmpty().none { it.id in saved })
        assertTrue(saved.none { id -> catalog.lessonOf.keys.any { it.id == id } })
        // Widget (any word, or vocabulary only).
        val widget = WidgetContentPicker(content, progress, clock)
        listOf(WidgetMode.RANDOM, WidgetMode.VOCABULARY).forEach { mode ->
            assertTrue(widget.candidates(WidgetSettings(mode = mode)).none { it.id in saved })
        }
        // Nothing was scheduled, marked, reviewed or reminded.
        assertEquals(studyBefore, studyRows())
    }

    @Test
    fun wordsStudiedBefore_keepTheirBehaviour() = runBlocking {
        save(kinhNghiem)
        // The hand-added word is studied: a new card, practised, and its state is STUDYING.
        assertTrue(manualId in newRefs())
        assertEquals("STUDYING", db.userContentDao().getUserWord(manualId)!!.studyState)
        assertTrue(manualId in myWords(includeSaved = false).map { it.item.id })
        // Its mark still works the usual way.
        db.progressDao().insertMark(ItemMarkEntity(ItemType.VOCABULARY, manualId, 1))
        assertTrue(content.observeVocabularyItems(ItemQuery(markedOnly = true)).first().any { it.item.id == manualId })
    }

    @Test
    fun deletingASavedWord_removesItsTranslations_andNothingOfThePack() = runBlocking {
        val packWords = db.countRows("vocabulary") - 1 // minus the hand-added word
        val id = save(kinhNghiem)
        assertTrue(words.deleteWord(id))
        assertEquals(0, count("SELECT COUNT(*) FROM user_word_translations WHERE vocabulary_id = '$id'"))
        assertEquals(0, count("SELECT COUNT(*) FROM user_words WHERE vocabulary_id = '$id'"))
        assertEquals(packWords + 1, db.countRows("vocabulary"))
        // A later bookmark of the same text is a new entry again.
        assertNull(words.observeBookmark(Language.VIETNAMESE, "kinh nghiệm").first())
    }

    @Test
    fun editingASavedEnglishWord_needsNoKana_andKeepsItSearchableByTranslation() = runBlocking {
        val id = save(bank.copy(translations = listOf(BookmarkTranslation(Language.JAPANESE, "銀行", "ぎんこう"))))
        val result = words.save(
            ItemType.VOCABULARY, id,
            mapOf(EditableField.WORD_EXPRESSION to "bank", EditableField.WORD_READING to "", EditableField.WORD_MEANING to "ngân hàng (tài chính)"),
        )
        assertTrue(result is SaveResult.Saved)
        val found = content.observeVocabularyItems(ItemQuery(text = "ぎんこう", lessonId = UserWords.LESSON_ID, userWordsOnly = true, includeSaved = true)).first()
        assertEquals(listOf(id), found.map { it.item.id })
        assertEquals("SAVED", db.userContentDao().getUserWord(id)!!.studyState)
    }

    @Test
    fun aSavedJapaneseWordWithoutAReading_canBeEdited_aStudiedOneStillNeedsItsKana() = runBlocking {
        // Found in the emulator check: 実験 saved from a machine translation (no reading known)
        // could not be edited at all, unless the learner made a reading up.
        val jikken = BookmarkEntry(Language.VIETNAMESE, "nghiệm", "nghiệm", JapaneseText("実験", null), "nghiệm", listOf(BookmarkTranslation(Language.JAPANESE, "実験", null)), null)
        val id = save(jikken)
        assertTrue(words.load(ItemType.VOCABULARY, id)!!.readingOptional)
        val edited = words.save(
            ItemType.VOCABULARY, id,
            mapOf(EditableField.WORD_EXPRESSION to "実験", EditableField.WORD_READING to "", EditableField.WORD_MEANING to "thí nghiệm"),
        )
        assertTrue(edited is SaveResult.Saved)
        assertEquals("thí nghiệm", db.userContentDao().getVocabulary(id)!!.meaningVi)
        assertEquals("SAVED", db.userContentDao().getUserWord(id)!!.studyState)

        // A word added by hand is studied (flashcards show its reading): kana stays required.
        assertTrue(!words.load(ItemType.VOCABULARY, manualId)!!.readingOptional)
        val manual = words.save(
            ItemType.VOCABULARY, manualId,
            mapOf(EditableField.WORD_EXPRESSION to "経由", EditableField.WORD_READING to "", EditableField.WORD_MEANING to "đi qua"),
        )
        assertEquals(SaveResult.Invalid(mapOf(EditableField.WORD_READING to FieldError.REQUIRED)), manual)
    }
}

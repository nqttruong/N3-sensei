package com.n3sensei.app.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.data.importing.ContentPackImporter
import com.n3sensei.app.data.local.N3Database
import com.n3sensei.app.data.local.entity.ItemMarkEntity
import com.n3sensei.app.data.local.entity.LearningProgressEntity
import com.n3sensei.app.data.local.entity.UserWordEntity
import com.n3sensei.app.domain.edit.EditableField
import com.n3sensei.app.domain.edit.KanjiExample
import com.n3sensei.app.domain.edit.ExampleSaveResult
import com.n3sensei.app.domain.edit.ExampleField
import com.n3sensei.app.domain.edit.ExampleDraft
import com.n3sensei.app.domain.edit.FieldError
import com.n3sensei.app.domain.edit.SaveResult
import com.n3sensei.app.domain.edit.UserWords
import com.n3sensei.app.domain.importing.ImportOutcome
import com.n3sensei.app.domain.model.ItemQuery
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.ProgressState
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
 * The learner's edits (`user_content_edits`) and own words ("Từ của tôi") in a real Room
 * database file, across pack updates, app restarts and items leaving a pack.
 */
@RunWith(AndroidJUnit4::class)
class UserContentRepositoryTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "user-content-test.db"
    private val clock = Clock.fixed(Instant.parse("2026-10-27T00:00:00Z"), ZoneOffset.UTC)
    private lateinit var db: N3Database
    private lateinit var importer: ContentPackImporter
    private lateinit var edits: OfflineUserContentRepository
    private val content get() = OfflineContentRepository(db.contentDao(), db.contentPackDao())

    private fun open() {
        db = N3Database.create(context, name)
        importer = ContentPackImporter(db.contentPackDao(), clock)
        edits = OfflineUserContentRepository(db.userContentDao(), clock)
    }

    @Before
    fun setUp() {
        context.deleteDatabase(name)
        open()
        assertEquals(ImportOutcome.IMPORTED, runBlocking { importer.import(pack(version = 1)) }.outcome)
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(name)
    }

    /** A small pack whose values the tests change between versions. */
    private fun pack(
        version: Int,
        kanjiMeaning: String = "trải qua",
        wordMeaning: String = "kinh nghiệm",
        wordNote: String? = "danh từ",
        withWord: Boolean = true,
    ) = """
        {"format": "n3sensei-content-pack", "formatVersion": 1,
         "pack": {"id": "p", "title": "Gói thử", "version": $version, "jlptLevel": "N3", "defaultSourceId": "s"},
         "sources": [{"id": "s", "name": "Nguồn thử", "url": null, "license": "thử", "attribution": null, "licenseVerified": false}],
         "examples": [],
         "lessons": [{"id": "l1", "number": 1, "title": "Bài 1", "description": null,
           "vocabulary": [${if (withWord) """{"id": "w1", "expression": "経験", "reading": "けいけん", "furigana": "{経験|けいけん}", "meaningVi": "$wordMeaning", "note": ${wordNote?.let { "\"$it\"" } ?: "null"}}, """ else ""}
                          {"id": "w2", "expression": "増える", "reading": "ふえる", "furigana": null, "meaningVi": "tăng lên"}],
           "kanji": [{"id": "k1", "character": "経", "onReadings": ["ケイ"], "kunReadings": ["へ.る"], "hanViet": "KINH", "meaningVi": "$kanjiMeaning", "strokeCount": 11, "note": null}],
           "grammar": []}]}
    """.trimIndent()

    private suspend fun word(id: String) = db.contentDao().getVocabulary(id)
    private suspend fun kanji(id: String) = db.contentDao().getKanji(id)!!

    @Test
    fun kanjiEdit_isSavedInRoom_andSurvivesClosingTheApp() = runBlocking {
        val result = edits.save(
            ItemType.KANJI, "k1",
            mapOf(EditableField.KANJI_MEANING to "kinh qua", EditableField.KANJI_ON to "ケイ、キョウ", EditableField.KANJI_HAN_VIET to "KINH"),
        )
        assertEquals(SaveResult.Saved("k1"), result)
        // HAN_VIET equals the pack's value: not stored as an edit.
        assertEquals(2, db.countRows("user_content_edits"))
        db.close()
        open()
        assertEquals("kinh qua", kanji("k1").meaningVi)
        assertEquals(listOf("ケイ", "キョウ"), kanji("k1").onReadings)
        // Search uses the merged values.
        assertEquals(listOf("k1"), content.searchKanji("kinh qua").map { it.id })
        val item = edits.load(ItemType.KANJI, "k1")!!
        assertEquals(setOf(EditableField.KANJI_MEANING, EditableField.KANJI_ON), item.edited)
        assertEquals("trải qua", item.original.getValue(EditableField.KANJI_MEANING))
    }

    @Test
    fun wordEdit_survivesAPackUpdate_andTheBaseFollowsThePack() = runBlocking {
        edits.save(ItemType.VOCABULARY, "w1", mapOf(EditableField.WORD_MEANING to "trải nghiệm"))
        // The pack's own meaning changes in version 2; the learner's value still wins.
        assertEquals(ImportOutcome.IMPORTED, importer.import(pack(version = 2, wordMeaning = "kinh nghiệm (bản mới)")).outcome)
        assertEquals("trải nghiệm", word("w1")!!.meaningVi)
        assertEquals("kinh nghiệm (bản mới)", edits.load(ItemType.VOCABULARY, "w1")!!.original.getValue(EditableField.WORD_MEANING))
        // Fields the learner did not edit follow the pack; revert brings the new pack value.
        assertTrue(edits.revert(ItemType.VOCABULARY, "w1"))
        assertEquals("kinh nghiệm (bản mới)", word("w1")!!.meaningVi)
        assertEquals(0, db.countRows("user_content_edits"))
    }

    @Test
    fun aClearedField_isNotTheSameAsAnUneditedField() = runBlocking {
        edits.save(ItemType.VOCABULARY, "w1", mapOf(EditableField.WORD_NOTE to ""))
        assertNull(word("w1")!!.note)
        // Version 2 changes the note: the learner cleared it, so it stays empty.
        importer.import(pack(version = 2, wordNote = "danh từ, する"))
        assertNull(word("w1")!!.note)
        // w2 has no edit: its note follows the pack (none here), and k1's meaning too.
        importer.import(pack(version = 3, kanjiMeaning = "kinh, trải qua"))
        assertEquals("kinh, trải qua", kanji("k1").meaningVi)
        assertNull(word("w1")!!.note)
    }

    @Test
    fun editedReading_hidesThePacksFurigana_andRevertBringsItBack() = runBlocking {
        edits.save(ItemType.VOCABULARY, "w1", mapOf(EditableField.WORD_READING to "けーけん"))
        assertEquals("けーけん", word("w1")!!.reading)
        assertNull(word("w1")!!.furigana)
        edits.revert(ItemType.VOCABULARY, "w1")
        assertEquals("{経験|けいけん}", word("w1")!!.furigana)
    }

    @Test
    fun invalidInput_isRejected_andNothingChanges() = runBlocking {
        val result = edits.save(
            ItemType.KANJI, "k1",
            mapOf(EditableField.KANJI_MEANING to "  ", EditableField.KANJI_STROKES to "99", EditableField.KANJI_KUN to "he.ru"),
        )
        assertEquals(
            SaveResult.Invalid(mapOf(EditableField.KANJI_MEANING to FieldError.REQUIRED, EditableField.KANJI_STROKES to FieldError.OUT_OF_RANGE, EditableField.KANJI_KUN to FieldError.NOT_KANA)),
            result,
        )
        assertEquals("trải qua", kanji("k1").meaningVi)
        assertEquals(0, db.countRows("user_content_edits"))
    }

    @Test
    fun savingTheSameFormTwice_writesNothingNew() = runBlocking {
        val form = edits.load(ItemType.KANJI, "k1")!!.values + (EditableField.KANJI_MEANING to "kinh qua")
        edits.save(ItemType.KANJI, "k1", form)
        val first = db.userContentDao().getEdits(ItemType.KANJI, "k1")
        edits.save(ItemType.KANJI, "k1", form)
        assertEquals(first, db.userContentDao().getEdits(ItemType.KANJI, "k1"))
        assertEquals(1, db.countRows("user_content_edits"))
    }

    @Test
    fun editsAndPackUpdates_keepIdsProgressMarksAndHistory() = runBlocking {
        val progress = LearningProgressEntity(ItemType.KANJI, "k1", ProgressState.REVIEW, 2, 1440, 9_000, 8_000, 3, 0, 1_000, 8_000)
        db.progressDao().upsert(progress)
        db.progressDao().insertMark(ItemMarkEntity(ItemType.VOCABULARY, "w1", 5))
        edits.save(ItemType.KANJI, "k1", mapOf(EditableField.KANJI_MEANING to "kinh qua"))
        edits.save(ItemType.VOCABULARY, "w1", mapOf(EditableField.WORD_MEANING to "trải nghiệm"))
        importer.import(pack(version = 2))
        assertEquals(progress, db.progressDao().get(ItemType.KANJI, "k1"))
        assertEquals(listOf("w1"), content.observeVocabularyItems(ItemQuery(markedOnly = true)).first().map { it.item.id })
        assertEquals(listOf("w1", "w2"), content.observeVocabularyItems(ItemQuery()).first().map { it.item.id })
        assertEquals("kinh qua", kanji("k1").meaningVi)
    }

    @Test
    fun anEditedItemLeavingThePack_keepsItsEdit_whichAppliesAgainIfItReturns() = runBlocking {
        edits.save(ItemType.VOCABULARY, "w1", mapOf(EditableField.WORD_MEANING to "trải nghiệm"))
        assertEquals(ImportOutcome.IMPORTED, importer.import(pack(version = 2, withWord = false)).outcome)
        assertNull(word("w1"))
        assertEquals(1, edits.countOrphans())
        assertNull(edits.load(ItemType.VOCABULARY, "w1"))
        assertEquals(SaveResult.NotFound, edits.save(ItemType.VOCABULARY, "w1", mapOf(EditableField.WORD_MEANING to "x")))
        importer.import(pack(version = 3))
        assertEquals("trải nghiệm", word("w1")!!.meaningVi)
        assertEquals(0, edits.countOrphans())
    }

    @Test
    fun myWords_areAPackOfTheirOwn_thatPackUpdatesNeverTouch() = runBlocking {
        val added = edits.addWord(
            mapOf(EditableField.WORD_EXPRESSION to "経由", EditableField.WORD_READING to "けいゆ", EditableField.WORD_MEANING to "đi qua"),
        ) as SaveResult.Saved
        assertTrue(added.id.startsWith(UserWords.ID_PREFIX))
        val mine = word(added.id)!!
        assertEquals(UserWords.LESSON_ID, mine.lessonId)
        assertEquals("Từ của tôi", db.contentPackDao().getPack(UserWords.PACK_ID)!!.title)
        importer.import(pack(version = 2))
        importer.import(pack(version = 3, withWord = false))
        assertEquals(mine, word(added.id))
        // Found by search like any word; editing it changes the row itself (no edit rows).
        assertEquals(listOf(added.id), content.searchVocabulary("di qua").map { it.id })
        edits.save(ItemType.VOCABULARY, added.id, mapOf(EditableField.WORD_MEANING to "đi qua, quá cảnh"))
        assertEquals("đi qua, quá cảnh", word(added.id)!!.meaningVi)
        assertEquals(0, db.countRows("user_content_edits"))
        db.close()
        open()
        assertEquals("đi qua, quá cảnh", word(added.id)!!.meaningVi)
        assertEquals(1, db.countRows("vocabulary WHERE lesson_id = '${UserWords.LESSON_ID}'"))
    }

    /**
     * "Từ của tôi" lists only words added with the add action. Rows that look like the learner's
     * but were not added by it (a pack word in `user_words`, a row in the lesson without the
     * registry entry or the generated id) are left out of the list but not deleted, and editing
     * one does not make it the learner's.
     */
    @Test
    fun myWordsList_showsOnlyWordsTheLearnerAdded() = runBlocking {
        val added = (edits.addWord(
            mapOf(EditableField.WORD_EXPRESSION to "経由", EditableField.WORD_READING to "けいゆ", EditableField.WORD_MEANING to "đi qua"),
        ) as SaveResult.Saved).id
        val dao = db.userContentDao()
        val packWord = word("w2")!!
        // A pack word wrongly registered as the learner's.
        dao.upsertUserWord(UserWordEntity("w2", 1, 1))
        // Rows placed in the lesson without the add action: one with a generated-looking id but
        // no registry entry, one with a pack-style id and a registry entry.
        dao.insertVocabulary(packWord.copy(id = "user-w-legacy", expression = "増加", reading = "ぞうか", lessonId = UserWords.LESSON_ID, sortOrder = 100))
        dao.insertVocabulary(packWord.copy(id = "copied", expression = "増大", reading = "ぞうだい", lessonId = UserWords.LESSON_ID, sortOrder = 101))
        dao.upsertUserWord(UserWordEntity("copied", 1, 1))
        val vocabularyBefore = db.countRows("vocabulary")

        val mine = content.observeVocabularyItems(ItemQuery(userWordsOnly = true)).first().map { it.item.id }
        assertEquals(listOf(added), mine)
        // The general list still has every word: nothing was removed anywhere.
        assertEquals(vocabularyBefore, content.observeVocabularyItems(ItemQuery()).first().size)
        assertEquals(packWord, word("w2"))

        // Editing a row the learner did not add keeps it out of the list.
        edits.save(ItemType.VOCABULARY, "user-w-legacy", mapOf(EditableField.WORD_MEANING to "gia tăng"))
        assertNull(dao.getUserWord("user-w-legacy"))
        assertEquals(listOf(added), content.observeVocabularyItems(ItemQuery(userWordsOnly = true)).first().map { it.item.id })
        // Editing the learner's own word keeps it there and its creation date.
        val created = dao.getUserWord(added)!!.createdAt
        edits.save(ItemType.VOCABULARY, added, mapOf(EditableField.WORD_MEANING to "đi qua, quá cảnh"))
        assertEquals(created, dao.getUserWord(added)!!.createdAt)
        assertEquals(listOf(added), content.observeVocabularyItems(ItemQuery(userWordsOnly = true, text = "qua")).first().map { it.item.id })
    }

    @Test
    fun kanjiExamples_areAddedChangedAndDeleted_surviveUpdatesAndRestarts_andShowWithTheKanji() = runBlocking {
        val first = edits.saveKanjiExample("k1", null, ExampleDraft("経験", "けいけん", "kinh nghiệm")) as ExampleSaveResult.Saved
        assertTrue(first.id.startsWith(UserWords.KANJI_EXAMPLE_ID_PREFIX))
        val second = edits.saveKanjiExample("k1", null, ExampleDraft("経験があります", "{経験|けいけん}があります", "có kinh nghiệm")) as ExampleSaveResult.Saved
        // Kana for an all-kanji word becomes one ruby; markup is kept as typed.
        assertEquals(
            listOf(
                KanjiExample(first.id, "経験", "{経験|けいけん}", "kinh nghiệm", own = true),
                KanjiExample(second.id, "経験があります", "{経験|けいけん}があります", "có kinh nghiệm", own = true),
            ),
            edits.kanjiExamples("k1"),
        )
        // On the flashcard and the kanji page, after the pack's own words.
        assertEquals(listOf("経験", "経験があります"), content.getKanjiWords("k1").map { it.japanese })
        assertEquals(listOf("kinh nghiệm", "có kinh nghiệm"), content.getKanjiDetail("k1")!!.examples.map { it.translationVi })

        // Invalid: no kanji in it, furigana that does not match, missing meaning. Nothing written.
        val invalid = edits.saveKanjiExample("k1", null, ExampleDraft("増える", "{経験|けいけん}", "")) as ExampleSaveResult.Invalid
        assertEquals(
            mapOf(ExampleField.JAPANESE to FieldError.MISSING_KANJI, ExampleField.FURIGANA to FieldError.FURIGANA_MISMATCH, ExampleField.MEANING to FieldError.REQUIRED),
            invalid.errors,
        )
        assertEquals(2, edits.kanjiExamples("k1").size)

        // A pack update (also one that drops and re-adds the kanji rows) keeps them.
        importer.import(pack(version = 2, kanjiMeaning = "kinh qua"))
        db.close()
        open()
        assertEquals(listOf(first.id, second.id), edits.kanjiExamples("k1").map { it.id })

        assertEquals(ExampleSaveResult.Saved(first.id), edits.saveKanjiExample("k1", first.id, ExampleDraft("経験", "", "trải nghiệm")))
        assertEquals(KanjiExample(first.id, "経験", null, "trải nghiệm", own = true), edits.kanjiExamples("k1").first())
        assertTrue(edits.deleteKanjiExample(second.id))
        assertEquals(listOf(first.id), edits.kanjiExamples("k1").map { it.id })
        assertEquals(0, db.countRows("example_sentences WHERE id = '${second.id}'"))
        // Ids that are not the learner's examples are neither changed nor deleted.
        assertEquals(ExampleSaveResult.NotFound, edits.saveKanjiExample("k1", "pack-example", ExampleDraft("経験", "", "x")))
        assertFalse(edits.deleteKanjiExample("pack-example"))
        assertEquals(ExampleSaveResult.NotFound, edits.saveKanjiExample("no-such-kanji", null, ExampleDraft("経験", "", "x")))
        // The kanji itself, its id and progress-relevant rows are untouched.
        assertEquals("k1", kanji("k1").id)
    }

    @Test
    fun aWordThatAlreadyExists_isNotAddedAgain() = runBlocking {
        val again = edits.addWord(mapOf(EditableField.WORD_EXPRESSION to "経験", EditableField.WORD_READING to "けいけん", EditableField.WORD_MEANING to "kinh nghiệm"))
        assertEquals(SaveResult.DuplicateWord("w1", "Gói thử"), again)
        val first = edits.addWord(mapOf(EditableField.WORD_EXPRESSION to "経由", EditableField.WORD_READING to "けいゆ", EditableField.WORD_MEANING to "đi qua"))
        assertTrue(first is SaveResult.Saved)
        assertTrue(edits.addWord(mapOf(EditableField.WORD_EXPRESSION to "経由", EditableField.WORD_READING to "けいゆ", EditableField.WORD_MEANING to "đi qua")) is SaveResult.DuplicateWord)
        assertEquals(1, db.countRows("vocabulary WHERE lesson_id = '${UserWords.LESSON_ID}'"))
    }

    @Test
    fun deletingMyWord_removesItsScheduleAndMark_butOnlyMyWordsCanBeDeleted() = runBlocking {
        val id = (edits.addWord(mapOf(EditableField.WORD_EXPRESSION to "経由", EditableField.WORD_READING to "けいゆ", EditableField.WORD_MEANING to "đi qua")) as SaveResult.Saved).id
        db.progressDao().upsert(LearningProgressEntity(ItemType.VOCABULARY, id, ProgressState.LEARNING, 0, 20, 1, 1, 1, 0, 1, 1))
        db.progressDao().insertMark(ItemMarkEntity(ItemType.VOCABULARY, id, 1))
        assertFalse(edits.deleteWord("w1"))
        assertTrue(word("w1") != null)
        assertTrue(edits.deleteWord(id))
        assertNull(word(id))
        assertNull(db.progressDao().get(ItemType.VOCABULARY, id))
        assertEquals(0, db.countRows("item_marks"))
    }

    @Test
    fun aPackCannotReplaceMyWords() = runBlocking {
        val hostile = pack(version = 1).replace("\"id\": \"p\"", "\"id\": \"${UserWords.PACK_ID}\"")
        assertEquals(ImportOutcome.FAILED, importer.import(hostile).outcome)
        val stolenId = pack(version = 2).replace("\"id\": \"w2\"", "\"id\": \"${UserWords.ID_PREFIX}x\"")
        val report = importer.import(stolenId)
        assertEquals(ImportOutcome.IMPORTED, report.outcome)
        assertNull(word("${UserWords.ID_PREFIX}x"))
    }
}

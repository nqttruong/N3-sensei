package com.n3sensei.app.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.n3sensei.app.data.local.dao.ContentDao
import com.n3sensei.app.data.local.dao.ContentPackDao
import com.n3sensei.app.data.local.entity.KanjiEntity
import com.n3sensei.app.data.local.entity.LessonEntity
import com.n3sensei.app.data.local.entity.VocabularyEntity
import com.n3sensei.app.domain.furigana.Furigana
import com.n3sensei.app.testing.SeedData
import com.n3sensei.app.testing.countRows
import com.n3sensei.app.testing.inMemoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContentPackDaoTest {

    private lateinit var db: N3Database
    private lateinit var packDao: ContentPackDao
    private lateinit var contentDao: ContentDao

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        packDao = db.contentPackDao()
        contentDao = db.contentDao()
    }

    @After
    fun tearDown() = db.close()

    private val contentTables = listOf(
        "lessons", "vocabulary", "kanji", "grammar", "example_sentences",
        "vocabulary_examples", "kanji_examples", "grammar_examples", "vocabulary_tags",
    )

    @Test
    fun replacePack_insertsEverything() = runTest {
        packDao.replacePack(SeedData.bundle())

        val pack = packDao.getPack(SeedData.PACK_ID)
        assertNotNull(pack)
        assertTrue(pack!!.isSeed)
        assertEquals(2, db.countRows("lessons"))
        assertEquals(4, db.countRows("vocabulary"))
        assertEquals(2, db.countRows("kanji"))
        assertEquals(1, db.countRows("grammar"))
        assertEquals(4, db.countRows("example_sentences"))
        assertEquals(6, db.countRows("vocabulary_examples") + db.countRows("kanji_examples") + db.countRows("grammar_examples"))
    }

    @Test
    fun replacePack_twice_replacesInsteadOfDuplicating() = runTest {
        packDao.replacePack(SeedData.bundle(version = 1))
        packDao.replacePack(SeedData.bundle(version = 2))

        assertEquals(2, packDao.getPack(SeedData.PACK_ID)!!.version)
        assertEquals(1, db.countRows("content_packs"))
        assertEquals(4, db.countRows("vocabulary"))
        assertEquals(1, db.countRows("content_sources"))
    }

    @Test
    fun replacePack_withInvalidRow_rollsBackAndKeepsPreviousContent() = runTest {
        packDao.replacePack(SeedData.bundle(version = 1))
        val orphan = SeedData.bundle().vocabulary.first().copy(id = "orphan", lessonId = "missing-lesson", sortOrder = 99)
        val broken = SeedData.bundle(version = 2).let { it.copy(vocabulary = it.vocabulary + orphan) }

        assertThrows(SQLiteConstraintException::class.java) {
            kotlinx.coroutines.runBlocking { packDao.replacePack(broken) }
        }

        assertEquals(1, packDao.getPack(SeedData.PACK_ID)!!.version)
        assertEquals(4, db.countRows("vocabulary"))
        assertNull(contentDao.getVocabulary("orphan"))
    }

    @Test
    fun deletePack_cascadesToContent_butKeepsSourcesTagsAndProgressTables() = runTest {
        packDao.replacePack(SeedData.bundle())

        assertEquals(1, packDao.deletePack(SeedData.PACK_ID))

        contentTables.forEach { assertEquals("$it should be empty", 0, db.countRows(it)) }
        assertEquals(1, db.countRows("content_sources"))
        assertEquals(1, db.countRows("tags"))
        assertTrue(packDao.observePacks().first().isEmpty())
    }

    @Test
    fun lessonWithUnknownPack_violatesForeignKey() = runTest {
        assertThrows(SQLiteConstraintException::class.java) {
            kotlinx.coroutines.runBlocking {
                packDao.insertLessons(listOf(LessonEntity("x", "no-such-pack", 1, "x", "N3", null)))
            }
        }
    }

    @Test
    fun duplicateLessonNumberInPack_violatesUniqueConstraint() = runTest {
        packDao.replacePack(SeedData.bundle())
        assertThrows(SQLiteConstraintException::class.java) {
            kotlinx.coroutines.runBlocking {
                packDao.insertLessons(listOf(LessonEntity("dup", SeedData.PACK_ID, 1, "dup", "N3", null)))
            }
        }
    }

    @Test
    fun duplicateKanjiInLesson_violatesUniqueConstraint() = runTest {
        packDao.replacePack(SeedData.bundle())
        val dup = KanjiEntity("dup", SeedData.LESSON_1, SeedData.SOURCE_ID, "経", emptyList(), emptyList(), null, "x", null, 99, null)
        assertThrows(SQLiteConstraintException::class.java) {
            kotlinx.coroutines.runBlocking { packDao.insertKanji(listOf(dup)) }
        }
    }

    @Test
    fun duplicateVocabularyId_violatesPrimaryKey() = runTest {
        packDao.replacePack(SeedData.bundle())
        val dup: VocabularyEntity = SeedData.bundle().vocabulary.first().copy(sortOrder = 50)
        assertThrows(SQLiteConstraintException::class.java) {
            kotlinx.coroutines.runBlocking { packDao.insertVocabulary(listOf(dup)) }
        }
    }

    @Test
    fun deletingReferencedSource_isRestricted() = runTest {
        packDao.replacePack(SeedData.bundle())
        assertThrows(SQLiteConstraintException::class.java) {
            db.openHelper.writableDatabase.execSQL("DELETE FROM content_sources WHERE id = '${SeedData.SOURCE_ID}'")
        }
    }

    @Test
    fun kanjiReadingLists_roundTrip() = runTest {
        packDao.replacePack(SeedData.bundle())
        val zou = contentDao.getKanji(SeedData.K_ZOU)!!
        assertEquals(listOf("ゾウ"), zou.onReadings)
        assertEquals(listOf("ま.す", "ふ.える", "ふ.やす"), zou.kunReadings)
    }

    @Test
    fun seedFurigana_isWellFormedAndMatchesText() {
        val bundle = SeedData.bundle()
        bundle.vocabulary.filter { it.furigana != null }.forEach {
            assertEquals(it.expression, Furigana.baseText(it.furigana!!))
            assertEquals(it.reading, Furigana.readingText(it.furigana!!))
        }
        bundle.examples.forEach { assertEquals(it.japanese, Furigana.baseText(it.furigana!!)) }
    }
}

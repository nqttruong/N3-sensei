package com.n3sensei.app.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.data.local.entity.ItemMarkEntity
import com.n3sensei.app.data.local.entity.ReviewRecordEntity
import com.n3sensei.app.data.repository.OfflineContentRepository
import com.n3sensei.app.data.repository.OfflineReviewSessionRepository
import com.n3sensei.app.data.repository.OfflineUserContentRepository
import com.n3sensei.app.domain.edit.EditableField
import com.n3sensei.app.domain.edit.SaveResult
import com.n3sensei.app.domain.model.ItemQuery
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.LearningStatus
import com.n3sensei.app.domain.model.ReviewRating
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Verifies the upgrade path from every exported schema version to the current one, using the
 * real migration list and no destructive fallback, and checks that data written with the v1
 * schema survives the 1 → 2 migration and is usable through the v2 features.
 */
@RunWith(AndroidJUnit4::class)
class N3DatabaseMigrationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val schemaDir = N3Database::class.java.name

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation,
        N3Database::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    private fun exportedVersions(): List<Int> =
        instrumentation.context.assets.list(schemaDir).orEmpty()
            .map { it.removeSuffix(".json").toInt() }
            .sorted()

    @Test
    fun currentVersion_hasExportedSchema() {
        val versions = exportedVersions()
        assertTrue("No schemas exported", versions.isNotEmpty())
        assertEquals(N3Database.VERSION, versions.last())
    }

    @Test
    fun everyExportedVersion_migratesToCurrentAndMatchesSchema() {
        exportedVersions().forEach { version ->
            val name = "migration-test-v$version"
            instrumentation.targetContext.deleteDatabase(name)
            helper.createDatabase(name, version).close()
            helper.runMigrationsAndValidate(name, N3Database.VERSION, true, *N3Migrations.ALL).close()
            instrumentation.targetContext.deleteDatabase(name)
        }
    }

    @Test
    fun dataWrittenWithV1Schema_isReadableAfterOpeningWithRoom() = runTest {
        val name = "migration-test-data-v1"
        instrumentation.targetContext.deleteDatabase(name)
        helper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO content_sources VALUES ('s', 'Nguồn', NULL, 'license', NULL, 0)")
            execSQL("INSERT INTO content_packs VALUES ('p', 'Pack', 1, 's', 0, 0)")
            execSQL("INSERT INTO lessons VALUES ('l', 'p', 1, 'Bài 1', 'N3', NULL)")
            execSQL(
                "INSERT INTO vocabulary (id, lesson_id, source_id, expression, reading, furigana, meaning_vi, " +
                    "part_of_speech, jlpt_level, sort_order, note) " +
                    "VALUES ('v', 'l', 's', '経験', 'けいけん', '{経験|けいけん}', 'kinh nghiệm', NULL, 'N3', 1, NULL)",
            )
            execSQL(
                "INSERT INTO kanji (id, lesson_id, source_id, character, on_readings, kun_readings, han_viet, " +
                    "meaning_vi, stroke_count, sort_order, note) " +
                    "VALUES ('k', 'l', 's', '経', 'ケイ\u001Fキョウ', 'へ.る', 'KINH', 'trải qua', 11, 1, NULL)",
            )
            execSQL(
                "INSERT INTO learning_progress (item_type, item_id, state, step_index, interval_minutes, due_at, " +
                    "last_reviewed_at, review_count, lapse_count, created_at, updated_at) " +
                    "VALUES ('VOCABULARY', 'v', 'REVIEW', 2, 1440, 1000, 500, 3, 0, 1, 500)",
            )
            close()
        }

        val db = Room.databaseBuilder(instrumentation.targetContext, N3Database::class.java, name)
            .addMigrations(*N3Migrations.ALL)
            .build()
        try {
            assertEquals("kinh nghiệm", db.contentDao().getVocabulary("v")!!.meaningVi)
            val progress = db.progressDao().get(ItemType.VOCABULARY, "v")!!
            assertEquals(1440L, progress.intervalMinutes)
            assertEquals(3, progress.reviewCount)

            // v2: search_text was back-filled, so accent-insensitive search finds old rows.
            val repository = OfflineContentRepository(db.contentDao(), db.contentPackDao())
            assertEquals(listOf("v"), repository.searchVocabulary("kinh nghiem").map { it.id })
            assertEquals(listOf("k"), repository.searchKanji("きょう").map { it.id })
            // v2: marks table exists and works with migrated items.
            db.progressDao().insertMark(ItemMarkEntity(ItemType.VOCABULARY, "v", markedAt = 1))
            assertEquals(1, db.progressDao().observeMarkedCount().first())
            assertEquals(LearningStatus.REVIEW, repository.observeVocabularyItems(ItemQuery()).first().single().status)
        } finally {
            db.close()
            instrumentation.targetContext.deleteDatabase(name)
        }
    }

    @Test
    fun reviewHistoryWrittenWithV2Schema_getsWasNewBackfilled_andAllowsNewIdempotentRecords() = runTest {
        val name = "migration-test-data-v2"
        instrumentation.targetContext.deleteDatabase(name)
        helper.createDatabase(name, 2).apply {
            listOf(100L, 200L, 300L).forEach { at ->
                execSQL(
                    "INSERT INTO review_records (item_type, item_id, rating, reviewed_at, interval_before_minutes, " +
                        "interval_after_minutes, due_at_after) VALUES ('KANJI', 'k', 'GOOD', $at, 0, 60, ${at + 3_600_000})",
                )
            }
            execSQL(
                "INSERT INTO review_records (item_type, item_id, rating, reviewed_at, interval_before_minutes, " +
                    "interval_after_minutes, due_at_after) VALUES ('GRAMMAR', 'g', 'AGAIN', 150, 0, 20, 1200150)",
            )
            close()
        }
        helper.runMigrationsAndValidate(name, 3, true, N3Migrations.MIGRATION_2_3).use { db ->
            db.query("SELECT item_id, reviewed_at, was_new, request_id FROM review_records ORDER BY id").use { c ->
                val rows = generateSequence { if (c.moveToNext()) Triple(c.getString(0), c.getLong(1), c.getInt(2)) to c.isNull(3) else null }.toList()
                assertEquals(listOf(1, 0, 0, 1), rows.map { it.first.third })
                assertTrue(rows.all { it.second }) // request_id is NULL for old rows
            }
        }
        val room = Room.databaseBuilder(instrumentation.targetContext, N3Database::class.java, name)
            .addMigrations(*N3Migrations.ALL)
            .build()
        try {
            val dao = room.progressDao()
            val record = ReviewRecordEntity(
                itemType = ItemType.KANJI, itemId = "k", rating = ReviewRating.GOOD, reviewedAt = 400,
                intervalBeforeMinutes = 60, intervalAfterMinutes = 1440, dueAtAfter = 86_400_400, requestId = "fresh",
            )
            dao.insertRecord(record)
            assertEquals(1, dao.countRecordsWithRequest("fresh"))
            assertEquals(4, dao.getHistory(ItemType.KANJI, "k", 10).size)
        } finally {
            room.close()
            instrumentation.targetContext.deleteDatabase(name)
        }
    }

    @Test
    fun progressWrittenWithV3Schema_survivesAndSessionsCanBeRecorded() = runTest {
        val name = "migration-test-data-v3"
        instrumentation.targetContext.deleteDatabase(name)
        helper.createDatabase(name, 3).apply {
            execSQL(
                "INSERT INTO learning_progress (item_type, item_id, state, step_index, interval_minutes, due_at, " +
                    "last_reviewed_at, review_count, lapse_count, created_at, updated_at) " +
                    "VALUES ('VOCABULARY', 'v', 'REVIEW', 3, 2880, 5000, 1000, 4, 1, 100, 1000)",
            )
            close()
        }
        helper.runMigrationsAndValidate(name, 4, true, N3Migrations.MIGRATION_3_4).use { db ->
            db.query("SELECT step_index, due_at FROM learning_progress").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(3, c.getInt(0))
                assertEquals(5000L, c.getLong(1))
            }
            db.query("SELECT COUNT(*) FROM review_sessions").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
        }
        val room = Room.databaseBuilder(instrumentation.targetContext, N3Database::class.java, name)
            .addMigrations(*N3Migrations.ALL)
            .build()
        try {
            val sessions = OfflineReviewSessionRepository(room.reviewSessionDao())
            sessions.start("s", planned = 4, at = Instant.ofEpochMilli(10))
            sessions.updateAnswered("s", 2, Instant.ofEpochMilli(20))
            assertEquals(2, sessions.observeRecent(5).first().single().answered)
        } finally {
            room.close()
            instrumentation.targetContext.deleteDatabase(name)
        }
    }

    @Test
    fun contentProgressAndHistoryWrittenWithV4Schema_surviveAndLearnerEditsWork() = runTest {
        val name = "migration-test-data-v4"
        instrumentation.targetContext.deleteDatabase(name)
        helper.createDatabase(name, 4).apply {
            execSQL("INSERT INTO content_sources VALUES ('s', 'Nguồn', NULL, 'license', NULL, 0)")
            execSQL("INSERT INTO content_packs VALUES ('p', 'Pack', 1, 's', 0, 0)")
            execSQL("INSERT INTO lessons VALUES ('l', 'p', 1, 'Bài 1', 'N3', NULL)")
            execSQL(
                "INSERT INTO vocabulary (id, lesson_id, source_id, expression, reading, furigana, meaning_vi, part_of_speech, " +
                    "jlpt_level, sort_order, note, search_text) VALUES ('v', 'l', 's', '経験', 'けいけん', NULL, 'kinh nghiệm', NULL, 'N3', 1, NULL, '経験 けいけん kinh nghiem')",
            )
            execSQL(
                "INSERT INTO kanji (id, lesson_id, source_id, character, on_readings, kun_readings, han_viet, meaning_vi, " +
                    "stroke_count, sort_order, note, search_text) VALUES ('k', 'l', 's', '経', 'ケイ', '', 'KINH', 'trải qua', 11, 1, NULL, '経 ケイ kinh trai qua')",
            )
            execSQL(
                "INSERT INTO learning_progress (item_type, item_id, state, step_index, interval_minutes, due_at, " +
                    "last_reviewed_at, review_count, lapse_count, created_at, updated_at) " +
                    "VALUES ('KANJI', 'k', 'REVIEW', 2, 1440, 9000, 800, 5, 1, 100, 800)",
            )
            execSQL("INSERT INTO item_marks VALUES ('VOCABULARY', 'v', 7)")
            execSQL(
                "INSERT INTO review_records (item_type, item_id, rating, reviewed_at, interval_before_minutes, " +
                    "interval_after_minutes, due_at_after, request_id, was_new) VALUES ('KANJI', 'k', 'GOOD', 800, 60, 1440, 9000, 'r1', 0)",
            )
            execSQL("INSERT INTO review_sessions VALUES ('s1', 700, 800, 5, 5, 1)")
            close()
        }
        helper.runMigrationsAndValidate(name, 5, true, N3Migrations.MIGRATION_4_5).use { db ->
            db.query("SELECT COUNT(*) FROM user_content_edits").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
        }
        val room = Room.databaseBuilder(instrumentation.targetContext, N3Database::class.java, name)
            .addMigrations(*N3Migrations.ALL)
            .build()
        try {
            val before = room.progressDao().get(ItemType.KANJI, "k")!!
            assertEquals(5, before.reviewCount)
            assertEquals(1, room.progressDao().getHistory(ItemType.KANJI, "k", 10).size)
            assertEquals(1, room.progressDao().observeMarkedCount().first())
            assertEquals("kinh nghiệm", room.contentDao().getVocabulary("v")!!.meaningVi)

            // The new table works on the migrated rows, without touching progress.
            val edits = OfflineUserContentRepository(room.userContentDao(), Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC))
            assertEquals(SaveResult.Saved("k"), edits.save(ItemType.KANJI, "k", mapOf(EditableField.KANJI_MEANING to "kinh qua, trải qua")))
            assertEquals("kinh qua, trải qua", room.contentDao().getKanji("k")!!.meaningVi)
            assertEquals(before, room.progressDao().get(ItemType.KANJI, "k"))
            assertTrue(edits.addWord(mapOf(EditableField.WORD_EXPRESSION to "経由", EditableField.WORD_READING to "けいゆ", EditableField.WORD_MEANING to "đi qua")) is SaveResult.Saved)
        } finally {
            room.close()
            instrumentation.targetContext.deleteDatabase(name)
        }
    }

    @Test
    fun learnerEditsAndOwnWordsWrittenWithV5Schema_surviveTheMigrationTo6() = runTest {
        val name = "migration-test-data-v5"
        instrumentation.targetContext.deleteDatabase(name)
        helper.createDatabase(name, 5).apply {
            execSQL("INSERT INTO content_sources VALUES ('user-words', 'Bạn tự thêm', NULL, 'x', NULL, 0)")
            execSQL("INSERT INTO content_packs VALUES ('user-words', 'Từ của tôi', 1, 'user-words', 0, 0)")
            execSQL("INSERT INTO lessons VALUES ('user-words-l1', 'user-words', 1, 'Từ của tôi', 'N3', NULL)")
            execSQL(
                "INSERT INTO vocabulary (id, lesson_id, source_id, expression, reading, furigana, meaning_vi, part_of_speech, " +
                    "jlpt_level, sort_order, note, search_text) VALUES ('user-w-1', 'user-words-l1', 'user-words', '経由', 'けいゆ', NULL, 'đi qua', NULL, 'N3', 0, NULL, '')",
            )
            execSQL("INSERT INTO user_content_edits VALUES ('KANJI', 'k', 'meaningVi', 'kinh qua', 'trải qua', 10, 20)")
            execSQL(
                "INSERT INTO learning_progress (item_type, item_id, state, step_index, interval_minutes, due_at, " +
                    "last_reviewed_at, review_count, lapse_count, created_at, updated_at) " +
                    "VALUES ('VOCABULARY', 'user-w-1', 'LEARNING', 1, 60, 5000, 1000, 2, 0, 100, 1000)",
            )
            close()
        }
        helper.runMigrationsAndValidate(name, 6, true, N3Migrations.MIGRATION_5_6).use { db ->
            db.query("SELECT value, base_value, base_changed FROM user_content_edits").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("kinh qua", c.getString(0))
                assertEquals("trải qua", c.getString(1))
                assertEquals(0, c.getInt(2))
            }
            db.query("SELECT vocabulary_id FROM user_words").use { c -> assertTrue(c.moveToFirst()); assertEquals("user-w-1", c.getString(0)) }
            db.query("SELECT COUNT(*) FROM vocabulary_kanji_links").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
            db.query("SELECT review_count FROM learning_progress WHERE item_id = 'user-w-1'").use { c -> c.moveToFirst(); assertEquals(2, c.getInt(0)) }
        }
        instrumentation.targetContext.deleteDatabase(name)
    }

    @Test
    fun learnerDataWrittenWithV6Schema_survivesTheMigrationTo7_withNoKanjiExamplesYet() = runTest {
        val name = "migration-test-data-v6"
        instrumentation.targetContext.deleteDatabase(name)
        helper.createDatabase(name, 6).apply {
            execSQL("INSERT INTO user_words VALUES ('user-w-1', 10, 20)")
            execSQL("INSERT INTO vocabulary_kanji_links VALUES ('user-w-1', 'k', 30)")
            execSQL("INSERT INTO user_content_edits VALUES ('KANJI', 'k', 'meaningVi', 'kinh qua', 'trải qua', 10, 20, 0)")
            close()
        }
        helper.runMigrationsAndValidate(name, 7, true, N3Migrations.MIGRATION_6_7).use { db ->
            db.query("SELECT created_at, updated_at FROM user_words WHERE vocabulary_id = 'user-w-1'").use { c ->
                assertTrue(c.moveToFirst()); assertEquals(10, c.getInt(0)); assertEquals(20, c.getInt(1))
            }
            db.query("SELECT COUNT(*) FROM vocabulary_kanji_links").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
            db.query("SELECT value FROM user_content_edits").use { c -> c.moveToFirst(); assertEquals("kinh qua", c.getString(0)) }
            db.query("SELECT COUNT(*) FROM user_kanji_examples").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
        }
        instrumentation.targetContext.deleteDatabase(name)
    }

    @Test
    fun dataWrittenWithV7Schema_survivesTheMigrationTo8_withAnEmptyReminderHistory() = runTest {
        val name = "migration-test-data-v7"
        instrumentation.targetContext.deleteDatabase(name)
        helper.createDatabase(name, 7).apply {
            execSQL("INSERT INTO user_kanji_examples VALUES ('user-x-k-1', 'k', 0, 10, 20)")
            execSQL("INSERT INTO user_words VALUES ('user-w-1', 10, 20)")
            close()
        }
        helper.runMigrationsAndValidate(name, 8, true, N3Migrations.MIGRATION_7_8).use { db ->
            db.query("SELECT kanji_id FROM user_kanji_examples").use { c -> assertTrue(c.moveToFirst()); assertEquals("k", c.getString(0)) }
            db.query("SELECT COUNT(*) FROM user_words").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
            db.query("SELECT COUNT(*) FROM reminder_history").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
        }
        instrumentation.targetContext.deleteDatabase(name)
    }

    @Test
    fun learnerDataWrittenWithV8Schema_survivesTheMigrationTo9_andTheExamTablesStartEmpty() = runTest {
        val name = "migration-test-data-v8"
        instrumentation.targetContext.deleteDatabase(name)
        helper.createDatabase(name, 8).apply {
            execSQL(
                "INSERT INTO learning_progress (item_type, item_id, state, step_index, interval_minutes, due_at, " +
                    "last_reviewed_at, review_count, lapse_count, created_at, updated_at) " +
                    "VALUES ('KANJI', 'k', 'REVIEW', 3, 2880, 5000, 1000, 4, 1, 100, 1000)",
            )
            execSQL("INSERT INTO user_words VALUES ('user-w-1', 10, 20)")
            execSQL("INSERT INTO user_kanji_examples VALUES ('user-x-k-1', 'k', 0, 10, 20)")
            execSQL("INSERT INTO reminder_history VALUES ('KANJI', 'k', 3000, 2)")
            close()
        }
        helper.runMigrationsAndValidate(name, 9, true, N3Migrations.MIGRATION_8_9).use { db ->
            db.query("SELECT step_index, review_count, lapse_count FROM learning_progress WHERE item_id = 'k'").use { c ->
                assertTrue(c.moveToFirst()); assertEquals(3, c.getInt(0)); assertEquals(4, c.getInt(1)); assertEquals(1, c.getInt(2))
            }
            db.query("SELECT COUNT(*) FROM user_words").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
            db.query("SELECT COUNT(*) FROM user_kanji_examples").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
            db.query("SELECT shown_count FROM reminder_history").use { c -> c.moveToFirst(); assertEquals(2, c.getInt(0)) }
            listOf(
                "exam_sets", "exam_sections", "exam_passages", "exam_questions", "exam_question_items",
                "exam_attempts", "exam_answers", "error_records", "review_items",
            ).forEach { table ->
                db.query("SELECT COUNT(*) FROM $table").use { c -> c.moveToFirst(); assertEquals(table, 0, c.getInt(0)) }
            }
            // Foreign keys of the new tables hold on the migrated database.
            db.query("PRAGMA foreign_key_check").use { c -> assertFalse(c.moveToFirst()) }
        }
        instrumentation.targetContext.deleteDatabase(name)
    }

    @Test
    fun anAttemptInProgressWrittenWithV9Schema_survivesTheMigrationTo10_onItsFirstQuestionWithNoFlags() = runTest {
        val name = "migration-test-data-v9"
        instrumentation.targetContext.deleteDatabase(name)
        helper.createDatabase(name, 9).apply {
            execSQL("INSERT INTO exam_attempts VALUES ('a-1', 'set-1', 'Đề', 1000, NULL, 1800, NULL, NULL, 6, 'IN_PROGRESS')")
            execSQL("INSERT INTO exam_answers VALUES ('a-1', 'q-1', 2, NULL, 1, 4000, NULL, 1, 2000, '{}')")
            execSQL("INSERT INTO user_words VALUES ('user-w-1', 10, 20)")
            close()
        }
        helper.runMigrationsAndValidate(name, 10, true, N3Migrations.MIGRATION_9_10).use { db ->
            db.query("SELECT status, current_question_id, time_limit_seconds FROM exam_attempts WHERE id = 'a-1'").use { c ->
                assertTrue(c.moveToFirst()); assertEquals("IN_PROGRESS", c.getString(0)); assertTrue(c.isNull(1)); assertEquals(1800, c.getInt(2))
            }
            db.query("SELECT selected_index, change_count FROM exam_answers").use { c ->
                assertTrue(c.moveToFirst()); assertEquals(2, c.getInt(0)); assertEquals(1, c.getInt(1))
            }
            db.query("SELECT COUNT(*) FROM user_words").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
            db.query("SELECT COUNT(*) FROM exam_flags").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
            // A flag goes with its attempt.
            db.execSQL("PRAGMA foreign_keys = ON")
            db.execSQL("INSERT INTO exam_flags VALUES ('a-1', 'q-1', 3000)")
            db.execSQL("DELETE FROM exam_attempts WHERE id = 'a-1'")
            db.query("SELECT COUNT(*) FROM exam_flags").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
            db.query("PRAGMA foreign_key_check").use { c -> assertFalse(c.moveToFirst()) }
        }
        instrumentation.targetContext.deleteDatabase(name)
    }
}

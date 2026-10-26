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
}

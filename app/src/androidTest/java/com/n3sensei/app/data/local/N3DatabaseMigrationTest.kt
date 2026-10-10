package com.n3sensei.app.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.data.local.entity.ItemMarkEntity
import com.n3sensei.app.data.repository.OfflineContentRepository
import com.n3sensei.app.domain.model.ItemQuery
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.LearningStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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
}

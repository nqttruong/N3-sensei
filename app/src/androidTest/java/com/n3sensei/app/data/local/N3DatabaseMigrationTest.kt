package com.n3sensei.app.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.domain.model.ItemType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the upgrade path from every exported schema version to the current one, using the
 * real migration list and no destructive fallback. With only version 1 exported, this checks
 * that the v1 schema file matches the entities and that v1 data opens through Room; each new
 * version automatically adds its own path to the loop.
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
        } finally {
            db.close()
            instrumentation.targetContext.deleteDatabase(name)
        }
    }
}

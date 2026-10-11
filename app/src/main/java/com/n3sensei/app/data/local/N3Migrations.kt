package com.n3sensei.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Every schema migration, in order. The app never falls back to destructive migration, so a
 * missing step makes the database open fail instead of silently deleting learner data.
 *
 * To change the schema:
 * 1. Bump [N3Database] `version` and build; Room exports `app/schemas/.../<version>.json`.
 * 2. Add a `Migration(old, new)` here, copying table SQL from the exported JSON.
 * 3. Commit the new schema JSON; `N3DatabaseMigrationTest` then covers the new step.
 */
object N3Migrations {

    /**
     * v2: learner marks (`item_marks`), grammar exercises, and normalized `search_text` on
     * vocabulary, kanji and grammar, back-filled for rows that already exist.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `item_marks` (`item_type` TEXT NOT NULL, `item_id` TEXT NOT NULL, " +
                    "`marked_at` INTEGER NOT NULL, PRIMARY KEY(`item_type`, `item_id`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `grammar_exercises` (`id` TEXT NOT NULL, `grammar_id` TEXT NOT NULL, " +
                    "`sort_order` INTEGER NOT NULL, `prompt` TEXT NOT NULL, `options` TEXT NOT NULL, " +
                    "`answer_index` INTEGER NOT NULL, `explanation_vi` TEXT, PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`grammar_id`) REFERENCES `grammar`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_grammar_exercises_grammar_id_sort_order` " +
                    "ON `grammar_exercises` (`grammar_id`, `sort_order`)",
            )
            listOf("vocabulary", "kanji", "grammar").forEach { table ->
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `search_text` TEXT NOT NULL DEFAULT ''")
            }
            backfillSearchText(db, "vocabulary", listOf("expression", "reading", "meaning_vi")) { SearchText.of(*it) }
            backfillSearchText(db, "kanji", listOf("character", "on_readings", "kun_readings", "han_viet", "meaning_vi")) {
                // Reading lists are stored with a separator; search treats them as plain text.
                SearchText.of(it[0], it[1]?.toReadings(), it[2]?.toReadings(), it[3], it[4])
            }
            backfillSearchText(db, "grammar", listOf("pattern", "structure", "meaning_vi")) { SearchText.of(*it) }
        }
    }

    /**
     * v3: idempotent answers and daily limits. `review_records` gets `request_id` (unique; NULL
     * for older rows) and `was_new`, back-filled as true for each item's earliest record.
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `review_records` ADD COLUMN `request_id` TEXT")
            db.execSQL("ALTER TABLE `review_records` ADD COLUMN `was_new` INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_review_records_request_id` ON `review_records` (`request_id`)",
            )
            db.execSQL(
                "UPDATE `review_records` SET `was_new` = 1 WHERE `id` IN " +
                    "(SELECT MIN(`id`) FROM `review_records` GROUP BY `item_type`, `item_id`)",
            )
        }
    }

    /** v4: `review_sessions` for the dashboard's session history (no existing data to convert). */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `review_sessions` (`id` TEXT NOT NULL, `started_at` INTEGER NOT NULL, " +
                    "`last_activity_at` INTEGER NOT NULL, `planned` INTEGER NOT NULL, `answered` INTEGER NOT NULL, " +
                    "`finished` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_review_sessions_started_at` ON `review_sessions` (`started_at`)")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

    private fun String.toReadings(): String = StringListCodec.decode(this).joinToString(" ")

    private fun backfillSearchText(
        db: SupportSQLiteDatabase,
        table: String,
        columns: List<String>,
        build: (Array<String?>) -> String,
    ) {
        val updates = mutableListOf<Pair<String, String>>()
        db.query("SELECT `id`, ${columns.joinToString { "`$it`" }} FROM `$table`").use { cursor ->
            while (cursor.moveToNext()) {
                val values = Array(columns.size) { i -> if (cursor.isNull(i + 1)) null else cursor.getString(i + 1) }
                updates += cursor.getString(0) to build(values)
            }
        }
        updates.forEach { (id, text) ->
            db.execSQL("UPDATE `$table` SET `search_text` = ? WHERE `id` = ?", arrayOf<Any>(text, id))
        }
    }
}

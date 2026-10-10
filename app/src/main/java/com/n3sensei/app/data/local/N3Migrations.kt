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

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)

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

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

    /**
     * v5: `user_content_edits`, the learner's changes to pack items (Part F). A new empty
     * table: no existing row is read or changed.
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `user_content_edits` (`item_type` TEXT NOT NULL, `item_id` TEXT NOT NULL, " +
                    "`field` TEXT NOT NULL, `value` TEXT NOT NULL, `base_value` TEXT NOT NULL, `created_at` INTEGER NOT NULL, " +
                    "`updated_at` INTEGER NOT NULL, PRIMARY KEY(`item_type`, `item_id`, `field`))",
            )
        }
    }

    /**
     * v6 (additions only): `user_content_edits.base_changed` (conflict flag, false for existing
     * edits), `vocabulary_kanji_links` (learner's word ↔ kanji links, empty) and `user_words`
     * (dates of the learner's own words: existing words get the migration time, their real
     * creation time was never stored).
     */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `user_content_edits` ADD COLUMN `base_changed` INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `vocabulary_kanji_links` (`vocabulary_id` TEXT NOT NULL, `kanji_id` TEXT NOT NULL, " +
                    "`created_at` INTEGER NOT NULL, PRIMARY KEY(`vocabulary_id`, `kanji_id`))",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_vocabulary_kanji_links_kanji_id` ON `vocabulary_kanji_links` (`kanji_id`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `user_words` (`vocabulary_id` TEXT NOT NULL, `created_at` INTEGER NOT NULL, " +
                    "`updated_at` INTEGER NOT NULL, PRIMARY KEY(`vocabulary_id`))",
            )
            db.execSQL(
                "INSERT INTO `user_words` (`vocabulary_id`, `created_at`, `updated_at`) " +
                    "SELECT v.`id`, CAST(strftime('%s','now') AS INTEGER) * 1000, CAST(strftime('%s','now') AS INTEGER) * 1000 " +
                    "FROM `vocabulary` v JOIN `lessons` l ON l.`id` = v.`lesson_id` WHERE l.`pack_id` = 'user-words'",
            )
        }
    }

    /** 6 → 7: `user_kanji_examples`, the learner's own examples of a kanji (empty). Nothing else changes. */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `user_kanji_examples` (`example_id` TEXT NOT NULL, `kanji_id` TEXT NOT NULL, " +
                    "`sort_order` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`example_id`))",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_kanji_examples_kanji_id` ON `user_kanji_examples` (`kanji_id`)")
        }
    }

    /** 7 → 8: `reminder_history`, when each item was last shown in a study reminder (empty). */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `reminder_history` (`item_type` TEXT NOT NULL, `item_id` TEXT NOT NULL, " +
                    "`last_shown_at` INTEGER NOT NULL, `shown_count` INTEGER NOT NULL, PRIMARY KEY(`item_type`, `item_id`))",
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)

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

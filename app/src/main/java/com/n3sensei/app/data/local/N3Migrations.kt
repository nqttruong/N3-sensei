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

    /**
     * 8 → 9: the JLPT exam module. Only new, empty tables (exam content, attempts and answers,
     * error notebook and its reviews); no existing table or row changes. Statements copied from
     * the schema Room exports for version 9, so the result matches it exactly.
     */
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            listOf(
                "CREATE TABLE IF NOT EXISTS `exam_sets` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT, `jlpt_level` TEXT NOT NULL, `exam_type` TEXT NOT NULL, `duration_seconds` INTEGER NOT NULL, `source` TEXT, `created_at` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                "CREATE INDEX IF NOT EXISTS `index_exam_sets_jlpt_level` ON `exam_sets` (`jlpt_level`)",
                "CREATE TABLE IF NOT EXISTS `exam_sections` (`id` TEXT NOT NULL, `exam_set_id` TEXT NOT NULL, `section` TEXT NOT NULL, `duration_seconds` INTEGER, `section_order` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`exam_set_id`) REFERENCES `exam_sets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                "CREATE INDEX IF NOT EXISTS `index_exam_sections_exam_set_id` ON `exam_sections` (`exam_set_id`)",
                "CREATE TABLE IF NOT EXISTS `exam_passages` (`id` TEXT NOT NULL, `exam_set_id` TEXT NOT NULL, `title` TEXT, `text` TEXT, `furigana` TEXT, `audio_path` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`exam_set_id`) REFERENCES `exam_sets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                "CREATE INDEX IF NOT EXISTS `index_exam_passages_exam_set_id` ON `exam_passages` (`exam_set_id`)",
                "CREATE TABLE IF NOT EXISTS `exam_questions` (`id` TEXT NOT NULL, `exam_set_id` TEXT NOT NULL, `section_id` TEXT NOT NULL, `question_type` TEXT NOT NULL, `answer_format` TEXT NOT NULL, `content` TEXT NOT NULL, `content_furigana` TEXT, `options` TEXT NOT NULL, `answer_index` INTEGER, `accepted_answers` TEXT NOT NULL, `explanation` TEXT, `difficulty` INTEGER, `passage_id` TEXT, `audio_path` TEXT, `question_order` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`exam_set_id`) REFERENCES `exam_sets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`section_id`) REFERENCES `exam_sections`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`passage_id`) REFERENCES `exam_passages`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
                "CREATE INDEX IF NOT EXISTS `index_exam_questions_exam_set_id_question_order` ON `exam_questions` (`exam_set_id`, `question_order`)",
                "CREATE INDEX IF NOT EXISTS `index_exam_questions_section_id` ON `exam_questions` (`section_id`)",
                "CREATE INDEX IF NOT EXISTS `index_exam_questions_passage_id` ON `exam_questions` (`passage_id`)",
                "CREATE TABLE IF NOT EXISTS `exam_question_items` (`question_id` TEXT NOT NULL, `item_type` TEXT NOT NULL, `item_id` TEXT NOT NULL, PRIMARY KEY(`question_id`, `item_type`, `item_id`), FOREIGN KEY(`question_id`) REFERENCES `exam_questions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                "CREATE INDEX IF NOT EXISTS `index_exam_question_items_item_type_item_id` ON `exam_question_items` (`item_type`, `item_id`)",
                "CREATE TABLE IF NOT EXISTS `exam_attempts` (`id` TEXT NOT NULL, `exam_set_id` TEXT NOT NULL, `exam_title` TEXT NOT NULL, `started_at` INTEGER NOT NULL, `submitted_at` INTEGER, `time_limit_seconds` INTEGER NOT NULL, `duration_seconds` INTEGER, `correct_count` INTEGER, `question_count` INTEGER NOT NULL, `status` TEXT NOT NULL, PRIMARY KEY(`id`))",
                "CREATE INDEX IF NOT EXISTS `index_exam_attempts_exam_set_id` ON `exam_attempts` (`exam_set_id`)",
                "CREATE INDEX IF NOT EXISTS `index_exam_attempts_started_at` ON `exam_attempts` (`started_at`)",
                "CREATE TABLE IF NOT EXISTS `exam_answers` (`attempt_id` TEXT NOT NULL, `question_id` TEXT NOT NULL, `selected_index` INTEGER, `text_answer` TEXT, `is_correct` INTEGER NOT NULL, `time_spent_ms` INTEGER NOT NULL, `confidence` TEXT, `change_count` INTEGER NOT NULL, `answered_at` INTEGER NOT NULL, `question_snapshot` TEXT NOT NULL, PRIMARY KEY(`attempt_id`, `question_id`), FOREIGN KEY(`attempt_id`) REFERENCES `exam_attempts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                "CREATE INDEX IF NOT EXISTS `index_exam_answers_question_id` ON `exam_answers` (`question_id`)",
                "CREATE TABLE IF NOT EXISTS `error_records` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `question_id` TEXT NOT NULL, `exam_set_id` TEXT NOT NULL, `error_type` TEXT NOT NULL, `error_count` INTEGER NOT NULL, `first_wrong_at` INTEGER NOT NULL, `last_wrong_at` INTEGER NOT NULL, `mastery_level` INTEGER NOT NULL, `status` TEXT NOT NULL, `notes` TEXT, `question_snapshot` TEXT NOT NULL)",
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_error_records_question_id` ON `error_records` (`question_id`)",
                "CREATE INDEX IF NOT EXISTS `index_error_records_status` ON `error_records` (`status`)",
                "CREATE TABLE IF NOT EXISTS `review_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `error_record_id` INTEGER NOT NULL, `next_review_at` INTEGER NOT NULL, `interval_minutes` INTEGER NOT NULL, `step_index` INTEGER NOT NULL, `review_count` INTEGER NOT NULL, `success_count` INTEGER NOT NULL, `last_reviewed_at` INTEGER, `review_status` TEXT NOT NULL, FOREIGN KEY(`error_record_id`) REFERENCES `error_records`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_review_items_error_record_id` ON `review_items` (`error_record_id`)",
                "CREATE INDEX IF NOT EXISTS `index_review_items_next_review_at` ON `review_items` (`next_review_at`)",
            ).forEach(db::execSQL)
        }
    }

    /**
     * 9 → 10: exam sessions. `exam_attempts` gets `current_question_id` (null for the attempts
     * already there: they open on their first question) and `exam_flags` holds the questions
     * marked to look at again (empty). No row changes.
     */
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `exam_attempts` ADD COLUMN `current_question_id` TEXT")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `exam_flags` (`attempt_id` TEXT NOT NULL, `question_id` TEXT NOT NULL, " +
                    "`flagged_at` INTEGER NOT NULL, PRIMARY KEY(`attempt_id`, `question_id`), FOREIGN KEY(`attempt_id`) " +
                    "REFERENCES `exam_attempts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
        }
    }

    /**
     * 10 → 11: `exam_attempt_items`, the graded questions of each submitted attempt (empty:
     * attempts submitted before have no rows and their result is rebuilt from their answers).
     */
    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `exam_attempt_items` (`attempt_id` TEXT NOT NULL, `position` INTEGER NOT NULL, " +
                    "`question_id` TEXT NOT NULL, `section` TEXT NOT NULL, `skill` TEXT NOT NULL, `outcome` TEXT NOT NULL, " +
                    "`question_snapshot` TEXT NOT NULL, PRIMARY KEY(`attempt_id`, `position`), FOREIGN KEY(`attempt_id`) " +
                    "REFERENCES `exam_attempts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
        }
    }

    /**
     * 11 → 12: the error notebook. `error_records` gets the learner's flag ("cần chú ý"), when they
     * marked it understood, and the level and skill to filter by; `error_review_log` keeps every
     * review (empty). Level and skill are filled in for existing errors from their exam set and
     * their latest graded question, when those exist; otherwise they stay null. No row is lost.
     */
    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `error_records` ADD COLUMN `is_important` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `error_records` ADD COLUMN `understood_at` INTEGER")
            db.execSQL("ALTER TABLE `error_records` ADD COLUMN `jlpt_level` TEXT")
            db.execSQL("ALTER TABLE `error_records` ADD COLUMN `skill` TEXT")
            db.execSQL("UPDATE `error_records` SET `jlpt_level` = (SELECT s.`jlpt_level` FROM `exam_sets` s WHERE s.`id` = `error_records`.`exam_set_id`)")
            db.execSQL(
                "UPDATE `error_records` SET `skill` = (SELECT i.`skill` FROM `exam_attempt_items` i JOIN `exam_attempts` a ON a.`id` = i.`attempt_id` " +
                    "WHERE i.`question_id` = `error_records`.`question_id` ORDER BY a.`submitted_at` DESC LIMIT 1)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `error_review_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `error_record_id` INTEGER NOT NULL, " +
                    "`reviewed_at` INTEGER NOT NULL, `correct` INTEGER NOT NULL, `counted` INTEGER NOT NULL, `step_before` INTEGER NOT NULL, " +
                    "`step_after` INTEGER NOT NULL, FOREIGN KEY(`error_record_id`) REFERENCES `error_records`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_error_review_log_error_record_id` ON `error_review_log` (`error_record_id`)")
        }
    }

    /** 12 → 13: each error review records which question was asked and of what kind (null before). */
    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `error_review_log` ADD COLUMN `question_id` TEXT")
            db.execSQL("ALTER TABLE `error_review_log` ADD COLUMN `question_kind` TEXT")
        }
    }

    /**
     * 13 → 14: "Dịch nhanh" bookmarks. `user_words` says whether a word is studied: every existing
     * word gets `STUDYING` (the column default), so nothing changes for them; bookmarks are added
     * as `SAVED`. `source_language`/`source_key` (null for existing words) identify a bookmark,
     * unique together. `user_word_translations` keeps a bookmark's English/Japanese translations
     * (empty). Only additions: no row, progress, history or example is changed.
     */
    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `user_words` ADD COLUMN `study_state` TEXT NOT NULL DEFAULT 'STUDYING'")
            db.execSQL("ALTER TABLE `user_words` ADD COLUMN `source_language` TEXT")
            db.execSQL("ALTER TABLE `user_words` ADD COLUMN `source_key` TEXT")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_user_words_source_language_source_key` ON `user_words` (`source_language`, `source_key`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `user_word_translations` (`vocabulary_id` TEXT NOT NULL, `language` TEXT NOT NULL, " +
                    "`text` TEXT NOT NULL, `reading` TEXT, `meaning` TEXT, `sort_order` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`vocabulary_id`, `language`, `text`))",
            )
        }
    }

    /** 14 → 15: isolated JLPT AI Review mirror, checkpoint and append-only outbox. */
    val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `synced_exams` (`id` TEXT NOT NULL, `server_id` TEXT NOT NULL, `title` TEXT NOT NULL, `category` TEXT NOT NULL, `jlpt_level` TEXT NOT NULL, `description` TEXT, `source_url` TEXT, `status` TEXT NOT NULL, `is_deleted` INTEGER NOT NULL, `updated_cursor` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_synced_exams_updated_cursor` ON `synced_exams` (`updated_cursor`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `synced_passages` (`id` TEXT NOT NULL, `server_id` TEXT NOT NULL, `exam_id` TEXT NOT NULL, `text` TEXT NOT NULL, `is_deleted` INTEGER NOT NULL, `updated_cursor` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_synced_passages_exam_id` ON `synced_passages` (`exam_id`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `synced_questions` (`id` TEXT NOT NULL, `server_id` TEXT NOT NULL, `exam_id` TEXT NOT NULL, `passage_id` TEXT, `question_number` INTEGER NOT NULL, `question_type` TEXT NOT NULL, `content` TEXT NOT NULL, `options` TEXT NOT NULL, `answer_index` INTEGER, `answer_verified` INTEGER NOT NULL, `is_deleted` INTEGER NOT NULL, `updated_cursor` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_synced_questions_exam_id` ON `synced_questions` (`exam_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_synced_questions_passage_id` ON `synced_questions` (`passage_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_synced_questions_updated_cursor` ON `synced_questions` (`updated_cursor`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `synced_explanations` (`id` TEXT NOT NULL, `server_id` TEXT NOT NULL, `question_id` TEXT NOT NULL, `schema_version` TEXT NOT NULL, `explanation_json` TEXT NOT NULL, `available` INTEGER NOT NULL, `updated_cursor` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_synced_explanations_question_id` ON `synced_explanations` (`question_id`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `synced_attempts` (`id` TEXT NOT NULL, `server_id` TEXT NOT NULL, `exam_id` TEXT NOT NULL, `started_at` TEXT, `completed_at` TEXT, `total_questions` INTEGER NOT NULL, `source` TEXT, `updated_cursor` INTEGER NOT NULL, `is_deleted` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_synced_attempts_exam_id` ON `synced_attempts` (`exam_id`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `synced_knowledge` (`id` TEXT NOT NULL, `server_id` TEXT NOT NULL, `knowledge_type` TEXT NOT NULL, `jlpt_level` TEXT NOT NULL, `title` TEXT NOT NULL, `content` TEXT NOT NULL, `details_json` TEXT NOT NULL, `linked_question_ids_json` TEXT NOT NULL, `status` TEXT NOT NULL, `merged_into_id` TEXT, `version` INTEGER NOT NULL, `updated_cursor` INTEGER NOT NULL, `is_deleted` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_synced_knowledge_knowledge_type` ON `synced_knowledge` (`knowledge_type`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_synced_knowledge_status` ON `synced_knowledge` (`status`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `synced_attempt_answers` (`server_id` TEXT NOT NULL, `attempt_id` TEXT NOT NULL, `question_id` TEXT NOT NULL, `question_number` INTEGER NOT NULL, `selected_index` INTEGER, `correct_index` INTEGER, `is_correct` INTEGER, `updated_cursor` INTEGER NOT NULL, `is_deleted` INTEGER NOT NULL, PRIMARY KEY(`attempt_id`, `question_id`))")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_synced_attempt_answers_question_id` ON `synced_attempt_answers` (`question_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_synced_attempt_answers_is_correct` ON `synced_attempt_answers` (`is_correct`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `synced_review_items` (`id` TEXT NOT NULL, `server_id` TEXT NOT NULL, `review_type` TEXT NOT NULL, `status` TEXT NOT NULL, `source_question_id` TEXT, `source_question_ids_json` TEXT NOT NULL, `knowledge_id` TEXT, `related_knowledge_id` TEXT, `source_explanation_id` TEXT, `remote_version` INTEGER NOT NULL, `updated_cursor` INTEGER NOT NULL, `local_step_index` INTEGER, `local_interval_minutes` INTEGER NOT NULL, `local_due_at` INTEGER, `local_last_reviewed_at` INTEGER, `local_review_count` INTEGER NOT NULL, `is_deleted` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_synced_review_items_status` ON `synced_review_items` (`status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_synced_review_items_local_due_at` ON `synced_review_items` (`local_due_at`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `synced_review_events` (`request_id` TEXT NOT NULL, `item_type` TEXT NOT NULL, `item_id` TEXT NOT NULL, `rating` TEXT NOT NULL, `reviewed_at` INTEGER NOT NULL, `interval_before_minutes` INTEGER NOT NULL, `interval_after_minutes` INTEGER NOT NULL, `due_at_after` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `uploaded_at` INTEGER, `attempt_count` INTEGER NOT NULL, `last_error` TEXT, PRIMARY KEY(`request_id`))")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_synced_review_events_uploaded_at` ON `synced_review_events` (`uploaded_at`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_synced_review_events_item_id` ON `synced_review_events` (`item_id`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `sync_checkpoints` (`id` TEXT NOT NULL, `cursor` INTEGER NOT NULL, `schema_version` TEXT NOT NULL, `last_success_at` INTEGER, `last_error` TEXT, `downloaded_count` INTEGER NOT NULL, `last_upload_at` INTEGER, PRIMARY KEY(`id`))")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15)

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

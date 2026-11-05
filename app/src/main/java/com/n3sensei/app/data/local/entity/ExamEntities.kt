package com.n3sensei.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.n3sensei.app.domain.model.ItemType

// JLPT exam module (schema v9). Two groups, like the rest of the database:
// - exam content (sets, sections, passages, questions, their links to study items): replaced
//   as a whole when an exam set is saved again, with foreign keys inside the group;
// - learner data (attempts, answers, error records, review items): keyed by ids only, no
//   foreign key to the content, so deleting or updating an exam set never removes history.

/** An exam: a full mock test, one skill, or a generated practice set. */
@Entity(tableName = "exam_sets", indices = [Index("jlpt_level")])
data class ExamSetEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String?,
    @ColumnInfo(name = "jlpt_level") val jlptLevel: String,
    /** FULL, SECTION or GENERATED (see ExamType). */
    @ColumnInfo(name = "exam_type") val examType: String,
    /** Time limit of the whole set; sections may also have their own. */
    @ColumnInfo(name = "duration_seconds") val durationSeconds: Int,
    /** Where the questions come from and under which terms; shown with the exam, never invented. */
    val source: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** A part of an exam (文字・語彙, 文法, 読解, 聴解) with its own time limit and order. */
@Entity(
    tableName = "exam_sections",
    foreignKeys = [ForeignKey(entity = ExamSetEntity::class, parentColumns = ["id"], childColumns = ["exam_set_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("exam_set_id")],
)
data class ExamSectionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "exam_set_id") val examSetId: String,
    /** VOCABULARY, GRAMMAR, READING or LISTENING (see ExamSectionKind). */
    val section: String,
    @ColumnInfo(name = "duration_seconds") val durationSeconds: Int?,
    @ColumnInfo(name = "section_order") val sectionOrder: Int,
)

/** A reading text or a listening audio used by one or more questions. */
@Entity(
    tableName = "exam_passages",
    foreignKeys = [ForeignKey(entity = ExamSetEntity::class, parentColumns = ["id"], childColumns = ["exam_set_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("exam_set_id")],
)
data class ExamPassageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "exam_set_id") val examSetId: String,
    val title: String?,
    /** Reading text (or a listening transcript), with optional `{base|reading}` furigana. */
    val text: String?,
    val furigana: String?,
    /** Audio file inside the app's storage, for listening; null for reading. */
    @ColumnInfo(name = "audio_path") val audioPath: String?,
)

@Entity(
    tableName = "exam_questions",
    foreignKeys = [
        ForeignKey(entity = ExamSetEntity::class, parentColumns = ["id"], childColumns = ["exam_set_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ExamSectionEntity::class, parentColumns = ["id"], childColumns = ["section_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ExamPassageEntity::class, parentColumns = ["id"], childColumns = ["passage_id"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [
        Index(value = ["exam_set_id", "question_order"]),
        Index("section_id"),
        Index("passage_id"),
    ],
)
data class ExamQuestionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "exam_set_id") val examSetId: String,
    @ColumnInfo(name = "section_id") val sectionId: String,
    /** The JLPT task (KANJI_READING, GRAMMAR_FORM, READING_COMPREHENSION…); see QuestionType. */
    @ColumnInfo(name = "question_type") val questionType: String,
    /** CHOICE (pick one option) or TEXT (type the answer); see AnswerFormat. */
    @ColumnInfo(name = "answer_format") val answerFormat: String,
    val content: String,
    /** `{base|reading}` markup of [content], when it has furigana. */
    @ColumnInfo(name = "content_furigana") val contentFurigana: String?,
    /** Choices for CHOICE questions; empty for TEXT. */
    val options: List<String>,
    /** Correct choice (0-based) for CHOICE questions. */
    @ColumnInfo(name = "answer_index") val answerIndex: Int?,
    /** Accepted answers for TEXT questions (compared after normalising width and spaces). */
    @ColumnInfo(name = "accepted_answers") val acceptedAnswers: List<String>,
    val explanation: String?,
    /** 1 (easy) … 5 (hard), when the author gives one. */
    val difficulty: Int?,
    @ColumnInfo(name = "passage_id") val passageId: String?,
    /** Audio of this question alone (e.g. 即時応答); a shared audio is on the passage. */
    @ColumnInfo(name = "audio_path") val audioPath: String?,
    @ColumnInfo(name = "question_order") val questionOrder: Int,
)

/**
 * Study items a question tests (zero or more of any kind). No foreign key to vocabulary,
 * kanji or grammar: those are replaced by content pack updates and the link must survive.
 */
@Entity(
    tableName = "exam_question_items",
    primaryKeys = ["question_id", "item_type", "item_id"],
    foreignKeys = [ForeignKey(entity = ExamQuestionEntity::class, parentColumns = ["id"], childColumns = ["question_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["item_type", "item_id"])],
)
data class ExamQuestionItemEntity(
    @ColumnInfo(name = "question_id") val questionId: String,
    @ColumnInfo(name = "item_type") val itemType: ItemType,
    @ColumnInfo(name = "item_id") val itemId: String,
)

/** One sitting of an exam set. Many per set; kept when the set changes or is deleted. */
@Entity(tableName = "exam_attempts", indices = [Index("exam_set_id"), Index("started_at")])
data class ExamAttemptEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "exam_set_id") val examSetId: String,
    /** The set's title when the attempt started, so history stays readable without the set. */
    @ColumnInfo(name = "exam_title") val examTitle: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "submitted_at") val submittedAt: Long?,
    @ColumnInfo(name = "time_limit_seconds") val timeLimitSeconds: Int,
    /** Time actually used, set on submit. */
    @ColumnInfo(name = "duration_seconds") val durationSeconds: Long?,
    @ColumnInfo(name = "correct_count") val correctCount: Int?,
    @ColumnInfo(name = "question_count") val questionCount: Int,
    /** IN_PROGRESS, SUBMITTED, EXPIRED or ABANDONED (see AttemptStatus). */
    val status: String,
    /** The question the learner was on, so a resumed attempt opens where it was left (schema 10). */
    @ColumnInfo(name = "current_question_id") val currentQuestionId: String? = null,
)

/** A question the learner marked to look at again before submitting (schema 10). */
@Entity(
    tableName = "exam_flags",
    primaryKeys = ["attempt_id", "question_id"],
    foreignKeys = [ForeignKey(entity = ExamAttemptEntity::class, parentColumns = ["id"], childColumns = ["attempt_id"], onDelete = ForeignKey.CASCADE)],
)
data class ExamFlagEntity(
    @ColumnInfo(name = "attempt_id") val attemptId: String,
    @ColumnInfo(name = "question_id") val questionId: String,
    @ColumnInfo(name = "flagged_at") val flaggedAt: Long,
)

/** The learner's answer to one question in one attempt (changing it updates the row). */
@Entity(
    tableName = "exam_answers",
    primaryKeys = ["attempt_id", "question_id"],
    foreignKeys = [ForeignKey(entity = ExamAttemptEntity::class, parentColumns = ["id"], childColumns = ["attempt_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("question_id")],
)
data class ExamAnswerEntity(
    @ColumnInfo(name = "attempt_id") val attemptId: String,
    @ColumnInfo(name = "question_id") val questionId: String,
    @ColumnInfo(name = "selected_index") val selectedIndex: Int?,
    @ColumnInfo(name = "text_answer") val textAnswer: String?,
    @ColumnInfo(name = "is_correct") val isCorrect: Boolean,
    @ColumnInfo(name = "time_spent_ms") val timeSpentMs: Long,
    /** LOW, MEDIUM or HIGH when the learner says how sure they are; null otherwise. */
    val confidence: String?,
    /** How many times the answer was changed in this attempt. */
    @ColumnInfo(name = "change_count") val changeCount: Int,
    @ColumnInfo(name = "answered_at") val answeredAt: Long,
    /** The question as it was answered (JSON), so the review works after the set changed. */
    @ColumnInfo(name = "question_snapshot") val questionSnapshot: String,
)

/** A question the learner got wrong at least once ("sổ tay lỗi sai"): one row per question. */
@Entity(tableName = "error_records", indices = [Index(value = ["question_id"], unique = true), Index("status")])
data class ErrorRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "question_id") val questionId: String,
    @ColumnInfo(name = "exam_set_id") val examSetId: String,
    /** Main cause of the last mistake (see ErrorType); refined by the error analysis. */
    @ColumnInfo(name = "error_type") val errorType: String,
    @ColumnInfo(name = "error_count") val errorCount: Int,
    @ColumnInfo(name = "first_wrong_at") val firstWrongAt: Long,
    @ColumnInfo(name = "last_wrong_at") val lastWrongAt: Long,
    /** 0 … ladder size: the review step reached; equals the review item's step. */
    @ColumnInfo(name = "mastery_level") val masteryLevel: Int,
    /** ACTIVE, MASTERED or ARCHIVED (see ErrorStatus). */
    val status: String,
    val notes: String?,
    @ColumnInfo(name = "question_snapshot") val questionSnapshot: String,
)

/** Spaced-repetition state of an error record, on the same ladder as the review tab. */
@Entity(
    tableName = "review_items",
    foreignKeys = [ForeignKey(entity = ErrorRecordEntity::class, parentColumns = ["id"], childColumns = ["error_record_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["error_record_id"], unique = true), Index("next_review_at")],
)
data class ReviewItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "error_record_id") val errorRecordId: Long,
    @ColumnInfo(name = "next_review_at") val nextReviewAt: Long,
    /** Minutes, not days: the ladder starts at 20 minutes. */
    @ColumnInfo(name = "interval_minutes") val intervalMinutes: Long,
    @ColumnInfo(name = "step_index") val stepIndex: Int,
    @ColumnInfo(name = "review_count") val reviewCount: Int,
    @ColumnInfo(name = "success_count") val successCount: Int,
    @ColumnInfo(name = "last_reviewed_at") val lastReviewedAt: Long?,
    /** NEW, LEARNING, REVIEW or MASTERED (see ReviewStatus). */
    @ColumnInfo(name = "review_status") val reviewStatus: String,
)

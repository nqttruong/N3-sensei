package com.n3sensei.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Shared learning content. Rows are owned by a content pack and replaced wholesale when the
// pack is updated, so they never hold learner state. IDs are stable strings supplied by the
// pack so that progress keyed by them survives updates.

@Entity(tableName = "content_sources")
data class ContentSourceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val url: String?,
    val license: String,
    val attribution: String?,
    @ColumnInfo(name = "license_verified") val licenseVerified: Boolean,
)

@Entity(
    tableName = "content_packs",
    foreignKeys = [
        ForeignKey(
            entity = ContentSourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["source_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("source_id")],
)
data class ContentPackEntity(
    @PrimaryKey val id: String,
    val title: String,
    /** Monotonic version of the pack's content, used to decide whether to re-import. */
    val version: Int,
    @ColumnInfo(name = "source_id") val sourceId: String,
    /** True for seed/test data that must never be presented as real course content. */
    @ColumnInfo(name = "is_seed") val isSeed: Boolean,
    @ColumnInfo(name = "imported_at") val importedAt: Long,
)

@Entity(
    tableName = "lessons",
    foreignKeys = [
        ForeignKey(
            entity = ContentPackEntity::class,
            parentColumns = ["id"],
            childColumns = ["pack_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["pack_id", "number"], unique = true)],
)
data class LessonEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "pack_id") val packId: String,
    val number: Int,
    val title: String,
    @ColumnInfo(name = "jlpt_level") val jlptLevel: String,
    val description: String?,
)

private const val LESSON_FK_COLUMN = "lesson_id"
private const val SOURCE_FK_COLUMN = "source_id"

@Entity(
    tableName = "vocabulary",
    foreignKeys = [
        ForeignKey(
            entity = LessonEntity::class,
            parentColumns = ["id"],
            childColumns = [LESSON_FK_COLUMN],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ContentSourceEntity::class,
            parentColumns = ["id"],
            childColumns = [SOURCE_FK_COLUMN],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = [LESSON_FK_COLUMN, "sort_order"], unique = true),
        Index(SOURCE_FK_COLUMN),
        Index("expression"),
        Index("reading"),
    ],
)
data class VocabularyEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = LESSON_FK_COLUMN) val lessonId: String,
    @ColumnInfo(name = SOURCE_FK_COLUMN) val sourceId: String,
    val expression: String,
    val reading: String,
    val furigana: String?,
    @ColumnInfo(name = "meaning_vi") val meaningVi: String,
    @ColumnInfo(name = "part_of_speech") val partOfSpeech: String?,
    @ColumnInfo(name = "jlpt_level") val jlptLevel: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    val note: String?,
    /** Derived by [com.n3sensei.app.data.local.SearchText] on insert; never set by packs. */
    @ColumnInfo(name = "search_text", defaultValue = "") val searchText: String = "",
)

@Entity(
    tableName = "kanji",
    foreignKeys = [
        ForeignKey(
            entity = LessonEntity::class,
            parentColumns = ["id"],
            childColumns = [LESSON_FK_COLUMN],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ContentSourceEntity::class,
            parentColumns = ["id"],
            childColumns = [SOURCE_FK_COLUMN],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = [LESSON_FK_COLUMN, "sort_order"], unique = true),
        Index(value = [LESSON_FK_COLUMN, "character"], unique = true),
        Index(SOURCE_FK_COLUMN),
        Index("character"),
    ],
)
data class KanjiEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = LESSON_FK_COLUMN) val lessonId: String,
    @ColumnInfo(name = SOURCE_FK_COLUMN) val sourceId: String,
    val character: String,
    @ColumnInfo(name = "on_readings") val onReadings: List<String>,
    @ColumnInfo(name = "kun_readings") val kunReadings: List<String>,
    @ColumnInfo(name = "han_viet") val hanViet: String?,
    @ColumnInfo(name = "meaning_vi") val meaningVi: String,
    @ColumnInfo(name = "stroke_count") val strokeCount: Int?,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    val note: String?,
    @ColumnInfo(name = "search_text", defaultValue = "") val searchText: String = "",
)

@Entity(
    tableName = "grammar",
    foreignKeys = [
        ForeignKey(
            entity = LessonEntity::class,
            parentColumns = ["id"],
            childColumns = [LESSON_FK_COLUMN],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ContentSourceEntity::class,
            parentColumns = ["id"],
            childColumns = [SOURCE_FK_COLUMN],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = [LESSON_FK_COLUMN, "sort_order"], unique = true),
        Index(SOURCE_FK_COLUMN),
        Index("pattern"),
    ],
)
data class GrammarEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = LESSON_FK_COLUMN) val lessonId: String,
    @ColumnInfo(name = SOURCE_FK_COLUMN) val sourceId: String,
    val pattern: String,
    val structure: String,
    @ColumnInfo(name = "meaning_vi") val meaningVi: String,
    val usage: String,
    @ColumnInfo(name = "caution_note") val cautionNote: String?,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "search_text", defaultValue = "") val searchText: String = "",
)

/** Short multiple-choice exercise belonging to a grammar point (content, not learner data). */
@Entity(
    tableName = "grammar_exercises",
    foreignKeys = [
        ForeignKey(
            entity = GrammarEntity::class,
            parentColumns = ["id"],
            childColumns = ["grammar_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["grammar_id", "sort_order"], unique = true)],
)
data class GrammarExerciseEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "grammar_id") val grammarId: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    val prompt: String,
    val options: List<String>,
    @ColumnInfo(name = "answer_index") val answerIndex: Int,
    @ColumnInfo(name = "explanation_vi") val explanationVi: String?,
)

/** Example sentence or phrase; may be linked to several items through the *_examples tables. */
@Entity(
    tableName = "example_sentences",
    foreignKeys = [
        ForeignKey(
            entity = ContentPackEntity::class,
            parentColumns = ["id"],
            childColumns = ["pack_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ContentSourceEntity::class,
            parentColumns = ["id"],
            childColumns = [SOURCE_FK_COLUMN],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("pack_id"), Index(SOURCE_FK_COLUMN)],
)
data class ExampleSentenceEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "pack_id") val packId: String,
    @ColumnInfo(name = SOURCE_FK_COLUMN) val sourceId: String,
    val japanese: String,
    val furigana: String?,
    @ColumnInfo(name = "translation_vi") val translationVi: String,
)

@Entity(
    tableName = "vocabulary_examples",
    primaryKeys = ["vocabulary_id", "example_id"],
    foreignKeys = [
        ForeignKey(
            entity = VocabularyEntity::class,
            parentColumns = ["id"],
            childColumns = ["vocabulary_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExampleSentenceEntity::class,
            parentColumns = ["id"],
            childColumns = ["example_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("example_id")],
)
data class VocabularyExampleCrossRef(
    @ColumnInfo(name = "vocabulary_id") val vocabularyId: String,
    @ColumnInfo(name = "example_id") val exampleId: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
)

@Entity(
    tableName = "kanji_examples",
    primaryKeys = ["kanji_id", "example_id"],
    foreignKeys = [
        ForeignKey(
            entity = KanjiEntity::class,
            parentColumns = ["id"],
            childColumns = ["kanji_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExampleSentenceEntity::class,
            parentColumns = ["id"],
            childColumns = ["example_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("example_id")],
)
data class KanjiExampleCrossRef(
    @ColumnInfo(name = "kanji_id") val kanjiId: String,
    @ColumnInfo(name = "example_id") val exampleId: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
)

@Entity(
    tableName = "grammar_examples",
    primaryKeys = ["grammar_id", "example_id"],
    foreignKeys = [
        ForeignKey(
            entity = GrammarEntity::class,
            parentColumns = ["id"],
            childColumns = ["grammar_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExampleSentenceEntity::class,
            parentColumns = ["id"],
            childColumns = ["example_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("example_id")],
)
data class GrammarExampleCrossRef(
    @ColumnInfo(name = "grammar_id") val grammarId: String,
    @ColumnInfo(name = "example_id") val exampleId: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
)

/** Topic or category label shared across packs, e.g. "topic:work". */
@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
)

@Entity(
    tableName = "vocabulary_tags",
    primaryKeys = ["vocabulary_id", "tag_id"],
    foreignKeys = [
        ForeignKey(
            entity = VocabularyEntity::class,
            parentColumns = ["id"],
            childColumns = ["vocabulary_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tag_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tag_id")],
)
data class VocabularyTagCrossRef(
    @ColumnInfo(name = "vocabulary_id") val vocabularyId: String,
    @ColumnInfo(name = "tag_id") val tagId: String,
)

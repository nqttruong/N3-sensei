package com.n3sensei.app.data.local.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import com.n3sensei.app.data.local.entity.ContentPackEntity
import com.n3sensei.app.data.local.entity.ContentSourceEntity
import com.n3sensei.app.data.local.entity.ExampleSentenceEntity
import com.n3sensei.app.data.local.entity.GrammarEntity
import com.n3sensei.app.data.local.entity.GrammarExerciseEntity
import com.n3sensei.app.data.local.entity.KanjiEntity
import com.n3sensei.app.data.local.entity.LessonEntity
import com.n3sensei.app.data.local.entity.TagEntity
import com.n3sensei.app.data.local.entity.VocabularyEntity
import com.n3sensei.app.domain.model.ProgressState
import com.n3sensei.app.domain.model.ReviewRating

data class LessonSummaryRow(
    @Embedded val lesson: LessonEntity,
    @ColumnInfo(name = "vocabulary_count") val vocabularyCount: Int,
    @ColumnInfo(name = "kanji_count") val kanjiCount: Int,
    @ColumnInfo(name = "grammar_count") val grammarCount: Int,
)

data class VocabularyDetailRow(
    val vocabulary: VocabularyEntity,
    val examples: List<ExampleSentenceEntity>,
    val tags: List<TagEntity>,
    val source: ContentSourceEntity,
)

data class KanjiDetailRow(
    val kanji: KanjiEntity,
    val examples: List<ExampleSentenceEntity>,
    val source: ContentSourceEntity,
)

data class GrammarDetailRow(
    val grammar: GrammarEntity,
    val examples: List<ExampleSentenceEntity>,
    val source: ContentSourceEntity,
    val exercises: List<GrammarExerciseEntity>,
)

// Browse rows: an item plus the learner's state (progress_state is null for new items).

data class VocabularyItemRow(
    @Embedded val vocabulary: VocabularyEntity,
    @ColumnInfo(name = "is_marked") val isMarked: Boolean,
    @ColumnInfo(name = "progress_state") val progressState: ProgressState?,
)

data class KanjiItemRow(
    @Embedded val kanji: KanjiEntity,
    @ColumnInfo(name = "is_marked") val isMarked: Boolean,
    @ColumnInfo(name = "progress_state") val progressState: ProgressState?,
)

data class GrammarItemRow(
    @Embedded val grammar: GrammarEntity,
    @ColumnInfo(name = "is_marked") val isMarked: Boolean,
    @ColumnInfo(name = "progress_state") val progressState: ProgressState?,
)

data class ItemStateRow(
    @ColumnInfo(name = "progress_state") val progressState: ProgressState?,
    @ColumnInfo(name = "is_marked") val isMarked: Boolean,
)

data class ContentPackRow(
    @Embedded val pack: ContentPackEntity,
    @ColumnInfo(name = "lesson_count") val lessonCount: Int,
)

data class ContentTotalsRow(
    val lessons: Int,
    val vocabulary: Int,
    val kanji: Int,
    val grammar: Int,
)

data class ProgressSummaryRow(
    @ColumnInfo(name = "tracked_items") val trackedItems: Int,
    val learning: Int,
    @ColumnInfo(name = "in_review") val inReview: Int,
    @ColumnInfo(name = "due_now") val dueNow: Int,
    @ColumnInfo(name = "total_reviews") val totalReviews: Int,
)

data class RatingCountRow(
    val rating: ReviewRating,
    val count: Int,
)

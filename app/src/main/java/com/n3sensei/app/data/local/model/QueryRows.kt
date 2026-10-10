package com.n3sensei.app.data.local.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import com.n3sensei.app.data.local.entity.ContentSourceEntity
import com.n3sensei.app.data.local.entity.ExampleSentenceEntity
import com.n3sensei.app.data.local.entity.GrammarEntity
import com.n3sensei.app.data.local.entity.KanjiEntity
import com.n3sensei.app.data.local.entity.LessonEntity
import com.n3sensei.app.data.local.entity.TagEntity
import com.n3sensei.app.data.local.entity.VocabularyEntity

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
)

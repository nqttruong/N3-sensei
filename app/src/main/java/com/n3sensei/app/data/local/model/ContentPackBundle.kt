package com.n3sensei.app.data.local.model

import com.n3sensei.app.data.local.entity.ContentPackEntity
import com.n3sensei.app.data.local.entity.ContentSourceEntity
import com.n3sensei.app.data.local.entity.ExampleSentenceEntity
import com.n3sensei.app.data.local.entity.GrammarEntity
import com.n3sensei.app.data.local.entity.GrammarExampleCrossRef
import com.n3sensei.app.data.local.entity.KanjiEntity
import com.n3sensei.app.data.local.entity.KanjiExampleCrossRef
import com.n3sensei.app.data.local.entity.LessonEntity
import com.n3sensei.app.data.local.entity.TagEntity
import com.n3sensei.app.data.local.entity.VocabularyEntity
import com.n3sensei.app.data.local.entity.VocabularyExampleCrossRef
import com.n3sensei.app.data.local.entity.VocabularyTagCrossRef

/** Everything belonging to one content pack, written in a single transaction. */
data class ContentPackBundle(
    val sources: List<ContentSourceEntity>,
    val pack: ContentPackEntity,
    val lessons: List<LessonEntity>,
    val vocabulary: List<VocabularyEntity> = emptyList(),
    val kanji: List<KanjiEntity> = emptyList(),
    val grammar: List<GrammarEntity> = emptyList(),
    val examples: List<ExampleSentenceEntity> = emptyList(),
    val vocabularyExamples: List<VocabularyExampleCrossRef> = emptyList(),
    val kanjiExamples: List<KanjiExampleCrossRef> = emptyList(),
    val grammarExamples: List<GrammarExampleCrossRef> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val vocabularyTags: List<VocabularyTagCrossRef> = emptyList(),
)

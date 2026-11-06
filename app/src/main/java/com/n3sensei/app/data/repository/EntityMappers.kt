package com.n3sensei.app.data.repository

import com.n3sensei.app.data.local.entity.ContentSourceEntity
import com.n3sensei.app.data.local.entity.ExampleSentenceEntity
import com.n3sensei.app.data.local.entity.GrammarEntity
import com.n3sensei.app.data.local.entity.GrammarExerciseEntity
import com.n3sensei.app.data.local.entity.KanjiEntity
import com.n3sensei.app.data.local.entity.LearningProgressEntity
import com.n3sensei.app.data.local.entity.ReviewRecordEntity
import com.n3sensei.app.data.local.entity.TagEntity
import com.n3sensei.app.data.local.entity.VocabularyEntity
import com.n3sensei.app.data.local.model.ContentPackRow
import com.n3sensei.app.data.local.model.GrammarDetailRow
import com.n3sensei.app.data.local.model.GrammarItemRow
import com.n3sensei.app.data.local.model.KanjiDetailRow
import com.n3sensei.app.data.local.model.KanjiItemRow
import com.n3sensei.app.data.local.model.LessonSummaryRow
import com.n3sensei.app.data.local.model.VocabularyDetailRow
import com.n3sensei.app.data.local.model.VocabularyItemRow
import com.n3sensei.app.domain.model.ContentPackInfo
import com.n3sensei.app.domain.model.ContentSource
import com.n3sensei.app.domain.model.ExampleSentence
import com.n3sensei.app.domain.model.GrammarDetail
import com.n3sensei.app.domain.model.GrammarExercise
import com.n3sensei.app.domain.model.GrammarPoint
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.Kanji
import com.n3sensei.app.domain.model.KanjiDetail
import com.n3sensei.app.domain.model.LearningProgress
import com.n3sensei.app.domain.model.LearningStatus
import com.n3sensei.app.domain.model.LessonSummary
import com.n3sensei.app.domain.model.ReviewRecord
import com.n3sensei.app.domain.model.Tag
import com.n3sensei.app.domain.model.TrackedItem
import com.n3sensei.app.domain.model.Vocabulary
import com.n3sensei.app.domain.model.VocabularyDetail
import com.n3sensei.app.domain.model.WordTranslation
import java.time.Instant

internal fun LessonSummaryRow.toDomain() = LessonSummary(
    id = lesson.id,
    packId = lesson.packId,
    number = lesson.number,
    title = lesson.title,
    jlptLevel = lesson.jlptLevel,
    description = lesson.description,
    vocabularyCount = vocabularyCount,
    kanjiCount = kanjiCount,
    grammarCount = grammarCount,
)

internal fun ContentSourceEntity.toDomain() = ContentSource(id, name, url, license, attribution, licenseVerified)

internal fun VocabularyEntity.toDomain() = Vocabulary(
    id = id,
    lessonId = lessonId,
    expression = expression,
    reading = reading,
    furigana = furigana,
    meaningVi = meaningVi,
    partOfSpeech = partOfSpeech,
    jlptLevel = jlptLevel,
    note = note,
)

internal fun KanjiEntity.toDomain() = Kanji(
    id = id,
    lessonId = lessonId,
    character = character,
    onReadings = onReadings,
    kunReadings = kunReadings,
    hanViet = hanViet,
    meaningVi = meaningVi,
    strokeCount = strokeCount,
    note = note,
)

internal fun GrammarEntity.toDomain() = GrammarPoint(
    id = id,
    lessonId = lessonId,
    pattern = pattern,
    structure = structure,
    meaningVi = meaningVi,
    usage = usage,
    cautionNote = cautionNote,
)

internal fun ExampleSentenceEntity.toDomain() = ExampleSentence(id, japanese, furigana, translationVi)

internal fun TagEntity.toDomain() = Tag(id, name)

internal fun VocabularyDetailRow.toDomain() = VocabularyDetail(
    vocabulary = vocabulary.toDomain(),
    examples = examples.map { it.toDomain() },
    tags = tags.map { it.toDomain() },
    source = source.toDomain(),
    linkedKanji = linkedKanji.map { it.toDomain() },
    saved = saved,
    translations = translations.map { WordTranslation(it.language, it.text, it.reading) },
)

internal fun KanjiDetailRow.toDomain() =
    KanjiDetail(kanji.toDomain(), examples.map { it.toDomain() }, source.toDomain(), linkedVocabulary.map { it.toDomain() })

internal fun GrammarDetailRow.toDomain() =
    GrammarDetail(grammar.toDomain(), examples.map { it.toDomain() }, source.toDomain(), exercises.map { it.toDomain() })

internal fun GrammarExerciseEntity.toDomain() = GrammarExercise(id, prompt, options, answerIndex, explanationVi)

internal fun VocabularyItemRow.toDomain() = TrackedItem(vocabulary.toDomain(), LearningStatus.from(progressState), isMarked, isSaved)

internal fun KanjiItemRow.toDomain() = TrackedItem(kanji.toDomain(), LearningStatus.from(progressState), isMarked)

internal fun GrammarItemRow.toDomain() = TrackedItem(grammar.toDomain(), LearningStatus.from(progressState), isMarked)

internal fun ContentPackRow.toDomain() = ContentPackInfo(pack.id, pack.title, pack.version, pack.isSeed, lessonCount)

private fun Long.toInstant(): Instant = Instant.ofEpochMilli(this)

internal fun LearningProgressEntity.toDomain() = LearningProgress(
    item = ItemRef(itemType, itemId),
    state = state,
    stepIndex = stepIndex,
    intervalMinutes = intervalMinutes,
    dueAt = dueAt?.toInstant(),
    lastReviewedAt = lastReviewedAt?.toInstant(),
    reviewCount = reviewCount,
    lapseCount = lapseCount,
    createdAt = createdAt.toInstant(),
    updatedAt = updatedAt.toInstant(),
)

internal fun LearningProgress.toEntity() = LearningProgressEntity(
    itemType = item.type,
    itemId = item.id,
    state = state,
    stepIndex = stepIndex,
    intervalMinutes = intervalMinutes,
    dueAt = dueAt?.toEpochMilli(),
    lastReviewedAt = lastReviewedAt?.toEpochMilli(),
    reviewCount = reviewCount,
    lapseCount = lapseCount,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
)

internal fun ReviewRecordEntity.toDomain() = ReviewRecord(
    id = id,
    item = ItemRef(itemType, itemId),
    rating = rating,
    reviewedAt = reviewedAt.toInstant(),
    intervalBeforeMinutes = intervalBeforeMinutes,
    intervalAfterMinutes = intervalAfterMinutes,
    dueAtAfter = dueAtAfter.toInstant(),
    wasNew = wasNew,
    requestId = requestId,
)

internal fun ReviewRecord.toEntity() = ReviewRecordEntity(
    id = id,
    itemType = item.type,
    itemId = item.id,
    rating = rating,
    reviewedAt = reviewedAt.toEpochMilli(),
    intervalBeforeMinutes = intervalBeforeMinutes,
    intervalAfterMinutes = intervalAfterMinutes,
    dueAtAfter = dueAtAfter.toEpochMilli(),
    requestId = requestId,
    wasNew = wasNew,
)

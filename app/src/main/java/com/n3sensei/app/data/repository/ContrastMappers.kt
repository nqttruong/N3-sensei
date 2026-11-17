package com.n3sensei.app.data.repository

import com.n3sensei.app.data.local.SearchText
import com.n3sensei.app.data.local.dao.ContrastGroupWithItems
import com.n3sensei.app.data.local.dao.ContrastPackRows
import com.n3sensei.app.data.local.entity.ContrastAttemptEntity
import com.n3sensei.app.data.local.entity.ContrastExampleEntity
import com.n3sensei.app.data.local.entity.ContrastGroupEntity
import com.n3sensei.app.data.local.entity.ContrastItemEntity
import com.n3sensei.app.data.local.entity.ContrastNoteEntity
import com.n3sensei.app.data.local.entity.ContrastOptionEntity
import com.n3sensei.app.data.local.entity.ContrastPackEntity
import com.n3sensei.app.data.local.entity.ContrastProgressEntity
import com.n3sensei.app.data.local.entity.ContrastQuestionEntity
import com.n3sensei.app.data.local.entity.ContrastRetiredGroupEntity
import com.n3sensei.app.data.local.entity.ContrastReviewEntity
import com.n3sensei.app.data.local.entity.ContrastSessionEntity
import com.n3sensei.app.data.local.entity.ContrastSessionItemEntity
import com.n3sensei.app.domain.contrast.Collocation
import com.n3sensei.app.domain.contrast.CommonMistake
import com.n3sensei.app.domain.contrast.ComparisonRow
import com.n3sensei.app.domain.contrast.Confusion
import com.n3sensei.app.domain.contrast.ContrastAttempt
import com.n3sensei.app.domain.contrast.ContrastContentType
import com.n3sensei.app.domain.contrast.ContrastDetail
import com.n3sensei.app.domain.contrast.ContrastExample
import com.n3sensei.app.domain.contrast.ContrastGroup
import com.n3sensei.app.domain.contrast.ContrastItem
import com.n3sensei.app.domain.contrast.ContrastOption
import com.n3sensei.app.domain.contrast.ContrastProgress
import com.n3sensei.app.domain.contrast.ContrastQuestion
import com.n3sensei.app.domain.contrast.ContrastReview
import com.n3sensei.app.domain.contrast.ContrastSession
import com.n3sensei.app.domain.contrast.Correction
import com.n3sensei.app.domain.contrast.ItemDetails
import com.n3sensei.app.domain.contrast.MemoryTip
import com.n3sensei.app.domain.contrast.PracticeScope
import com.n3sensei.app.domain.contrast.QuestionContext
import com.n3sensei.app.domain.contrast.QuestionPrompt
import com.n3sensei.app.domain.contrast.Radical
import com.n3sensei.app.domain.contrast.RetiredGroup
import com.n3sensei.app.domain.contrast.Sentence
import com.n3sensei.app.domain.contrast.SessionItem
import com.n3sensei.app.domain.contrast.SnapshotValue
import com.n3sensei.app.domain.contrast.SourceReference
import com.n3sensei.app.domain.contrast.TheoryBlock
import com.n3sensei.app.domain.contrast.validation.ValidatedContrastPack
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant

// Room entities ↔ Contrast domain models. Entities never leave the data layer. Display-only
// structures are stored as JSON with the private DTOs below (their field names are a storage
// format: never rename them without a migration).

/** Kinds of contrast_notes rows. Stored; never rename. */
internal object ContrastNoteKind {
    const val THEORY = "THEORY"
    const val COMPARISON = "COMPARISON"
    const val TIP = "TIP"
    const val MISTAKE = "MISTAKE"
}

private val json = Json { ignoreUnknownKeys = false }

@Serializable private data class CollocationJson(val japanese: String, val furigana: String? = null, val meaningVi: String)
@Serializable private data class RadicalJson(val character: String, val nameVi: String)

@Serializable
private data class DetailsJson(
    val type: String,
    val meaningVi: String,
    val connection: String? = null,
    val nuanceVi: String? = null,
    val reading: String? = null,
    val usageVi: String? = null,
    val collocations: List<CollocationJson>? = null,
    val hanViet: String? = null,
    val onReadings: List<String>? = null,
    val kunReadings: List<String>? = null,
    val radical: RadicalJson? = null,
    val shapeNoteVi: String? = null,
)

@Serializable private data class TheoryJson(val id: String, val titleVi: String, val bodyVi: String)
@Serializable private data class ComparisonJson(val aspectVi: String, val values: Map<String, String>)
@Serializable private data class TipJson(val id: String, val textVi: String)
@Serializable private data class SentenceJson(val japanese: String, val furigana: String? = null)

@Serializable
private data class MistakeJson(
    val id: String,
    val situationVi: String,
    val wrong: SentenceJson,
    val correct: SentenceJson,
    val explanationVi: String,
    val expectedItemId: String? = null,
    val chosenItemId: String? = null,
)

@Serializable private data class PromptJson(val japanese: String, val furigana: String? = null, val translationVi: String? = null)
@Serializable private data class ContextJson(val descriptionVi: String, val japanese: String? = null, val furigana: String? = null)
@Serializable private data class CorrectionJson(val text: String, val furigana: String? = null, val itemId: String? = null)

// ---- Domain → entities (content, written when a validated pack is installed) ----

internal fun encodeSnapshot(snapshot: Map<String, SnapshotValue>): String =
    JsonObject(snapshot.mapValues { (_, v) ->
        when (v) {
            is SnapshotValue.Text -> JsonPrimitive(v.value)
            is SnapshotValue.Texts -> JsonArray(v.values.map(::JsonPrimitive))
        }
    }).toString()

internal fun decodeSnapshot(text: String): Map<String, SnapshotValue> =
    json.parseToJsonElement(text).let { it as JsonObject }.mapValues { (_, v) ->
        if (v is JsonArray) SnapshotValue.Texts(v.map { it.jsonPrimitive.content }) else SnapshotValue.Text(v.jsonPrimitive.content)
    }

private fun ItemDetails.toJson(): String = json.encodeToString(
    DetailsJson.serializer(),
    when (this) {
        is ItemDetails.Grammar -> DetailsJson(type.name, meaningVi, connection = connection, nuanceVi = nuanceVi)
        is ItemDetails.Vocabulary -> DetailsJson(
            type.name, meaningVi, reading = reading, usageVi = usageVi,
            collocations = collocations.map { CollocationJson(it.japanese, it.furigana, it.meaningVi) },
        )
        is ItemDetails.Kanji -> DetailsJson(
            type.name, meaningVi, hanViet = hanViet, onReadings = onReadings, kunReadings = kunReadings,
            radical = radical?.let { RadicalJson(it.character, it.nameVi) }, shapeNoteVi = shapeNoteVi,
        )
    },
)

private fun decodeDetails(text: String): ItemDetails {
    val d = json.decodeFromString(DetailsJson.serializer(), text)
    return when (ContrastContentType.valueOf(d.type)) {
        ContrastContentType.GRAMMAR -> ItemDetails.Grammar(d.meaningVi, requireNotNull(d.connection), requireNotNull(d.nuanceVi))
        ContrastContentType.VOCABULARY -> ItemDetails.Vocabulary(
            requireNotNull(d.reading), d.meaningVi, requireNotNull(d.usageVi),
            d.collocations.orEmpty().map { Collocation(it.japanese, it.furigana, it.meaningVi) },
        )
        ContrastContentType.KANJI -> ItemDetails.Kanji(
            d.meaningVi, d.hanViet, d.onReadings.orEmpty(), d.kunReadings.orEmpty(),
            d.radical?.let { Radical(it.character, it.nameVi) }, requireNotNull(d.shapeNoteVi),
        )
    }
}

private fun searchTextOf(detail: ContrastDetail): String = SearchText.of(
    detail.group.title,
    detail.group.summaryVi,
    *detail.items.flatMap { item ->
        listOf(item.label) + when (val d = item.details) {
            is ItemDetails.Grammar -> listOf(d.meaningVi)
            is ItemDetails.Vocabulary -> listOf(d.reading, d.meaningVi)
            is ItemDetails.Kanji -> listOf(d.meaningVi, d.hanViet) + d.onReadings + d.kunReadings
        }
    }.toTypedArray(),
)

/** The rows of a validated pack (PUBLISHED content only, checked before this point). */
internal fun ValidatedContrastPack.toRows(installedAt: Instant, schemaVersion: Int): ContrastPackRows {
    val pack = ContrastPackEntity(id, title, jlptLevel, contentVersion, schemaVersion, publishedAt, licenseText, licenseVerified, installedAt.toEpochMilli())
    val groups = mutableListOf<ContrastGroupEntity>()
    val items = mutableListOf<ContrastItemEntity>()
    val notes = mutableListOf<ContrastNoteEntity>()
    val examples = mutableListOf<ContrastExampleEntity>()
    val questions = mutableListOf<ContrastQuestionEntity>()
    val options = mutableListOf<ContrastOptionEntity>()
    this.groups.forEachIndexed { g, validated ->
        val detail = validated.detail
        val group = detail.group
        groups += ContrastGroupEntity(group.id, id, group.contentType, group.jlptLevel, group.title, group.summaryVi, group.revision, g, searchTextOf(detail))
        detail.items.forEachIndexed { i, item ->
            val s = item.source
            items += ContrastItemEntity(
                item.id, group.id, i, item.label, item.labelFurigana, s.sourceType, s.sourceContentId, s.sourcePackId,
                s.sourceSchemaVersion, encodeSnapshot(s.sourceSnapshot), s.sourceFingerprint, item.details.toJson(),
            )
        }
        detail.theory.forEachIndexed { k, t ->
            notes += ContrastNoteEntity(group.id, ContrastNoteKind.THEORY, k, json.encodeToString(TheoryJson.serializer(), TheoryJson(t.id, t.titleVi, t.bodyVi)))
        }
        detail.comparison.forEachIndexed { k, r ->
            notes += ContrastNoteEntity(group.id, ContrastNoteKind.COMPARISON, k, json.encodeToString(ComparisonJson.serializer(), ComparisonJson(r.aspectVi, r.values)))
        }
        detail.memoryTips.forEachIndexed { k, t ->
            notes += ContrastNoteEntity(group.id, ContrastNoteKind.TIP, k, json.encodeToString(TipJson.serializer(), TipJson(t.id, t.textVi)))
        }
        detail.commonMistakes.forEachIndexed { k, m ->
            val payload = MistakeJson(
                m.id, m.situationVi, SentenceJson(m.wrong.japanese, m.wrong.furigana), SentenceJson(m.correct.japanese, m.correct.furigana),
                m.explanationVi, m.confusion?.expectedItemId, m.confusion?.chosenItemId,
            )
            notes += ContrastNoteEntity(group.id, ContrastNoteKind.MISTAKE, k, json.encodeToString(MistakeJson.serializer(), payload))
        }
        detail.examples.forEachIndexed { k, e -> examples += ContrastExampleEntity(e.id, group.id, k, e.itemId, e.japanese, e.furigana, e.translationVi) }
        validated.questions.forEachIndexed { k, q ->
            questions += ContrastQuestionEntity(
                q.id, group.id, k, q.revision, q.type, q.difficulty, q.targetItemId, q.instructionVi,
                q.prompt?.let { json.encodeToString(PromptJson.serializer(), PromptJson(it.japanese, it.furigana, it.translationVi)) },
                q.context?.let { json.encodeToString(ContextJson.serializer(), ContextJson(it.descriptionVi, it.japanese, it.furigana)) },
                q.correction?.let { json.encodeToString(CorrectionJson.serializer(), CorrectionJson(it.text, it.furigana, it.itemId)) },
                q.correctOptionId, q.explanationVi,
            )
            q.options.forEachIndexed { o, opt -> options += ContrastOptionEntity(q.id, opt.id, o, opt.text, opt.furigana, opt.itemId, opt.feedbackVi) }
        }
    }
    val retired = retiredGroups.map { ContrastRetiredGroupEntity(id, it.id, it.retiredInVersion, it.reasonVi, it.replacedBy) }
    return ContrastPackRows(pack, groups, items, notes, examples, questions, options, retired)
}

// ---- Entities → domain (content) ----

internal fun ContrastItemEntity.toDomain() = ContrastItem(
    id = id,
    label = label,
    labelFurigana = labelFurigana,
    source = SourceReference(sourceType, sourceContentId, sourcePackId, sourceSchemaVersion, decodeSnapshot(sourceSnapshotJson), sourceFingerprint),
    details = decodeDetails(detailsJson),
)

internal fun ContrastGroupWithItems.toDomain() = ContrastGroup(
    id = group.id,
    packId = group.packId,
    contentType = group.contentType,
    jlptLevel = group.jlptLevel,
    title = group.title,
    summaryVi = group.summaryVi,
    revision = group.revision,
    itemLabels = items.sortedBy { it.sortOrder }.map { it.label },
    questionCount = questionIds.size,
)

internal fun detailOf(
    row: ContrastGroupWithItems,
    notes: List<ContrastNoteEntity>,
    examples: List<ContrastExampleEntity>,
    pack: ContrastPackEntity,
): ContrastDetail {
    fun payloads(kind: String) = notes.filter { it.kind == kind }.sortedBy { it.sortOrder }.map { it.payloadJson }
    return ContrastDetail(
        group = row.toDomain(),
        items = row.items.sortedBy { it.sortOrder }.map { it.toDomain() },
        theory = payloads(ContrastNoteKind.THEORY).map { json.decodeFromString(TheoryJson.serializer(), it).let { t -> TheoryBlock(t.id, t.titleVi, t.bodyVi) } },
        comparison = payloads(ContrastNoteKind.COMPARISON).map { json.decodeFromString(ComparisonJson.serializer(), it).let { c -> ComparisonRow(c.aspectVi, c.values) } },
        examples = examples.sortedBy { it.sortOrder }.map { ContrastExample(it.id, it.itemId, it.japanese, it.furigana, it.translationVi) },
        memoryTips = payloads(ContrastNoteKind.TIP).map { json.decodeFromString(TipJson.serializer(), it).let { t -> MemoryTip(t.id, t.textVi) } },
        commonMistakes = payloads(ContrastNoteKind.MISTAKE).map {
            val m = json.decodeFromString(MistakeJson.serializer(), it)
            CommonMistake(
                m.id, m.situationVi, Sentence(m.wrong.japanese, m.wrong.furigana), Sentence(m.correct.japanese, m.correct.furigana), m.explanationVi,
                if (m.expectedItemId != null && m.chosenItemId != null) Confusion(m.expectedItemId, m.chosenItemId) else null,
            )
        },
        licenseText = pack.licenseText,
        licenseVerified = pack.licenseVerified,
    )
}

internal fun ContrastQuestionEntity.toDomain(options: List<ContrastOptionEntity>) = ContrastQuestion(
    id = id,
    groupId = groupId,
    revision = revision,
    type = type,
    difficulty = difficulty,
    targetItemId = targetItemId,
    instructionVi = instructionVi,
    prompt = promptJson?.let { json.decodeFromString(PromptJson.serializer(), it).let { p -> QuestionPrompt(p.japanese, p.furigana, p.translationVi) } },
    context = contextJson?.let { json.decodeFromString(ContextJson.serializer(), it).let { c -> QuestionContext(c.descriptionVi, c.japanese, c.furigana) } },
    options = options.sortedBy { it.sortOrder }.map { ContrastOption(it.optionId, it.text, it.furigana, it.itemId, it.feedbackVi) },
    correctOptionId = correctOptionId,
    correction = correctionJson?.let { json.decodeFromString(CorrectionJson.serializer(), it).let { c -> Correction(c.text, c.furigana, c.itemId) } },
    explanationVi = explanationVi,
)

internal fun ContrastRetiredGroupEntity.toDomain() = RetiredGroup(groupId, retiredInVersion, reasonVi, replacedBy)

// ---- Learner data ----

private fun Instant.ms() = toEpochMilli()
private fun Long.instant(): Instant = Instant.ofEpochMilli(this)

internal fun ContrastAttempt.toEntity() = ContrastAttemptEntity(
    requestId, sessionId, position, groupId, groupRevision, questionId, questionRevision, questionType, contentPackId, contentVersion,
    outcome, chosenOptionId, correctOptionId, expectedItemId, chosenItemId, chosenText, correctText, answeredAt.ms(),
)

internal fun ContrastAttemptEntity.toDomain() = ContrastAttempt(
    requestId, sessionId, position, groupId, groupRevision, questionId, questionRevision, questionType, contentPackId, contentVersion,
    outcome, chosenOptionId, correctOptionId, expectedItemId, chosenItemId, chosenText, correctText, answeredAt.instant(),
)

internal fun ContrastProgress.toEntity() =
    ContrastProgressEntity(groupId, stepIndex, dueAt.ms(), lastReviewedAt.ms(), reviewCount, lapseCount, createdAt.ms(), updatedAt.ms())

internal fun ContrastProgressEntity.toDomain() =
    ContrastProgress(groupId, stepIndex, dueAt.instant(), lastReviewedAt.instant(), reviewCount, lapseCount, createdAt.instant(), updatedAt.instant())

internal fun ContrastReview.toEntity() = ContrastReviewEntity(
    0, groupId, sessionId, reviewedAt.ms(), answered, correct, incorrect, skipped, rating, stepBefore, stepAfter, dueAtAfter.ms(), contentVersion,
)

internal fun ContrastReviewEntity.toDomain() = ContrastReview(
    id, groupId, sessionId, reviewedAt.instant(), answered, correct, incorrect, skipped, rating, stepBefore, stepAfter, dueAtAfter.instant(), contentVersion,
)

internal object ContrastScopeCodec {
    fun kind(scope: PracticeScope): String = when (scope) {
        PracticeScope.Due -> "DUE"
        is PracticeScope.Group -> "GROUP"
        is PracticeScope.Type -> "TYPE"
    }

    fun value(scope: PracticeScope): String? = when (scope) {
        PracticeScope.Due -> null
        is PracticeScope.Group -> scope.groupId
        is PracticeScope.Type -> scope.contentType.name
    }

    fun decode(kind: String, value: String?): PracticeScope = when (kind) {
        "DUE" -> PracticeScope.Due
        "GROUP" -> PracticeScope.Group(requireNotNull(value))
        "TYPE" -> PracticeScope.Type(ContrastContentType.valueOf(requireNotNull(value)))
        else -> error("Unknown session scope $kind")
    }
}

internal fun ContrastSession.toEntities(): Pair<ContrastSessionEntity, List<ContrastSessionItemEntity>> =
    ContrastSessionEntity(id, ContrastScopeCodec.kind(scope), ContrastScopeCodec.value(scope), status, startedAt.ms(), lastActivityAt.ms(), contentVersion) to
        items.map { it.toEntity(id) }

internal fun SessionItem.toEntity(sessionId: String) = ContrastSessionItemEntity(sessionId, position, groupId, questionId, questionRevision, blockIndex)

internal fun ContrastSessionEntity.toDomain(items: List<ContrastSessionItemEntity>) = ContrastSession(
    id = id,
    scope = ContrastScopeCodec.decode(scopeKind, scopeValue),
    status = status,
    startedAt = startedAt.instant(),
    lastActivityAt = lastActivityAt.instant(),
    contentVersion = contentVersion,
    items = items.sortedBy { it.position }.map { SessionItem(it.position, it.groupId, it.questionId, it.questionRevision, it.blockIndex) },
)

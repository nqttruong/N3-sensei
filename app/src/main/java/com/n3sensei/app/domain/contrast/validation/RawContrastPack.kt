package com.n3sensei.app.domain.contrast.validation

import com.n3sensei.app.domain.contrast.ComparisonRow
import com.n3sensei.app.domain.contrast.ContrastExample
import com.n3sensei.app.domain.contrast.ContrastOption
import com.n3sensei.app.domain.contrast.Correction
import com.n3sensei.app.domain.contrast.ItemDetails
import com.n3sensei.app.domain.contrast.MemoryTip
import com.n3sensei.app.domain.contrast.QuestionContext
import com.n3sensei.app.domain.contrast.QuestionPrompt
import com.n3sensei.app.domain.contrast.Sentence
import com.n3sensei.app.domain.contrast.SnapshotValue
import com.n3sensei.app.domain.contrast.TheoryBlock

// A Contrast pack as read from JSON, before validation (CONTENT_SCHEMA). Fields that have their
// own validation code are nullable (or raw strings for enums) so the validator can report them;
// the rest are plain and are the JSON parser's job (SCHEMA_VIOLATION, parser of M4).

data class RawContrastPack(
    val format: String?,
    val schemaVersion: Int?,
    val pack: RawPackMeta?,
    val sourcePacks: List<RawSourcePack>,
    val groups: List<RawGroup>,
    val retiredGroups: List<RawRetiredGroup>,
)

data class RawPackMeta(
    val id: String?,
    val title: String?,
    val description: String?,
    val jlptLevel: String?,
    val contentVersion: Int?,
    val publishedAt: String?,
    val license: RawLicense?,
)

data class RawLicense(val text: String?, val licenseVerified: Boolean?)

data class RawSourcePack(val id: String, val version: Int)

data class RawWarningAck(val code: String, val target: String)

data class RawApproval(
    /** Author of the approved draft; equal to [approvedBy] for a self-approval (allowed, reported). */
    val authoredBy: String,
    val approvedBy: String,
    val approvedAt: String,
    /** The group revision this approval is for; must equal the group's revision. */
    val approvedRevision: Int,
    val aiAssisted: Boolean,
    val acknowledgedWarnings: List<RawWarningAck>,
)

data class RawGroup(
    val id: String?,
    val revision: Int?,
    val status: String?,
    val contentType: String?,
    val jlptLevel: String?,
    val title: String?,
    val summaryVi: String?,
    val updatedAt: String?,
    val approval: RawApproval?,
    val items: List<RawItem>?,
    val theory: List<TheoryBlock>?,
    val comparison: List<ComparisonRow>?,
    val examples: List<ContrastExample>?,
    val memoryTips: List<MemoryTip>?,
    val commonMistakes: List<RawMistake>?,
    val questions: List<RawQuestion>?,
)

data class RawSource(
    val sourceType: String,
    val sourceContentId: String,
    val sourcePackId: String,
    val sourceSchemaVersion: Int,
    val sourceSnapshot: Map<String, SnapshotValue>,
    val sourceFingerprint: String,
)

data class RawItem(
    val id: String,
    val label: String,
    val labelFurigana: String?,
    val source: RawSource,
    val grammar: ItemDetails.Grammar?,
    val vocabulary: ItemDetails.Vocabulary?,
    val kanji: ItemDetails.Kanji?,
)

data class RawMistake(
    val id: String,
    val situationVi: String,
    val wrong: Sentence,
    val correct: Sentence,
    val explanationVi: String,
    val expectedItemId: String?,
    val confusedWithItemId: String?,
)

data class RawQuestion(
    val id: String,
    val revision: Int,
    val type: String?,
    val difficulty: Int,
    val targetItemId: String,
    val instructionVi: String,
    val prompt: QuestionPrompt?,
    val context: QuestionContext?,
    val options: List<ContrastOption>,
    val correctOptionId: String?,
    val correction: Correction?,
    val explanationVi: String?,
)

data class RawRetiredGroup(val id: String, val retiredInVersion: Int, val reasonVi: String, val replacedBy: String?)

package com.n3sensei.app.domain.contrast

// Contrast Lab content (read-only, from a PUBLISHED Contrast pack). See
// docs/CONTRAST_LAB_DOMAIN_CONTRACT.md §1.1. Independent of ItemType and of the existing SRS.

/** Kind of a Contrast group. Deliberately not [com.n3sensei.app.domain.model.ItemType]. Codes are the pack's. */
enum class ContrastContentType {
    GRAMMAR, VOCABULARY, KANJI;

    companion object {
        fun fromCode(code: String?): ContrastContentType? = entries.firstOrNull { it.name == code }
    }
}

/** The four exercise types. Codes are the pack's and will be stored; never rename an entry. */
enum class ContrastQuestionType {
    MULTIPLE_CHOICE, ERROR_DETECTION, FILL_IN_THE_BLANK, CONTEXT_SELECTION;

    companion object {
        fun fromCode(code: String?): ContrastQuestionType? = entries.firstOrNull { it.name == code }
    }
}

/** Editorial lifecycle (CONTENT_SCHEMA §7). The app only ever accepts [PUBLISHED]. */
enum class ContrastContentStatus {
    DRAFT, AI_GENERATED, REVIEWING, APPROVED, PUBLISHED, ARCHIVED;

    companion object {
        fun fromCode(code: String?): ContrastContentStatus? = entries.firstOrNull { it.name == code }
    }
}

/** A group to tell apart, as listed and searched (the summary part of [ContrastDetail]). */
data class ContrastGroup(
    val id: String,
    val packId: String,
    val contentType: ContrastContentType,
    val jlptLevel: String,
    val title: String,
    val summaryVi: String,
    val revision: Int,
    /** 2–4 labels, in pack order. */
    val itemLabels: List<String>,
    val questionCount: Int,
)

/** One value of a source snapshot: a string field, or a list field (kanji readings). */
sealed interface SnapshotValue {
    data class Text(val value: String) : SnapshotValue
    data class Texts(val values: List<String>) : SnapshotValue
}

/**
 * Where a Contrast item comes from in the existing content, without a foreign key (D3).
 * [sourceContentId] is the existing row id (`String`, e.g. `n3g-g001`), as in [com.n3sensei.app.domain.model.ItemRef].
 */
data class SourceReference(
    val sourceType: ContrastContentType,
    val sourceContentId: String,
    val sourcePackId: String,
    val sourceSchemaVersion: Int,
    val sourceSnapshot: Map<String, SnapshotValue>,
    val sourceFingerprint: String,
)

/** Detail written by Contrast for the comparison; exactly one kind, matching the group's type. */
sealed interface ItemDetails {
    val type: ContrastContentType

    data class Grammar(val meaningVi: String, val connection: String, val nuanceVi: String) : ItemDetails {
        override val type get() = ContrastContentType.GRAMMAR
    }

    data class Vocabulary(
        val reading: String,
        val meaningVi: String,
        val usageVi: String,
        val collocations: List<Collocation>,
    ) : ItemDetails {
        override val type get() = ContrastContentType.VOCABULARY
    }

    data class Kanji(
        val meaningVi: String,
        val hanViet: String?,
        val onReadings: List<String>,
        val kunReadings: List<String>,
        val radical: Radical?,
        val shapeNoteVi: String,
    ) : ItemDetails {
        override val type get() = ContrastContentType.KANJI
    }
}

data class Collocation(val japanese: String, val furigana: String?, val meaningVi: String)

data class Radical(val character: String, val nameVi: String)

/** One of the 2–4 things compared in a group. */
data class ContrastItem(
    val id: String,
    val label: String,
    val labelFurigana: String?,
    val source: SourceReference,
    val details: ItemDetails,
)

/** How a source reference matches the existing content now (ARCHITECTURE §7.3). Never stored. */
enum class SourceRefStatus { OK, SOURCE_CHANGED, SOURCE_MISSING, AMBIGUOUS, UNVERIFIABLE }

/**
 * Result of resolving a [SourceReference].
 *
 * @property currentId the existing row to open, only when it still exists (OK, SOURCE_CHANGED).
 * @property candidateIds rows matching the snapshot when the id is gone; never picked automatically.
 */
data class ResolvedSource(
    val status: SourceRefStatus,
    val currentId: String? = null,
    val candidateIds: List<String> = emptyList(),
)

data class TheoryBlock(val id: String, val titleVi: String, val bodyVi: String)

/** One row of the comparison table: [values] maps item id → text. */
data class ComparisonRow(val aspectVi: String, val values: Map<String, String>)

data class ContrastExample(
    val id: String,
    val itemId: String,
    val japanese: String,
    val furigana: String?,
    val translationVi: String,
)

data class MemoryTip(val id: String, val textVi: String)

data class Sentence(val japanese: String, val furigana: String?)

/** A typical mistake; [confusion] is present for a mistake of one item for another. */
data class CommonMistake(
    val id: String,
    val situationVi: String,
    val wrong: Sentence,
    val correct: Sentence,
    val explanationVi: String,
    val confusion: Confusion?,
)

/** "Expected [expectedItemId], used [chosenItemId] instead": a directed confusion (A → B ≠ B → A). */
data class Confusion(val expectedItemId: String, val chosenItemId: String)

/**
 * Everything needed to study one group. Questions are deliberately not here (they belong to
 * practice, so the theory screen never shows answers).
 */
data class ContrastDetail(
    val group: ContrastGroup,
    val items: List<ContrastItem>,
    /** Filled when read ([GetContrastGroupDetail]); keyed by item id. */
    val sources: Map<String, ResolvedSource> = emptyMap(),
    val theory: List<TheoryBlock>,
    val comparison: List<ComparisonRow>,
    val examples: List<ContrastExample>,
    val memoryTips: List<MemoryTip>,
    val commonMistakes: List<CommonMistake>,
    val licenseText: String,
    val licenseVerified: Boolean,
)

data class QuestionPrompt(val japanese: String, val furigana: String?, val translationVi: String?)

data class QuestionContext(val descriptionVi: String, val japanese: String?, val furigana: String?)

/** ERROR_DETECTION: the correct form replacing the wrong segment. */
data class Correction(val text: String, val furigana: String?, val itemId: String?)

data class ContrastOption(
    val id: String,
    val text: String,
    val furigana: String?,
    /** The group item this option stands for (for A → B analysis); null for a distractor. */
    val itemId: String?,
    val feedbackVi: String?,
)

/**
 * One exercise. Answers are chosen among [options] (no typed answers in v1, so no normalization
 * or accepted-answer list exists): grading compares option ids only.
 */
data class ContrastQuestion(
    val id: String,
    val groupId: String,
    val revision: Int,
    val type: ContrastQuestionType,
    /** 1–3; the planner picks fresh questions of the same difficulty for delayed reviews. */
    val difficulty: Int,
    val targetItemId: String,
    val instructionVi: String,
    val prompt: QuestionPrompt?,
    val context: QuestionContext?,
    val options: List<ContrastOption>,
    val correctOptionId: String,
    val correction: Correction?,
    val explanationVi: String,
) {
    fun option(id: String): ContrastOption? = options.firstOrNull { it.id == id }
    val correctOption: ContrastOption get() = options.first { it.id == correctOptionId }
}

/** The installed Contrast pack, as the importer compares a new one with it. */
data class InstalledContrastPack(
    val id: String,
    val contentVersion: Int,
    val licenseVerified: Boolean,
    val groupIds: Set<String>,
)

/** A group no longer published, with the reason and its replacement if any. */
data class RetiredGroup(val id: String, val retiredInVersion: Int, val reasonVi: String, val replacedBy: String?)

/** List filter; [query] is matched accent-insensitively by the data layer. */
data class ContrastFilter(
    val query: String? = null,
    val contentType: ContrastContentType? = null,
    val jlptLevel: String? = null,
)

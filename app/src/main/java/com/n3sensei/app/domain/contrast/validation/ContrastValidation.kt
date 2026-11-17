package com.n3sensei.app.domain.contrast.validation

/** ERROR: the pack is not imported (nothing is written). WARNING: imported, a reviewer must acknowledge it. INFO: for information. */
enum class ContrastSeverity { ERROR, WARNING, INFO }

/** Where validation runs; only a few codes change severity between the two (CONTENT_SCHEMA §8). */
enum class ValidationMode {
    /** `build_pack.py`, against the source packs: every source mismatch is an ERROR. */
    BUILD,

    /** The app's importer, against the database: source mismatches are WARNINGs (sources may be updated independently). */
    IMPORT,
}

/**
 * The 40 validation codes of CONTENT_SCHEMA §8 (FURIGANA_MISSING has two rows there: ERROR
 * for required fields, WARNING for options/context/correction). Same names in
 * `tools/content/contrast/validator.py`.
 */
enum class ContrastIssueCode {
    INVALID_JSON,
    SCHEMA_VIOLATION,
    UNSUPPORTED_FORMAT,
    MISSING_METADATA,
    INVALID_CONTENT_VERSION,
    OLDER_OR_SAME_VERSION,
    STATUS_NOT_PUBLISHED,
    DUPLICATE_GROUP_ID,
    DUPLICATE_ITEM_ID,
    DUPLICATE_ID,
    DUPLICATE_SOURCE_IN_GROUP,
    UNKNOWN_ITEM_REFERENCE,
    ITEM_TYPE_MISMATCH,
    SOURCE_NOT_FOUND,
    SOURCE_CHANGED,
    SOURCE_AMBIGUOUS,
    SOURCE_PACK_NOT_LISTED,
    SOURCE_SCHEMA_UNSUPPORTED,
    MISSING_CORRECT_ANSWER,
    CORRECT_OPTION_NOT_IN_OPTIONS,
    TOO_FEW_OPTIONS,
    DUPLICATE_OPTION,
    UNSUPPORTED_QUESTION_TYPE,
    TARGET_NOT_CORRECT_OPTION,
    BLANK_MARKER_INVALID,
    SEGMENTS_NOT_SENTENCE,
    MISSING_EXPLANATION,
    NO_QUESTIONS,
    TOO_FEW_ITEMS,
    FURIGANA_INVALID,
    FURIGANA_MISMATCH,
    FURIGANA_MISSING,
    ANSWER_CHANGED_SAME_ID,
    GROUP_DISAPPEARED,
    FEW_OPTIONS,
    EMPTY_SECTION,
    QUESTION_TYPES_INCOMPLETE,
    LICENSE_UNVERIFIED,

    /** A group keeps its id but its set of item ids changed (a group with other items needs a new id). ERROR. */
    ITEMS_CHANGED_SAME_GROUP_ID,

    /** Content under a group's or question's revision changed without a higher revision (or the revision went down). ERROR. */
    REVISION_NOT_INCREMENTED,
    ;

    companion object {
        /** Codes whose severity depends on [ValidationMode]: ERROR when building, WARNING when importing. */
        val MODE_DEPENDENT = setOf(SOURCE_NOT_FOUND, SOURCE_CHANGED, SOURCE_AMBIGUOUS, SOURCE_SCHEMA_UNSUPPORTED, GROUP_DISAPPEARED)

        /** Editorial warnings a reviewer acknowledges in `approval.acknowledgedWarnings`. */
        val ACKNOWLEDGEABLE = setOf(FURIGANA_MISSING, FEW_OPTIONS, EMPTY_SECTION, QUESTION_TYPES_INCOMPLETE)
    }
}

/**
 * One finding. [path] uses the shared notation `groups[0].questions[3].options[1].furigana`.
 * [target] is the acknowledgement key of an editorial WARNING (group id, question id, or
 * `questionId/optionId`); [acknowledged] is set only for those.
 */
data class ContrastIssue(
    val code: ContrastIssueCode,
    val severity: ContrastSeverity,
    val path: String,
    val target: String? = null,
    val acknowledged: Boolean? = null,
    val detail: String? = null,
)

data class ContrastValidationResult(val issues: List<ContrastIssue>, val pack: ValidatedContrastPack?) {
    val hasErrors: Boolean get() = issues.any { it.severity == ContrastSeverity.ERROR }
    val unacknowledgedWarnings: List<ContrastIssue> get() = issues.filter { it.acknowledged == false }
}

package com.n3sensei.app.domain.importing

enum class ImportRecordType { LESSON, VOCABULARY, KANJI, GRAMMAR, EXAMPLE, EXERCISE, TAG }

data class ImportCount(val valid: Int = 0, val skipped: Int = 0)

enum class IssueSeverity {
    /** The import was aborted; nothing was written. */
    FATAL,

    /** The record was skipped; the rest of the pack was imported. */
    ERROR,

    /** Imported, but something looks off (e.g. a broken optional reference). */
    WARNING,
}

/** Machine-readable issue kinds; the UI turns them into localized text. */
enum class IssueCode {
    INVALID_JSON,
    READ_ERROR,
    UNSUPPORTED_FORMAT,
    MISSING_FIELD,
    INVALID_VALUE,
    DUPLICATE_ID,
    DUPLICATE_ENTRY,
    DUPLICATE_LESSON_NUMBER,
    UNKNOWN_SOURCE,
    UNKNOWN_EXAMPLE,
    UNKNOWN_TAG,
    INVALID_FURIGANA,
    FURIGANA_MISMATCH,
    INVALID_KANJI_CHARACTER,
    INVALID_EXERCISE,
    LESSON_SIZE_DIFFERS,
    NO_LESSONS,
    OLDER_VERSION,
    DATABASE_ERROR,
}

/**
 * @property location where in the pack, e.g. `lessons[0].vocabulary[3] (id=n3-l01-v004)`.
 * @property detail the offending field or value, when useful.
 */
data class ImportIssue(
    val severity: IssueSeverity,
    val code: IssueCode,
    val location: String,
    val detail: String? = null,
)

enum class ImportOutcome {
    /** Written to the database. */
    IMPORTED,

    /** Nothing written: the installed version is the same or newer (bundled content only). */
    UP_TO_DATE,

    /** Nothing written because of a fatal issue. */
    FAILED,
}

data class ImportReport(
    val outcome: ImportOutcome,
    val packId: String? = null,
    val packTitle: String? = null,
    val packVersion: Int? = null,
    val counts: Map<ImportRecordType, ImportCount> = emptyMap(),
    val issues: List<ImportIssue> = emptyList(),
) {
    val validRecords: Int get() = counts.values.sumOf { it.valid }
    val skippedRecords: Int get() = counts.values.sumOf { it.skipped }
    val errorCount: Int get() = issues.count { it.severity != IssueSeverity.WARNING }
    val warningCount: Int get() = issues.count { it.severity == IssueSeverity.WARNING }
}

/**
 * A valid pack, checked but not written yet.
 *
 * @property installedVersion version currently installed with the same pack id; null for a new pack.
 * @property removedItems items of the installed version missing from this one: they disappear
 *   from lessons and lists; progress for them is kept (and shown again if they come back).
 * @property removedWithProgress how many of [removedItems] the learner has already studied.
 * @property source the pack text, imported as is on confirmation.
 */
data class ImportPreview(
    val report: ImportReport,
    val installedVersion: Int?,
    val removedItems: Int,
    val removedWithProgress: Int,
    val source: String,
) {
    /** Replacing a pack that is installed: needs an explicit confirmation. */
    val replacesInstalled: Boolean get() = installedVersion != null
}

sealed interface ImportCheck {
    data class Ready(val preview: ImportPreview) : ImportCheck

    /** The pack cannot be imported; [report] says why. Nothing was written. */
    data class Failed(val report: ImportReport) : ImportCheck
}

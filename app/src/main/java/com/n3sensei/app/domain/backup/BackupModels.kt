package com.n3sensei.app.domain.backup

import com.n3sensei.app.domain.importing.IssueSeverity
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ProgressState
import com.n3sensei.app.domain.model.ReviewRating
import com.n3sensei.app.domain.model.ThemeMode
import com.n3sensei.app.domain.reminder.LockScreenPrivacy
import com.n3sensei.app.domain.reminder.ReminderFrequency
import com.n3sensei.app.domain.reminder.ReminderSettings
import com.n3sensei.app.domain.srs.ReviewSettings

// Learner data in a backup. Content (lessons, words, sources) is never included: it comes from
// content packs with their own licences. Timestamps are epoch milliseconds (UTC), as stored.

data class ProgressBackup(
    val ref: ItemRef,
    val state: ProgressState,
    val stepIndex: Int,
    val intervalMinutes: Long,
    val dueAt: Long?,
    val lastReviewedAt: Long?,
    val reviewCount: Int,
    val lapseCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

/** One answer, as stored. */
data class ReviewRecordBackup(
    val ref: ItemRef,
    val rating: ReviewRating,
    val reviewedAt: Long,
    val intervalBeforeMinutes: Long,
    val intervalAfterMinutes: Long,
    val dueAtAfter: Long,
    /** Null for answers recorded before database v3. */
    val requestId: String?,
    /** First answer of the item while it was new (counts as a new item in statistics). */
    val wasNew: Boolean,
)

data class MarkBackup(val ref: ItemRef, val markedAt: Long)

data class SessionBackup(
    val id: String,
    val startedAt: Long,
    val lastActivityAt: Long,
    val planned: Int,
    val answered: Int,
    val finished: Boolean,
)

/**
 * Reminder preferences without the on/off switch: notification permission belongs to the
 * device, so a restore keeps the current on/off state.
 */
data class ReminderBackup(
    val startHour: Int,
    val endHour: Int,
    val frequency: ReminderFrequency,
    val showReading: Boolean,
    val showMeaning: Boolean,
    val lockScreen: LockScreenPrivacy,
) {
    fun applyTo(current: ReminderSettings): ReminderSettings =
        current.copy(
            startHour = startHour, endHour = endHour, frequency = frequency,
            showReading = showReading, showMeaning = showMeaning, lockScreen = lockScreen,
        )

    companion object {
        fun from(s: ReminderSettings) = ReminderBackup(s.startHour, s.endHour, s.frequency, s.showReading, s.showMeaning, s.lockScreen)
    }
}

/** Settings that are restored. Onboarding state and reminder bookkeeping are not backed up. */
data class SettingsBackup(
    val themeMode: ThemeMode,
    val showFurigana: Boolean,
    val review: ReviewSettings,
    val reminder: ReminderBackup,
)

data class BackupData(
    val progress: List<ProgressBackup> = emptyList(),
    val reviewRecords: List<ReviewRecordBackup> = emptyList(),
    val marks: List<MarkBackup> = emptyList(),
    val sessions: List<SessionBackup> = emptyList(),
    val settings: SettingsBackup? = null,
) {
    val isEmpty: Boolean get() = progress.isEmpty() && reviewRecords.isEmpty() && marks.isEmpty() && sessions.isEmpty() && settings == null

    fun count(section: BackupSection): Int = when (section) {
        BackupSection.PROGRESS -> progress.size
        BackupSection.REVIEW_RECORDS -> reviewRecords.size
        BackupSection.MARKS -> marks.size
        BackupSection.SESSIONS -> sessions.size
    }
}

enum class BackupSection { PROGRESS, REVIEW_RECORDS, MARKS, SESSIONS }

/**
 * How a backup's records merge into the database: [added] (new), [updated] (backup is newer
 * and replaces the local row), [unchanged] (already present, or the local row is newer).
 */
data class MergeCount(val added: Int = 0, val updated: Int = 0, val unchanged: Int = 0) {
    val written: Int get() = added + updated
}

/** Machine-readable problems; the UI turns them into localized text. */
enum class BackupIssueCode {
    /** File could not be opened or read. */
    READ_ERROR,
    WRITE_ERROR,
    TOO_LARGE,
    EMPTY_FILE,
    INVALID_JSON,
    NOT_A_BACKUP,

    /** formatVersion is newer than this app understands. */
    NEWER_VERSION,

    /** formatVersion is not one this app has ever written. */
    UNSUPPORTED_VERSION,
    MISSING_FIELD,
    INVALID_VALUE,
    DUPLICATE_ID,

    /** Settings section is invalid; settings are not restored, data is. */
    INVALID_SETTINGS,

    /** Data for items not in the installed content (kept, like progress of removed items). */
    UNKNOWN_ITEMS,
    DATABASE_ERROR,
    SETTINGS_ERROR,
}

/**
 * @property severity FATAL: nothing restored; ERROR: that entry was left out; WARNING: restored.
 * @property location e.g. `progress[3]`.
 */
data class BackupIssue(
    val severity: IssueSeverity,
    val code: BackupIssueCode,
    val location: String = "",
    val detail: String? = null,
)

/** A checked backup file, ready to restore. Nothing has been written yet. */
data class RestorePreview(
    val createdAt: Long,
    val formatVersion: Int,
    val appVersion: String?,
    val data: BackupData,
    /** Entries left out because they were invalid or duplicated in the file. */
    val skippedEntries: Int,
    val issues: List<BackupIssue>,
    /** What a restore would do to the current database. */
    val plan: Map<BackupSection, MergeCount>,
) {
    val hasWork: Boolean get() = plan.values.any { it.written > 0 } || data.settings != null
}

sealed interface InspectResult {
    data class Ready(val preview: RestorePreview) : InspectResult
    data class Rejected(val issues: List<BackupIssue>) : InspectResult
}

data class RestoreReport(
    /** False when nothing was written to the database. */
    val success: Boolean,
    val counts: Map<BackupSection, MergeCount> = emptyMap(),
    val settingsRestored: Boolean = false,
    val skippedEntries: Int = 0,
    val issues: List<BackupIssue> = emptyList(),
)

data class ExportResult(
    val success: Boolean,
    val counts: Map<BackupSection, Int> = emptyMap(),
    val issues: List<BackupIssue> = emptyList(),
)

/** Export and restore of learner data through files the user picks (Storage Access Framework). */
interface BackupRepository {
    suspend fun export(documentUri: String): ExportResult

    /** Reads and checks a file and plans the merge; never writes. */
    suspend fun inspect(documentUri: String): InspectResult

    /** Merges [preview] into the database in one transaction, then (optionally) restores settings. */
    suspend fun restore(preview: RestorePreview, restoreSettings: Boolean): RestoreReport
}

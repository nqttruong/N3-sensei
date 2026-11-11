package com.n3sensei.app.data.backup

import kotlinx.serialization.Serializable

// Wire format of a backup file (see docs/BACKUP.md). Entries are read one by one as raw JSON
// elements so that one malformed entry is reported and skipped instead of failing the whole
// file. Never rename a field: older files must keep loading.

const val BACKUP_FORMAT = "n3sensei-backup"

/** Format versions this app can read; the last one is written. */
val SUPPORTED_BACKUP_VERSIONS = 1..1
val BACKUP_FORMAT_VERSION = SUPPORTED_BACKUP_VERSIONS.last

// Top level (read and written by BackupCodec, entry by entry): format, formatVersion,
// createdAt (epoch millis), appVersion, includesContent (always false), settings (SettingsDto),
// progress, marks, reviewSessions, reviewRecords (arrays of the DTOs below).

@Serializable
data class ProgressDto(
    val itemType: String? = null,
    val itemId: String? = null,
    val state: String? = null,
    val stepIndex: Int? = null,
    val intervalMinutes: Long? = null,
    val dueAt: Long? = null,
    val lastReviewedAt: Long? = null,
    val reviewCount: Int? = null,
    val lapseCount: Int? = null,
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
)

@Serializable
data class ReviewRecordDto(
    val itemType: String? = null,
    val itemId: String? = null,
    val rating: String? = null,
    val reviewedAt: Long? = null,
    val intervalBeforeMinutes: Long? = null,
    val intervalAfterMinutes: Long? = null,
    val dueAtAfter: Long? = null,
    val requestId: String? = null,
    val wasNew: Boolean? = null,
)

@Serializable
data class MarkDto(
    val itemType: String? = null,
    val itemId: String? = null,
    val markedAt: Long? = null,
)

@Serializable
data class SessionDto(
    val id: String? = null,
    val startedAt: Long? = null,
    val lastActivityAt: Long? = null,
    val planned: Int? = null,
    val answered: Int? = null,
    val finished: Boolean? = null,
)

@Serializable
data class SettingsDto(
    val themeMode: String? = null,
    val showFurigana: Boolean? = null,
    val review: ReviewSettingsDto? = null,
    val reminder: ReminderDto? = null,
)

@Serializable
data class ReviewSettingsDto(
    /** Same text as stored in DataStore, e.g. `20m,1h,1d,2d,1w,2w,1mo`. */
    val intervals: String? = null,
    /** The shared cap of older versions; written as the vocabulary cap so they can still read new files. */
    val newPerDay: Int? = null,
    val reviewsPerDay: Int? = null,
    val newVocabularyPerDay: Int? = null,
    val newKanjiPerDay: Int? = null,
    val newGrammarPerDay: Int? = null,
)

@Serializable
data class ReminderDto(
    val startHour: Int? = null,
    val endHour: Int? = null,
    val frequency: String? = null,
    val showReading: Boolean? = null,
    val showMeaning: Boolean? = null,
    val lockScreen: String? = null,
    val maxPerDay: Int? = null,
    val content: List<String>? = null,
    val reviewOnly: Boolean? = null,
)

package com.n3sensei.app.data.backup

import com.n3sensei.app.domain.backup.BackupData
import com.n3sensei.app.domain.backup.BackupIssue
import com.n3sensei.app.domain.backup.BackupIssueCode
import com.n3sensei.app.domain.backup.MarkBackup
import com.n3sensei.app.domain.backup.ProgressBackup
import com.n3sensei.app.domain.backup.ReminderBackup
import com.n3sensei.app.domain.backup.ReviewRecordBackup
import com.n3sensei.app.domain.backup.SessionBackup
import com.n3sensei.app.domain.backup.SettingsBackup
import com.n3sensei.app.domain.importing.IssueSeverity
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.ProgressState
import com.n3sensei.app.domain.model.ReviewRating
import com.n3sensei.app.domain.model.ThemeMode
import com.n3sensei.app.domain.reminder.LockScreenPrivacy
import com.n3sensei.app.domain.reminder.ReminderFrequency
import com.n3sensei.app.domain.srs.DailyLimits
import com.n3sensei.app.domain.srs.ReviewLadder
import com.n3sensei.app.domain.srs.ReviewSettings
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** Backup file ⇄ [BackupData], with every check needed before anything is written. */
object BackupCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Longest accepted id; content and request ids are far shorter. */
    const val MAX_ID_LENGTH = 200

    sealed interface Decoded {
        data class Ok(
            val createdAt: Long,
            val formatVersion: Int,
            val appVersion: String?,
            val data: BackupData,
            val skippedEntries: Int,
            val issues: List<BackupIssue>,
        ) : Decoded

        data class Rejected(val issues: List<BackupIssue>) : Decoded
    }

    fun encode(data: BackupData, createdAt: Long, appVersion: String?): String {
        val dto = BackupDto(
            format = BACKUP_FORMAT,
            formatVersion = BACKUP_FORMAT_VERSION,
            createdAt = createdAt,
            appVersion = appVersion,
            includesContent = false,
            settings = data.settings?.let { json.encodeToJsonElement(SettingsDto.serializer(), it.toDto()) },
            progress = data.progress.map { json.encodeToJsonElement(ProgressDto.serializer(), it.toDto()) },
            reviewRecords = data.reviewRecords.map { json.encodeToJsonElement(ReviewRecordDto.serializer(), it.toDto()) },
            marks = data.marks.map { json.encodeToJsonElement(MarkDto.serializer(), MarkDto(it.ref.type.name, it.ref.id, it.markedAt)) },
            reviewSessions = data.sessions.map {
                json.encodeToJsonElement(SessionDto.serializer(), SessionDto(it.id, it.startedAt, it.lastActivityAt, it.planned, it.answered, it.finished))
            },
        )
        return json.encodeToString(BackupDto.serializer(), dto)
    }

    fun decode(text: String): Decoded {
        if (text.isBlank()) return reject(BackupIssueCode.EMPTY_FILE)
        val dto = try {
            json.decodeFromString(BackupDto.serializer(), text)
        } catch (e: SerializationException) {
            return reject(BackupIssueCode.INVALID_JSON, detail = e.message?.take(MAX_DETAIL))
        } catch (e: IllegalArgumentException) {
            return reject(BackupIssueCode.INVALID_JSON, detail = e.message?.take(MAX_DETAIL))
        }
        if (dto.format != BACKUP_FORMAT) return reject(BackupIssueCode.NOT_A_BACKUP, "format", dto.format)
        val version = dto.formatVersion ?: return reject(BackupIssueCode.MISSING_FIELD, "formatVersion")
        if (version > SUPPORTED_BACKUP_VERSIONS.last) return reject(BackupIssueCode.NEWER_VERSION, "formatVersion", version.toString())
        if (version !in SUPPORTED_BACKUP_VERSIONS) return reject(BackupIssueCode.UNSUPPORTED_VERSION, "formatVersion", version.toString())
        val createdAt = dto.createdAt?.takeIf { it >= 0 } ?: return reject(BackupIssueCode.MISSING_FIELD, "createdAt")
        val missing = listOf(
            "progress" to dto.progress, "reviewRecords" to dto.reviewRecords,
            "marks" to dto.marks, "reviewSessions" to dto.reviewSessions,
        ).firstOrNull { it.second == null }
        if (missing != null) return reject(BackupIssueCode.MISSING_FIELD, missing.first)

        val reader = Reader()
        val progress = reader.section("progress", dto.progress!!, ProgressDto.serializer(), { it.ref }) { readProgress(it) }
        val records = reader.section("reviewRecords", dto.reviewRecords!!, ReviewRecordDto.serializer(), { it.requestId ?: it.key() }) { readRecord(it) }
        val marks = reader.section("marks", dto.marks!!, MarkDto.serializer(), { it.ref }) { readMark(it) }
        val sessions = reader.section("reviewSessions", dto.reviewSessions!!, SessionDto.serializer(), { it.id }) { readSession(it) }
        val settings = dto.settings?.let { reader.settings(it) }

        return Decoded.Ok(
            createdAt = createdAt,
            formatVersion = version,
            appVersion = dto.appVersion,
            data = BackupData(progress, records, marks, sessions, settings),
            skippedEntries = reader.skipped,
            issues = reader.issues,
        )
    }

    private fun reject(code: BackupIssueCode, location: String = "", detail: String? = null) =
        Decoded.Rejected(listOf(BackupIssue(IssueSeverity.FATAL, code, location, detail)))

    private fun ReviewRecordBackup.key() = "${ref.type}/${ref.id}@$reviewedAt/$rating"

    /** Thrown inside an entry check; becomes an ERROR issue for that entry. */
    private class Invalid(val code: BackupIssueCode, val field: String) : Exception()

    private class Reader {
        val issues = mutableListOf<BackupIssue>()
        var skipped = 0

        fun <D, T> section(
            name: String,
            elements: List<JsonElement>,
            serializer: KSerializer<D>,
            identity: (T) -> Any,
            read: (D) -> T,
        ): List<T> {
            val seen = HashSet<Any>()
            val out = ArrayList<T>(elements.size)
            elements.forEachIndexed { i, element ->
                val location = "$name[$i]"
                val value = try {
                    read(json.decodeFromJsonElement(serializer, element))
                } catch (e: Invalid) {
                    error(e.code, location, e.field)
                    return@forEachIndexed
                } catch (e: SerializationException) {
                    error(BackupIssueCode.INVALID_VALUE, location, e.message?.take(MAX_DETAIL))
                    return@forEachIndexed
                } catch (e: IllegalArgumentException) {
                    error(BackupIssueCode.INVALID_VALUE, location, e.message?.take(MAX_DETAIL))
                    return@forEachIndexed
                }
                if (seen.add(identity(value))) out += value else error(BackupIssueCode.DUPLICATE_ID, location, identity(value).toString())
            }
            return out
        }

        fun settings(element: JsonElement): SettingsBackup? = try {
            readSettings(json.decodeFromJsonElement(SettingsDto.serializer(), element))
        } catch (e: Invalid) {
            issues += BackupIssue(IssueSeverity.ERROR, BackupIssueCode.INVALID_SETTINGS, "settings", e.field)
            null
        } catch (e: SerializationException) {
            issues += BackupIssue(IssueSeverity.ERROR, BackupIssueCode.INVALID_SETTINGS, "settings", e.message?.take(MAX_DETAIL))
            null
        } catch (e: IllegalArgumentException) {
            issues += BackupIssue(IssueSeverity.ERROR, BackupIssueCode.INVALID_SETTINGS, "settings", e.message?.take(MAX_DETAIL))
            null
        }

        private fun error(code: BackupIssueCode, location: String, detail: String?) {
            skipped++
            issues += BackupIssue(IssueSeverity.ERROR, code, location, detail)
        }
    }

    // --- field checks -------------------------------------------------------------------------

    private fun <T> required(value: T?, field: String): T = value ?: throw Invalid(BackupIssueCode.MISSING_FIELD, field)

    private fun check(ok: Boolean, field: String) {
        if (!ok) throw Invalid(BackupIssueCode.INVALID_VALUE, field)
    }

    private inline fun <reified E : Enum<E>> enumField(value: String?, field: String): E =
        enumValues<E>().firstOrNull { it.name == required(value, field) } ?: throw Invalid(BackupIssueCode.INVALID_VALUE, field)

    private fun ref(type: String?, id: String?): ItemRef {
        val itemType = enumField<ItemType>(type, "itemType")
        val itemId = required(id, "itemId")
        check(itemId.isNotBlank() && itemId.length <= MAX_ID_LENGTH, "itemId")
        return ItemRef(itemType, itemId)
    }

    private fun time(value: Long?, field: String): Long = required(value, field).also { check(it >= 0, field) }

    private fun nonNegative(value: Long?, field: String): Long = required(value, field).also { check(it >= 0, field) }

    private fun nonNegative(value: Int?, field: String): Int = required(value, field).also { check(it >= 0, field) }

    private fun readProgress(d: ProgressDto) = ProgressBackup(
        ref = ref(d.itemType, d.itemId),
        state = enumField(d.state, "state"),
        stepIndex = nonNegative(d.stepIndex, "stepIndex").also { check(it < ReviewLadder.MAX_STEPS, "stepIndex") },
        intervalMinutes = nonNegative(d.intervalMinutes, "intervalMinutes"),
        dueAt = d.dueAt?.also { check(it >= 0, "dueAt") },
        lastReviewedAt = d.lastReviewedAt?.also { check(it >= 0, "lastReviewedAt") },
        reviewCount = nonNegative(d.reviewCount, "reviewCount"),
        lapseCount = nonNegative(d.lapseCount, "lapseCount"),
        createdAt = time(d.createdAt, "createdAt"),
        updatedAt = time(d.updatedAt, "updatedAt"),
    )

    private fun readRecord(d: ReviewRecordDto) = ReviewRecordBackup(
        ref = ref(d.itemType, d.itemId),
        rating = enumField(d.rating, "rating"),
        reviewedAt = time(d.reviewedAt, "reviewedAt"),
        intervalBeforeMinutes = nonNegative(d.intervalBeforeMinutes, "intervalBeforeMinutes"),
        intervalAfterMinutes = nonNegative(d.intervalAfterMinutes, "intervalAfterMinutes"),
        dueAtAfter = time(d.dueAtAfter, "dueAtAfter"),
        requestId = d.requestId?.also { check(it.isNotBlank() && it.length <= MAX_ID_LENGTH, "requestId") },
        wasNew = required(d.wasNew, "wasNew"),
    )

    private fun readMark(d: MarkDto) = MarkBackup(ref(d.itemType, d.itemId), time(d.markedAt, "markedAt"))

    private fun readSession(d: SessionDto): SessionBackup {
        val id = required(d.id, "id").also { check(it.isNotBlank() && it.length <= MAX_ID_LENGTH, "id") }
        val planned = nonNegative(d.planned, "planned")
        val answered = nonNegative(d.answered, "answered").also { check(it <= planned, "answered") }
        return SessionBackup(id, time(d.startedAt, "startedAt"), time(d.lastActivityAt, "lastActivityAt"), planned, answered, required(d.finished, "finished"))
    }

    private fun readSettings(d: SettingsDto): SettingsBackup {
        val review = required(d.review, "review")
        val ladder = ReviewLadder.decodeOrNull(required(review.intervals, "review.intervals"))
            ?: throw Invalid(BackupIssueCode.INVALID_VALUE, "review.intervals")
        val newPerDay = required(review.newPerDay, "review.newPerDay").also { check(it in 0..DailyLimits.MAX_NEW, "review.newPerDay") }
        val reviewsPerDay = required(review.reviewsPerDay, "review.reviewsPerDay").also { check(it in 1..DailyLimits.MAX_REVIEWS, "review.reviewsPerDay") }
        val r = required(d.reminder, "reminder")
        return SettingsBackup(
            themeMode = enumField(d.themeMode, "themeMode"),
            showFurigana = required(d.showFurigana, "showFurigana"),
            review = ReviewSettings(ladder, DailyLimits(newPerDay, reviewsPerDay)),
            reminder = ReminderBackup(
                startHour = required(r.startHour, "reminder.startHour").also { check(it in 0..23, "reminder.startHour") },
                endHour = required(r.endHour, "reminder.endHour").also { check(it in 0..23, "reminder.endHour") },
                frequency = enumField<ReminderFrequency>(r.frequency, "reminder.frequency"),
                showReading = required(r.showReading, "reminder.showReading"),
                showMeaning = required(r.showMeaning, "reminder.showMeaning"),
                lockScreen = enumField<LockScreenPrivacy>(r.lockScreen, "reminder.lockScreen"),
            ),
        )
    }

    // --- to wire ------------------------------------------------------------------------------

    private fun ProgressBackup.toDto() = ProgressDto(
        ref.type.name, ref.id, state.name, stepIndex, intervalMinutes, dueAt, lastReviewedAt,
        reviewCount, lapseCount, createdAt, updatedAt,
    )

    private fun ReviewRecordBackup.toDto() = ReviewRecordDto(
        ref.type.name, ref.id, rating.name, reviewedAt, intervalBeforeMinutes, intervalAfterMinutes, dueAtAfter, requestId, wasNew,
    )

    private fun SettingsBackup.toDto() = SettingsDto(
        themeMode = themeMode.name,
        showFurigana = showFurigana,
        review = ReviewSettingsDto(review.ladder.encode(), review.limits.newPerDay, review.limits.reviewsPerDay),
        reminder = ReminderDto(
            reminder.startHour, reminder.endHour, reminder.frequency.name,
            reminder.showReading, reminder.showMeaning, reminder.lockScreen.name,
        ),
    )

    private const val MAX_DETAIL = 300
}

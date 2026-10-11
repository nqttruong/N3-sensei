package com.n3sensei.app.data.backup

import android.content.Context
import androidx.core.net.toUri
import com.n3sensei.app.data.local.dao.BackupDao
import com.n3sensei.app.domain.backup.BackupData
import com.n3sensei.app.domain.backup.BackupIssue
import com.n3sensei.app.domain.backup.BackupIssueCode
import com.n3sensei.app.domain.backup.BackupMerge
import com.n3sensei.app.domain.backup.BackupRepository
import com.n3sensei.app.domain.backup.BackupSection
import com.n3sensei.app.domain.backup.ExportResult
import com.n3sensei.app.domain.backup.InspectResult
import com.n3sensei.app.domain.backup.ReminderBackup
import com.n3sensei.app.domain.backup.RestorePreview
import com.n3sensei.app.domain.backup.RestoreReport
import com.n3sensei.app.domain.backup.SettingsBackup
import com.n3sensei.app.domain.importing.IssueSeverity
import com.n3sensei.app.domain.reminder.ReminderScheduler
import com.n3sensei.app.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.time.Clock

/**
 * Backup and restore through document URIs from the Storage Access Framework. A restore
 * merges into the database in one transaction ([BackupDao.merge]) and never deletes data; the
 * settings are written only after the data was restored.
 */
class AndroidBackupRepository(
    private val context: Context,
    private val dao: BackupDao,
    private val preferences: UserPreferencesRepository,
    private val reminderScheduler: ReminderScheduler,
    private val clock: Clock,
    private val appVersion: String?,
) : BackupRepository {

    override suspend fun export(documentUri: String): ExportResult {
        val prefs = preferences.preferences.first()
        val data = dao.snapshot().copy(
            settings = SettingsBackup(prefs.themeMode, prefs.showFurigana, prefs.reviewSettings, ReminderBackup.from(prefs.reminderSettings)),
        )
        val text = BackupCodec.encode(data, clock.millis(), appVersion)
        val written = withContext(Dispatchers.IO) {
            try {
                // "wt": truncate, so overwriting a longer existing file leaves no trailing bytes.
                context.contentResolver.openOutputStream(documentUri.toUri(), "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } != null
            } catch (e: IOException) {
                false
            } catch (e: SecurityException) {
                false
            }
        }
        if (!written) return ExportResult(false, issues = listOf(BackupIssue(IssueSeverity.FATAL, BackupIssueCode.WRITE_ERROR)))
        return ExportResult(true, counts = BackupSection.entries.associateWith { data.count(it) })
    }

    override suspend fun inspect(documentUri: String): InspectResult {
        val bytes = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(documentUri.toUri())?.use { it.readUpTo(MAX_BYTES + 1) }
            } catch (e: IOException) {
                null
            } catch (e: SecurityException) {
                null
            }
        } ?: return rejected(BackupIssueCode.READ_ERROR)
        if (bytes.size > MAX_BYTES) return rejected(BackupIssueCode.TOO_LARGE, "${MAX_BYTES / (1024 * 1024)} MB")
        return inspectText(bytes.toString(Charsets.UTF_8))
    }

    /** Checks [text] and plans the merge against the current database. */
    suspend fun inspectText(text: String): InspectResult {
        val decoded = withContext(Dispatchers.Default) { BackupCodec.decode(text) }
        val ok = when (decoded) {
            is BackupCodec.Decoded.Rejected -> return InspectResult.Rejected(decoded.issues)
            is BackupCodec.Decoded.Ok -> decoded
        }
        val plan = BackupMerge.plan(ok.data, dao.localKeys())
        return InspectResult.Ready(
            RestorePreview(
                createdAt = ok.createdAt,
                formatVersion = ok.formatVersion,
                appVersion = ok.appVersion,
                data = ok.data,
                skippedEntries = ok.skippedEntries,
                issues = ok.issues + listOfNotNull(unknownItemsWarning(ok.data)),
                plan = plan.counts,
            ),
        )
    }

    override suspend fun restore(preview: RestorePreview, restoreSettings: Boolean): RestoreReport {
        val counts = try {
            dao.merge(preview.data)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Rolled back: the database is exactly as before; settings were not touched.
            return RestoreReport(
                success = false,
                skippedEntries = preview.skippedEntries,
                issues = preview.issues + BackupIssue(IssueSeverity.FATAL, BackupIssueCode.DATABASE_ERROR, detail = e.message?.take(300)),
            )
        }
        val settings = preview.data.settings?.takeIf { restoreSettings }
        val settingsIssue = settings?.let { restoreSettings(it) }
        return RestoreReport(
            success = true,
            counts = counts,
            settingsRestored = settings != null && settingsIssue == null,
            skippedEntries = preview.skippedEntries,
            issues = preview.issues + listOfNotNull(settingsIssue),
        )
    }

    /** Null on success. Reminder on/off stays as it is on this device. */
    private suspend fun restoreSettings(settings: SettingsBackup): BackupIssue? = try {
        preferences.setThemeMode(settings.themeMode)
        preferences.setShowFurigana(settings.showFurigana)
        preferences.setReviewSettings(settings.review)
        val reminder = settings.reminder.applyTo(preferences.preferences.first().reminderSettings)
        preferences.setReminderSettings(reminder)
        reminderScheduler.apply(reminder)
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        BackupIssue(IssueSeverity.ERROR, BackupIssueCode.SETTINGS_ERROR, detail = e.message?.take(300))
    }

    private suspend fun unknownItemsWarning(data: BackupData): BackupIssue? {
        val content = dao.contentRefs()
        val unknown = (data.progress.map { it.ref } + data.reviewRecords.map { it.ref } + data.marks.map { it.ref })
            .toSet().count { it !in content }
        return if (unknown == 0) null else BackupIssue(IssueSeverity.WARNING, BackupIssueCode.UNKNOWN_ITEMS, detail = unknown.toString())
    }

    private fun rejected(code: BackupIssueCode, detail: String? = null) =
        InspectResult.Rejected(listOf(BackupIssue(IssueSeverity.FATAL, code, detail = detail)))

    private fun InputStream.readUpTo(limit: Int): ByteArray {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
        while (buffer.size() < limit) {
            val read = read(chunk, 0, minOf(chunk.size, limit - buffer.size()))
            if (read < 0) break
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }

    companion object {
        /** About 300 000 answers; far above years of daily study. */
        const val MAX_BYTES = 64 * 1024 * 1024
    }
}

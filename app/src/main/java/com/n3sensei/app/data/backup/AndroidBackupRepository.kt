package com.n3sensei.app.data.backup

import android.content.Context
import android.provider.DocumentsContract
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
import java.io.FilterInputStream
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
        val settings = SettingsBackup(prefs.themeMode, prefs.showFurigana, prefs.reviewSettings, ReminderBackup.from(prefs.reminderSettings))
        val counts = mutableMapOf<BackupSection, Int>()
        val failure: BackupIssueCode? = try {
            withContext(Dispatchers.IO) {
                // "wt": truncate, so overwriting a longer existing file leaves no trailing bytes.
                val stream = context.contentResolver.openOutputStream(documentUri.toUri(), "wt") ?: return@withContext BackupIssueCode.WRITE_ERROR
                stream.bufferedWriter(Charsets.UTF_8).use { out ->
                    val writer = BackupCodec.Writer(out, clock.millis(), appVersion)
                    var records = 0
                    dao.export(
                        onBase = { base ->
                            writer.begin(settings, base.progress, base.marks, base.sessions)
                            BackupSection.entries.forEach { counts[it] = base.count(it) }
                        },
                        onRecords = { page ->
                            page.forEach(writer::record)
                            records += page.size
                        },
                    )
                    writer.end()
                    counts[BackupSection.REVIEW_RECORDS] = records
                }
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            BackupIssueCode.WRITE_ERROR
        } catch (e: SecurityException) {
            BackupIssueCode.WRITE_ERROR
        } catch (e: Exception) {
            BackupIssueCode.DATABASE_ERROR
        }
        if (failure != null) {
            // The file may be incomplete; it is not a backup anyone should restore later.
            withContext(Dispatchers.IO) { runCatching { DocumentsContract.deleteDocument(context.contentResolver, documentUri.toUri()) } }
            return ExportResult(false, issues = listOf(BackupIssue(IssueSeverity.FATAL, failure)))
        }
        return ExportResult(true, counts = counts)
    }

    override suspend fun inspect(documentUri: String): InspectResult {
        val decoded = try {
            withContext(Dispatchers.IO) {
                val stream = context.contentResolver.openInputStream(documentUri.toUri()) ?: return@withContext null
                LimitedInputStream(stream, MAX_BYTES).use { BackupCodec.decode(it) }
            }
        } catch (e: TooLargeException) {
            return rejected(BackupIssueCode.TOO_LARGE, "${MAX_BYTES / (1024 * 1024)} MB")
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        } ?: return rejected(BackupIssueCode.READ_ERROR)
        return preview(decoded)
    }

    /** Checks [text] and plans the merge against the current database. */
    suspend fun inspectText(text: String): InspectResult = preview(withContext(Dispatchers.Default) { BackupCodec.decode(text) })

    private suspend fun preview(decoded: BackupCodec.Decoded): InspectResult {
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

    companion object {
        /**
         * Files are read as a stream, so this only guards against picking a wrong, huge file.
         * 300 000 answers (about two years at 400 a day) take roughly 75 MB.
         */
        const val MAX_BYTES = 256L * 1024 * 1024
    }
}

private class TooLargeException : IOException()

/** Fails with [TooLargeException] once more than [limit] bytes have been read. */
private class LimitedInputStream(input: InputStream, private val limit: Long) : FilterInputStream(input) {
    private var count = 0L

    override fun read(): Int = super.read().also { if (it >= 0) add(1) }

    override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) add(it.toLong()) }

    private fun add(n: Long) {
        count += n
        if (count > limit) throw TooLargeException()
    }
}

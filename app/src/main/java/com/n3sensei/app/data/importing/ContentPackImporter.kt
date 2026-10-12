package com.n3sensei.app.data.importing

import com.n3sensei.app.data.local.dao.ContentPackDao
import com.n3sensei.app.domain.importing.ImportCheck
import com.n3sensei.app.domain.importing.ImportIssue
import com.n3sensei.app.domain.importing.ImportOutcome
import com.n3sensei.app.domain.importing.ImportPreview
import com.n3sensei.app.domain.importing.ImportReport
import com.n3sensei.app.domain.importing.IssueCode
import com.n3sensei.app.domain.importing.IssueSeverity
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.Clock

/**
 * Parses, validates and writes a content pack. The write is one transaction
 * ([ContentPackDao.replacePack]): either the whole valid part of the pack replaces the old
 * version, or nothing changes. Progress and marks are keyed by stable item IDs and are never
 * touched.
 */
class ContentPackImporter(
    private val packDao: ContentPackDao,
    private val clock: Clock,
) {
    /**
     * @param onlyIfNewer skip the write when the same pack is installed at this or a newer
     *   version (used for content bundled with the app).
     */
    suspend fun import(text: String, onlyIfNewer: Boolean = false): ImportReport {
        val dto = when (val parsed = ContentPackJsonParser.parse(text)) {
            is ContentPackJsonParser.Result.Parsed -> parsed.dto
            is ContentPackJsonParser.Result.Invalid -> return parsed.report
        }

        val result = ContentPackValidator(importedAt = clock.millis()).validate(dto)
        val bundle = result.bundle ?: return result.report

        val installed = packDao.getPack(bundle.pack.id)
        if (onlyIfNewer && installed != null && installed.version >= bundle.pack.version) {
            return result.report.copy(outcome = ImportOutcome.UP_TO_DATE)
        }
        val versionWarning = installed?.takeIf { it.version > bundle.pack.version }?.let {
            ImportIssue(IssueSeverity.WARNING, IssueCode.OLDER_VERSION, "pack.version", "${bundle.pack.version} < ${it.version}")
        }

        return try {
            packDao.replacePack(bundle)
            result.report.copy(issues = result.report.issues + listOfNotNull(versionWarning))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The transaction was rolled back; the previously installed content is unchanged.
            result.report.copy(
                outcome = ImportOutcome.FAILED,
                issues = result.report.issues +
                    ImportIssue(IssueSeverity.FATAL, IssueCode.DATABASE_ERROR, "", e.message?.take(MAX_DETAIL)),
            )
        }
    }

    /**
     * Validates [text] and compares it with the installed pack of the same id, without writing.
     * The same checks run again in [import] when the user confirms.
     */
    suspend fun check(text: String): ImportCheck {
        val dto = when (val parsed = ContentPackJsonParser.parse(text)) {
            is ContentPackJsonParser.Result.Parsed -> parsed.dto
            is ContentPackJsonParser.Result.Invalid -> return ImportCheck.Failed(parsed.report)
        }
        val result = ContentPackValidator(importedAt = clock.millis()).validate(dto)
        val bundle = result.bundle ?: return ImportCheck.Failed(result.report)
        val installed = packDao.getPack(bundle.pack.id)
        val versionWarning = installed?.takeIf { it.version > bundle.pack.version }?.let {
            ImportIssue(IssueSeverity.WARNING, IssueCode.OLDER_VERSION, "pack.version", "${bundle.pack.version} < ${it.version}")
        }
        val removed = if (installed == null) {
            emptyList()
        } else {
            val incoming = bundle.vocabulary.map { "VOCABULARY:${it.id}" } + bundle.kanji.map { "KANJI:${it.id}" } + bundle.grammar.map { "GRAMMAR:${it.id}" }
            packDao.getItemKeys(installed.id) - incoming.toSet()
        }
        return ImportCheck.Ready(
            ImportPreview(
                report = result.report.copy(issues = result.report.issues + listOfNotNull(versionWarning)),
                installedVersion = installed?.version,
                removedItems = removed.size,
                removedWithProgress = removed.chunked(MAX_SQL_ARGS).sumOf { packDao.countWithProgress(it) },
                source = text,
            ),
        )
    }

    companion object {
        internal const val MAX_DETAIL = 300
        private const val MAX_SQL_ARGS = 500

        fun failed(code: IssueCode, location: String, detail: String? = null) = ImportReport(
            outcome = ImportOutcome.FAILED,
            issues = listOf(ImportIssue(IssueSeverity.FATAL, code, location, detail)),
        )
    }
}

/** JSON → [ContentPackDto]; syntax or type errors become a FAILED report. */
object ContentPackJsonParser {
    private val json = Json { ignoreUnknownKeys = true }

    sealed interface Result {
        data class Parsed(val dto: ContentPackDto) : Result
        data class Invalid(val report: ImportReport) : Result
    }

    fun parse(text: String): Result = try {
        Result.Parsed(json.decodeFromString(ContentPackDto.serializer(), text))
    } catch (e: SerializationException) {
        invalid(e)
    } catch (e: IllegalArgumentException) {
        invalid(e)
    }

    private fun invalid(e: Exception) =
        Result.Invalid(ContentPackImporter.failed(IssueCode.INVALID_JSON, "", e.message?.take(ContentPackImporter.MAX_DETAIL)))
}

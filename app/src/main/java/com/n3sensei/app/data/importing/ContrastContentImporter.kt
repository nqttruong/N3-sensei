package com.n3sensei.app.data.importing

import com.n3sensei.app.data.local.dao.ContrastDao
import com.n3sensei.app.data.repository.OfflineContrastContentRepository
import com.n3sensei.app.data.repository.RoomContrastSourceResolver
import com.n3sensei.app.domain.contrast.ContrastContentType
import com.n3sensei.app.domain.contrast.InstalledContrastPack
import com.n3sensei.app.domain.contrast.SourceReference
import com.n3sensei.app.domain.contrast.validation.ContrastIssue
import com.n3sensei.app.domain.contrast.validation.ContrastIssueCode
import com.n3sensei.app.domain.contrast.validation.ContrastSeverity
import com.n3sensei.app.domain.contrast.validation.RawContrastPack
import com.n3sensei.app.domain.contrast.validation.ValidatedContrastGroup
import com.n3sensei.app.domain.contrast.validation.ValidationMode
import java.time.Clock

/** Result of one Contrast pack import. */
data class ContrastImportReport(
    val outcome: Outcome,
    val packId: String?,
    val contentVersion: Int?,
    /** Version installed before this import (same pack id), if any. */
    val previousVersion: Int?,
    /** Structure + semantic findings (ContrastPackCheck), with their severity and path. */
    val issues: List<ContrastIssue>,
    val failure: Failure? = null,
    /** Who authored and approved each group, as the pack states it (self-approval is reported, D1). */
    val approvals: List<Approval> = emptyList(),
    /** Set when the write failed and was rolled back. */
    val databaseError: String? = null,
) {
    enum class Outcome {
        /** Written in one transaction and read back. */
        IMPORTED,

        /** Nothing written: this version (or a newer one) is installed. */
        UP_TO_DATE,

        /** Nothing written (see [failure]); the content installed before is unchanged. */
        FAILED,
    }

    enum class Failure {
        /** At least one ERROR (structure, semantics, sources at build, revisions…). */
        INVALID,

        /** An editorial WARNING without the reviewer's acknowledgement: a release must not carry one. */
        UNACKNOWLEDGED_WARNING,

        /** The database write failed and was rolled back. */
        DATABASE,

        /** The written pack could not be read back as written. */
        NOT_VERIFIED,
    }

    data class Approval(val groupId: String?, val revision: Int?, val authoredBy: String, val approvedBy: String, val approvedRevision: Int) {
        val selfApproved: Boolean get() = authoredBy == approvedBy
    }
}

/**
 * Imports a Contrast pack (docs/CONTRAST_LAB_IMPORTER.md):
 *
 * parse → structure → semantics → approval rules → sources resolved against Room → comparison with
 * the installed content (ITEMS_CHANGED_SAME_GROUP_ID, REVISION_NOT_INCREMENTED, GROUP_DISAPPEARED,
 * version) → one transaction → read back.
 *
 * Any ERROR refuses the whole pack (nothing written, installed content unchanged). Source
 * mismatches are WARNINGs at import (the source packs may be updated on their own) and are kept in
 * the report. Learner data (sessions, attempts, progress, reviews) is never touched: those tables
 * have no foreign key and the write only replaces content rows.
 *
 * The reviewer registry is a release-tool check (`build_pack.py --reviewers`): a reviewer id in the
 * JSON is a statement, not proof, and the app has no registry to check it against.
 */
class ContrastContentImporter(
    private val dao: ContrastDao,
    private val clock: Clock,
) {
    private val content = OfflineContrastContentRepository(dao)
    private val sources = RoomContrastSourceResolver(dao)

    suspend fun import(text: String): ContrastImportReport {
        val parsed = ContrastPackParser.parse(text, ValidationMode.IMPORT)
        val raw = parsed.raw ?: return ContrastImportReport(ContrastImportReport.Outcome.FAILED, null, null, null, parsed.issues, ContrastImportReport.Failure.INVALID)
        val packId = raw.pack?.id
        val installed = content.installedPack()?.takeIf { it.id == packId }
        val previousGroups = installed?.let { installedContent(it) }
        val lookup = sources.lookupFor(references(raw))
        val validation = ContrastPackCheck.check(parsed, ValidationMode.IMPORT, lookup, installed, previousGroups).validation

        fun report(outcome: ContrastImportReport.Outcome, failure: ContrastImportReport.Failure? = null, error: String? = null) =
            ContrastImportReport(outcome, packId, raw.pack?.contentVersion, installed?.contentVersion, validation.issues, failure, approvals(raw), error)

        if (validation.hasErrors) return report(ContrastImportReport.Outcome.FAILED, ContrastImportReport.Failure.INVALID)
        if (validation.issues.any { it.code == ContrastIssueCode.OLDER_OR_SAME_VERSION }) return report(ContrastImportReport.Outcome.UP_TO_DATE)
        if (validation.unacknowledgedWarnings.isNotEmpty()) return report(ContrastImportReport.Outcome.FAILED, ContrastImportReport.Failure.UNACKNOWLEDGED_WARNING)
        val pack = checkNotNull(validation.pack) { "No ERROR means a validated pack" }

        try {
            content.replaceInstalledPack(pack, schemaVersion = SCHEMA_VERSION, now = clock.instant())
        } catch (e: Exception) {
            // Room rolled the transaction back: the content installed before is unchanged.
            return report(ContrastImportReport.Outcome.FAILED, ContrastImportReport.Failure.DATABASE, e.javaClass.simpleName + ": " + e.message)
        }
        val readBack = content.installedPack()
        val verified = readBack != null && readBack.id == pack.id && readBack.contentVersion == pack.contentVersion &&
            readBack.groupIds == pack.groups.map { it.detail.group.id }.toSet() &&
            pack.groups.all { g -> content.getDetail(g.detail.group.id) == g.detail && content.getQuestions(g.detail.group.id) == g.questions }
        return if (verified) report(ContrastImportReport.Outcome.IMPORTED) else report(ContrastImportReport.Outcome.FAILED, ContrastImportReport.Failure.NOT_VERIFIED)
    }

    /** The installed content as domain models, for the revision rules. */
    private suspend fun installedContent(installed: InstalledContrastPack): List<ValidatedContrastGroup> =
        installed.groupIds.mapNotNull { id -> content.getDetail(id)?.let { ValidatedContrastGroup(it, content.getQuestions(id)) } }

    private fun references(raw: RawContrastPack): List<SourceReference> = raw.groups.flatMap { g -> g.items.orEmpty() }.mapNotNull { item ->
        val type = ContrastContentType.fromCode(item.source.sourceType) ?: return@mapNotNull null
        SourceReference(type, item.source.sourceContentId, item.source.sourcePackId, item.source.sourceSchemaVersion, item.source.sourceSnapshot, item.source.sourceFingerprint)
    }

    private fun approvals(raw: RawContrastPack) = raw.groups.mapNotNull { g ->
        g.approval?.let { ContrastImportReport.Approval(g.id, g.revision, it.authoredBy, it.approvedBy, it.approvedRevision) }
    }

    companion object {
        /** The pack `schemaVersion` this app reads. */
        const val SCHEMA_VERSION = 1

        fun summary(report: ContrastImportReport): String = buildString {
            append("${report.packId} v${report.contentVersion}: ${report.outcome}")
            report.failure?.let { append(" ($it)") }
            report.databaseError?.let { append(" — $it") }
            val errors = report.issues.filter { it.severity == ContrastSeverity.ERROR }
            if (errors.isNotEmpty()) append("; ").append(errors.take(10).joinToString { "${it.code} ${it.path}" })
        }
    }
}

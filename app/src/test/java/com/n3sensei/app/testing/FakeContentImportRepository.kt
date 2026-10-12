package com.n3sensei.app.testing

import com.n3sensei.app.domain.importing.ImportCheck
import com.n3sensei.app.domain.importing.ImportOutcome
import com.n3sensei.app.domain.importing.ImportPreview
import com.n3sensei.app.domain.importing.ImportReport
import com.n3sensei.app.domain.repository.ContentImportRepository

class FakeContentImportRepository(
    var nextReport: ImportReport = ImportReport(ImportOutcome.IMPORTED, packId = "p", packTitle = "Pack", packVersion = 1),
) : ContentImportRepository {
    val checkedUris = mutableListOf<String>()
    val imported = mutableListOf<ImportPreview>()
    var bundledInstalls = 0

    /** Version installed with the same pack id; null means the pack is new. */
    var installedVersion: Int? = null
    var failCheck: ImportReport? = null

    override suspend fun checkDocument(documentUri: String): ImportCheck {
        checkedUris += documentUri
        failCheck?.let { return ImportCheck.Failed(it) }
        return ImportCheck.Ready(ImportPreview(nextReport, installedVersion, removedItems = 2, removedWithProgress = 1, source = documentUri))
    }

    override suspend fun importChecked(preview: ImportPreview): ImportReport {
        imported += preview
        return nextReport
    }

    override suspend fun installBundledContent(): ImportReport {
        bundledInstalls++
        return ImportReport(ImportOutcome.UP_TO_DATE)
    }
}

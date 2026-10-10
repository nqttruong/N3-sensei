package com.n3sensei.app.testing

import com.n3sensei.app.domain.importing.ImportOutcome
import com.n3sensei.app.domain.importing.ImportReport
import com.n3sensei.app.domain.repository.ContentImportRepository

class FakeContentImportRepository(
    var nextReport: ImportReport = ImportReport(ImportOutcome.IMPORTED, packId = "p", packTitle = "Pack", packVersion = 1),
) : ContentImportRepository {
    val importedUris = mutableListOf<String>()
    var bundledInstalls = 0

    override suspend fun importDocument(documentUri: String): ImportReport {
        importedUris += documentUri
        return nextReport
    }

    override suspend fun installBundledContent(): ImportReport {
        bundledInstalls++
        return ImportReport(ImportOutcome.UP_TO_DATE)
    }
}

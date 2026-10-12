package com.n3sensei.app.domain.repository

import com.n3sensei.app.domain.importing.ImportCheck
import com.n3sensei.app.domain.importing.ImportPreview
import com.n3sensei.app.domain.importing.ImportReport

/** Imports content packs. Learner progress and marks are never modified. */
interface ContentImportRepository {
    /** Reads and validates a pack the user picked (content URI string) and compares it with the installed version; writes nothing. */
    suspend fun checkDocument(documentUri: String): ImportCheck

    /** Imports a pack checked by [checkDocument], after the user confirmed. */
    suspend fun importChecked(preview: ImportPreview): ImportReport

    /** Installs the pack bundled with the app if it is missing or older than the bundled one. */
    suspend fun installBundledContent(): ImportReport
}

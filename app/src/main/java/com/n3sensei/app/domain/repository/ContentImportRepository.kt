package com.n3sensei.app.domain.repository

import com.n3sensei.app.domain.importing.ImportReport

/** Imports content packs. Learner progress and marks are never modified. */
interface ContentImportRepository {
    /** Validates and imports a pack from a document the user picked (content URI string). */
    suspend fun importDocument(documentUri: String): ImportReport

    /** Installs the pack bundled with the app if it is missing or older than the bundled one. */
    suspend fun installBundledContent(): ImportReport
}

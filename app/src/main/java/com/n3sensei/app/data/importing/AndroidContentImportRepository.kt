package com.n3sensei.app.data.importing

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import com.n3sensei.app.domain.importing.ImportCheck
import com.n3sensei.app.domain.importing.ImportOutcome
import com.n3sensei.app.domain.importing.ImportPreview
import com.n3sensei.app.domain.importing.ImportReport
import com.n3sensei.app.domain.importing.IssueCode
import com.n3sensei.app.domain.repository.ContentImportRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

class AndroidContentImportRepository(
    private val context: Context,
    private val importer: ContentPackImporter,
    private val bundledAssetPath: String = BUNDLED_PACK_ASSET,
) : ContentImportRepository {

    /** Switched off by instrumented tests that need a database with only their own data. */
    @Volatile
    internal var bundledContentEnabled: Boolean = true

    override suspend fun checkDocument(documentUri: String): ImportCheck {
        val bytes = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(documentUri.toUri())?.use { it.readUpTo(MAX_BYTES + 1) }
            } catch (e: IOException) {
                null
            } catch (e: SecurityException) {
                null
            }
        } ?: return ImportCheck.Failed(ContentPackImporter.failed(IssueCode.READ_ERROR, documentUri))
        if (bytes.size > MAX_BYTES) {
            return ImportCheck.Failed(ContentPackImporter.failed(IssueCode.READ_ERROR, documentUri, "> ${MAX_BYTES / (1024 * 1024)} MB"))
        }
        return importer.check(bytes.toString(Charsets.UTF_8))
    }

    override suspend fun importChecked(preview: ImportPreview): ImportReport = importer.import(preview.source)

    override suspend fun installBundledContent(): ImportReport {
        if (!bundledContentEnabled) return ImportReport(ImportOutcome.UP_TO_DATE)
        val text = withContext(Dispatchers.IO) {
            context.assets.open(bundledAssetPath).use { it.readBytes().toString(Charsets.UTF_8) }
        }
        return importer.import(text, onlyIfNewer = true).also { report ->
            if (report.outcome == ImportOutcome.FAILED) Log.e(TAG, "Bundled content failed to install: ${report.issues}")
        }
    }

    /** Reads at most [limit] bytes, so an oversized file is detected without loading it all. */
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
        const val BUNDLED_PACK_ASSET = "content/n3sensei-illustration.json"
        private const val TAG = "ContentImport"
        private const val MAX_BYTES = 10 * 1024 * 1024
    }
}

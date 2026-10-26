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
    private val bundledAssetPaths: List<String> = BUNDLED_PACK_ASSETS,
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

    override suspend fun installBundledContent(): List<ImportReport> {
        if (!bundledContentEnabled) return emptyList()
        // Each pack on its own: one that fails does not stop the others.
        return bundledAssetPaths.map { path ->
            val text = withContext(Dispatchers.IO) {
                try {
                    context.assets.open(path).use { it.readBytes().toString(Charsets.UTF_8) }
                } catch (e: IOException) {
                    null // missing or unreadable asset: reported, never a crash
                }
            }
            val report = if (text == null) ContentPackImporter.failed(IssueCode.READ_ERROR, path) else importer.import(text, onlyIfNewer = true)
            report.also { if (it.outcome == ImportOutcome.FAILED) Log.e(TAG, "Bundled content $path failed to install: ${it.issues}") }
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
        const val MIMIKARA_PACK_ASSET = "content/mimikara-n3-vocab.json"
        const val N3_GRAMMAR_PACK_ASSET = "content/n3-grammar.json"
        const val N3_KANJI_PACK_ASSET = "content/n3-kanji.json"

        /** Packs shipped with the app, installed in this order (see docs/CONTENT_FORMAT.md). */
        val BUNDLED_PACK_ASSETS = listOf(BUNDLED_PACK_ASSET, MIMIKARA_PACK_ASSET, N3_GRAMMAR_PACK_ASSET, N3_KANJI_PACK_ASSET)
        private const val TAG = "ContentImport"
        private const val MAX_BYTES = 10 * 1024 * 1024
    }
}

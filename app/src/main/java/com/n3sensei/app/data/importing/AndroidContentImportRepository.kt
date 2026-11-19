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
    /** Contrast Lab packs (M4); installed only after every source pack above installed without failure. */
    private val contrastImporter: ContrastContentImporter? = null,
    private val contrastAssetPaths: List<String> = CONTRAST_BUNDLED_PACK_ASSETS,
    /**
     * Debug-build preview pack of unreviewed drafts ([ContrastPreviewAssets]; empty in release). Used
     * only while [contrastAssetPaths] (the reviewed packs) is empty.
     */
    private val contrastPreviewPaths: List<String> = emptyList(),
    private val readContrastAsset: (String) -> String? = { path -> context.assets.open(path).use { it.readBytes().toString(Charsets.UTF_8) } },
) : ContentImportRepository {

    /**
     * Results of the last Contrast installation, kept apart from [installBundledContent]'s list so a
     * Contrast problem never shows up as (or blocks) a failure of the learning content. There is no
     * Contrast UI yet (M5): failures are logged and retried at the next start (nothing was written).
     */
    @Volatile
    var lastContrastReports: List<ContrastImportReport> = emptyList()
        private set

    /** Switched off by instrumented tests that need a database with only their own data. */
    @Volatile
    internal var bundledContentEnabled: Boolean = true

    /** Switched off by instrumented tests that bring their own Contrast pack (debug builds only matter). */
    @Volatile
    internal var contrastPreviewEnabled: Boolean = true

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
        }.also { reports -> installContrast(sourcesReady = reports.none { it.outcome == ImportOutcome.FAILED }) }
    }

    /**
     * Contrast packs reference the grammar / vocabulary / kanji rows, so they are installed after
     * the source packs and only when none of those failed (otherwise their sources would be judged
     * against incomplete content; they are retried at the next start). Never throws: a Contrast
     * failure leaves the installed Contrast content and every learning feature as they were.
     */
    private suspend fun installContrast(sourcesReady: Boolean) {
        val contrast = contrastImporter ?: return
        // A reviewed pack always wins; the debug preview is only a stand-in while none is shipped.
        val paths = contrastAssetPaths.ifEmpty { if (contrastPreviewEnabled) contrastPreviewPaths else emptyList() }
        if (paths.isEmpty()) return
        if (!sourcesReady) {
            Log.w(TAG, "Contrast packs not installed: a source pack failed")
            lastContrastReports = emptyList()
            return
        }
        lastContrastReports = paths.map { path ->
            val text = withContext(Dispatchers.IO) {
                try {
                    readContrastAsset(path)
                } catch (e: IOException) {
                    null
                }
            }
            val report = if (text == null) {
                ContrastImportReport(ContrastImportReport.Outcome.FAILED, null, null, null, emptyList(), ContrastImportReport.Failure.INVALID, databaseError = "unreadable asset $path")
            } else {
                try {
                    contrast.import(text)
                } catch (e: Exception) {
                    ContrastImportReport(ContrastImportReport.Outcome.FAILED, null, null, null, emptyList(), ContrastImportReport.Failure.DATABASE, databaseError = e.toString())
                }
            }
            report.also { if (it.outcome == ContrastImportReport.Outcome.FAILED) Log.e(TAG, "Contrast pack $path not installed: ${ContrastContentImporter.summary(it)}") }
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

        /** Kanji N2, built from the user's Anki deck by tools/content/n2_kanji (see docs/N2_KANJI.md). */
        const val N2_KANJI_PACK_ASSET = "content/n2-kanji.json"

        /** Packs shipped with the app, installed in this order (see docs/CONTENT_FORMAT.md). */
        val BUNDLED_PACK_ASSETS = listOf(BUNDLED_PACK_ASSET, MIMIKARA_PACK_ASSET, N3_GRAMMAR_PACK_ASSET, N3_KANJI_PACK_ASSET, N2_KANJI_PACK_ASSET)

        /**
         * Contrast Lab packs shipped with the app (installed after the packs above). EMPTY on purpose:
         * only a pack built by tools/content/contrast/build_pack.py from drafts approved by a registered
         * reviewer may be listed here; docs/contrast/contrast-pack.sample.json is not reviewed and is not shipped.
         */
        val CONTRAST_BUNDLED_PACK_ASSETS: List<String> = emptyList()
        private const val TAG = "ContentImport"
        private const val MAX_BYTES = 10 * 1024 * 1024
    }
}

package com.n3sensei.app.data.translate

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.n3sensei.app.domain.translate.Language
import com.n3sensei.app.domain.translate.MachineTranslator
import com.n3sensei.app.domain.translate.ModelDownloadException
import com.n3sensei.app.domain.translate.TranslationFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.tasks.await

/**
 * Google ML Kit on-device translation. The text is translated on the device; the network is
 * used only to download a language model (once, about 35–48 MB each), from Google.
 *
 * - ML Kit's models pair English with another language, so Vietnamese ↔ Japanese goes
 *   through English inside ML Kit (lower quality than a direct model).
 * - Downloads are shared: asking twice for the same models waits for the one download. A
 *   cancelled caller stops waiting; the download itself goes on (ML Kit tasks cannot be
 *   cancelled) and is there next time.
 * - Translators are kept per language pair until [release]. Translations run one at a time
 *   (ML Kit's native code is not safe to load for two pairs at once).
 * - ML Kit gives no download progress, readings or pronunciations.
 */
class MlKitMachineTranslator(private val context: Context) : MachineTranslator {

    private val modelManager by lazy { RemoteModelManager.getInstance() }
    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloads = mutableMapOf<Pair<Language, Language>, Deferred<Unit>>()
    private val translators = mutableMapOf<Pair<Language, Language>, Translator>()
    private val nativeLock = Mutex()

    override suspend fun missingModels(from: Language, to: Language): Set<Language> =
        setOf(from, to).filterTo(mutableSetOf()) { language ->
            !modelManager.isModelDownloaded(TranslateRemoteModel.Builder(code(language)).build()).await()
        }

    override suspend fun downloadModels(from: Language, to: Language) {
        val missing = missingModels(from, to)
        if (missing.isEmpty()) return
        if (!isOnline()) throw ModelDownloadException(TranslationFailure.OFFLINE)
        // Through the model manager, not a translator: closing translators (a dialog closing)
        // never interrupts a download, which goes on and is there next time.
        val download = synchronized(downloads) {
            downloads[from to to]?.takeIf { it.isActive } ?: downloadScope.async {
                missing.forEach { language ->
                    modelManager.download(TranslateRemoteModel.Builder(code(language)).build(), DownloadConditions.Builder().build()).await()
                }
            }.also { downloads[from to to] = it }
        }
        try {
            download.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ModelDownloadException(if (isOnline()) TranslationFailure.MODEL_DOWNLOAD_FAILED else TranslationFailure.OFFLINE, e)
        }
    }

    /**
     * One ML Kit translation at a time. Two language pairs loading at once abort the process in
     * ML Kit's native code (seen on the emulator: "init_google.cc Check failed"), so the lock is
     * held until ML Kit's task ends, not only until a cancelled caller stops waiting.
     */
    override suspend fun translate(text: String, from: Language, to: Language): String {
        nativeLock.lock()
        val task = try {
            translator(from, to).translate(text)
        } catch (e: Throwable) {
            nativeLock.unlock()
            throw e
        }
        task.addOnCompleteListener { nativeLock.unlock() }
        return task.await()
    }

    /** Download sizes of ML Kit 17.0.3 (translate_models_metadata.json): en–ja 48 MB, en–vi 35.5 MB. */
    override fun approximateDownloadMb(languages: Set<Language>): Int? =
        languages.sumOf { DOWNLOAD_MB[it] ?: return null }.takeIf { it > 0 }

    override fun release() {
        synchronized(translators) {
            translators.values.forEach { it.close() }
            translators.clear()
        }
    }

    private fun translator(from: Language, to: Language): Translator = synchronized(translators) {
        translators.getOrPut(from to to) {
            Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(code(from)).setTargetLanguage(code(to)).build())
        }
    }

    private fun isOnline(): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private companion object {
        /** English is part of every model; its own download is counted with the other language. */
        val DOWNLOAD_MB = mapOf(Language.JAPANESE to 48, Language.VIETNAMESE to 36, Language.ENGLISH to 0)
    }

    private fun code(language: Language): String = when (language) {
        Language.VIETNAMESE -> TranslateLanguage.VIETNAMESE
        Language.ENGLISH -> TranslateLanguage.ENGLISH
        Language.JAPANESE -> TranslateLanguage.JAPANESE
    }
}

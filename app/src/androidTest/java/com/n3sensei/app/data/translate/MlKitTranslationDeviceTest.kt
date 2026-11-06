package com.n3sensei.app.data.translate

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.n3sensei.app.domain.translate.FieldState
import com.n3sensei.app.domain.translate.Language
import com.n3sensei.app.domain.translate.Provider
import com.n3sensei.app.domain.translate.TranslationFailure
import com.n3sensei.app.domain.translate.TranslationMode
import com.n3sensei.app.domain.translate.TranslationOrchestrator
import com.n3sensei.app.domain.translate.TranslationRequest
import com.n3sensei.app.testing.AppTestState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real ML Kit on the device: model downloads (about 84 MB for Vietnamese and Japanese), on-device
 * translation, and offline use. Run on request only, on an emulator with network:
 * `-Pandroid.testInstrumentationRunnerArguments.mlkit=true`.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class MlKitTranslationDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var machine: MlKitMachineTranslator
    private lateinit var engine: TranslationOrchestrator

    private fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).close()

    private fun network(on: Boolean) {
        val state = if (on) "enable" else "disable"
        shell("svc wifi $state")
        shell("svc data $state")
        Thread.sleep(3_000)
    }

    @Before
    fun setUp() {
        assumeTrue("Run with -Pandroid.testInstrumentationRunnerArguments.mlkit=true", InstrumentationRegistry.getArguments().getString("mlkit") == "true")
        AppTestState.reset(seedContent = true)
        machine = MlKitMachineTranslator(context)
        engine = TranslationOrchestrator(ContentDictionaryProvider(AppTestState.appContainer.database.contentDao()), machine)
    }

    @After
    fun tearDown() {
        if (::machine.isInitialized) machine.release()
        network(true)
    }

    private fun <T> FieldState<T>.values() = valuesOrEmpty.map { it.value }

    @Test
    fun downloadsOnce_translatesOnTheDevice_andWorksOffline() = runBlocking {
        val models = RemoteModelManager.getInstance()
        // Start without the Vietnamese and Japanese models.
        listOf(TranslateLanguage.VIETNAMESE, TranslateLanguage.JAPANESE).forEach {
            models.deleteDownloadedModel(TranslateRemoteModel.Builder(it).build()).await()
        }
        network(true)
        val hello = TranslationRequest("Good morning", Language.ENGLISH, TranslationMode.BOTH)
        val waiting = engine.translate(hello)
        assertEquals(setOf(Language.VIETNAMESE, Language.JAPANESE), waiting.missingModels)

        // Offline and no model: said so, nothing invented.
        network(false)
        val offline = engine.translate(hello, allowModelDownload = true)
        assertEquals(FieldState.Failed(TranslationFailure.OFFLINE), offline.vietnameseMeaning)

        network(true)
        val done = engine.translate(hello, allowModelDownload = true)
        assertTrue(done.vietnameseMeaning.values().single().isNotBlank())
        assertTrue(done.japaneseTranslation.values().single().text.isNotBlank())
        assertEquals(setOf(Provider.MACHINE_TRANSLATION), done.providers)

        // The models stay: offline translation in every direction.
        network(false)
        val ja = engine.translate(TranslationRequest("ありがとうございます", Language.JAPANESE, TranslationMode.BOTH))
        assertTrue(ja.vietnameseMeaning is FieldState.Available)
        assertTrue(ja.englishTranslation is FieldState.Available)
        val vi = engine.translate(TranslationRequest("Hôm nay trời đẹp.", Language.VIETNAMESE, TranslationMode.BOTH))
        assertTrue(vi.englishTranslation is FieldState.Available)
        assertTrue(vi.japaneseTranslation is FieldState.Available)
        // A word of the content: the dictionary answers first, reading included.
        val keiken = engine.translate(TranslationRequest("経験", Language.JAPANESE, TranslationMode.BOTH))
        assertEquals(listOf("kinh nghiệm"), keiken.vietnameseMeaning.values())
        assertEquals(listOf("けいけん"), keiken.japaneseReading.values())
        assertTrue(keiken.englishTranslation is FieldState.Available)
        // Downloading again is a no-op.
        machine.downloadModels(Language.VIETNAMESE, Language.JAPANESE)
    }

    /**
     * Found in the Chrome check: two language pairs loading at once (the orchestrator asks for
     * Vietnamese and Japanese together) aborted the process inside ML Kit's native code
     * ("init_google.cc Check failed"). Only the first load in a process can hit it: run this
     * test on its own (`-e class ...#firstUseOfSeveralPairsAtOnce_doesNotCrash`).
     */
    @Test
    fun firstUseOfSeveralPairsAtOnce_doesNotCrash() = runBlocking {
        network(true)
        machine.downloadModels(Language.ENGLISH, Language.VIETNAMESE)
        machine.downloadModels(Language.ENGLISH, Language.JAPANESE)
        val pairs = listOf(
            Language.ENGLISH to Language.VIETNAMESE,
            Language.ENGLISH to Language.JAPANESE,
            Language.VIETNAMESE to Language.JAPANESE,
            Language.JAPANESE to Language.ENGLISH,
        )
        val texts = pairs.map { (from, to) ->
            async(Dispatchers.Default) { machine.translate(if (from == Language.JAPANESE) "おはよう" else if (from == Language.VIETNAMESE) "Chào buổi sáng" else "Good morning", from, to) }
        }.awaitAll()
        assertTrue(texts.toString(), texts.all { it.isNotBlank() })
    }
}

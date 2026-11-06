package com.n3sensei.app.domain.translate

import com.n3sensei.app.domain.translate.Language.ENGLISH
import com.n3sensei.app.domain.translate.Language.JAPANESE
import com.n3sensei.app.domain.translate.Language.VIETNAMESE
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationOrchestratorTest {

    // --- Fakes: no network, no device. ---

    private class FakeDictionary : DictionaryProvider {
        val japanese = mutableMapOf<String, List<DictionaryEntry>>()
        val vietnamese = mutableMapOf<String, List<DictionaryEntry>>()
        var version = "v1"
        override suspend fun lookupJapanese(text: String) = japanese[text].orEmpty()
        override suspend fun lookupVietnamese(text: String) = vietnamese[TranslationOrchestrator.comparable(text)].orEmpty()
        override suspend fun lookupEnglish(text: String) = emptyList<DictionaryEntry>()
        override suspend fun version() = version
    }

    private class FakeMachine : MachineTranslator {
        val downloaded = mutableSetOf(ENGLISH)
        val translations = mutableMapOf<Triple<String, Language, Language>, String>()
        var online = true
        var downloads = 0
        val calls = mutableListOf<Triple<String, Language, Language>>()
        var gate: CompletableDeferred<Unit>? = null
        val downloadsStarted = mutableListOf<Pair<Language, Language>>()
        var downloadGate: CompletableDeferred<Unit>? = null

        override suspend fun missingModels(from: Language, to: Language) = setOf(from, to) - downloaded
        override suspend fun downloadModels(from: Language, to: Language) {
            if (!online) throw ModelDownloadException(TranslationFailure.OFFLINE)
            downloadsStarted += from to to
            downloadGate?.await()
            downloads++
            downloaded += setOf(from, to)
        }
        override suspend fun translate(text: String, from: Language, to: Language): String {
            check(missingModels(from, to).isEmpty()) { "model missing" }
            gate?.await()
            calls += Triple(text, from, to)
            return translations[Triple(text, from, to)] ?: error("no translation")
        }
        override fun approximateDownloadMb(languages: Set<Language>): Int? = null
        override fun release() = Unit
    }

    private val dictionary = FakeDictionary()
    private val machine = FakeMachine()
    private val engine = TranslationOrchestrator(dictionary, machine)

    private val keiken = DictionaryEntry(
        japanese = "経験", reading = "けいけん", meaningVi = "kinh nghiệm",
        notes = listOf("danh từ"), examples = listOf(TranslationExample("日本で働いた経験があります。", null, "Tôi có kinh nghiệm làm việc ở Nhật.")),
        sourceName = "Gói A",
    )

    private fun request(text: String, source: Language, mode: TranslationMode) = TranslationRequest(text, source, mode)

    private fun <T> FieldState<T>.values() = valuesOrEmpty.map { it.value }
    private fun <T> FieldState<T>.providers() = valuesOrEmpty.map { it.provider }.toSet()

    private fun allModels() = machine.downloaded.addAll(Language.entries)

    @Test
    fun japaneseWord_inTheDictionary_givesMeaningReadingExamplesAndNotes_withoutMachineTranslation() = runTest {
        dictionary.japanese["経験"] = listOf(keiken)
        val r = engine.translate(request("経験", JAPANESE, TranslationMode.JAPANESE))
        assertEquals(listOf("kinh nghiệm"), r.vietnameseMeaning.values())
        assertEquals(setOf(Provider.CONTENT_DICTIONARY), r.vietnameseMeaning.providers())
        assertEquals(listOf("けいけん"), r.japaneseReading.values())
        assertEquals(1, r.examples.values().size)
        assertEquals(listOf("danh từ"), r.usageNotes.values())
        // Same language as the source: not translated into itself.
        assertEquals(FieldState.NotRequested, r.japaneseTranslation)
        assertEquals(FieldState.NotRequested, r.englishTranslation)
        assertTrue(machine.calls.isEmpty())
    }

    @Test
    fun allSixDirections_withEachMode_askForTheRightLanguages() = runTest {
        allModels()
        listOf(
            Triple("hello", ENGLISH, VIETNAMESE) to "xin chào", Triple("hello", ENGLISH, JAPANESE) to "こんにちは",
            Triple("こんにちは", JAPANESE, VIETNAMESE) to "xin chào", Triple("こんにちは", JAPANESE, ENGLISH) to "hello",
            Triple("xin chào", VIETNAMESE, ENGLISH) to "hello", Triple("xin chào", VIETNAMESE, JAPANESE) to "こんにちは",
        ).forEach { (k, v) -> machine.translations[k] = v }

        // Vietnamese → English / Japanese / both.
        with(engine.translate(request("xin chào", VIETNAMESE, TranslationMode.ENGLISH))) {
            assertEquals(listOf("hello"), englishTranslation.values())
            assertEquals(FieldState.NotRequested, japaneseTranslation)
            assertEquals(FieldState.NotRequested, vietnameseMeaning)
        }
        with(engine.translate(request("xin chào", VIETNAMESE, TranslationMode.BOTH))) {
            assertEquals(listOf("hello"), englishTranslation.values())
            assertEquals(listOf(JapaneseText("こんにちは", null)), japaneseTranslation.values())
        }
        // English → always Vietnamese; Japanese only when asked.
        with(engine.translate(request("hello", ENGLISH, TranslationMode.ENGLISH))) {
            assertEquals(listOf("xin chào"), vietnameseMeaning.values())
            assertEquals(FieldState.NotRequested, englishTranslation)
            assertEquals(FieldState.NotRequested, japaneseTranslation)
        }
        with(engine.translate(request("hello", ENGLISH, TranslationMode.JAPANESE))) {
            assertEquals(listOf("xin chào"), vietnameseMeaning.values())
            assertEquals(listOf("こんにちは"), japaneseTranslation.values().map { it.text })
        }
        // Japanese → always Vietnamese; English only when asked.
        with(engine.translate(request("こんにちは", JAPANESE, TranslationMode.JAPANESE))) {
            assertEquals(listOf("xin chào"), vietnameseMeaning.values())
            assertEquals(FieldState.NotRequested, englishTranslation)
        }
        with(engine.translate(request("こんにちは", JAPANESE, TranslationMode.BOTH))) {
            assertEquals(listOf("xin chào"), vietnameseMeaning.values())
            assertEquals(listOf("hello"), englishTranslation.values())
            assertEquals(setOf(Provider.MACHINE_TRANSLATION), englishTranslation.providers())
        }
    }

    @Test
    fun vietnameseWord_inTheDictionary_givesTheJapaneseWordAndItsReading_andMachineEnglish() = runTest {
        allModels()
        dictionary.vietnamese["kinh nghiệm"] = listOf(keiken)
        machine.translations[Triple("Kinh nghiệm", VIETNAMESE, ENGLISH)] = "experience"
        val r = engine.translate(request("Kinh nghiệm", VIETNAMESE, TranslationMode.BOTH))
        assertEquals(listOf(JapaneseText("経験", "けいけん")), r.japaneseTranslation.values())
        assertEquals(setOf(Provider.CONTENT_DICTIONARY), r.japaneseTranslation.providers())
        assertEquals(listOf("けいけん"), r.japaneseReading.values())
        assertEquals(listOf("experience"), r.englishTranslation.values())
        // The dictionary's Japanese is never replaced by machine translation.
        assertTrue(machine.calls.none { it.third == JAPANESE })
    }

    @Test
    fun machineJapanese_getsAReadingOnlyFromTheDictionarysSameWord() = runTest {
        allModels()
        dictionary.japanese["経験"] = listOf(keiken)
        machine.translations[Triple("experience", ENGLISH, JAPANESE)] = "経験"
        machine.translations[Triple("experience", ENGLISH, VIETNAMESE)] = "kinh nghiệm"
        machine.translations[Triple("hard work", ENGLISH, JAPANESE)] = "努力"
        machine.translations[Triple("hard work", ENGLISH, VIETNAMESE)] = "nỗ lực"
        val known = engine.translate(request("experience", ENGLISH, TranslationMode.BOTH))
        assertEquals(listOf(JapaneseText("経験", "けいけん")), known.japaneseTranslation.values())
        assertEquals(listOf("けいけん"), known.japaneseReading.values())
        // No dictionary word: no reading, said plainly.
        val unknown = engine.translate(request("hard work", ENGLISH, TranslationMode.BOTH))
        assertEquals(listOf(JapaneseText("努力", null)), unknown.japaneseTranslation.values())
        assertEquals(FieldState.Unavailable, unknown.japaneseReading)
    }

    @Test
    fun noPronunciationNoRomajiNoExamples_areUnavailable_neverMadeUp() = runTest {
        allModels()
        machine.translations[Triple("It is raining today.", ENGLISH, VIETNAMESE)] = "Hôm nay trời mưa."
        machine.translations[Triple("It is raining today.", ENGLISH, JAPANESE)] = "今日は雨が降っています。"
        val r = engine.translate(request("It is raining today.", ENGLISH, TranslationMode.BOTH))
        assertEquals(FieldState.Unavailable, r.englishPronunciation)
        assertEquals(FieldState.Unavailable, r.japaneseRomaji)
        assertEquals(FieldState.Unavailable, r.examples)
        assertEquals(FieldState.Unavailable, r.usageNotes)
        assertEquals(listOf("Hôm nay trời mưa."), r.vietnameseMeaning.values())
    }

    @Test
    fun aWordWithSeveralSenses_keepsEachSense() = runTest {
        dictionary.japanese["しっかり"] = listOf(
            DictionaryEntry("しっかり", "しっかり", "toàn bộ"),
            DictionaryEntry("しっかり", "しっかり", "kiên quyết, chắc chắn, đáng tin cậy"),
        )
        val r = engine.translate(request("しっかり", JAPANESE, TranslationMode.JAPANESE))
        assertEquals(listOf("toàn bộ", "kiên quyết, chắc chắn, đáng tin cậy"), r.vietnameseMeaning.values())
        assertEquals(listOf("しっかり"), r.japaneseReading.values())
    }

    @Test
    fun missingModel_isReported_andNotDownloadedUnlessAllowed() = runTest {
        machine.translations[Triple("hello", ENGLISH, VIETNAMESE)] = "xin chào"
        val waiting = engine.translate(request("hello", ENGLISH, TranslationMode.ENGLISH))
        assertEquals(FieldState.NeedsModel(setOf(VIETNAMESE)), waiting.vietnameseMeaning)
        assertEquals(setOf(VIETNAMESE), waiting.missingModels)
        assertEquals(0, machine.downloads)

        val done = engine.translate(request("hello", ENGLISH, TranslationMode.ENGLISH), allowModelDownload = true)
        assertEquals(listOf("xin chào"), done.vietnameseMeaning.values())
        assertEquals(1, machine.downloads)
        // Offline afterwards: the model is on the device, translation still works.
        machine.online = false
        machine.translations[Triple("thank you", ENGLISH, VIETNAMESE)] = "cảm ơn"
        assertEquals(listOf("cảm ơn"), engine.translate(request("thank you", ENGLISH, TranslationMode.ENGLISH)).vietnameseMeaning.values())
    }

    @Test
    fun allowedDownloads_forEveryTarget_startTogether() = runTest {
        // Closing the dialog cancels the request: a download still waiting its turn behind
        // another one would never start (found on the emulator: Vietnamese came, Japanese not).
        machine.translations[Triple("hello", ENGLISH, VIETNAMESE)] = "xin chào"
        machine.translations[Triple("hello", ENGLISH, JAPANESE)] = "こんにちは"
        machine.downloadGate = CompletableDeferred()
        val job = async { engine.translate(request("hello", ENGLISH, TranslationMode.BOTH), allowModelDownload = true) }
        testScheduler.runCurrent()
        assertEquals(setOf(ENGLISH to VIETNAMESE, ENGLISH to JAPANESE), machine.downloadsStarted.toSet())
        machine.downloadGate!!.complete(Unit)
        val r = job.await()
        assertEquals(listOf("xin chào"), r.vietnameseMeaning.values())
        assertEquals(listOf("こんにちは"), r.japaneseTranslation.values().map { it.text })
    }

    @Test
    fun offline_withoutTheModel_saysOffline() = runTest {
        machine.online = false
        val r = engine.translate(request("hello", ENGLISH, TranslationMode.ENGLISH), allowModelDownload = true)
        assertEquals(FieldState.Failed(TranslationFailure.OFFLINE), r.vietnameseMeaning)
    }

    @Test
    fun aTranslatorError_isAFailure_notAnEmptyTranslation() = runTest {
        allModels()
        machine.translations[Triple("x", ENGLISH, VIETNAMESE)] = "   "
        assertEquals(FieldState.Failed(TranslationFailure.TRANSLATION_FAILED), engine.translate(request("x", ENGLISH, TranslationMode.ENGLISH)).vietnameseMeaning)
        // No translation known by the fake: the translator throws.
        assertEquals(FieldState.Failed(TranslationFailure.TRANSLATION_FAILED), engine.translate(request("y", ENGLISH, TranslationMode.ENGLISH)).vietnameseMeaning)
    }

    @Test
    fun aWrongSourceLanguage_findsNoDictionaryEntry_andIsTranslatedAsGiven() = runTest {
        allModels()
        dictionary.japanese["経験"] = listOf(keiken)
        machine.translations[Triple("経験", VIETNAMESE, ENGLISH)] = "experience"
        // 経験 sent as Vietnamese: the Japanese dictionary is not used, nothing is mixed up.
        val r = engine.translate(request("経験", VIETNAMESE, TranslationMode.ENGLISH))
        assertEquals(listOf("experience"), r.englishTranslation.values())
        assertEquals(FieldState.Unavailable, r.examples)
    }

    @Test
    fun results_areCached_perTextLanguagesAndVersion_onlyWhenComplete() = runTest {
        allModels()
        machine.translations[Triple("hello", ENGLISH, VIETNAMESE)] = "xin chào"
        machine.translations[Triple("hello", ENGLISH, JAPANESE)] = "こんにちは"
        engine.translate(request("hello", ENGLISH, TranslationMode.ENGLISH))
        engine.translate(request("  hello ", ENGLISH, TranslationMode.ENGLISH))
        assertEquals(1, machine.calls.size)
        // Other targets: not the cached result.
        val both = engine.translate(request("hello", ENGLISH, TranslationMode.BOTH))
        assertEquals(listOf("こんにちは"), both.japaneseTranslation.values().map { it.text })
        // New dictionary content: looked up again.
        dictionary.version = "v2"
        engine.translate(request("hello", ENGLISH, TranslationMode.ENGLISH))
        assertEquals(4, machine.calls.size)

        // A result waiting for a model is never cached.
        val fresh = FakeMachine()
        val other = TranslationOrchestrator(dictionary, fresh)
        fresh.translations[Triple("hello", ENGLISH, VIETNAMESE)] = "xin chào"
        assertTrue(other.translate(request("hello", ENGLISH, TranslationMode.ENGLISH)).vietnameseMeaning is FieldState.NeedsModel)
        fresh.downloaded += VIETNAMESE
        assertEquals(listOf("xin chào"), other.translate(request("hello", ENGLISH, TranslationMode.ENGLISH)).vietnameseMeaning.values())
    }

    @Test
    fun cancelling_stopsTheRequest_andCachesNothing() = runTest {
        allModels()
        machine.translations[Triple("hello", ENGLISH, VIETNAMESE)] = "xin chào"
        machine.gate = CompletableDeferred()
        val job = async { engine.translate(request("hello", ENGLISH, TranslationMode.ENGLISH)) }
        yield()
        job.cancel()
        assertTrue(job.isCancelled)
        machine.gate = null
        engine.translate(request("hello", ENGLISH, TranslationMode.ENGLISH))
        assertEquals(1, machine.calls.size)
    }
}

package com.n3sensei.app.feature.translate

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import com.n3sensei.app.domain.edit.EditableField
import com.n3sensei.app.domain.edit.ExampleDraft
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.UserPreferences
import com.n3sensei.app.domain.repository.UserContentRepository
import com.n3sensei.app.domain.translate.BookmarkEntry
import com.n3sensei.app.domain.translate.BookmarkResult
import com.n3sensei.app.domain.translate.BookmarkTranslation
import com.n3sensei.app.domain.translate.FieldState
import com.n3sensei.app.domain.translate.Language
import com.n3sensei.app.domain.translate.Provider
import com.n3sensei.app.domain.translate.QuickTranslateSettings
import com.n3sensei.app.domain.translate.ScriptLanguageDetector
import com.n3sensei.app.domain.translate.SelectedText
import com.n3sensei.app.domain.translate.Sourced
import com.n3sensei.app.domain.translate.Speaker
import com.n3sensei.app.domain.translate.SpeechState
import com.n3sensei.app.domain.translate.TranslationFailure
import com.n3sensei.app.domain.translate.TranslationMode
import com.n3sensei.app.domain.translate.TranslationRepository
import com.n3sensei.app.domain.translate.TranslationRequest
import com.n3sensei.app.domain.translate.TranslationResult
import com.n3sensei.app.domain.translate.Utterance
import com.n3sensei.app.domain.translate.VoiceAvailability
import com.n3sensei.app.testing.FakeUserPreferencesRepository
import com.n3sensei.app.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class QuickTranslateViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    /** Answers from [answer]; a request can be held until [release] (to test ordering and cancelling). */
    private class FakeTranslations : TranslationRepository {
        val requests = mutableListOf<Pair<TranslationRequest, Boolean>>()
        val gates = mutableMapOf<String, CompletableDeferred<Unit>>()
        var cancelled = 0
        var released = 0
        var answer: (TranslationRequest, Boolean) -> TranslationResult = { r, _ -> result(r, vi = FieldState.Available(listOf(Sourced("nghĩa", Provider.MACHINE_TRANSLATION)))) }

        override suspend fun translate(request: TranslationRequest, allowModelDownload: Boolean): TranslationResult {
            requests += request to allowModelDownload
            try {
                gates[request.originalText]?.await()
            } catch (e: kotlinx.coroutines.CancellationException) {
                cancelled++
                throw e
            }
            return answer(request, allowModelDownload)
        }

        override fun approximateDownloadMb(languages: Set<Language>) = 48
        override fun release() {
            released++
        }
    }

    private companion object {
        fun result(
            r: TranslationRequest,
            vi: FieldState<String> = FieldState.NotRequested,
            en: FieldState<String> = FieldState.Unavailable,
        ) = TranslationResult(
            originalText = r.originalText, sourceLanguage = r.sourceLanguage,
            vietnameseMeaning = if (Language.VIETNAMESE in r.requestedTargets) vi else FieldState.NotRequested,
            englishTranslation = if (Language.ENGLISH in r.requestedTargets) en else FieldState.NotRequested,
            englishPronunciation = FieldState.Unavailable,
            japaneseTranslation = if (Language.JAPANESE in r.requestedTargets) FieldState.Unavailable else FieldState.NotRequested,
            japaneseReading = FieldState.Unavailable, japaneseRomaji = FieldState.Unavailable,
            examples = FieldState.Unavailable, usageNotes = FieldState.Unavailable,
        )
    }

    /** Records what is read; every voice ready. */
    private class FakeSpeaker : Speaker {
        override val state = MutableStateFlow(SpeechState(voices = Language.entries.associateWith { VoiceAvailability.AVAILABLE }))
        val spoken = mutableListOf<Utterance>()
        var stops = 0
        var shutdowns = 0
        override fun speak(utterance: Utterance): Boolean {
            spoken += utterance
            state.value = state.value.copy(speaking = utterance)
            return true
        }
        override fun stop() {
            stops++
            state.value = state.value.copy(speaking = null)
        }
        override fun shutdown() {
            shutdowns++
        }
    }

    private val speaker = FakeSpeaker()

    /** Saves bookmarks in memory by identity; everything else is unused here. */
    private class FakeUserContent : UserContentRepository {
        val saved = MutableStateFlow<Map<Pair<Language, String>, String>>(emptyMap())
        val entries = mutableListOf<BookmarkEntry>()
        var fail = false
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun saveBookmark(entry: BookmarkEntry): BookmarkResult {
            gate?.await()
            if (fail) return BookmarkResult.Failed
            entries += entry
            val key = entry.sourceLanguage to entry.key
            val existing = saved.value[key]
            saved.value = saved.value + (key to (existing ?: "user-w-${saved.value.size + 1}"))
            return BookmarkResult.Saved(saved.value.getValue(key), existing == null)
        }
        override fun observeBookmark(sourceLanguage: Language, key: String) = saved.map { it[sourceLanguage to key] }
        override suspend fun load(type: ItemType, id: String) = error("unused")
        override suspend fun save(type: ItemType, id: String, values: Map<EditableField, String>, linkedKanji: Set<String>?, allowDuplicate: Boolean) = error("unused")
        override suspend fun revert(type: ItemType, id: String) = error("unused")
        override suspend fun addWord(values: Map<EditableField, String>, linkedKanji: Set<String>, allowDuplicate: Boolean) = error("unused")
        override suspend fun deleteWord(id: String) = error("unused")
        override suspend fun kanjiChoices(text: String) = error("unused")
        override suspend fun kanjiExamples(kanjiId: String) = error("unused")
        override suspend fun saveKanjiExample(kanjiId: String, exampleId: String?, draft: ExampleDraft) = error("unused")
        override suspend fun deleteKanjiExample(exampleId: String) = error("unused")
        override suspend fun countOrphans() = error("unused")
    }

    private val userContent = FakeUserContent()

    private val prefs = FakeUserPreferencesRepository(UserPreferences(quickTranslate = QuickTranslateSettings(mode = TranslationMode.ENGLISH)))
    private val translations = FakeTranslations()
    private val store = ViewModelStore()
    private val vm by lazy {
        ViewModelProvider.create(store, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: kotlin.reflect.KClass<T>, extras: CreationExtras): T =
                QuickTranslateViewModel(prefs, ScriptLanguageDetector(), translations, speaker, userContent) as T
        })[QuickTranslateViewModel::class]
    }
    private val state get() = vm.state.value!!

    @Test
    fun detectsTheSource_usesTheSettingsMode_andTranslates() = runTest {
        vm.show(SelectedText.Ready("経験があります", readOnly = true))
        assertEquals(Language.JAPANESE, state.source)
        assertEquals(TranslationMode.ENGLISH, state.mode)
        assertEquals(listOf(Language.VIETNAMESE, Language.ENGLISH), state.targets)
        val asked = translations.requests.single().first
        assertEquals(listOf(Language.VIETNAMESE, Language.ENGLISH), asked.requestedTargets)
        // English asked but not answered: partial.
        assertTrue(state.translation is TranslationUiState.PartialSuccess)
    }

    @Test
    fun everythingAnswered_isSuccess_nothing_isEmpty_failures_areErrors() = runTest {
        translations.answer = { r, _ -> result(r, vi = FieldState.Available(listOf(Sourced("chào", Provider.MACHINE_TRANSLATION))), en = FieldState.Available(listOf(Sourced("hi", Provider.MACHINE_TRANSLATION)))) }
        vm.show(SelectedText.Ready("こんにちは", readOnly = false))
        assertTrue(state.translation is TranslationUiState.Success)

        translations.answer = { r, _ -> result(r, vi = FieldState.Unavailable, en = FieldState.Unavailable) }
        vm.show(SelectedText.Ready("さようなら", readOnly = false))
        assertTrue(state.translation is TranslationUiState.Empty)

        translations.answer = { r, _ -> result(r, vi = FieldState.Failed(TranslationFailure.OFFLINE), en = FieldState.Failed(TranslationFailure.OFFLINE)) }
        vm.show(SelectedText.Ready("ありがとう", readOnly = false))
        assertEquals(TranslationUiState.Error(TranslationFailure.OFFLINE), state.translation)
    }

    @Test
    fun aMissingModel_waitsForTheLearner_thenDownloads() = runTest {
        translations.answer = { r, download ->
            if (download) result(r, vi = FieldState.Available(listOf(Sourced("xin chào", Provider.MACHINE_TRANSLATION))))
            else result(r, vi = FieldState.NeedsModel(setOf(Language.VIETNAMESE)))
        }
        vm.show(SelectedText.Ready("hello", readOnly = false))
        assertEquals(setOf(Language.VIETNAMESE), (state.translation as TranslationUiState.ModelRequired).languages)
        assertEquals(48, (state.translation as TranslationUiState.ModelRequired).approximateMb)
        // Never downloaded on its own.
        assertTrue(translations.requests.none { it.second })

        translations.gates["hello"] = CompletableDeferred()
        vm.downloadModels()
        // The first try (no download) waits at the gate: still loading.
        assertEquals(TranslationUiState.Loading, state.translation)
        translations.gates["hello"]!!.complete(Unit)
        assertTrue(state.translation is TranslationUiState.Success)
        assertTrue(translations.requests.last().second)
    }

    @Test
    fun anOfflineError_canBeRetried_andKeepsTheDownloadTheLearnerAllowed() = runTest {
        var online = false
        translations.answer = { r, download ->
            when {
                !download -> result(r, vi = FieldState.NeedsModel(setOf(Language.VIETNAMESE)))
                !online -> result(r, vi = FieldState.Failed(TranslationFailure.OFFLINE))
                else -> result(r, vi = FieldState.Available(listOf(Sourced("xin chào", Provider.MACHINE_TRANSLATION))))
            }
        }
        vm.show(SelectedText.Ready("hello", readOnly = false))
        vm.downloadModels()
        assertEquals(TranslationUiState.Error(TranslationFailure.OFFLINE), state.translation)

        online = true
        val before = translations.requests.size
        vm.retry()
        assertTrue(translations.requests.size > before)
        assertTrue(translations.requests.last().second)
        assertTrue(state.translation is TranslationUiState.Success)

        // A new text starts afresh: no download until the learner allows it again.
        vm.show(SelectedText.Ready("thanks", readOnly = false))
        assertTrue(state.translation is TranslationUiState.ModelRequired)
        assertTrue(!translations.requests.last().second)
    }

    @Test
    fun changingTheSourceOrMode_cancelsTheRunningRequest_andAnOldResultNeverWins() = runTest {
        translations.gates["kinh nghiem"] = CompletableDeferred()
        vm.show(SelectedText.Ready("kinh nghiem", readOnly = false))
        assertEquals(TranslationUiState.Loading, state.translation)
        vm.chooseSource(Language.ENGLISH)
        vm.chooseMode(TranslationMode.BOTH)
        assertTrue(translations.cancelled >= 1)
        // Every request after the first was for the newest choice; releasing the gate changes nothing.
        translations.gates["kinh nghiem"]!!.complete(Unit)
        assertEquals(Language.ENGLISH, translations.requests.last().first.sourceLanguage)
        assertEquals(listOf(Language.VIETNAMESE, Language.JAPANESE), translations.requests.last().first.requestedTargets)
        assertEquals(Language.ENGLISH, (state.translation as? TranslationUiState.PartialSuccess)?.result?.sourceLanguage)
        // The text is still the selected one.
        assertEquals(SelectedText.Ready("kinh nghiem", readOnly = false), state.selected)
    }

    @Test
    fun noLanguageOrTurnedOff_translatesNothing() = runTest {
        vm.show(SelectedText.Ready("123", readOnly = false))
        assertNull(state.source)
        assertEquals(TranslationUiState.Idle, state.translation)
        prefs.setQuickTranslateSettings(QuickTranslateSettings(enabled = false))
        vm.show(SelectedText.Ready("experience", readOnly = false))
        assertEquals(TranslationUiState.Idle, state.translation)
        assertTrue(translations.requests.isEmpty())
    }

    @Test
    fun choicesAreForThisDialog_detailsFoldForEachNewText() = runTest {
        val text = SelectedText.Ready("kinh nghiem", readOnly = false)
        vm.show(text)
        vm.chooseSource(Language.ENGLISH)
        vm.toggleDetails()
        assertTrue(state.detailsExpanded)
        assertEquals(QuickTranslateSettings(mode = TranslationMode.ENGLISH), prefs.preferences.value.quickTranslate)
        // The same text again (a rotation) keeps the choices; another text starts afresh.
        vm.show(text)
        assertEquals(Language.ENGLISH, state.chosenSource)
        vm.show(SelectedText.Ready("experience", readOnly = false))
        assertNull(state.chosenSource)
        assertEquals(false, state.detailsExpanded)
    }

    @Test
    fun speakers_readTheRightTextInTheRightLanguage_neverOnTheirOwn() = runTest {
        translations.answer = { r, _ ->
            result(r, vi = FieldState.Available(listOf(Sourced("kinh nghiệm", Provider.CONTENT_DICTIONARY))), en = FieldState.Available(listOf(Sourced("experience", Provider.MACHINE_TRANSLATION))))
                .copy(japaneseTranslation = FieldState.NotRequested)
        }
        vm.show(SelectedText.Ready("経験", readOnly = false))
        assertTrue(speaker.spoken.isEmpty()) // nothing read when the dialog opens
        assertEquals(Utterance("経験", Language.JAPANESE), state.sourceUtterance)
        val english = state.utteranceFor(Language.ENGLISH)!!
        assertEquals(Utterance("experience", Language.ENGLISH), english)
        // The popup's Vietnamese row is read with the Vietnamese voice.
        assertEquals(Utterance("kinh nghiệm", Language.VIETNAMESE), state.utteranceFor(Language.VIETNAMESE))
        vm.speak(english)
        assertEquals(listOf(english), speaker.spoken)
        // Tapping the same speaker again stops it.
        val stopsBefore = speaker.stops
        vm.speak(english)
        assertEquals(stopsBefore + 1, speaker.stops)
        assertNull(speaker.state.value.speaking)
    }

    @Test
    fun japaneseSpeaker_readsTheDictionaryKana_elseTheJapaneseText() = runTest {
        translations.answer = { r, _ ->
            result(r, vi = FieldState.NotRequested).copy(
                japaneseTranslation = FieldState.Available(listOf(Sourced(com.n3sensei.app.domain.translate.JapaneseText("経験", "けいけん"), Provider.CONTENT_DICTIONARY))),
            )
        }
        prefs.setQuickTranslateSettings(QuickTranslateSettings(mode = TranslationMode.JAPANESE))
        vm.show(SelectedText.Ready("kinh nghiệm", readOnly = false))
        assertEquals(Utterance("けいけん", Language.JAPANESE), state.utteranceFor(Language.JAPANESE))
    }

    @Test
    fun changingTextSourceOrMode_stopsReading() = runTest {
        vm.show(SelectedText.Ready("experience", readOnly = false))
        vm.speak(Utterance("experience", Language.ENGLISH))
        vm.chooseMode(TranslationMode.BOTH)
        assertNull(speaker.state.value.speaking)
        vm.speak(Utterance("experience", Language.ENGLISH))
        vm.chooseSource(Language.VIETNAMESE)
        assertNull(speaker.state.value.speaking)
        vm.speak(Utterance("experience", Language.ENGLISH))
        vm.show(SelectedText.Ready("other", readOnly = false))
        assertNull(speaker.state.value.speaking)
        vm.speak(Utterance("other", Language.ENGLISH))
        vm.stopSpeaking()
        assertNull(speaker.state.value.speaking)
    }

    @Test
    fun oneBookmark_savesTheWholeEntryOnce_andShowsSavedOnlyAfterTheWrite() = runTest {
        translations.answer = { r, _ ->
            result(r, vi = FieldState.Available(listOf(Sourced("kinh nghiệm", Provider.MACHINE_TRANSLATION))), en = FieldState.Available(listOf(Sourced("experience", Provider.MACHINE_TRANSLATION))))
        }
        vm.show(SelectedText.Ready("経験", readOnly = false))
        assertTrue(state.bookmark.available)
        assertEquals(false, state.bookmark.saved)

        userContent.gate = CompletableDeferred()
        vm.bookmark()
        vm.bookmark() // a second tap while saving does nothing
        assertTrue(state.bookmark.saving)
        assertEquals(false, state.bookmark.saved)
        userContent.gate!!.complete(Unit)
        assertTrue(state.bookmark.saved)
        assertEquals(1, userContent.entries.size)
        assertEquals(listOf(BookmarkTranslation(Language.ENGLISH, "experience", null)), userContent.entries.single().translations)

        // Saved: tapping again adds what is missing to the same entry, never a second one.
        vm.bookmark()
        assertEquals(1, userContent.saved.value.size)
        // Never studied by bookmarking: nothing was read or scheduled.
        assertTrue(speaker.spoken.isEmpty())
    }

    @Test
    fun closingTheDialogWhileSaving_stillSavesTheWholeEntry() = runTest {
        vm.show(SelectedText.Ready("experience", readOnly = false))
        userContent.gate = CompletableDeferred()
        vm.bookmark()
        store.clear() // the dialog closes before the write finished
        userContent.gate!!.complete(Unit)
        assertEquals(1, userContent.entries.size)
    }

    @Test
    fun aFailedSave_isSaid_andNothingIsShownAsSaved() = runTest {
        userContent.fail = true
        vm.show(SelectedText.Ready("experience", readOnly = false))
        vm.bookmark()
        assertTrue(state.bookmark.failed)
        assertEquals(false, state.bookmark.saved)
    }

    @Test
    fun nothingToSave_noBookmark() = runTest {
        translations.answer = { r, _ -> result(r, vi = FieldState.NeedsModel(setOf(Language.VIETNAMESE))) }
        vm.show(SelectedText.Ready("hello", readOnly = false))
        assertEquals(false, state.bookmark.available)
        vm.bookmark()
        assertTrue(userContent.entries.isEmpty())
    }

    @Test
    fun closingTheDialog_cancelsAndFreesTheTranslators() = runTest {
        translations.gates["hello"] = CompletableDeferred()
        vm.show(SelectedText.Ready("hello", readOnly = false))
        store.clear()
        assertEquals(1, translations.cancelled)
        assertEquals(1, translations.released)
        // The speech engine is freed too.
        assertEquals(1, speaker.shutdowns)
    }
}

package com.n3sensei.app.feature.translate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.domain.repository.UserContentRepository
import com.n3sensei.app.domain.repository.UserPreferencesRepository
import com.n3sensei.app.domain.translate.BookmarkEntry
import com.n3sensei.app.domain.translate.BookmarkResult
import com.n3sensei.app.domain.translate.FieldState
import com.n3sensei.app.domain.translate.Language
import com.n3sensei.app.domain.translate.LanguageDetection
import com.n3sensei.app.domain.translate.LanguageDetector
import com.n3sensei.app.domain.translate.QuickTranslateSettings
import com.n3sensei.app.domain.translate.SelectedText
import com.n3sensei.app.domain.translate.Speaker
import com.n3sensei.app.domain.translate.SpeechState
import com.n3sensei.app.domain.translate.TranslationFailure
import com.n3sensei.app.domain.translate.TranslationMode
import com.n3sensei.app.domain.translate.TranslationRepository
import com.n3sensei.app.domain.translate.TranslationRequest
import com.n3sensei.app.domain.translate.TranslationResult
import com.n3sensei.app.domain.translate.Utterance
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where the translation of the dialog stands. */
sealed interface TranslationUiState {
    /** Nothing to translate: no text, language not chosen yet, or "Dịch nhanh" turned off. */
    data object Idle : TranslationUiState

    data object Loading : TranslationUiState

    /** Downloading the models of [languages] (no progress figure: ML Kit gives none). */
    data class DownloadingModel(val languages: Set<Language>) : TranslationUiState

    /** Everything asked for is there. */
    data class Success(val result: TranslationResult) : TranslationUiState

    /** Some of what was asked for is there; the rest is missing or failed (see the fields). */
    data class PartialSuccess(val result: TranslationResult) : TranslationUiState

    /**
     * Some languages need a model the learner has not downloaded: [result] shows what is
     * there already; downloading waits for the learner ([approximateMb] when known).
     */
    data class ModelRequired(val result: TranslationResult, val languages: Set<Language>, val approximateMb: Int?) : TranslationUiState

    /** Nothing was found and nothing failed. */
    data class Empty(val result: TranslationResult) : TranslationUiState

    /** Nothing could be given: [failure] says why (shown in plain words, never technical). */
    data class Error(val failure: TranslationFailure) : TranslationUiState

    companion object {
        /** The state for a result: what was asked for and what came back. */
        fun of(result: TranslationResult, approximateMb: (Set<Language>) -> Int?): TranslationUiState {
            val asked = listOf(result.vietnameseMeaning, result.englishTranslation, result.japaneseTranslation)
                .filter { it != FieldState.NotRequested }
            val missing = result.missingModels
            return when {
                missing.isNotEmpty() -> ModelRequired(result, missing, approximateMb(missing))
                asked.all { it is FieldState.Available } -> Success(result)
                asked.any { it is FieldState.Available } -> PartialSuccess(result)
                asked.any { it is FieldState.Failed } -> Error(asked.filterIsInstance<FieldState.Failed>().first().failure)
                else -> Empty(result)
            }
        }
    }
}

/** The dialog's one bookmark. */
data class BookmarkUiState(
    /** Something to save (a meaning or a translation); otherwise no bookmark is shown. */
    val available: Boolean = false,
    /** This entry is in "Từ của tôi" (live from the database). */
    val saved: Boolean = false,
    val saving: Boolean = false,
    /** The last save failed (nothing was written). */
    val failed: Boolean = false,
)

/** What the dialog shows. */
data class QuickTranslateUiState(
    val selected: SelectedText,
    /** Null when there is no text to detect. */
    val detection: LanguageDetection?,
    /** The learner's own choice of source language in this dialog, if any. */
    val chosenSource: Language?,
    /** Mode of Settings → Dịch nhanh. */
    val settingsMode: TranslationMode,
    /** The learner's own choice of mode in this dialog, if any (Settings keep theirs). */
    val chosenMode: TranslationMode?,
    val enabled: Boolean,
    val translation: TranslationUiState = TranslationUiState.Idle,
    /** "Ví dụ và cách dùng" unfolded (folded by default, for each new text). */
    val detailsExpanded: Boolean = false,
    val speech: SpeechState = SpeechState(),
    val bookmark: BookmarkUiState = BookmarkUiState(),
) {
    /** Source language used: the learner's choice, else the detected one; null = still to choose. */
    val source: Language? get() = chosenSource ?: detection?.language
    val mode: TranslationMode get() = chosenMode ?: settingsMode
    val targets: List<Language> get() = source?.let { mode.targetsFor(it) }.orEmpty()

    private val result: TranslationResult?
        get() = when (val t = translation) {
            is TranslationUiState.Success -> t.result
            is TranslationUiState.PartialSuccess -> t.result
            is TranslationUiState.ModelRequired -> t.result
            else -> null
        }

    /** The selected text, read with its language's voice (when the language is known). */
    val sourceUtterance: Utterance?
        get() = (selected as? SelectedText.Ready)?.let { ready -> source?.let { Utterance(ready.text, it) } }

    /**
     * What the speaker of a result row reads: the English translation, or the Japanese one
     * (its dictionary kana when there is one, else the Japanese text; never romaji). Null when
     * there is nothing to read in that language.
     */
    fun utteranceFor(language: Language): Utterance? = when (language) {
        Language.ENGLISH -> result?.englishTranslation?.valuesOrEmpty?.firstOrNull()?.let { Utterance(it.value, Language.ENGLISH) }
        Language.JAPANESE -> result?.japaneseTranslation?.valuesOrEmpty?.firstOrNull()?.let { Utterance(it.value.reading ?: it.value.text, Language.JAPANESE) }
        Language.VIETNAMESE -> null
    }
}

/**
 * State of the "Dịch nhanh" dialog: the selected text, its detected language, the mode from
 * Settings and the translation. Choices made in the dialog (source language, mode) last for
 * this dialog only and never change the settings.
 *
 * Each change of text, source, mode or download permission starts a new translation and cancels
 * the one running, so an older result never replaces a newer one. Models are downloaded only
 * after [downloadModels]. Closing the dialog cancels everything and frees the translators.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QuickTranslateViewModel(
    preferences: UserPreferencesRepository,
    private val detector: LanguageDetector,
    private val translations: TranslationRepository,
    private val speaker: Speaker,
    private val userContent: UserContentRepository,
) : ViewModel() {

    private val text = MutableStateFlow<Pair<SelectedText, LanguageDetection?>?>(null)
    private val chosenSource = MutableStateFlow<Language?>(null)
    private val chosenMode = MutableStateFlow<TranslationMode?>(null)
    /** Whether models may be downloaded, and how many times the learner asked to try again. */
    private data class Attempt(val allowDownload: Boolean = false, val retries: Int = 0)

    private val attempt = MutableStateFlow(Attempt())
    private val detailsExpanded = MutableStateFlow(false)
    private val settings: Flow<QuickTranslateSettings> = preferences.preferences.map { it.quickTranslate }.catch { emit(QuickTranslateSettings()) }

    private data class Ask(val request: TranslationRequest?, val attempt: Attempt)

    private val translation: Flow<TranslationUiState> = combine(text, chosenSource, chosenMode, settings, attempt) { shown, source, mode, s, tries ->
        val ready = shown?.first as? SelectedText.Ready
        val language = source ?: shown?.second?.language
        val request = if (ready != null && language != null && s.enabled) {
            TranslationRequest(ready.text, language, mode ?: s.mode)
        } else {
            null
        }
        Ask(request, tries)
    }
        .distinctUntilChanged()
        .transformLatest { ask ->
            val request = ask.request ?: return@transformLatest emit(TranslationUiState.Idle)
            emit(TranslationUiState.Loading)
            val first = translations.translate(request, allowModelDownload = false)
            if (first.missingModels.isNotEmpty() && ask.attempt.allowDownload) {
                emit(TranslationUiState.DownloadingModel(first.missingModels))
                emit(state(translations.translate(request, allowModelDownload = true)))
            } else {
                emit(state(first))
            }
        }

    private val translationState: StateFlow<TranslationUiState> = translation.stateIn(viewModelScope, SharingStarted.Eagerly, TranslationUiState.Idle)

    /** What the bookmark would save for the translation shown (null: nothing yet). */
    private val entry: StateFlow<BookmarkEntry?> = translationState
        .map { t -> resultOf(t)?.let(BookmarkEntry::from) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val savedId: Flow<String?> = entry.flatMapLatest { e ->
        if (e == null) flowOf(null) else userContent.observeBookmark(e.sourceLanguage, e.key).catch { emit(null) }
    }

    private val bookmarkSaving = MutableStateFlow(false)
    private val bookmarkFailed = MutableStateFlow(false)

    private val bookmark: Flow<BookmarkUiState> = combine(entry, savedId, bookmarkSaving, bookmarkFailed) { e, id, saving, failed ->
        BookmarkUiState(available = e != null, saved = e != null && id != null, saving = saving, failed = failed)
    }

    /** Null until the text and the settings are known (milliseconds). */
    val state: StateFlow<QuickTranslateUiState?> = combine(
        combine(text, chosenSource, chosenMode, settings, detailsExpanded) { shown, source, mode, s, expanded ->
            shown?.let { (selected, detection) ->
                QuickTranslateUiState(selected, detection, source, s.mode, mode, s.enabled, detailsExpanded = expanded)
            }
        },
        translationState,
        speaker.state,
        bookmark,
    ) { ui, t, speech, b -> ui?.copy(translation = t, speech = speech, bookmark = b) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private fun resultOf(t: TranslationUiState): TranslationResult? = when (t) {
        is TranslationUiState.Success -> t.result
        is TranslationUiState.PartialSuccess -> t.result
        is TranslationUiState.ModelRequired -> t.result
        else -> null
    }

    /**
     * Saves the entry shown into "Từ của tôi" as a word saved, not studied (no progress, review,
     * mark or reminder). Again on a saved entry: adds what it lacks, never a second word, never
     * a deletion. The bookmark shows "saved" only once the transaction is written.
     */
    fun bookmark() {
        val current = entry.value ?: return
        if (!bookmarkSaving.compareAndSet(expect = false, update = true)) return
        bookmarkFailed.value = false
        viewModelScope.launch {
            try {
                // A save is never cut short by the dialog closing: the transaction (milliseconds)
                // completes, so a tap is never silently lost.
                bookmarkFailed.value = withContext(NonCancellable) { userContent.saveBookmark(current) } is BookmarkResult.Failed
            } finally {
                bookmarkSaving.value = false
            }
        }
    }

    private fun state(result: TranslationResult) = TranslationUiState.of(result, translations::approximateDownloadMb)

    /**
     * Shows [selected]. The same text again (the activity recreated after a rotation) keeps the
     * choices made; another text starts afresh.
     */
    fun show(selected: SelectedText) {
        if (text.value?.first == selected) return
        speaker.stop()
        bookmarkFailed.value = false
        chosenSource.value = null
        chosenMode.value = null
        attempt.value = Attempt()
        detailsExpanded.value = false
        text.value = selected to (selected as? SelectedText.Ready)?.let { detector.detect(it.text) }
    }

    fun chooseSource(language: Language) {
        speaker.stop()
        chosenSource.value = language
    }

    fun chooseMode(mode: TranslationMode) {
        speaker.stop()
        chosenMode.value = mode
    }

    /** Reads [utterance] aloud, or stops it when it is the one being read (a second tap). Never on its own. */
    fun speak(utterance: Utterance) {
        if (speaker.state.value.speaking == utterance) speaker.stop() else speaker.speak(utterance)
    }

    /** The dialog is no longer visible. */
    fun stopSpeaking() = speaker.stop()

    /** The learner agreed to download the missing language models (for this dialog). */
    fun downloadModels() {
        attempt.update { it.copy(allowDownload = true) }
    }

    /** Asks again after a failure (e.g. back online): the same request, the same permission to download. */
    fun retry() {
        attempt.update { it.copy(retries = it.retries + 1) }
    }

    fun toggleDetails() {
        detailsExpanded.value = !detailsExpanded.value
    }

    override fun onCleared() {
        speaker.shutdown()
        translations.release()
    }
}

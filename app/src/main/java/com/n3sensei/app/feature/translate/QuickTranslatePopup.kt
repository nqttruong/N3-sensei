package com.n3sensei.app.feature.translate

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.icon.N3Icons
import com.n3sensei.app.core.designsystem.theme.N3SenseiTheme
import com.n3sensei.app.core.designsystem.theme.japanese
import com.n3sensei.app.core.ui.content.labelRes
import com.n3sensei.app.domain.translate.FieldState
import com.n3sensei.app.domain.translate.JapaneseText
import com.n3sensei.app.domain.translate.Language
import com.n3sensei.app.domain.translate.LanguageDetection
import com.n3sensei.app.domain.translate.Provider
import com.n3sensei.app.domain.translate.SelectedText
import com.n3sensei.app.domain.translate.Sourced
import com.n3sensei.app.domain.translate.SpeechState
import com.n3sensei.app.domain.translate.TranslationFailure
import com.n3sensei.app.domain.translate.TranslationMode
import com.n3sensei.app.domain.translate.TranslationResult
import com.n3sensei.app.domain.translate.Utterance
import com.n3sensei.app.domain.translate.VoiceAvailability

object QuickTranslateTags {
    const val POPUP = "quick_translate_popup"
    const val DRAG_HANDLE = "quick_translate_drag_handle"
    const val TEXT = "quick_translate_text"
    const val CLOSE = "quick_translate_close"
    const val LOADING = "quick_translate_loading"
    const val DOWNLOAD = "quick_translate_download"
    const val BOOKMARK = "quick_translate_bookmark"
    const val RETRY = "quick_translate_retry"
    const val NOTICE = "quick_translate_notice"
    const val VOICE_HINT = "quick_translate_voice_hint"
    const val PERMISSION = "quick_translate_permission"
    const val OPEN_SETTINGS = "quick_translate_open_settings"
    const val LATER = "quick_translate_later"
    fun speak(language: Language) = "quick_translate_speak_${language.code}"
    fun result(language: Language) = "quick_translate_result_${language.code}"
}

/** At most this many senses or words in a row. */
private const val ROW_VALUES = 2

/**
 * The small floating "Dịch nhanh" popup: the selected text, one row per target language (code,
 * translation, speaker), one bookmark for the whole entry and X. Nothing else (no language or
 * mode choice, no sources, examples or readings): those stay in the app.
 *
 * [dragHandleModifier] is put on the header's icon and text only, so the bookmark, the speakers and X
 * are never drag areas. The window around it is the overlay's own small window (no dim).
 */
@Composable
fun QuickTranslatePopup(
    state: QuickTranslateUiState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier,
    onSpeak: (Utterance) -> Unit = {},
    onBookmark: () -> Unit = {},
    onDownloadModels: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 3.dp,
        modifier = modifier
            .widthIn(min = 220.dp, max = 280.dp)
            .testTag(QuickTranslateTags.POPUP),
    ) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 10.dp)) {
            Header(state, onClose, onBookmark, dragHandleModifier)
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .padding(end = 8.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Body(state, onSpeak, onDownloadModels, onRetry)
            }
        }
    }
}

@Composable
private fun Header(state: QuickTranslateUiState, onClose: () -> Unit, onBookmark: () -> Unit, modifier: Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .then(modifier)
                .testTag(QuickTranslateTags.DRAG_HANDLE),
        ) {
            Icon(
                painterResource(R.drawable.ic_translate),
                contentDescription = stringResource(R.string.quick_translate_title),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                (state.selected as? SelectedText.Ready)?.text ?: stringResource(R.string.quick_translate_title),
                style = MaterialTheme.typography.titleMedium.japanese(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(start = 8.dp, top = 12.dp, bottom = 12.dp)
                    .testTag(QuickTranslateTags.TEXT),
            )
        }
        // One bookmark for the whole entry; greyed out until there is something to save.
        if (state.enabled && state.selected is SelectedText.Ready) BookmarkButton(state.bookmark, onBookmark)
        IconButton(onClick = onClose, modifier = Modifier.testTag(QuickTranslateTags.CLOSE)) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.quick_translate_close))
        }
    }
}

@Composable
private fun BookmarkButton(bookmark: BookmarkUiState, onBookmark: () -> Unit) {
    val description = stringResource(if (bookmark.saved) R.string.quick_translate_bookmarked else R.string.quick_translate_bookmark)
    IconButton(
        onClick = onBookmark,
        enabled = bookmark.available && !bookmark.saving,
        modifier = Modifier
            .testTag(QuickTranslateTags.BOOKMARK)
            .semantics { stateDescription = description },
    ) {
        Icon(
            if (bookmark.saved) N3Icons.Marked else N3Icons.Unmarked,
            contentDescription = description,
            tint = if (bookmark.saved) MaterialTheme.colorScheme.primary else LocalContentColor.current,
        )
    }
}

@Composable
private fun Body(state: QuickTranslateUiState, onSpeak: (Utterance) -> Unit, onDownloadModels: () -> Unit, onRetry: () -> Unit) {
    when (val selected = state.selected) {
        is SelectedText.TooLong -> return Notice(stringResource(R.string.quick_translate_too_long, selected.codePoints, selected.limit))
        SelectedText.Blank, SelectedText.Missing -> return Notice(stringResource(R.string.quick_translate_no_text))
        is SelectedText.Ready -> Unit
    }
    if (!state.enabled) return Notice(stringResource(R.string.quick_translate_disabled))
    if (state.source == null) return Notice(stringResource(R.string.quick_translate_unknown_language))
    val translation = state.translation
    when (translation) {
        TranslationUiState.Idle -> Unit
        TranslationUiState.Loading, is TranslationUiState.DownloadingModel -> {
            if (translation is TranslationUiState.DownloadingModel) Notice(stringResource(R.string.quick_translate_downloading_short))
            LinearProgressIndicator(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .testTag(QuickTranslateTags.LOADING),
            )
        }
        is TranslationUiState.Error -> {
            Notice(failureMessage(translation.failure))
            TextButton(onClick = onRetry, modifier = Modifier.testTag(QuickTranslateTags.RETRY)) {
                Text(stringResource(R.string.quick_translate_retry))
            }
        }
        is TranslationUiState.Empty -> Notice(stringResource(R.string.quick_translate_empty))
        is TranslationUiState.Success -> state.targets.forEach { ResultRow(it, translation.result, state, onSpeak) }
        is TranslationUiState.PartialSuccess -> state.targets.forEach { ResultRow(it, translation.result, state, onSpeak) }
        is TranslationUiState.ModelRequired -> {
            state.targets.forEach { ResultRow(it, translation.result, state, onSpeak) }
            val names = state.targets.filter { it in translation.languages }.joinToString(", ") { it.code.uppercase() }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Hint(
                    translation.approximateMb?.let { stringResource(R.string.quick_translate_model_short, names, it) }
                        ?: stringResource(R.string.quick_translate_model_short_unknown_size, names),
                    Modifier.weight(1f),
                )
                TextButton(onClick = onDownloadModels, modifier = Modifier.testTag(QuickTranslateTags.DOWNLOAD)) {
                    Text(stringResource(R.string.quick_translate_download_short))
                }
            }
        }
    }
    val failedVoice = state.speech.failed?.takeIf { it in state.targets }
    if (failedVoice != null) {
        Hint(stringResource(R.string.quick_translate_speak_failed_short, stringResource(failedVoice.labelRes())), Modifier.testTag(QuickTranslateTags.VOICE_HINT))
    }
    if (state.bookmark.failed) Hint(stringResource(R.string.quick_translate_bookmark_failed))
}

/** "VI  và  [speaker]": the language code, its translation (or why there is none) and its speaker. */
@Composable
private fun ResultRow(language: Language, result: TranslationResult, state: QuickTranslateUiState, onSpeak: (Utterance) -> Unit) {
    val (text, status) = rowText(language, result)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(QuickTranslateTags.result(language)),
    ) {
        Text(
            language.code.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            // Never broken over two lines (large font scales): at least the usual width, more if needed.
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .widthIn(min = 28.dp)
                .padding(end = 6.dp),
        )
        if (text != null) {
            Text(
                text,
                style = if (language == Language.JAPANESE) MaterialTheme.typography.bodyLarge.japanese() else MaterialTheme.typography.bodyLarge,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            SpeakButton(utteranceFor(language, text, state), state.speech, onSpeak, Modifier.testTag(QuickTranslateTags.speak(language)))
        } else {
            Hint(status.orEmpty(), Modifier.weight(1f).padding(vertical = 12.dp))
        }
    }
}

/** The row's text, or (null, a short reason). */
@Composable
private fun rowText(language: Language, result: TranslationResult): Pair<String?, String?> {
    val field: FieldState<String> = when (language) {
        Language.VIETNAMESE -> result.vietnameseMeaning
        Language.ENGLISH -> result.englishTranslation
        Language.JAPANESE -> when (val ja = result.japaneseTranslation) {
            is FieldState.Available -> FieldState.Available(ja.values.map { Sourced(it.value.text, it.provider, it.sourceName) })
            is FieldState.NeedsModel -> ja
            is FieldState.Failed -> ja
            FieldState.Unavailable -> FieldState.Unavailable
            FieldState.NotRequested -> FieldState.NotRequested
        }
    }
    val separator = if (language == Language.JAPANESE) "、" else "; "
    return when (field) {
        is FieldState.Available -> field.values.map { it.value }.distinct().take(ROW_VALUES).joinToString(separator) to null
        is FieldState.NeedsModel -> null to stringResource(R.string.quick_translate_field_needs_model)
        is FieldState.Failed -> null to failureMessage(field.failure)
        FieldState.Unavailable, FieldState.NotRequested -> null to stringResource(R.string.quick_translate_empty)
    }
}

/** What a row's speaker reads: the row's own text in its language (Japanese: the dictionary kana when known). */
private fun utteranceFor(language: Language, text: String, state: QuickTranslateUiState): Utterance =
    state.utteranceFor(language) ?: Utterance(text, language)

/** One speaker icon, active only when the language's voice is ready; a second tap stops. */
@Composable
private fun SpeakButton(utterance: Utterance, speech: SpeechState, onSpeak: (Utterance) -> Unit, modifier: Modifier = Modifier) {
    val voice = speech.voices[utterance.language] ?: VoiceAvailability.PENDING
    val speaking = speech.speaking == utterance
    val name = stringResource(utterance.language.labelRes())
    val description = when {
        voice == VoiceAvailability.PENDING -> stringResource(R.string.quick_translate_speak_pending)
        voice != VoiceAvailability.AVAILABLE -> stringResource(R.string.quick_translate_speak_missing, name)
        speaking -> stringResource(R.string.quick_translate_speak_stop)
        else -> stringResource(R.string.quick_translate_speak, name)
    }
    IconButton(onClick = { onSpeak(utterance) }, enabled = voice == VoiceAvailability.AVAILABLE, modifier = modifier) {
        Icon(
            painterResource(R.drawable.ic_volume_up),
            contentDescription = description,
            tint = if (speaking) MaterialTheme.colorScheme.primary else LocalContentColor.current,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun failureMessage(failure: TranslationFailure): String = stringResource(
    when (failure) {
        TranslationFailure.OFFLINE -> R.string.quick_translate_error_offline_short
        TranslationFailure.MODEL_DOWNLOAD_FAILED -> R.string.quick_translate_error_download_short
        TranslationFailure.TRANSLATION_FAILED -> R.string.quick_translate_error_translation_short
    },
)

@Composable
private fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

@Composable
private fun Notice(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(vertical = 8.dp)
            .testTag(QuickTranslateTags.NOTICE),
    )
}

// --- Preview pane only: example states (never shown in the app). ---

private fun previewState(text: String, source: Language, translation: TranslationUiState) = QuickTranslateUiState(
    selected = SelectedText.Ready(text, readOnly = true),
    detection = LanguageDetection(source, null, isAmbiguous = false, candidates = listOf(source)),
    chosenSource = null,
    settingsMode = TranslationMode.BOTH,
    chosenMode = null,
    enabled = true,
    translation = translation,
    speech = SpeechState(voices = Language.entries.associateWith { VoiceAvailability.AVAILABLE }),
)

private val previewResult = TranslationResult(
    originalText = "and",
    sourceLanguage = Language.ENGLISH,
    vietnameseMeaning = FieldState.Available(listOf(Sourced("và", Provider.MACHINE_TRANSLATION))),
    englishTranslation = FieldState.NotRequested,
    englishPronunciation = FieldState.Unavailable,
    japaneseTranslation = FieldState.Available(listOf(Sourced(JapaneseText("と", null), Provider.MACHINE_TRANSLATION))),
    japaneseReading = FieldState.Unavailable,
    japaneseRomaji = FieldState.Unavailable,
    examples = FieldState.Unavailable,
    usageNotes = FieldState.Unavailable,
)

@Preview
@Composable
private fun PopupPreview() {
    N3SenseiTheme { QuickTranslatePopup(previewState("and", Language.ENGLISH, TranslationUiState.Success(previewResult)), {}) }
}

@Preview
@Composable
private fun PopupLoadingDarkPreview() {
    N3SenseiTheme(darkTheme = true) { QuickTranslatePopup(previewState("học tập", Language.VIETNAMESE, TranslationUiState.Loading), {}) }
}

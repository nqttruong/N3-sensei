package com.n3sensei.app.feature.translate

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.icon.N3Icons
import com.n3sensei.app.core.designsystem.theme.N3SenseiTheme
import com.n3sensei.app.core.designsystem.theme.japanese
import com.n3sensei.app.core.ui.content.ExampleSentenceItem
import com.n3sensei.app.core.ui.content.labelRes
import com.n3sensei.app.core.ui.content.shortLabelRes
import com.n3sensei.app.domain.model.ExampleSentence
import com.n3sensei.app.domain.translate.FieldState
import com.n3sensei.app.domain.translate.JapaneseText
import com.n3sensei.app.domain.translate.Language
import com.n3sensei.app.domain.translate.LanguageDetection
import com.n3sensei.app.domain.translate.Provider
import com.n3sensei.app.domain.translate.SelectedText
import com.n3sensei.app.domain.translate.Sourced
import com.n3sensei.app.domain.translate.TranslationExample
import com.n3sensei.app.domain.translate.TranslationFailure
import com.n3sensei.app.domain.translate.TranslationMode
import com.n3sensei.app.domain.translate.TranslationResult
import com.n3sensei.app.domain.translate.SpeechState
import com.n3sensei.app.domain.translate.Utterance
import com.n3sensei.app.domain.translate.VoiceAvailability

object QuickTranslateTags {
    const val DIALOG = "quick_translate_dialog"
    const val TEXT = "quick_translate_text"
    const val CLOSE = "quick_translate_close"
    const val LOADING = "quick_translate_loading"
    const val DOWNLOAD = "quick_translate_download"
    const val DETAILS = "quick_translate_details"
    const val INSTALL_VOICES = "quick_translate_install_voices"
    const val VOICE_HINT = "quick_translate_voice_hint"
    const val BOOKMARK = "quick_translate_bookmark"
    const val RETRY = "quick_translate_retry"
    const val SPEAK_SOURCE = "quick_translate_speak_source"
    fun speak(language: Language) = "quick_translate_speak_${language.code}"
    fun source(language: Language) = "quick_translate_source_${language.code}"
    fun mode(mode: TranslationMode) = "quick_translate_mode_${mode.name}"
    fun result(language: Language) = "quick_translate_result_${language.code}"
}

/** At most this many senses in the short meaning. */
private const val SHORT_SENSES = 3

/**
 * "Dịch nhanh": the text chosen in another app, its language (detected, can be changed), the
 * translation mode, and the translation into each target language (dictionary data or on-device
 * machine translation, said as such). Examples and usage notes fold away under one row. The
 * window around it is the activity's own floating window (dim, rounded surface drawn here), so
 * there is a single dialog layer. Pronunciation and bookmark come with their own PRs: no button
 * is shown before it works.
 */
@Composable
fun QuickTranslateScreen(
    state: QuickTranslateUiState,
    onChooseSource: (Language) -> Unit,
    onChooseMode: (TranslationMode) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onDownloadModels: () -> Unit = {},
    onToggleDetails: () -> Unit = {},
    onSpeak: (Utterance) -> Unit = {},
    onInstallVoices: () -> Unit = {},
    onBookmark: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    // Never taller than the screen: the body scrolls (long text, large font scale).
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.85f).dp
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .testTag(QuickTranslateTags.DIALOG),
    ) {
        Column(Modifier.padding(start = 24.dp, end = 8.dp, top = 12.dp, bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_translate), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(R.string.quick_translate_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .weight(1f)
                        .semantics { heading() },
                )
                // One bookmark for the whole entry, only when there is something to save.
                if (state.enabled && state.bookmark.available) BookmarkButton(state.bookmark, onBookmark)
                IconButton(onClick = onClose, modifier = Modifier.testTag(QuickTranslateTags.CLOSE)) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.quick_translate_close))
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(end = 16.dp, top = 8.dp),
            ) {
                when (val selected = state.selected) {
                    is SelectedText.Ready -> {
                        Label(stringResource(R.string.quick_translate_selected_label))
                        // Japanese glyph shapes for kanji; Latin and Vietnamese text is unaffected.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // The Box takes the weight: SelectionContainer alone lets a wrapping text
                            // take the whole row and push the speaker out.
                            Box(Modifier.weight(1f)) {
                                SelectionContainer {
                                    Text(
                                        selected.text,
                                        style = MaterialTheme.typography.headlineSmall.japanese(),
                                        modifier = Modifier.testTag(QuickTranslateTags.TEXT),
                                    )
                                }
                            }
                            if (state.enabled) SpeakButton(state.sourceUtterance, state.speech, onSpeak, Modifier.testTag(QuickTranslateTags.SPEAK_SOURCE))
                        }
                        if (state.enabled) {
                            if (state.bookmark.failed) Hint(stringResource(R.string.quick_translate_bookmark_failed))
                            if (state.source == Language.JAPANESE) SourceReading(state.translation)
                            SourceChoice(state, onChooseSource)
                            ModeChoice(state, onChooseMode)
                            Translation(state, onDownloadModels, onToggleDetails, onSpeak, onRetry)
                            MissingVoices(state, onInstallVoices)
                        } else {
                            Notice(stringResource(R.string.quick_translate_disabled))
                        }
                    }
                    is SelectedText.TooLong -> Notice(stringResource(R.string.quick_translate_too_long, selected.codePoints, selected.limit))
                    SelectedText.Blank, SelectedText.Missing -> Notice(stringResource(R.string.quick_translate_no_text))
                }
            }
        }
    }
}

/** Saves the entry into "Từ của tôi"; filled once saved (a tap then only adds missing translations). */
@Composable
private fun BookmarkButton(bookmark: BookmarkUiState, onBookmark: () -> Unit) {
    val description = stringResource(if (bookmark.saved) R.string.quick_translate_bookmarked else R.string.quick_translate_bookmark)
    IconButton(
        onClick = onBookmark,
        enabled = !bookmark.saving,
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

/** The kana reading of a Japanese selection, when the dictionary has it. */
@Composable
private fun SourceReading(translation: TranslationUiState) {
    val reading = translation.result?.japaneseReading?.valuesOrEmpty?.joinToString("、") { it.value }
    if (!reading.isNullOrEmpty()) {
        Text(
            stringResource(R.string.quick_translate_reading, reading),
            style = MaterialTheme.typography.titleMedium.japanese(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val TranslationUiState.result: TranslationResult?
    get() = when (this) {
        is TranslationUiState.Success -> result
        is TranslationUiState.PartialSuccess -> result
        is TranslationUiState.ModelRequired -> result
        is TranslationUiState.Empty -> result
        else -> null
    }

@Composable
private fun Translation(
    state: QuickTranslateUiState,
    onDownloadModels: () -> Unit,
    onToggleDetails: () -> Unit,
    onSpeak: (Utterance) -> Unit,
    onRetry: () -> Unit,
) {
    val translation = state.translation
    val result = translation.result
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        // Status changes are announced by TalkBack.
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        when (translation) {
            TranslationUiState.Idle -> Unit
            TranslationUiState.Loading -> Progress(stringResource(R.string.quick_translate_loading))
            is TranslationUiState.DownloadingModel -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Hint(stringResource(R.string.quick_translate_downloading, languages(translation.languages)))
                LinearProgressIndicator(
                    Modifier
                        .fillMaxWidth()
                        .testTag(QuickTranslateTags.LOADING),
                )
            }
            is TranslationUiState.Error -> {
                Notice(failureMessage(translation.failure))
                // Back online, or a transient failure: try again without reopening the dialog.
                FilledTonalButton(onClick = onRetry, modifier = Modifier.testTag(QuickTranslateTags.RETRY)) {
                    Text(stringResource(R.string.quick_translate_retry))
                }
            }
            is TranslationUiState.Empty -> Notice(stringResource(R.string.quick_translate_empty))
            else -> Unit
        }
        if (result != null && translation !is TranslationUiState.Empty) {
            state.targets.forEach { language -> ResultRow(language, result, state, onSpeak) }
        }
        // Right under the results it would complete, before the folded details.
        if (translation is TranslationUiState.ModelRequired) {
            val names = languages(translation.languages)
            Notice(
                translation.approximateMb?.let { stringResource(R.string.quick_translate_model_needed, names, it) }
                    ?: stringResource(R.string.quick_translate_model_needed_unknown_size, names),
            )
            FilledTonalButton(onClick = onDownloadModels, modifier = Modifier.testTag(QuickTranslateTags.DOWNLOAD)) {
                Text(stringResource(R.string.quick_translate_download))
            }
        }
        if (result != null) Details(result, state.detailsExpanded, onToggleDetails)
    }
}

@Composable
private fun languages(languages: Set<Language>): String =
    Language.entries.filter { it in languages }.map { stringResource(it.labelRes()) }.joinToString(", ")

@Composable
private fun failureMessage(failure: TranslationFailure): String = stringResource(
    when (failure) {
        TranslationFailure.OFFLINE -> R.string.quick_translate_error_offline
        TranslationFailure.MODEL_DOWNLOAD_FAILED -> R.string.quick_translate_error_download
        TranslationFailure.TRANSLATION_FAILED -> R.string.quick_translate_error_translation
    },
)

/** The translation into one language: value(s), reading or pronunciation, and where it comes from. */
@Composable
private fun ResultRow(language: Language, result: TranslationResult, state: QuickTranslateUiState, onSpeak: (Utterance) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(QuickTranslateTags.result(language)),
    ) {
        // The language, and one speaker for its translation (English, Japanese).
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label(stringResource(language.labelRes()), Modifier.weight(1f))
            SpeakButton(state.utteranceFor(language), state.speech, onSpeak, Modifier.testTag(QuickTranslateTags.speak(language)))
        }
        when (language) {
            Language.VIETNAMESE -> FieldText(result.vietnameseMeaning) { values ->
                Text(
                    values.take(SHORT_SENSES).joinToString("; ") { it.value },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Language.ENGLISH -> FieldText(result.englishTranslation) { values ->
                Text(values.joinToString("; ") { it.value }, style = MaterialTheme.typography.titleMedium)
                result.englishPronunciation.valuesOrEmpty.firstOrNull()?.let { Hint(stringResource(R.string.quick_translate_ipa, it.value)) }
            }
            Language.JAPANESE -> FieldText(result.japaneseTranslation) { values ->
                values.take(SHORT_SENSES).forEach { JapaneseLine(it.value) }
                result.japaneseRomaji.valuesOrEmpty.firstOrNull()?.let { Hint(stringResource(R.string.quick_translate_romaji, it.value)) }
            }
        }
    }
}

@Composable
private fun JapaneseLine(word: JapaneseText) {
    Text(word.text, style = MaterialTheme.typography.titleLarge.japanese())
    Hint(word.reading?.let { stringResource(R.string.quick_translate_reading, it) } ?: stringResource(R.string.quick_translate_no_reading))
}

/** A field's values with their origin, or why there are none. */
@Composable
private fun <T> FieldText(field: FieldState<T>, content: @Composable (List<Sourced<T>>) -> Unit) {
    when (field) {
        is FieldState.Available -> {
            content(field.values)
            val fromDictionary = field.values.any { it.provider == Provider.CONTENT_DICTIONARY }
            Caption(stringResource(if (fromDictionary) R.string.quick_translate_from_dictionary else R.string.quick_translate_from_machine))
        }
        is FieldState.NeedsModel -> Hint(stringResource(R.string.quick_translate_field_needs_model))
        is FieldState.Failed -> Hint(failureMessage(field.failure))
        FieldState.Unavailable -> Hint(stringResource(R.string.quick_translate_empty))
        FieldState.NotRequested -> Unit
    }
}

/** "Ví dụ và cách dùng": folded by default; absent when there is neither. */
@Composable
private fun Details(result: TranslationResult, expanded: Boolean, onToggle: () -> Unit) {
    val examples = result.examples.valuesOrEmpty
    val notes = result.usageNotes.valuesOrEmpty
    if (examples.isEmpty() && notes.isEmpty()) return
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        val action = stringResource(if (expanded) R.string.quick_translate_details_collapse else R.string.quick_translate_details_expand)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClickLabel = action, onClick = onToggle)
                .semantics { stateDescription = action }
                .testTag(QuickTranslateTags.DETAILS),
        ) {
            Text(stringResource(R.string.quick_translate_details), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Icon(if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null)
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                examples.forEachIndexed { i, example -> ExampleSentenceItem(example.value.toExampleSentence(i)) }
                if (notes.isNotEmpty()) {
                    Label(stringResource(R.string.quick_translate_usage))
                    notes.forEach { Text("• ${it.value}", style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}

private fun TranslationExample.toExampleSentence(index: Int) = ExampleSentence("quick-translate-$index", japanese, furigana, translationVi)

@Composable
private fun Progress(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag(QuickTranslateTags.LOADING),
    ) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Hint(text)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourceChoice(state: QuickTranslateUiState, onChoose: (Language) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Label(stringResource(R.string.quick_translate_source))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.selectableGroup()) {
            Language.entries.forEach { language ->
                FilterChip(
                    selected = state.source == language,
                    onClick = { onChoose(language) },
                    label = { Text(stringResource(language.shortLabelRes())) },
                    leadingIcon = { if (state.source == language) Icon(Icons.Filled.Check, contentDescription = null) },
                    modifier = Modifier
                        .semantics { role = Role.RadioButton }
                        .testTag(QuickTranslateTags.source(language)),
                )
            }
        }
        Hint(sourceHint(state.chosenSource, state.detection))
    }
}

@Composable
private fun sourceHint(chosen: Language?, detection: LanguageDetection?): String {
    val detected = detection?.language
    return when {
        chosen != null -> stringResource(R.string.quick_translate_source_chosen, stringResource(chosen.labelRes()))
        detected == null -> stringResource(R.string.quick_translate_detected_none)
        !detection.isAmbiguous -> stringResource(R.string.quick_translate_detected, stringResource(detected.labelRes()))
        detection.mixed -> stringResource(R.string.quick_translate_detected_mixed, stringResource(detected.labelRes()))
        else -> stringResource(R.string.quick_translate_detected_unsure, stringResource(detected.labelRes()))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModeChoice(state: QuickTranslateUiState, onChoose: (TranslationMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Label(stringResource(R.string.quick_translate_mode))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.selectableGroup()) {
            TranslationMode.entries.forEach { mode ->
                FilterChip(
                    selected = state.mode == mode,
                    onClick = { onChoose(mode) },
                    label = { Text(stringResource(mode.shortLabelRes())) },
                    leadingIcon = { if (state.mode == mode) Icon(Icons.Filled.Check, contentDescription = null) },
                    modifier = Modifier
                        .semantics { role = Role.RadioButton }
                        .testTag(QuickTranslateTags.mode(mode)),
                )
            }
        }
        val targets = state.targets
        Hint(
            if (targets.isEmpty()) {
                stringResource(R.string.quick_translate_targets_none)
            } else {
                stringResource(R.string.quick_translate_targets, targets.map { stringResource(it.labelRes()) }.joinToString(", "))
            },
        )
        if (state.mode != state.settingsMode) {
            Hint(stringResource(R.string.quick_translate_mode_changed, stringResource(state.settingsMode.labelRes())))
        }
    }
}

@Composable
private fun Label(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/**
 * One speaker icon (no text) for [utterance]: active only with its language's voice ready;
 * highlighted while it reads (a second tap stops). Nothing at all when there is nothing to read.
 */
@Composable
private fun SpeakButton(utterance: Utterance?, speech: SpeechState, onSpeak: (Utterance) -> Unit, modifier: Modifier = Modifier) {
    if (utterance == null) return
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
        )
    }
}

/**
 * Languages the dialog would read but the device has no voice for, or whose voice just failed
 * (the engine may need the network for it), with a way to install one.
 */
@Composable
private fun MissingVoices(state: QuickTranslateUiState, onInstall: () -> Unit) {
    val wanted = (listOfNotNull(state.sourceUtterance) + state.targets.mapNotNull { state.utteranceFor(it) }).map { it.language }.toSet()
    val missing = wanted.filter { it in state.speech.voices && state.speech.voices[it] in setOf(VoiceAvailability.MISSING_DATA, VoiceAvailability.NOT_SUPPORTED) }
    val failed = state.speech.failed?.takeIf { it in wanted && it !in missing }
    if (missing.isEmpty() && failed == null) return
    val message = if (failed != null) {
        stringResource(R.string.quick_translate_speak_failed, languages(setOf(failed)))
    } else {
        stringResource(R.string.quick_translate_voices_missing, languages(missing.toSet()))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Hint(message, Modifier.weight(1f).testTag(QuickTranslateTags.VOICE_HINT))
        TextButton(onClick = onInstall, modifier = Modifier.testTag(QuickTranslateTags.INSTALL_VOICES)) {
            Text(stringResource(R.string.quick_translate_install_voices))
        }
    }
}

@Composable
private fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

@Composable
private fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
}

@Composable
private fun Notice(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
    }
}

// --- Preview pane only: example states (never shown in the app). ---

private fun previewState(text: String, source: Language, translation: TranslationUiState, expanded: Boolean = false) = QuickTranslateUiState(
    selected = SelectedText.Ready(text, readOnly = true),
    detection = LanguageDetection(source, null, isAmbiguous = false, candidates = listOf(source)),
    chosenSource = null,
    settingsMode = TranslationMode.BOTH,
    chosenMode = null,
    enabled = true,
    translation = translation,
    detailsExpanded = expanded,
)

private val previewResult = TranslationResult(
    originalText = "経験",
    sourceLanguage = Language.JAPANESE,
    vietnameseMeaning = FieldState.Available(listOf(Sourced("kinh nghiệm", Provider.CONTENT_DICTIONARY))),
    englishTranslation = FieldState.Available(listOf(Sourced("experience", Provider.MACHINE_TRANSLATION))),
    englishPronunciation = FieldState.Unavailable,
    japaneseTranslation = FieldState.NotRequested,
    japaneseReading = FieldState.Available(listOf(Sourced("けいけん", Provider.CONTENT_DICTIONARY))),
    japaneseRomaji = FieldState.Unavailable,
    examples = FieldState.Available(
        listOf(Sourced(TranslationExample("日本で働いた経験があります。", "{日本|にほん}で{働|はたら}いた{経験|けいけん}があります。", "Tôi có kinh nghiệm làm việc ở Nhật."), Provider.CONTENT_DICTIONARY)),
    ),
    usageNotes = FieldState.Available(listOf(Sourced("danh từ, động từ する", Provider.CONTENT_DICTIONARY))),
)

@Preview
@Composable
private fun SuccessPreview() {
    N3SenseiTheme { QuickTranslateScreen(previewState("経験", Language.JAPANESE, TranslationUiState.Success(previewResult), expanded = true), {}, {}, {}) }
}

@Preview
@Composable
private fun PartialSuccessDarkPreview() {
    val partial = previewResult.copy(englishTranslation = FieldState.Failed(TranslationFailure.TRANSLATION_FAILED))
    N3SenseiTheme(darkTheme = true) { QuickTranslateScreen(previewState("経験", Language.JAPANESE, TranslationUiState.PartialSuccess(partial)), {}, {}, {}) }
}

@Preview
@Composable
private fun LoadingPreview() {
    N3SenseiTheme { QuickTranslateScreen(previewState("experience", Language.ENGLISH, TranslationUiState.Loading), {}, {}, {}) }
}

@Preview
@Composable
private fun ErrorPreview() {
    N3SenseiTheme { QuickTranslateScreen(previewState("experience", Language.ENGLISH, TranslationUiState.Error(TranslationFailure.OFFLINE)), {}, {}, {}) }
}

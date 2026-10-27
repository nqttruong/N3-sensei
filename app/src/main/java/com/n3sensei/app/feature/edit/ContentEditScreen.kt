package com.n3sensei.app.feature.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.InfoBanner
import com.n3sensei.app.core.designsystem.component.SectionHeader
import com.n3sensei.app.core.designsystem.theme.japanese
import com.n3sensei.app.core.designsystem.theme.japaneseText
import com.n3sensei.app.core.ui.state.LoadingContent
import com.n3sensei.app.domain.edit.EditableField
import com.n3sensei.app.domain.edit.FieldError
import com.n3sensei.app.domain.edit.FieldKind

/**
 * Form to edit a kanji, a word or a grammar point, or to add a word to "Từ của tôi". Pack
 * items are edited as a layer over the pack (kept across updates, revertible); the learner's
 * words are edited and deleted directly. Nothing is written until Lưu; Hủy leaves without saving.
 */
@Composable
fun ContentEditScreen(
    viewModel: ContentEditViewModel,
    onDone: (deleted: Boolean) -> Unit,
    onCancel: () -> Unit,
    onOpenWord: (vocabularyId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.event) {
        state.event?.let { onDone(it == EditEvent.DELETED) }
    }
    if (state.loading) return LoadingContent(modifier)
    if (state.problem == EditProblem.NOT_FOUND && state.item == null) {
        return Column(modifier.padding(16.dp)) { InfoBanner(stringResource(R.string.edit_not_found)) }
    }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmRevert by remember { mutableStateOf(false) }
    val item = state.item

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (item != null) {
            Text(japaneseText(item.label), style = MaterialTheme.typography.headlineMedium.japanese())
            Text(
                stringResource(if (item.userCreated) R.string.edit_own_word_hint else R.string.edit_pack_item_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (item.changedInPack.isNotEmpty()) InfoBanner(stringResource(R.string.edit_pack_changed_banner))
        } else {
            Text(stringResource(R.string.edit_new_word_hint), style = MaterialTheme.typography.bodyMedium)
        }
        state.fields.forEach { field ->
            if (field == EditableField.WORD_EXAMPLE_JA) SectionHeader(stringResource(R.string.edit_example_section))
            val error = state.errors[field]
            val edited = item != null && field in item.edited
            val original = item?.original?.get(field).orEmpty().ifEmpty { "—" }
            OutlinedTextField(
                value = state.values[field].orEmpty(),
                onValueChange = { viewModel.onChange(field, it) },
                label = { Text(stringResource(field.labelRes()) + if (field.required) " *" else "") },
                isError = error != null,
                supportingText = when {
                    error != null -> { { Text(stringResource(error.messageRes())) } }
                    item != null && field in item.changedInPack -> { { Text(stringResource(R.string.edit_field_pack_changed, original)) } }
                    edited -> { { Text(stringResource(R.string.edit_field_original, original)) } }
                    field.kind == FieldKind.READINGS -> { { Text(stringResource(R.string.edit_readings_hint)) } }
                    else -> null
                },
                singleLine = !field.multiline,
                keyboardOptions = if (field.kind == FieldKind.NUMBER) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
                textStyle = if (field.kind == FieldKind.TEXT) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyLarge.japanese(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (state.isWord) KanjiLinks(state, viewModel)
        state.duplicate?.let { duplicate ->
            Text(
                stringResource(R.string.edit_duplicate_word, duplicate.packTitle),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onOpenWord(duplicate.existingId) }) { Text(stringResource(R.string.edit_open_existing)) }
                OutlinedButton(onClick = { viewModel.save(allowDuplicate = true) }, enabled = !state.busy) {
                    Text(stringResource(R.string.edit_keep_both))
                }
            }
        }
        if (state.problem == EditProblem.SAVE_FAILED) {
            Text(
                stringResource(R.string.edit_save_failed),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (state.problem == EditProblem.NOT_FOUND) {
            Text(stringResource(R.string.edit_not_found), color = MaterialTheme.colorScheme.error)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onCancel, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.common_cancel))
            }
            Button(onClick = { viewModel.save() }, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                Text(stringResource(if (item == null) R.string.edit_add_word else R.string.edit_save))
            }
        }
        if (item != null && !item.userCreated && item.edited.isNotEmpty()) {
            OutlinedButton(onClick = { confirmRevert = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.edit_revert))
            }
        }
        if (item != null && item.userCreated) {
            OutlinedButton(onClick = { confirmDelete = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.edit_delete_word))
            }
        }
    }

    if (confirmRevert) {
        ConfirmDialog(
            text = stringResource(R.string.edit_revert_confirm),
            confirm = stringResource(R.string.edit_revert),
            onConfirm = { confirmRevert = false; viewModel.revert() },
            onDismiss = { confirmRevert = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            text = stringResource(if (item?.hasProgress == true) R.string.edit_delete_confirm_progress else R.string.edit_delete_confirm),
            confirm = stringResource(R.string.edit_delete_word),
            onConfirm = { confirmDelete = false; viewModel.delete() },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** Kanji the word is linked to: offered from the word's characters, plus any kanji looked up. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KanjiLinks(state: ContentEditUiState, viewModel: ContentEditViewModel) {
    SectionHeader(stringResource(R.string.edit_kanji_links))
    Text(
        stringResource(R.string.edit_kanji_links_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (state.kanjiChoices.isEmpty()) {
        Text(stringResource(R.string.edit_kanji_links_none), style = MaterialTheme.typography.bodyMedium)
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        state.kanjiChoices.forEach { choice ->
            FilterChip(
                selected = choice.id in state.linkedKanji,
                onClick = { viewModel.toggleKanji(choice.id) },
                label = {
                    Text(
                        japaneseText(listOfNotNull(choice.character, choice.hanViet, choice.packTitle).joinToString(" · ")),
                        style = MaterialTheme.typography.labelLarge.japanese(),
                    )
                },
            )
        }
    }
    var lookup by rememberSaveable { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = lookup,
            onValueChange = { lookup = it },
            label = { Text(stringResource(R.string.edit_kanji_lookup)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.japanese(),
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { viewModel.lookUpKanji(lookup) }, enabled = lookup.isNotBlank()) {
            Text(stringResource(R.string.edit_kanji_lookup_action))
        }
    }
    state.kanjiNotFound?.let { Text(stringResource(R.string.edit_kanji_not_found, it), color = MaterialTheme.colorScheme.error) }
}

@Composable
private fun ConfirmDialog(text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

private fun EditableField.labelRes(): Int = when (this) {
    EditableField.KANJI_HAN_VIET -> R.string.edit_field_han_viet
    EditableField.KANJI_MEANING, EditableField.WORD_MEANING, EditableField.GRAMMAR_MEANING -> R.string.edit_field_meaning
    EditableField.KANJI_ON -> R.string.kanji_on
    EditableField.KANJI_KUN -> R.string.kanji_kun
    EditableField.KANJI_STROKES -> R.string.edit_field_strokes
    EditableField.KANJI_NOTE, EditableField.WORD_NOTE -> R.string.vocabulary_note
    EditableField.WORD_EXPRESSION -> R.string.edit_field_expression
    EditableField.WORD_READING -> R.string.edit_field_reading
    EditableField.WORD_PART_OF_SPEECH -> R.string.edit_field_part_of_speech
    EditableField.WORD_EXAMPLE_JA -> R.string.edit_field_example_ja
    EditableField.WORD_EXAMPLE_VI -> R.string.edit_field_example_vi
    EditableField.GRAMMAR_PATTERN -> R.string.edit_field_pattern
    EditableField.GRAMMAR_STRUCTURE -> R.string.edit_field_structure
    EditableField.GRAMMAR_USAGE -> R.string.grammar_usage
    EditableField.GRAMMAR_CAUTION -> R.string.edit_field_caution
}

private fun FieldError.messageRes(): Int = when (this) {
    FieldError.REQUIRED -> R.string.edit_error_required
    FieldError.TOO_LONG -> R.string.edit_error_too_long
    FieldError.NOT_KANA -> R.string.edit_error_not_kana
    FieldError.NOT_A_NUMBER -> R.string.edit_error_not_number
    FieldError.OUT_OF_RANGE -> R.string.edit_error_strokes_range
    FieldError.EXAMPLE_INCOMPLETE -> R.string.edit_error_example_incomplete
}

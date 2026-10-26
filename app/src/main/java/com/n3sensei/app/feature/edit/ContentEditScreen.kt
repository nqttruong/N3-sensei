package com.n3sensei.app.feature.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.setValue
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
import com.n3sensei.app.core.designsystem.theme.japanese
import com.n3sensei.app.core.designsystem.theme.japaneseText
import com.n3sensei.app.core.ui.state.LoadingContent
import com.n3sensei.app.domain.edit.EditableField
import com.n3sensei.app.domain.edit.FieldError
import com.n3sensei.app.domain.edit.FieldKind

/**
 * Form to edit a kanji or a word, or to add a word to "Từ của tôi". Pack items are edited as
 * a layer over the pack (kept across updates, revertible); the learner's words are edited and
 * deleted directly.
 */
@Composable
fun ContentEditScreen(
    viewModel: ContentEditViewModel,
    onDone: (deleted: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.event) {
        state.event?.let { onDone(it == EditEvent.DELETED) }
    }
    if (state.loading) return LoadingContent(modifier)
    if (state.problem == EditProblem.NOT_FOUND) {
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
        } else {
            Text(stringResource(R.string.edit_new_word_hint), style = MaterialTheme.typography.bodyMedium)
        }
        state.fields.forEach { field ->
            val error = state.errors[field]
            val edited = item != null && field in item.edited
            OutlinedTextField(
                value = state.values[field].orEmpty(),
                onValueChange = { viewModel.onChange(field, it) },
                label = { Text(stringResource(field.labelRes()) + if (field.required) " *" else "") },
                isError = error != null,
                supportingText = when {
                    error != null -> { { Text(stringResource(error.messageRes())) } }
                    edited -> { { Text(stringResource(R.string.edit_field_original, item!!.original[field].orEmpty().ifEmpty { "—" })) } }
                    field.kind == FieldKind.READINGS -> { { Text(stringResource(R.string.edit_readings_hint)) } }
                    else -> null
                },
                singleLine = field != EditableField.KANJI_NOTE && field != EditableField.WORD_NOTE,
                keyboardOptions = if (field.kind == FieldKind.NUMBER) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
                textStyle = if (field.kind == FieldKind.TEXT) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyLarge.japanese(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        state.duplicateIn?.let {
            Text(
                stringResource(R.string.edit_duplicate_word, it),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (state.problem == EditProblem.SAVE_FAILED) {
            Text(
                stringResource(R.string.edit_save_failed),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Button(onClick = viewModel::save, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (item == null) R.string.edit_add_word else R.string.edit_save))
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
            text = stringResource(R.string.edit_delete_confirm),
            confirm = stringResource(R.string.edit_delete_word),
            onConfirm = { confirmDelete = false; viewModel.delete() },
            onDismiss = { confirmDelete = false },
        )
    }
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
    EditableField.KANJI_MEANING, EditableField.WORD_MEANING -> R.string.edit_field_meaning
    EditableField.KANJI_ON -> R.string.kanji_on
    EditableField.KANJI_KUN -> R.string.kanji_kun
    EditableField.KANJI_STROKES -> R.string.edit_field_strokes
    EditableField.KANJI_NOTE, EditableField.WORD_NOTE -> R.string.vocabulary_note
    EditableField.WORD_EXPRESSION -> R.string.edit_field_expression
    EditableField.WORD_READING -> R.string.edit_field_reading
    EditableField.WORD_PART_OF_SPEECH -> R.string.edit_field_part_of_speech
}

private fun FieldError.messageRes(): Int = when (this) {
    FieldError.REQUIRED -> R.string.edit_error_required
    FieldError.TOO_LONG -> R.string.edit_error_too_long
    FieldError.NOT_KANA -> R.string.edit_error_not_kana
    FieldError.NOT_A_NUMBER -> R.string.edit_error_not_number
    FieldError.OUT_OF_RANGE -> R.string.edit_error_strokes_range
}

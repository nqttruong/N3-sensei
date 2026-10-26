package com.n3sensei.app.feature.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.domain.edit.EditableField
import com.n3sensei.app.domain.edit.EditableItem
import com.n3sensei.app.domain.edit.FieldError
import com.n3sensei.app.domain.edit.SaveResult
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.repository.UserContentRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the form edits: an existing kanji / word, or a new word for "Từ của tôi". */
sealed interface EditTarget {
    data class Existing(val type: ItemType, val id: String) : EditTarget
    data object NewWord : EditTarget
}

/** One-shot outcome the screen reacts to (navigate back, or show a message and stay). */
enum class EditEvent { SAVED, REVERTED, DELETED }

enum class EditProblem { NOT_FOUND, SAVE_FAILED }

data class ContentEditUiState(
    val loading: Boolean = true,
    val item: EditableItem? = null,
    val fields: List<EditableField> = emptyList(),
    val values: Map<EditableField, String> = emptyMap(),
    val errors: Map<EditableField, FieldError> = emptyMap(),
    /** Set when the word already exists: its pack title. */
    val duplicateIn: String? = null,
    val problem: EditProblem? = null,
    val busy: Boolean = false,
    val event: EditEvent? = null,
) {
    val isNew get() = item == null && !loading && problem != EditProblem.NOT_FOUND
}

class ContentEditViewModel(
    private val target: EditTarget,
    private val repository: UserContentRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ContentEditUiState())
    val state: StateFlow<ContentEditUiState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            when (target) {
                EditTarget.NewWord -> _state.value = ContentEditUiState(
                    loading = false,
                    fields = EditableField.USER_WORD_FORM,
                    values = EditableField.USER_WORD_FORM.associateWith { "" },
                )
                is EditTarget.Existing -> {
                    val item = try {
                        repository.load(target.type, target.id)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                    _state.value = if (item == null) {
                        ContentEditUiState(loading = false, problem = EditProblem.NOT_FOUND)
                    } else {
                        ContentEditUiState(loading = false, item = item, fields = item.fields, values = item.values)
                    }
                }
            }
        }
    }

    fun onChange(field: EditableField, value: String) {
        _state.update { it.copy(values = it.values + (field to value), errors = it.errors - field, duplicateIn = null, problem = null) }
    }

    fun save() = launchAction {
        when (target) {
            EditTarget.NewWord -> repository.addWord(_state.value.values)
            is EditTarget.Existing -> repository.save(target.type, target.id, _state.value.values)
        }.let { result ->
            when (result) {
                is SaveResult.Saved -> _state.update { it.copy(event = EditEvent.SAVED) }
                is SaveResult.Invalid -> _state.update { it.copy(errors = result.errors) }
                is SaveResult.DuplicateWord -> _state.update { it.copy(duplicateIn = result.packTitle) }
                SaveResult.NotFound -> _state.update { it.copy(problem = EditProblem.NOT_FOUND) }
                SaveResult.Failed -> _state.update { it.copy(problem = EditProblem.SAVE_FAILED) }
            }
        }
    }

    fun revert() = launchAction {
        val existing = target as? EditTarget.Existing ?: return@launchAction
        if (repository.revert(existing.type, existing.id)) {
            _state.update { it.copy(event = EditEvent.REVERTED) }
        } else {
            _state.update { it.copy(problem = EditProblem.SAVE_FAILED) }
        }
    }

    fun delete() = launchAction {
        val existing = target as? EditTarget.Existing ?: return@launchAction
        if (repository.deleteWord(existing.id)) {
            _state.update { it.copy(event = EditEvent.DELETED) }
        } else {
            _state.update { it.copy(problem = EditProblem.SAVE_FAILED) }
        }
    }

    private fun launchAction(block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(problem = EditProblem.SAVE_FAILED) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }
}

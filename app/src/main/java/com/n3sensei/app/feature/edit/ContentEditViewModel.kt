package com.n3sensei.app.feature.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.n3sensei.app.domain.edit.EditableField
import com.n3sensei.app.domain.edit.EditableItem
import com.n3sensei.app.domain.edit.ExampleDraft
import com.n3sensei.app.domain.edit.ExampleField
import com.n3sensei.app.domain.edit.ExampleSaveResult
import com.n3sensei.app.domain.edit.KanjiExample
import com.n3sensei.app.domain.edit.KanjiExampleValidator
import com.n3sensei.app.domain.edit.FieldError
import com.n3sensei.app.domain.edit.KanjiChoice
import com.n3sensei.app.domain.edit.SaveResult
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.repository.UserContentRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the form edits: an existing kanji / word / grammar point, or a new word for "Từ của tôi". */
sealed interface EditTarget {
    data class Existing(val type: ItemType, val id: String) : EditTarget
    data object NewWord : EditTarget
}

/** One-shot outcome the screen reacts to (navigate back, or show a message and stay). */
enum class EditEvent { SAVED, REVERTED, DELETED }

enum class EditProblem { NOT_FOUND, SAVE_FAILED }

/**
 * The example dialog: [key] is the example being changed (null for a new one), [draft] what
 * is typed, [errors] from the last attempt to keep it.
 */
data class ExampleEditor(val key: String?, val draft: ExampleDraft, val errors: Map<ExampleField, FieldError> = emptyMap())

/** The word already exists: the learner opens it or saves anyway. */
data class DuplicateWarning(val existingId: String, val packTitle: String)

data class ContentEditUiState(
    val loading: Boolean = true,
    val item: EditableItem? = null,
    val fields: List<EditableField> = emptyList(),
    val values: Map<EditableField, String> = emptyMap(),
    val errors: Map<EditableField, FieldError> = emptyMap(),
    val duplicate: DuplicateWarning? = null,
    val problem: EditProblem? = null,
    val busy: Boolean = false,
    val event: EditEvent? = null,
    /** Words only: kanji the learner can link (from the word's characters and from lookups) and the chosen ones. */
    val kanjiChoices: List<KanjiChoice> = emptyList(),
    val linkedKanji: Set<String> = emptySet(),
    /** A looked-up character with no kanji in any pack. */
    val kanjiNotFound: String? = null,
    /**
     * Kanji only: its examples as they will be after Lưu (pack examples read only, then the
     * learner's; ids starting with [NEW_EXAMPLE] are not saved yet). Hủy discards the changes.
     */
    val examples: List<KanjiExample> = emptyList(),
    val deletedExamples: Set<String> = emptySet(),
    val changedExamples: Set<String> = emptySet(),
    val exampleEditor: ExampleEditor? = null,
) {
    val isKanji: Boolean get() = item?.type == ItemType.KANJI

    val isWord: Boolean get() = item?.type == ItemType.VOCABULARY || (item == null && fields.contains(EditableField.WORD_EXPRESSION))
}

/** Prefix of examples kept in the form but not saved yet. */
const val NEW_EXAMPLE = "new-"

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
                        ContentEditUiState(
                            loading = false, item = item, fields = item.fields, values = item.values,
                            kanjiChoices = item.linkedKanji, linkedKanji = item.linkedKanji.map { it.id }.toSet(),
                        )
                    }
                    if (item?.type == ItemType.VOCABULARY) addChoices(item.label)
                    if (item?.type == ItemType.KANJI) loadExamples(item.id)
                }
            }
        }
    }

    private suspend fun loadExamples(kanjiId: String) {
        val examples = try {
            repository.kanjiExamples(kanjiId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
        _state.update { it.copy(examples = examples) }
    }

    fun addExample() = _state.update { it.copy(exampleEditor = ExampleEditor(null, ExampleDraft())) }

    fun editExample(id: String) = _state.update { s ->
        val example = s.examples.firstOrNull { it.id == id && it.own } ?: return@update s
        s.copy(exampleEditor = ExampleEditor(id, example.toDraft()))
    }

    fun onExampleChange(draft: ExampleDraft) = _state.update { s ->
        s.copy(exampleEditor = s.exampleEditor?.copy(draft = draft, errors = emptyMap()))
    }

    fun dismissExampleEditor() = _state.update { it.copy(exampleEditor = null) }

    /** Checks the dialog's example and keeps it in the form (written on Lưu). */
    fun keepExample() = _state.update { s ->
        val editor = s.exampleEditor ?: return@update s
        val (valid, errors) = KanjiExampleValidator.validate(s.item?.label.orEmpty(), editor.draft)
        if (valid == null) return@update s.copy(exampleEditor = editor.copy(errors = errors))
        val key = editor.key ?: (NEW_EXAMPLE + s.examples.size + "-" + System.nanoTime())
        val example = KanjiExample(key, valid.japanese, valid.furigana, valid.meaningVi, own = true)
        val examples = if (editor.key == null) s.examples + example else s.examples.map { if (it.id == key) example else it }
        s.copy(
            examples = examples,
            changedExamples = if (editor.key == null || key.startsWith(NEW_EXAMPLE)) s.changedExamples else s.changedExamples + key,
            exampleEditor = null,
        )
    }

    /** Removes one of the learner's examples from the form (deleted on Lưu). */
    fun removeExample(id: String) = _state.update { s ->
        if (s.examples.none { it.id == id && it.own }) return@update s
        s.copy(
            examples = s.examples.filterNot { it.id == id },
            deletedExamples = if (id.startsWith(NEW_EXAMPLE)) s.deletedExamples else s.deletedExamples + id,
            changedExamples = s.changedExamples - id,
        )
    }

    /** Writes the kept example changes; false when one could not be written. */
    private suspend fun saveExamples(kanjiId: String): Boolean {
        val s = _state.value
        var ok = true
        s.deletedExamples.forEach { ok = repository.deleteKanjiExample(it) && ok }
        s.examples.filter { it.own && (it.id.startsWith(NEW_EXAMPLE) || it.id in s.changedExamples) }.forEach { e ->
            val id = e.id.takeUnless { it.startsWith(NEW_EXAMPLE) }
            ok = repository.saveKanjiExample(kanjiId, id, e.toDraft()) is ExampleSaveResult.Saved && ok
        }
        return ok
    }

    fun onChange(field: EditableField, value: String) {
        _state.update { it.copy(values = it.values + (field to value), errors = it.errors - field, duplicate = null, problem = null) }
        if (field == EditableField.WORD_EXPRESSION) viewModelScope.launch { addChoices(value) }
    }

    /** Offers the kanji of [text] for linking (keeps the ones already offered). */
    private suspend fun addChoices(text: String) {
        val found = try {
            repository.kanjiChoices(text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
        _state.update { s -> s.copy(kanjiChoices = (s.kanjiChoices + found).distinctBy { it.id }) }
    }

    /** Looks up a kanji typed by the learner (one that is not in the word). */
    fun lookUpKanji(text: String) {
        viewModelScope.launch {
            val before = _state.value.kanjiChoices.size
            addChoices(text)
            val found = _state.value.kanjiChoices.size > before || _state.value.kanjiChoices.any { it.character in text }
            _state.update { it.copy(kanjiNotFound = if (found) null else text.trim()) }
        }
    }

    fun toggleKanji(id: String) {
        _state.update { s -> s.copy(linkedKanji = if (id in s.linkedKanji) s.linkedKanji - id else s.linkedKanji + id) }
    }

    fun save(allowDuplicate: Boolean = false) = launchAction {
        val s = _state.value
        val links = if (s.isWord) s.linkedKanji else null
        when (target) {
            EditTarget.NewWord -> repository.addWord(s.values, s.linkedKanji, allowDuplicate)
            is EditTarget.Existing -> repository.save(target.type, target.id, s.values, links, allowDuplicate)
        }.let { result ->
            when (result) {
                is SaveResult.Saved -> {
                    val kanji = (target as? EditTarget.Existing)?.takeIf { it.type == ItemType.KANJI }
                    if (kanji == null || saveExamples(kanji.id)) {
                        _state.update { it.copy(event = EditEvent.SAVED) }
                    } else {
                        // Some example could not be written: stay, with the saved state reloaded.
                        _state.update { it.copy(problem = EditProblem.SAVE_FAILED, deletedExamples = emptySet(), changedExamples = emptySet()) }
                        loadExamples(kanji.id)
                    }
                }
                is SaveResult.Invalid -> _state.update { it.copy(errors = result.errors) }
                is SaveResult.DuplicateWord -> _state.update { it.copy(duplicate = DuplicateWarning(result.existingId, result.packTitle)) }
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

package com.n3sensei.app.data.repository

import com.n3sensei.app.data.local.ContentEdits
import com.n3sensei.app.data.local.StringListCodec
import com.n3sensei.app.data.local.dao.UserContentDao
import com.n3sensei.app.data.local.entity.KanjiEntity
import com.n3sensei.app.data.local.entity.UserContentEditEntity
import com.n3sensei.app.data.local.entity.VocabularyEntity
import com.n3sensei.app.data.local.withSearchText
import com.n3sensei.app.domain.edit.ContentEditValidator
import com.n3sensei.app.domain.edit.EditableField
import com.n3sensei.app.domain.edit.EditableItem
import com.n3sensei.app.domain.edit.FieldKind
import com.n3sensei.app.domain.edit.SaveResult
import com.n3sensei.app.domain.edit.UserWords
import com.n3sensei.app.domain.furigana.Furigana
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.repository.UserContentRepository
import kotlinx.coroutines.CancellationException
import java.time.Clock
import java.util.UUID

class OfflineUserContentRepository(
    private val dao: UserContentDao,
    private val clock: Clock,
    private val newId: () -> String = { UserWords.ID_PREFIX + UUID.randomUUID() },
) : UserContentRepository {

    // Form text ↔ stored value: only readings differ (、-joined in the form, codec in the column).
    private fun stored(field: EditableField, form: String) =
        if (field.kind == FieldKind.READINGS) StringListCodec.encode(ContentEditValidator.readings(form)) else form

    private fun form(field: EditableField, stored: String) =
        if (field.kind == FieldKind.READINGS) StringListCodec.decode(stored).joinToString("、") else stored

    override suspend fun load(type: ItemType, id: String): EditableItem? = when (type) {
        ItemType.KANJI -> dao.getKanji(id)?.let { row -> kanjiItem(row, dao.getEdits(type, id)) }
        ItemType.VOCABULARY -> dao.getVocabulary(id)?.let { row ->
            val own = dao.packOfVocabulary(id) == UserWords.PACK_ID
            wordItem(row, if (own) emptyList() else dao.getEdits(type, id), own)
        }
        ItemType.GRAMMAR -> null
    }

    private fun kanjiItem(row: KanjiEntity, edits: List<UserContentEditEntity>): EditableItem {
        val base = ContentEdits.base(row, edits)
        val fields = EditableField.KANJI_FORM
        return EditableItem(
            type = ItemType.KANJI, id = row.id, label = row.character, userCreated = false, fields = fields,
            values = fields.associateWith { form(it, ContentEdits.get(row, it.key)) },
            original = fields.associateWith { form(it, ContentEdits.get(base, it.key)) },
            edited = fields.filter { f -> edits.any { it.field == f.key } }.toSet(),
        )
    }

    private fun wordItem(row: VocabularyEntity, edits: List<UserContentEditEntity>, own: Boolean): EditableItem {
        val base = ContentEdits.base(row, edits)
        val fields = if (own) EditableField.USER_WORD_FORM else EditableField.PACK_WORD_FORM
        return EditableItem(
            type = ItemType.VOCABULARY, id = row.id, label = row.expression, userCreated = own, fields = fields,
            values = fields.associateWith { form(it, ContentEdits.get(row, it.key)) },
            original = fields.associateWith { form(it, ContentEdits.get(base, it.key)) },
            edited = fields.filter { f -> edits.any { it.field == f.key } }.toSet(),
        )
    }

    override suspend fun save(type: ItemType, id: String, values: Map<EditableField, String>): SaveResult = guarded {
        val (valid, errors) = ContentEditValidator.validateAll(values)
        if (errors.isNotEmpty()) return@guarded SaveResult.Invalid(errors)
        val now = clock.millis()
        when (type) {
            ItemType.KANJI -> {
                if (valid.keys.any { it !in EditableField.KANJI_FORM }) return@guarded SaveResult.Failed
                val ok = dao.editKanji(id, valid.entries.associate { (f, v) -> f.key to stored(f, v) }, now)
                if (ok) SaveResult.Saved(id) else SaveResult.NotFound
            }
            ItemType.VOCABULARY -> {
                val row = dao.getVocabulary(id) ?: return@guarded SaveResult.NotFound
                if (dao.packOfVocabulary(id) == UserWords.PACK_ID) {
                    saveOwnWord(row, valid)
                } else {
                    if (valid.keys.any { it !in EditableField.PACK_WORD_FORM }) return@guarded SaveResult.Failed
                    val changes = valid.entries.associate { (f, v) -> f.key to stored(f, v) }.toMutableMap()
                    // A new reading makes the pack's furigana wrong: hide it (kept as the base, back on revert).
                    val reading = changes[EditableField.WORD_READING.key]
                    val furigana = ContentEdits.base(row, dao.getEdits(type, id)).furigana
                    if (reading != null && furigana != null && Furigana.readingText(furigana) != reading) {
                        changes[ContentEdits.FURIGANA] = ""
                    } else if (reading != null) {
                        changes[ContentEdits.FURIGANA] = furigana.orEmpty()
                    }
                    dao.editVocabulary(id, changes, now)
                    SaveResult.Saved(id)
                }
            }
            ItemType.GRAMMAR -> SaveResult.Failed
        }
    }

    private suspend fun saveOwnWord(row: VocabularyEntity, valid: Map<EditableField, String>): SaveResult {
        val updated = valid.entries.fold(row) { r, (f, v) -> ContentEdits.set(r, f.key, stored(f, v)) }
        dao.findWord(updated.expression, updated.reading, exceptId = row.id)?.let { return SaveResult.DuplicateWord(it.id, it.packTitle) }
        dao.updateVocabulary(updated.withSearchText())
        return SaveResult.Saved(row.id)
    }

    override suspend fun revert(type: ItemType, id: String): Boolean = runCatchingDb {
        when (type) {
            ItemType.KANJI -> dao.revertKanji(id)
            ItemType.VOCABULARY -> dao.packOfVocabulary(id) != UserWords.PACK_ID && dao.revertVocabulary(id)
            ItemType.GRAMMAR -> false
        }
    } ?: false

    override suspend fun addWord(values: Map<EditableField, String>): SaveResult = guarded {
        val (valid, errors) = ContentEditValidator.validateAll(EditableField.USER_WORD_FORM.associateWith { values[it].orEmpty() })
        if (errors.isNotEmpty()) return@guarded SaveResult.Invalid(errors)
        val expression = valid.getValue(EditableField.WORD_EXPRESSION)
        val reading = valid.getValue(EditableField.WORD_READING)
        dao.findWord(expression, reading)?.let { return@guarded SaveResult.DuplicateWord(it.id, it.packTitle) }
        val id = newId()
        dao.addUserWord(
            VocabularyEntity(
                id = id, lessonId = UserWords.LESSON_ID, sourceId = UserWords.SOURCE_ID,
                expression = expression, reading = reading, furigana = null,
                meaningVi = valid.getValue(EditableField.WORD_MEANING),
                partOfSpeech = valid[EditableField.WORD_PART_OF_SPEECH]?.ifEmpty { null },
                jlptLevel = "N3", sortOrder = 0,
                note = valid[EditableField.WORD_NOTE]?.ifEmpty { null },
            ),
            clock.millis(),
        )
        SaveResult.Saved(id)
    }

    override suspend fun deleteWord(id: String): Boolean = runCatchingDb { dao.deleteUserWord(id) } ?: false

    override suspend fun countOrphanEdits(): Int = dao.countOrphanEdits()

    private inline fun guarded(block: () -> SaveResult): SaveResult = runCatchingDb(block) ?: SaveResult.Failed

    private inline fun <T> runCatchingDb(block: () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}

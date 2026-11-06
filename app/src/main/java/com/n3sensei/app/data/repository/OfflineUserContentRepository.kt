package com.n3sensei.app.data.repository

import com.n3sensei.app.data.local.ContentEdits
import com.n3sensei.app.data.local.StringListCodec
import com.n3sensei.app.data.local.dao.KanjiChoiceRow
import com.n3sensei.app.data.local.dao.UserContentDao
import com.n3sensei.app.data.local.entity.ExampleSentenceEntity
import com.n3sensei.app.data.local.entity.UserContentEditEntity
import com.n3sensei.app.data.local.entity.UserWordEntity
import com.n3sensei.app.data.local.entity.UserWordTranslationEntity
import com.n3sensei.app.data.local.entity.VocabularyEntity
import com.n3sensei.app.domain.edit.ContentEditValidator
import com.n3sensei.app.domain.edit.EditableField
import com.n3sensei.app.domain.edit.EditableItem
import com.n3sensei.app.domain.edit.ExampleDraft
import com.n3sensei.app.domain.edit.ExampleSaveResult
import com.n3sensei.app.domain.edit.FieldKind
import com.n3sensei.app.domain.edit.KanjiChoice
import com.n3sensei.app.domain.edit.KanjiExample
import com.n3sensei.app.domain.edit.KanjiExampleValidator
import com.n3sensei.app.domain.edit.SaveResult
import com.n3sensei.app.domain.edit.UserWords
import com.n3sensei.app.domain.edit.ValidExample
import com.n3sensei.app.domain.furigana.Furigana
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.repository.UserContentRepository
import com.n3sensei.app.domain.translate.BookmarkEntry
import com.n3sensei.app.domain.translate.BookmarkResult
import com.n3sensei.app.domain.translate.Language
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class OfflineUserContentRepository(
    private val dao: UserContentDao,
    private val clock: Clock,
    private val newId: () -> String = { UserWords.ID_PREFIX + UUID.randomUUID() },
    private val newExampleId: () -> String = { UserWords.KANJI_EXAMPLE_ID_PREFIX + UUID.randomUUID() },
) : UserContentRepository {

    // Form text ↔ stored value: only readings differ (、-joined in the form, codec in the column).
    private fun stored(field: EditableField, form: String) =
        if (field.kind == FieldKind.READINGS) StringListCodec.encode(ContentEditValidator.readings(form)) else form

    private fun form(field: EditableField, stored: String) =
        if (field.kind == FieldKind.READINGS) StringListCodec.decode(stored).joinToString("、") else stored

    private fun KanjiChoiceRow.toChoice() = KanjiChoice(id, character, hanViet, meaningVi, packTitle)

    override suspend fun load(type: ItemType, id: String): EditableItem? = when (type) {
        ItemType.KANJI -> dao.getKanji(id)?.let { row ->
            val edits = dao.getEdits(type, id)
            item(type, id, row.character, false, EditableField.KANJI_FORM, edits, { ContentEdits.get(row, it) }, { ContentEdits.get(ContentEdits.base(row, edits), it) })
        }
        ItemType.GRAMMAR -> dao.getGrammar(id)?.let { row ->
            val edits = dao.getEdits(type, id)
            item(type, id, row.pattern, false, EditableField.GRAMMAR_FORM, edits, { ContentEdits.get(row, it) }, { ContentEdits.get(ContentEdits.base(row, edits), it) })
        }
        ItemType.VOCABULARY -> dao.getVocabulary(id)?.let { row ->
            val own = dao.packOfVocabulary(id) == UserWords.PACK_ID
            val edits = if (own) emptyList() else dao.getEdits(type, id)
            val example = if (own) dao.examplesOf(id).firstOrNull() else null
            val fields = if (own) EditableField.USER_WORD_FORM else EditableField.PACK_WORD_FORM
            val read: (VocabularyEntity, String) -> String = { r, key ->
                when (key) {
                    EditableField.WORD_EXAMPLE_JA.key -> example?.japanese.orEmpty()
                    EditableField.WORD_EXAMPLE_VI.key -> example?.translationVi.orEmpty()
                    else -> ContentEdits.get(r, key)
                }
            }
            val base = ContentEdits.base(row, edits)
            item(type, id, row.expression, own, fields, edits, { read(row, it) }, { read(base, it) }).copy(
                linkedKanji = dao.linkedKanji(id).map { it.toChoice() },
                hasProgress = dao.countProgress(type, id) > 0,
                readingOptional = own && isSavedOnly(id),
            )
        }
    }

    private fun item(
        type: ItemType,
        id: String,
        label: String,
        own: Boolean,
        fields: List<EditableField>,
        edits: List<UserContentEditEntity>,
        current: (String) -> String,
        base: (String) -> String,
    ): EditableItem {
        val byKey = edits.associateBy { it.field }
        return EditableItem(
            type = type, id = id, label = label, userCreated = own, fields = fields,
            values = fields.associateWith { form(it, current(it.key)) },
            original = fields.associateWith { form(it, base(it.key)) },
            edited = fields.filter { it.key in byKey }.toSet(),
            changedInPack = fields.filter { byKey[it.key]?.baseChanged == true }.toSet(),
        )
    }

    override suspend fun save(
        type: ItemType,
        id: String,
        values: Map<EditableField, String>,
        linkedKanji: Set<String>?,
        allowDuplicate: Boolean,
    ): SaveResult = guarded {
        val readingOptional = type == ItemType.VOCABULARY && isSavedOnly(id)
        val (valid, errors) = ContentEditValidator.validateAll(values, readingOptional)
        if (errors.isNotEmpty()) return@guarded SaveResult.Invalid(errors)
        val now = clock.millis()
        val changes = { allowed: List<EditableField> ->
            if (valid.keys.any { it !in allowed }) null else valid.entries.associate { (f, v) -> f.key to stored(f, v) }
        }
        when (type) {
            ItemType.KANJI -> {
                val c = changes(EditableField.KANJI_FORM) ?: return@guarded SaveResult.Failed
                if (dao.editKanji(id, c, now)) SaveResult.Saved(id) else SaveResult.NotFound
            }
            ItemType.GRAMMAR -> {
                val c = changes(EditableField.GRAMMAR_FORM) ?: return@guarded SaveResult.Failed
                if (dao.editGrammar(id, c, now)) SaveResult.Saved(id) else SaveResult.NotFound
            }
            ItemType.VOCABULARY -> {
                val row = dao.getVocabulary(id) ?: return@guarded SaveResult.NotFound
                if (dao.packOfVocabulary(id) == UserWords.PACK_ID) {
                    saveOwnWord(row, valid, allowDuplicate, now)?.let { return@guarded it }
                } else {
                    val c = changes(EditableField.PACK_WORD_FORM)?.toMutableMap() ?: return@guarded SaveResult.Failed
                    // A new reading makes the pack's furigana wrong: hide it (kept as the base, back on revert).
                    val reading = c[EditableField.WORD_READING.key]
                    val furigana = ContentEdits.base(row, dao.getEdits(type, id)).furigana
                    if (reading != null) {
                        c[ContentEdits.FURIGANA] = if (furigana != null && Furigana.readingText(furigana) != reading) "" else furigana.orEmpty()
                    }
                    dao.editVocabulary(id, c, now)
                }
                linkedKanji?.let { dao.setLinks(id, it, now) }
                SaveResult.Saved(id)
            }
        }
    }

    /** A "Dịch nhanh" bookmark not studied (`SAVED`): never in flashcards, so a reading is optional. */
    private suspend fun isSavedOnly(id: String): Boolean = dao.getUserWord(id)?.studyState == UserWordEntity.SAVED

    /** Null when saved; otherwise the result to return (duplicate). */
    private suspend fun saveOwnWord(row: VocabularyEntity, valid: Map<EditableField, String>, allowDuplicate: Boolean, now: Long): SaveResult? {
        val updated = valid.entries
            .filter { it.key != EditableField.WORD_EXAMPLE_JA && it.key != EditableField.WORD_EXAMPLE_VI }
            .fold(row) { r, (f, v) -> ContentEdits.set(r, f.key, stored(f, v)) }
        if (!allowDuplicate && (updated.expression != row.expression || updated.reading != row.reading)) {
            dao.findWord(updated.expression, updated.reading, exceptId = row.id)?.let { return SaveResult.DuplicateWord(it.id, it.packTitle) }
        }
        val current = dao.examplesOf(row.id).firstOrNull()
        val example = when {
            EditableField.WORD_EXAMPLE_JA in valid -> example(valid)
            current != null -> current.japanese to current.translationVi
            else -> null
        }
        dao.updateUserWord(updated, example, now)
        return null
    }

    private fun example(valid: Map<EditableField, String>): Pair<String, String>? {
        val ja = valid[EditableField.WORD_EXAMPLE_JA].orEmpty()
        val vi = valid[EditableField.WORD_EXAMPLE_VI].orEmpty()
        return if (ja.isEmpty()) null else ja to vi
    }

    override suspend fun revert(type: ItemType, id: String): Boolean = runCatchingDb {
        when (type) {
            ItemType.KANJI -> dao.revertKanji(id)
            ItemType.GRAMMAR -> dao.revertGrammar(id)
            ItemType.VOCABULARY -> dao.packOfVocabulary(id) != UserWords.PACK_ID && dao.revertVocabulary(id)
        }
    } ?: false

    override suspend fun addWord(values: Map<EditableField, String>, linkedKanji: Set<String>, allowDuplicate: Boolean): SaveResult = guarded {
        val (valid, errors) = ContentEditValidator.validateAll(EditableField.USER_WORD_FORM.associateWith { values[it].orEmpty() })
        if (errors.isNotEmpty()) return@guarded SaveResult.Invalid(errors)
        val expression = valid.getValue(EditableField.WORD_EXPRESSION)
        val reading = valid.getValue(EditableField.WORD_READING)
        if (!allowDuplicate) {
            dao.findWord(expression, reading)?.let { return@guarded SaveResult.DuplicateWord(it.id, it.packTitle) }
        }
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
            example(valid),
            linkedKanji,
            clock.millis(),
        )
        SaveResult.Saved(id)
    }

    override suspend fun deleteWord(id: String): Boolean = runCatchingDb { dao.deleteUserWord(id) } ?: false

    override suspend fun kanjiChoices(text: String): List<KanjiChoice> {
        val characters = text.codePoints().toArray()
            .filter { Character.UnicodeScript.of(it) == Character.UnicodeScript.HAN }
            .map { String(Character.toChars(it)) }
            .distinct()
        return if (characters.isEmpty()) emptyList() else dao.kanjiByCharacters(characters).map { it.toChoice() }
    }

    override suspend fun countOrphans(): Int = dao.countOrphans()

    override suspend fun kanjiExamples(kanjiId: String): List<KanjiExample> =
        dao.packKanjiExamples(kanjiId).map { KanjiExample(it.id, it.japanese, it.furigana, it.translationVi, own = false) } +
            dao.ownKanjiExamples(kanjiId).map { KanjiExample(it.id, it.japanese, it.furigana, it.translationVi, own = true) }

    override suspend fun saveKanjiExample(kanjiId: String, exampleId: String?, draft: ExampleDraft): ExampleSaveResult = try {
        val kanji = dao.getKanji(kanjiId)
        if (kanji == null) {
            ExampleSaveResult.NotFound
        } else {
            val (valid, errors) = KanjiExampleValidator.validate(kanji.character, draft)
            when {
                valid == null -> ExampleSaveResult.Invalid(errors)
                exampleId == null -> {
                    val id = newExampleId()
                    dao.addKanjiExample(kanjiId, valid.toEntity(id), clock.millis())
                    ExampleSaveResult.Saved(id)
                }
                dao.updateKanjiExample(valid.toEntity(exampleId), clock.millis()) -> ExampleSaveResult.Saved(exampleId)
                else -> ExampleSaveResult.NotFound
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ExampleSaveResult.Failed
    }

    private fun ValidExample.toEntity(id: String) =
        ExampleSentenceEntity(id, UserWords.PACK_ID, UserWords.SOURCE_ID, japanese, furigana, meaningVi)

    override suspend fun deleteKanjiExample(exampleId: String): Boolean = runCatchingDb { dao.deleteKanjiExample(exampleId) } ?: false

    private inline fun guarded(block: () -> SaveResult): SaveResult = runCatchingDb(block) ?: SaveResult.Failed

    private inline fun <T> runCatchingDb(block: () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    /** One bookmark at a time (two quick taps never race to create two words). */
    private val bookmarkWrites = Mutex()

    override suspend fun saveBookmark(entry: BookmarkEntry): BookmarkResult = try {
        bookmarkWrites.withLock {
            val now = clock.millis()
            val japanese = entry.japanese
            val row = VocabularyEntity(
                id = newId(), lessonId = UserWords.LESSON_ID, sourceId = UserWords.SOURCE_ID,
                expression = entry.headword,
                // Kana only for a Japanese word whose reading is known; never made up for English.
                reading = japanese?.reading.orEmpty(),
                furigana = null,
                meaningVi = entry.meaningVi.orEmpty(),
                partOfSpeech = null,
                // Unknown level: empty, never a made-up "N3".
                jlptLevel = "",
                sortOrder = 0,
                note = null,
            )
            val translations = entry.translations.map { t ->
                UserWordTranslationEntity(row.id, t.language.code, t.text, t.reading, null, 0, now)
            }
            val (id, created) = dao.saveBookmark(
                row, entry.sourceLanguage.code, entry.key, translations,
                entry.example?.let { it.japanese to it.translationVi }, now,
            )
            BookmarkResult.Saved(id, created)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        BookmarkResult.Failed
    }

    override fun observeBookmark(sourceLanguage: Language, key: String): Flow<String?> = dao.observeBookmark(sourceLanguage.code, key)
}

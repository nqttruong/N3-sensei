package com.n3sensei.app.data.importing

import com.n3sensei.app.data.local.entity.ContentPackEntity
import com.n3sensei.app.data.local.entity.ContentSourceEntity
import com.n3sensei.app.data.local.entity.ExampleSentenceEntity
import com.n3sensei.app.data.local.entity.GrammarEntity
import com.n3sensei.app.data.local.entity.GrammarExampleCrossRef
import com.n3sensei.app.data.local.entity.GrammarExerciseEntity
import com.n3sensei.app.data.local.entity.KanjiEntity
import com.n3sensei.app.data.local.entity.KanjiExampleCrossRef
import com.n3sensei.app.data.local.entity.LessonEntity
import com.n3sensei.app.data.local.entity.TagEntity
import com.n3sensei.app.data.local.entity.VocabularyEntity
import com.n3sensei.app.data.local.entity.VocabularyExampleCrossRef
import com.n3sensei.app.data.local.entity.VocabularyTagCrossRef
import com.n3sensei.app.data.local.model.ContentPackBundle
import com.n3sensei.app.domain.edit.UserWords
import com.n3sensei.app.domain.furigana.Furigana
import com.n3sensei.app.domain.importing.ImportCount
import com.n3sensei.app.domain.importing.ImportIssue
import com.n3sensei.app.domain.importing.ImportOutcome
import com.n3sensei.app.domain.importing.ImportRecordType
import com.n3sensei.app.domain.importing.ImportReport
import com.n3sensei.app.domain.importing.IssueCode
import com.n3sensei.app.domain.importing.IssueSeverity

/** Target size of one lesson unit; differences are reported as warnings only. */
data class LessonTarget(val vocabulary: Int = 30, val kanji: Int = 27, val grammar: Int = 7)

/**
 * Turns a parsed pack into database rows. Fatal problems (wrong format, no pack id, no valid
 * source or lesson) produce no bundle. Invalid records are skipped and reported; everything
 * else is kept. Pure Kotlin so it is tested on the JVM.
 */
class ContentPackValidator(
    private val importedAt: Long,
    private val target: LessonTarget = LessonTarget(),
) {
    class Result(val bundle: ContentPackBundle?, val report: ImportReport)

    private val issues = mutableListOf<ImportIssue>()
    private val counts = ImportRecordType.entries.associateWith { intArrayOf(0, 0) }

    private fun valid(type: ImportRecordType) = counts.getValue(type)[0]++
    private fun skipped(type: ImportRecordType, n: Int = 1) {
        counts.getValue(type)[1] += n
    }

    private fun issue(severity: IssueSeverity, code: IssueCode, location: String, detail: String? = null) {
        issues += ImportIssue(severity, code, location, detail)
    }

    private fun error(code: IssueCode, location: String, detail: String? = null) = issue(IssueSeverity.ERROR, code, location, detail)
    private fun warning(code: IssueCode, location: String, detail: String? = null) = issue(IssueSeverity.WARNING, code, location, detail)

    private fun report(outcome: ImportOutcome, pack: PackDto?) = ImportReport(
        outcome = outcome,
        packId = pack?.id,
        packTitle = pack?.title,
        packVersion = pack?.version,
        counts = counts.mapValues { (_, c) -> ImportCount(c[0], c[1]) }.filterValues { it.valid + it.skipped > 0 },
        issues = issues.toList(),
    )

    private fun fail(pack: PackDto?, code: IssueCode, location: String, detail: String? = null): Result {
        issue(IssueSeverity.FATAL, code, location, detail)
        return Result(null, report(ImportOutcome.FAILED, pack))
    }

    fun validate(dto: ContentPackDto): Result {
        if (dto.format != CONTENT_PACK_FORMAT) return fail(dto.pack, IssueCode.UNSUPPORTED_FORMAT, "format", dto.format)
        if (dto.formatVersion != CONTENT_PACK_FORMAT_VERSION) {
            return fail(dto.pack, IssueCode.UNSUPPORTED_FORMAT, "formatVersion", dto.formatVersion?.toString())
        }
        val pack = dto.pack ?: return fail(null, IssueCode.MISSING_FIELD, "pack")
        val packId = pack.id.clean() ?: return fail(pack, IssueCode.MISSING_FIELD, "pack.id")
        // Reserved for the learner's own words, which a pack must never replace.
        if (packId == UserWords.PACK_ID) return fail(pack, IssueCode.INVALID_VALUE, "pack.id", packId)
        val packTitle = pack.title.clean() ?: return fail(pack, IssueCode.MISSING_FIELD, "pack.title")
        val packVersion = pack.version ?: return fail(pack, IssueCode.MISSING_FIELD, "pack.version")
        if (packVersion < 1) return fail(pack, IssueCode.INVALID_VALUE, "pack.version", packVersion.toString())
        val jlptLevel = pack.jlptLevel.clean() ?: "N3"

        val sources = validateSources(dto.sources)
        if (sources.isEmpty()) return fail(pack, IssueCode.MISSING_FIELD, "sources")
        val sourceIds = sources.map { it.id }.toSet()
        val defaultSourceId = pack.defaultSourceId.clean() ?: sources.singleOrNull()?.id
        if (defaultSourceId == null || defaultSourceId !in sourceIds) {
            return fail(pack, IssueCode.UNKNOWN_SOURCE, "pack.defaultSourceId", pack.defaultSourceId)
        }
        fun resolveSource(sourceId: String?, location: String): String? {
            val id = sourceId.clean() ?: return defaultSourceId
            return id.takeIf { it in sourceIds } ?: run { error(IssueCode.UNKNOWN_SOURCE, location, id); null }
        }

        val tags = validateTags(dto.tags)
        val tagIds = tags.map { it.id }.toSet()
        val examples = validateExamples(dto.examples, packId, ::resolveSource)
        val exampleIds = examples.map { it.id }.toSet()

        val builder = BundleBuilder()
        val lessonIds = mutableSetOf<String>()
        val lessonNumbers = mutableSetOf<Int>()
        val itemIds = ItemIds()
        val kanjiLessons = mutableMapOf<String, String>()

        dto.lessons.forEachIndexed { index, lesson ->
            val loc = "lessons[$index]" + (lesson.id?.let { " (id=$it)" } ?: "")
            fun skipLesson(code: IssueCode, detail: String?) {
                error(code, loc, detail)
                skipped(ImportRecordType.LESSON)
                skipped(ImportRecordType.VOCABULARY, lesson.vocabulary.size)
                skipped(ImportRecordType.KANJI, lesson.kanji.size)
                skipped(ImportRecordType.GRAMMAR, lesson.grammar.size)
            }
            val id = lesson.id.clean() ?: return@forEachIndexed skipLesson(IssueCode.MISSING_FIELD, "id")
            val title = lesson.title.clean() ?: return@forEachIndexed skipLesson(IssueCode.MISSING_FIELD, "title")
            val number = lesson.number ?: return@forEachIndexed skipLesson(IssueCode.MISSING_FIELD, "number")
            if (number < 1) return@forEachIndexed skipLesson(IssueCode.INVALID_VALUE, "number=$number")
            if (!lessonIds.add(id)) return@forEachIndexed skipLesson(IssueCode.DUPLICATE_ID, id)
            if (!lessonNumbers.add(number)) return@forEachIndexed skipLesson(IssueCode.DUPLICATE_LESSON_NUMBER, number.toString())

            builder.lessons += LessonEntity(id, packId, number, title, jlptLevel, lesson.description.clean())
            valid(ImportRecordType.LESSON)

            val v = validateVocabulary(lesson, loc, id, jlptLevel, itemIds, tagIds, exampleIds, ::resolveSource, builder)
            val k = validateKanji(lesson, loc, id, itemIds, exampleIds, kanjiLessons, ::resolveSource, builder)
            val g = validateGrammar(lesson, loc, id, itemIds, exampleIds, ::resolveSource, builder)
            if (v != target.vocabulary || k != target.kanji || g != target.grammar) {
                warning(
                    IssueCode.LESSON_SIZE_DIFFERS,
                    loc,
                    "$v/$k/$g (${target.vocabulary}/${target.kanji}/${target.grammar})",
                )
            }
        }
        if (builder.lessons.isEmpty()) return fail(pack, IssueCode.NO_LESSONS, "lessons")

        val bundle = ContentPackBundle(
            sources = sources,
            pack = ContentPackEntity(packId, packTitle, packVersion, defaultSourceId, isSeed = false, importedAt = importedAt),
            lessons = builder.lessons,
            vocabulary = builder.vocabulary,
            kanji = builder.kanji,
            grammar = builder.grammar,
            examples = examples,
            vocabularyExamples = builder.vocabularyExamples,
            kanjiExamples = builder.kanjiExamples,
            grammarExamples = builder.grammarExamples,
            grammarExercises = builder.exercises,
            tags = tags,
            vocabularyTags = builder.vocabularyTags,
        )
        return Result(bundle, report(ImportOutcome.IMPORTED, pack))
    }

    private fun validateSources(sources: List<SourceDto>): List<ContentSourceEntity> {
        val ids = mutableSetOf<String>()
        return sources.mapIndexedNotNull { i, s ->
            val loc = "sources[$i]"
            val id = s.id.clean() ?: return@mapIndexedNotNull null.also { error(IssueCode.MISSING_FIELD, loc, "id") }
            val name = s.name.clean() ?: return@mapIndexedNotNull null.also { error(IssueCode.MISSING_FIELD, loc, "name") }
            val license = s.license.clean() ?: return@mapIndexedNotNull null.also { error(IssueCode.MISSING_FIELD, loc, "license") }
            if (!ids.add(id)) return@mapIndexedNotNull null.also { error(IssueCode.DUPLICATE_ID, loc, id) }
            ContentSourceEntity(id, name, s.url.clean(), license, s.attribution.clean(), s.licenseVerified)
        }
    }

    private fun validateTags(tags: List<TagDto>): List<TagEntity> {
        val ids = mutableSetOf<String>()
        val names = mutableSetOf<String>()
        return tags.mapIndexedNotNull { i, t ->
            val loc = "tags[$i]"
            fun skip(code: IssueCode, detail: String?) = null.also { error(code, loc, detail); skipped(ImportRecordType.TAG) }
            val id = t.id.clean() ?: return@mapIndexedNotNull skip(IssueCode.MISSING_FIELD, "id")
            val name = t.name.clean() ?: return@mapIndexedNotNull skip(IssueCode.MISSING_FIELD, "name")
            if (!ids.add(id)) return@mapIndexedNotNull skip(IssueCode.DUPLICATE_ID, id)
            if (!names.add(name)) return@mapIndexedNotNull skip(IssueCode.DUPLICATE_ENTRY, name)
            valid(ImportRecordType.TAG)
            TagEntity(id, name)
        }
    }

    private fun validateExamples(
        examples: List<ExampleDto>,
        packId: String,
        resolveSource: (String?, String) -> String?,
    ): List<ExampleSentenceEntity> {
        val ids = mutableSetOf<String>()
        return examples.mapIndexedNotNull { i, e ->
            val loc = "examples[$i]" + (e.id?.let { " (id=$it)" } ?: "")
            fun skip(code: IssueCode, detail: String?) = null.also { error(code, loc, detail); skipped(ImportRecordType.EXAMPLE) }
            val id = e.id.clean() ?: return@mapIndexedNotNull skip(IssueCode.MISSING_FIELD, "id")
            val japanese = e.japanese.clean() ?: return@mapIndexedNotNull skip(IssueCode.MISSING_FIELD, "japanese")
            val translation = e.translationVi.clean() ?: return@mapIndexedNotNull skip(IssueCode.MISSING_FIELD, "translationVi")
            val furigana = e.furigana.clean()
            if (furigana != null) {
                checkFurigana(furigana, base = japanese, reading = null)?.let { return@mapIndexedNotNull skip(it, furigana) }
            }
            if (!ids.add(id)) return@mapIndexedNotNull skip(IssueCode.DUPLICATE_ID, id)
            if (id.startsWith(UserWords.RESERVED_ID_PREFIX)) return@mapIndexedNotNull skip(IssueCode.INVALID_VALUE, id)
            val sourceId = resolveSource(e.sourceId, loc) ?: return@mapIndexedNotNull null.also { skipped(ImportRecordType.EXAMPLE) }
            valid(ImportRecordType.EXAMPLE)
            ExampleSentenceEntity(id, packId, sourceId, japanese, furigana, translation)
        }
    }

    private fun validateVocabulary(
        lesson: LessonDto,
        lessonLoc: String,
        lessonId: String,
        jlptLevel: String,
        itemIds: ItemIds,
        tagIds: Set<String>,
        exampleIds: Set<String>,
        resolveSource: (String?, String) -> String?,
        out: BundleBuilder,
    ): Int {
        var order = 0
        lesson.vocabulary.forEachIndexed { i, v ->
            val loc = "$lessonLoc.vocabulary[$i]" + (v.id?.let { " (id=$it)" } ?: "")
            fun skip(code: IssueCode, detail: String?) { error(code, loc, detail); skipped(ImportRecordType.VOCABULARY) }
            val id = v.id.clean() ?: return@forEachIndexed skip(IssueCode.MISSING_FIELD, "id")
            val expression = v.expression.clean() ?: return@forEachIndexed skip(IssueCode.MISSING_FIELD, "expression")
            val reading = v.reading.clean() ?: return@forEachIndexed skip(IssueCode.MISSING_FIELD, "reading")
            val meaning = v.meaningVi.clean() ?: return@forEachIndexed skip(IssueCode.MISSING_FIELD, "meaningVi")
            val furigana = v.furigana.clean()
            if (furigana != null) {
                checkFurigana(furigana, base = expression, reading = reading)?.let { return@forEachIndexed skip(it, furigana) }
            }
            if (id in itemIds.vocabulary) return@forEachIndexed skip(IssueCode.DUPLICATE_ID, id)
            if (id.startsWith(UserWords.RESERVED_ID_PREFIX)) return@forEachIndexed skip(IssueCode.INVALID_VALUE, id)
            // Same word and reading with another meaning is a different sense (e.g. しっかり
            // in two lessons of a textbook), not a duplicate.
            if (!itemIds.vocabularyEntries.add(Triple(expression, reading, meaning))) {
                return@forEachIndexed skip(IssueCode.DUPLICATE_ENTRY, "$expression ($reading): $meaning")
            }
            val sourceId = resolveSource(v.sourceId, loc) ?: return@forEachIndexed skipped(ImportRecordType.VOCABULARY)
            itemIds.vocabulary += id
            out.vocabulary += VocabularyEntity(
                id, lessonId, sourceId, expression, reading, furigana, meaning,
                v.partOfSpeech.clean(), jlptLevel, order++, v.note.clean(),
            )
            v.examples.distinct().forEachIndexed { n, ref ->
                if (ref in exampleIds) out.vocabularyExamples += VocabularyExampleCrossRef(id, ref, n) else warning(IssueCode.UNKNOWN_EXAMPLE, loc, ref)
            }
            v.tags.distinct().forEach { ref ->
                if (ref in tagIds) out.vocabularyTags += VocabularyTagCrossRef(id, ref) else warning(IssueCode.UNKNOWN_TAG, loc, ref)
            }
            valid(ImportRecordType.VOCABULARY)
        }
        return order
    }

    private fun validateKanji(
        lesson: LessonDto,
        lessonLoc: String,
        lessonId: String,
        itemIds: ItemIds,
        exampleIds: Set<String>,
        kanjiLessons: MutableMap<String, String>,
        resolveSource: (String?, String) -> String?,
        out: BundleBuilder,
    ): Int {
        var order = 0
        val inLesson = mutableSetOf<String>()
        lesson.kanji.forEachIndexed { i, k ->
            val loc = "$lessonLoc.kanji[$i]" + (k.id?.let { " (id=$it)" } ?: "")
            fun skip(code: IssueCode, detail: String?) { error(code, loc, detail); skipped(ImportRecordType.KANJI) }
            val id = k.id.clean() ?: return@forEachIndexed skip(IssueCode.MISSING_FIELD, "id")
            val character = k.character.clean() ?: return@forEachIndexed skip(IssueCode.MISSING_FIELD, "character")
            if (!isSingleKanji(character)) return@forEachIndexed skip(IssueCode.INVALID_KANJI_CHARACTER, character)
            val meaning = k.meaningVi.clean() ?: return@forEachIndexed skip(IssueCode.MISSING_FIELD, "meaningVi")
            if (id in itemIds.kanji) return@forEachIndexed skip(IssueCode.DUPLICATE_ID, id)
            if (!inLesson.add(character)) return@forEachIndexed skip(IssueCode.DUPLICATE_ENTRY, character)
            val sourceId = resolveSource(k.sourceId, loc) ?: return@forEachIndexed skipped(ImportRecordType.KANJI)
            kanjiLessons[character]?.let { other -> warning(IssueCode.DUPLICATE_ENTRY, loc, "$character ($other)") }
            kanjiLessons.putIfAbsent(character, lessonId)
            val strokes = k.strokeCount?.takeIf { it in 1..MAX_STROKES }
            if (k.strokeCount != null && strokes == null) warning(IssueCode.INVALID_VALUE, loc, "strokeCount=${k.strokeCount}")
            itemIds.kanji += id
            out.kanji += KanjiEntity(
                id, lessonId, sourceId, character,
                k.onReadings.mapNotNull { it.clean() }, k.kunReadings.mapNotNull { it.clean() },
                k.hanViet.clean(), meaning, strokes, order++, k.note.clean(),
            )
            k.examples.distinct().forEachIndexed { n, ref ->
                if (ref in exampleIds) out.kanjiExamples += KanjiExampleCrossRef(id, ref, n) else warning(IssueCode.UNKNOWN_EXAMPLE, loc, ref)
            }
            valid(ImportRecordType.KANJI)
        }
        return order
    }

    private fun validateGrammar(
        lesson: LessonDto,
        lessonLoc: String,
        lessonId: String,
        itemIds: ItemIds,
        exampleIds: Set<String>,
        resolveSource: (String?, String) -> String?,
        out: BundleBuilder,
    ): Int {
        var order = 0
        lesson.grammar.forEachIndexed { i, g ->
            val loc = "$lessonLoc.grammar[$i]" + (g.id?.let { " (id=$it)" } ?: "")
            fun skip(code: IssueCode, detail: String?) {
                error(code, loc, detail)
                skipped(ImportRecordType.GRAMMAR)
                skipped(ImportRecordType.EXERCISE, g.exercises.size)
            }
            val id = g.id.clean() ?: return@forEachIndexed skip(IssueCode.MISSING_FIELD, "id")
            val pattern = g.pattern.clean() ?: return@forEachIndexed skip(IssueCode.MISSING_FIELD, "pattern")
            val structure = g.structure.clean() ?: return@forEachIndexed skip(IssueCode.MISSING_FIELD, "structure")
            val meaning = g.meaningVi.clean() ?: return@forEachIndexed skip(IssueCode.MISSING_FIELD, "meaningVi")
            if (id in itemIds.grammar) return@forEachIndexed skip(IssueCode.DUPLICATE_ID, id)
            val sourceId = resolveSource(g.sourceId, loc) ?: return@forEachIndexed run {
                skipped(ImportRecordType.GRAMMAR)
                skipped(ImportRecordType.EXERCISE, g.exercises.size)
            }
            itemIds.grammar += id
            out.grammar += GrammarEntity(id, lessonId, sourceId, pattern, structure, meaning, g.usage.clean().orEmpty(), g.cautionNote.clean(), order++)
            g.examples.distinct().forEachIndexed { n, ref ->
                if (ref in exampleIds) out.grammarExamples += GrammarExampleCrossRef(id, ref, n) else warning(IssueCode.UNKNOWN_EXAMPLE, loc, ref)
            }
            var exerciseOrder = 0
            g.exercises.forEachIndexed { e, ex ->
                val exLoc = "$loc.exercises[$e]"
                val exId = ex.id.clean()
                val prompt = ex.prompt.clean()
                val options = ex.options.map { it.trim() }
                val answer = ex.answerIndex
                val problem = when {
                    exId == null -> "id"
                    prompt == null -> "prompt"
                    options.size < 2 || options.any { it.isEmpty() } -> "options"
                    options.toSet().size != options.size -> "options"
                    answer == null || answer !in options.indices -> "answerIndex"
                    !itemIds.exercises.add(exId) -> "id=$exId"
                    !Furigana.isValid(prompt) -> "prompt"
                    options.any { !Furigana.isValid(it) } -> "options"
                    else -> null
                }
                if (problem != null) {
                    error(IssueCode.INVALID_EXERCISE, exLoc, problem)
                    skipped(ImportRecordType.EXERCISE)
                } else {
                    out.exercises += GrammarExerciseEntity(exId!!, id, exerciseOrder++, prompt!!, options, answer!!, ex.explanationVi.clean())
                    valid(ImportRecordType.EXERCISE)
                }
            }
            valid(ImportRecordType.GRAMMAR)
        }
        return order
    }

    /** Null when [markup] is valid ruby whose base equals [base] (and reading equals [reading]). */
    private fun checkFurigana(markup: String, base: String, reading: String?): IssueCode? {
        if (!Furigana.isValid(markup)) return IssueCode.INVALID_FURIGANA
        if (Furigana.baseText(markup) != base) return IssueCode.FURIGANA_MISMATCH
        if (reading != null && Furigana.readingText(markup) != reading) return IssueCode.FURIGANA_MISMATCH
        return null
    }

    private fun isSingleKanji(text: String): Boolean {
        if (text.codePointCount(0, text.length) != 1) return false
        return Character.UnicodeScript.of(text.codePointAt(0)) == Character.UnicodeScript.HAN
    }

    private class ItemIds {
        val vocabulary = mutableSetOf<String>()
        /** expression, reading, meaning */
        val vocabularyEntries = mutableSetOf<Triple<String, String, String>>()
        val kanji = mutableSetOf<String>()
        val grammar = mutableSetOf<String>()
        val exercises = mutableSetOf<String>()
    }

    private class BundleBuilder {
        val lessons = mutableListOf<LessonEntity>()
        val vocabulary = mutableListOf<VocabularyEntity>()
        val kanji = mutableListOf<KanjiEntity>()
        val grammar = mutableListOf<GrammarEntity>()
        val exercises = mutableListOf<GrammarExerciseEntity>()
        val vocabularyExamples = mutableListOf<VocabularyExampleCrossRef>()
        val kanjiExamples = mutableListOf<KanjiExampleCrossRef>()
        val grammarExamples = mutableListOf<GrammarExampleCrossRef>()
        val vocabularyTags = mutableListOf<VocabularyTagCrossRef>()
    }

    private companion object {
        const val MAX_STROKES = 40
    }
}

/** Trimmed value, or null when missing or blank. */
private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

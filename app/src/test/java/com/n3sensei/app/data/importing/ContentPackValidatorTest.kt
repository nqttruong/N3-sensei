package com.n3sensei.app.data.importing

import com.n3sensei.app.domain.importing.ImportOutcome
import com.n3sensei.app.domain.importing.ImportRecordType
import com.n3sensei.app.domain.importing.ImportReport
import com.n3sensei.app.domain.importing.IssueCode
import com.n3sensei.app.domain.importing.IssueSeverity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentPackValidatorTest {

    private val source = SourceDto(id = "src", name = "Nguồn", license = "CC BY-SA 4.0")

    private fun vocab(id: String, expression: String = "経験", reading: String = "けいけん", furigana: String? = "{経験|けいけん}") =
        VocabularyDto(id = id, expression = expression, reading = reading, furigana = furigana, meaningVi = "kinh nghiệm")

    private fun pack(
        lessons: List<LessonDto> = listOf(LessonDto(id = "l1", number = 1, title = "Bài 1", vocabulary = listOf(vocab("v1")))),
        examples: List<ExampleDto> = emptyList(),
        tags: List<TagDto> = emptyList(),
        sources: List<SourceDto> = listOf(source),
        format: String = CONTENT_PACK_FORMAT,
        packDto: PackDto? = PackDto(id = "p", title = "Pack", version = 1),
    ) = ContentPackDto(format, CONTENT_PACK_FORMAT_VERSION, packDto, sources, tags, examples, lessons)

    private fun validate(dto: ContentPackDto) = ContentPackValidator(importedAt = 1L).validate(dto)

    private fun ImportReport.codes(severity: IssueSeverity) = issues.filter { it.severity == severity }.map { it.code }

    @Test
    fun validPack_producesBundleWithDefaultSourceAndSortOrder() {
        val result = validate(
            pack(lessons = listOf(LessonDto(id = "l1", number = 1, title = "Bài 1", vocabulary = listOf(vocab("v1"), vocab("v2", "増える", "ふえる", "{増|ふ}える"))))),
        )
        val bundle = assertNotNullBundle(result)
        assertEquals(ImportOutcome.IMPORTED, result.report.outcome)
        assertEquals(listOf(0, 1), bundle.vocabulary.map { it.sortOrder })
        assertTrue(bundle.vocabulary.all { it.sourceId == "src" })
        assertEquals("N3", bundle.lessons.single().jlptLevel)
        assertEquals(2, result.report.counts.getValue(ImportRecordType.VOCABULARY).valid)
        // A one-lesson pack below the 30/27/7 target only gets a warning.
        assertEquals(listOf(IssueCode.LESSON_SIZE_DIFFERS), result.report.codes(IssueSeverity.WARNING))
    }

    @Test
    fun wrongFormat_orMissingPack_isFatal() {
        val wrongFormat = validate(pack(format = "something-else"))
        assertNull(wrongFormat.bundle)
        assertEquals(ImportOutcome.FAILED, wrongFormat.report.outcome)
        assertEquals(listOf(IssueCode.UNSUPPORTED_FORMAT), wrongFormat.report.codes(IssueSeverity.FATAL))

        assertNull(validate(pack(packDto = PackDto(id = " ", title = "x", version = 1))).bundle)
        assertNull(validate(pack(packDto = PackDto(id = "p", title = "x", version = 0))).bundle)
        assertNull(validate(pack(sources = emptyList())).bundle)
    }

    @Test
    fun noValidLesson_isFatal() {
        val result = validate(pack(lessons = listOf(LessonDto(id = "l1", number = 1, title = null))))
        assertNull(result.bundle)
        assertTrue(IssueCode.NO_LESSONS in result.report.codes(IssueSeverity.FATAL))
    }

    @Test
    fun recordsWithMissingFields_areSkippedAndCounted() {
        val result = validate(
            pack(
                lessons = listOf(
                    LessonDto(
                        id = "l1", number = 1, title = "Bài 1",
                        vocabulary = listOf(vocab("v1"), VocabularyDto(id = "v2", expression = "増える", reading = "ふえる")),
                        kanji = listOf(KanjiDto(id = "k1", character = "経", meaningVi = "kinh"), KanjiDto(id = "k2", character = "増")),
                        grammar = listOf(GrammarDto(id = "g1", pattern = "〜ばかり", meaningVi = "vừa mới")),
                    ),
                ),
            ),
        )
        val bundle = assertNotNullBundle(result)
        assertEquals(listOf("v1"), bundle.vocabulary.map { it.id })
        assertEquals(listOf("k1"), bundle.kanji.map { it.id })
        assertTrue(bundle.grammar.isEmpty())
        assertEquals(1, result.report.counts.getValue(ImportRecordType.VOCABULARY).skipped)
        assertEquals(1, result.report.counts.getValue(ImportRecordType.KANJI).skipped)
        assertEquals(1, result.report.counts.getValue(ImportRecordType.GRAMMAR).skipped)
        assertEquals(3, result.report.codes(IssueSeverity.ERROR).count { it == IssueCode.MISSING_FIELD })
    }

    @Test
    fun duplicateIdsAndEntries_keepFirstAndSkipTheRest() {
        val result = validate(
            pack(
                lessons = listOf(
                    LessonDto(id = "l1", number = 1, title = "Bài 1", vocabulary = listOf(vocab("v1"), vocab("v1", "増える", "ふえる", null), vocab("v9"))),
                    LessonDto(id = "l1", number = 2, title = "Trùng id"),
                    LessonDto(id = "l3", number = 1, title = "Trùng số bài"),
                ),
            ),
        )
        val bundle = assertNotNullBundle(result)
        assertEquals(listOf("l1"), bundle.lessons.map { it.id })
        assertEquals(listOf("v1"), bundle.vocabulary.map { it.id })
        val errors = result.report.codes(IssueSeverity.ERROR)
        assertTrue(IssueCode.DUPLICATE_ID in errors)
        assertTrue(IssueCode.DUPLICATE_ENTRY in errors) // v9 repeats 経験 (けいけん)
        assertTrue(IssueCode.DUPLICATE_LESSON_NUMBER in errors)
    }

    @Test
    fun furiganaMustBeWellFormedAndMatchTextAndReading() {
        val result = validate(
            pack(
                lessons = listOf(
                    LessonDto(
                        id = "l1", number = 1, title = "Bài 1",
                        vocabulary = listOf(
                            vocab("ok"),
                            vocab("broken", "会議", "かいぎ", "{会議|かいぎ"),
                            vocab("wrong-base", "資料", "しりょう", "{資科|しりょう}"),
                            vocab("wrong-reading", "給料", "きゅうりょう", "{給料|きゅりょう}"),
                        ),
                    ),
                ),
            ),
        )
        assertEquals(listOf("ok"), assertNotNullBundle(result).vocabulary.map { it.id })
        val errors = result.report.codes(IssueSeverity.ERROR)
        assertEquals(1, errors.count { it == IssueCode.INVALID_FURIGANA })
        assertEquals(2, errors.count { it == IssueCode.FURIGANA_MISMATCH })
    }

    @Test
    fun kanjiCharacterMustBeOneHanCharacter() {
        val result = validate(
            pack(
                lessons = listOf(
                    LessonDto(
                        id = "l1", number = 1, title = "Bài 1",
                        kanji = listOf(
                            KanjiDto(id = "k1", character = "経", meaningVi = "a"),
                            KanjiDto(id = "k2", character = "経験", meaningVi = "b"),
                            KanjiDto(id = "k3", character = "か", meaningVi = "c"),
                            KanjiDto(id = "k4", character = "𠮟", meaningVi = "d"), // supplementary-plane kanji
                        ),
                    ),
                ),
            ),
        )
        assertEquals(listOf("k1", "k4"), assertNotNullBundle(result).kanji.map { it.id })
        assertEquals(2, result.report.codes(IssueSeverity.ERROR).count { it == IssueCode.INVALID_KANJI_CHARACTER })
    }

    @Test
    fun unknownReferences_areDroppedWithWarnings_unknownSourceSkipsRecord() {
        val result = validate(
            pack(
                examples = listOf(ExampleDto(id = "e1", japanese = "経験がある。", translationVi = "Có kinh nghiệm.")),
                tags = listOf(TagDto("t1", "Công việc")),
                lessons = listOf(
                    LessonDto(
                        id = "l1", number = 1, title = "Bài 1",
                        vocabulary = listOf(
                            vocab("v1").copy(examples = listOf("e1", "missing"), tags = listOf("t1", "nope")),
                            vocab("v2", "増える", "ふえる", null).copy(sourceId = "unknown-source"),
                        ),
                    ),
                ),
            ),
        )
        val bundle = assertNotNullBundle(result)
        assertEquals(listOf("v1"), bundle.vocabulary.map { it.id })
        assertEquals(listOf("e1"), bundle.vocabularyExamples.map { it.exampleId })
        assertEquals(listOf("t1"), bundle.vocabularyTags.map { it.tagId })
        val warnings = result.report.codes(IssueSeverity.WARNING)
        assertTrue(IssueCode.UNKNOWN_EXAMPLE in warnings)
        assertTrue(IssueCode.UNKNOWN_TAG in warnings)
        assertTrue(IssueCode.UNKNOWN_SOURCE in result.report.codes(IssueSeverity.ERROR))
    }

    @Test
    fun invalidExercises_areSkippedButGrammarIsKept() {
        fun exercise(id: String, options: List<String>, answer: Int?) =
            ExerciseDto(id = id, prompt = "{日本|にほん}に＿＿ばかりです。", options = options, answerIndex = answer)
        val result = validate(
            pack(
                lessons = listOf(
                    LessonDto(
                        id = "l1", number = 1, title = "Bài 1",
                        grammar = listOf(
                            GrammarDto(
                                id = "g1", pattern = "〜たばかり", structure = "V-た + ばかり", meaningVi = "vừa mới",
                                exercises = listOf(
                                    exercise("q1", listOf("{来|き}た", "{来|く}る"), 0),
                                    exercise("q2", listOf("one"), 0),
                                    exercise("q3", listOf("a", "b"), 5),
                                    exercise("q4", listOf("a", "a"), 0),
                                    exercise("q1", listOf("a", "b"), 1),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val bundle = assertNotNullBundle(result)
        assertEquals(listOf("g1"), bundle.grammar.map { it.id })
        assertEquals(listOf("q1"), bundle.grammarExercises.map { it.id })
        assertEquals(4, result.report.counts.getValue(ImportRecordType.EXERCISE).skipped)
    }

    @Test
    fun parser_reportsInvalidJsonAsFatal() {
        val result = ContentPackJsonParser.parse("{ not json")
        assertTrue(result is ContentPackJsonParser.Result.Invalid)
        val report = (result as ContentPackJsonParser.Result.Invalid).report
        assertEquals(ImportOutcome.FAILED, report.outcome)
        assertEquals(IssueCode.INVALID_JSON, report.issues.single().code)
    }

    @Test
    fun parser_reportsWrongTypesAsFatal() {
        val result = ContentPackJsonParser.parse("""{"format":"n3sensei-content-pack","formatVersion":"one"}""")
        assertTrue(result is ContentPackJsonParser.Result.Invalid)
    }

    @Test
    fun parser_ignoresUnknownFields() {
        val result = ContentPackJsonParser.parse("""{"format":"n3sensei-content-pack","formatVersion":1,"future":true}""")
        assertTrue(result is ContentPackJsonParser.Result.Parsed)
    }

    private fun assertNotNullBundle(result: ContentPackValidator.Result) =
        assertNotNull("Expected a bundle, issues: ${result.report.issues}", result.bundle).let { result.bundle!! }
}

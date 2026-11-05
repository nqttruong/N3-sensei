package com.n3sensei.app.testing

import com.n3sensei.app.domain.exam.AnswerFormat
import com.n3sensei.app.domain.exam.ExamPassage
import com.n3sensei.app.domain.exam.ExamQuestion
import com.n3sensei.app.domain.exam.ExamSection
import com.n3sensei.app.domain.exam.ExamSectionKind
import com.n3sensei.app.domain.exam.ExamSet
import com.n3sensei.app.domain.exam.ExamType
import com.n3sensei.app.domain.exam.QuestionType
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType

/**
 * TEST DATA ONLY: a small exam set written for these tests (not a JLPT paper, never shipped).
 * It has every shape the schema must hold: a choice question, a typed answer, two questions
 * sharing a reading text, a question with a shared audio and one with its own audio.
 */
object ExamTestData {
    const val SET_ID = "test-exam-1"
    const val Q_KANJI = "q-kanji"
    const val Q_FILL = "q-fill"
    const val Q_READ_1 = "q-read-1"
    const val Q_READ_2 = "q-read-2"
    const val Q_LISTEN = "q-listen"
    const val Q_RESPONSE = "q-response"

    private fun question(
        id: String,
        section: String,
        type: QuestionType,
        order: Int,
        content: String,
        options: List<String> = listOf("A", "B", "C", "D"),
        answerIndex: Int? = 0,
        format: AnswerFormat = AnswerFormat.CHOICE,
        accepted: List<String> = emptyList(),
        passageId: String? = null,
        audioPath: String? = null,
        related: List<ItemRef> = emptyList(),
    ) = ExamQuestion(
        id = id, sectionId = section, type = type, format = format, content = content, contentFurigana = null,
        options = if (format == AnswerFormat.TEXT) emptyList() else options, answerIndex = if (format == AnswerFormat.TEXT) null else answerIndex,
        acceptedAnswers = accepted, explanation = "Giải thích $id", difficulty = 2, passageId = passageId, audioPath = audioPath,
        order = order, relatedItems = related,
    )

    fun set(title: String = "Đề kiểm thử", createdAt: Long = 1_000) = ExamSet(
        id = SET_ID, title = title, description = "Chỉ dùng cho kiểm thử", jlptLevel = "N3", type = ExamType.FULL,
        durationSeconds = 1_800, source = "Dữ liệu kiểm thử", createdAt = createdAt,
        sections = listOf(
            ExamSection("s-vocab", ExamSectionKind.VOCABULARY, 600, 1),
            ExamSection("s-grammar", ExamSectionKind.GRAMMAR, 600, 2),
            ExamSection("s-reading", ExamSectionKind.READING, 600, 3),
            ExamSection("s-listening", ExamSectionKind.LISTENING, null, 4),
        ),
        passages = listOf(
            ExamPassage("p-text", "Thông báo", "来週の月曜日は図書館が休みです。", null, null),
            ExamPassage("p-audio", null, null, null, "exam/test-exam-1/listening-1.mp3"),
        ),
        questions = listOf(
            question(
                Q_KANJI, "s-vocab", QuestionType.KANJI_READING, 1, "「経験」の読み方",
                options = listOf("けいけん", "きょうけん", "けいげん", "きょうげん"), answerIndex = 0,
                related = listOf(ItemRef(ItemType.VOCABULARY, "v-keiken"), ItemRef(ItemType.KANJI, "k-kei")),
            ),
            question(
                Q_FILL, "s-grammar", QuestionType.GRAMMAR_FORM, 1, "雨が降った＿＿、試合は中止になった。",
                format = AnswerFormat.TEXT, accepted = listOf("ため", "ために"), related = listOf(ItemRef(ItemType.GRAMMAR, "g-tame")),
            ),
            question(Q_READ_1, "s-reading", QuestionType.READING_SHORT, 1, "図書館が休みなのはいつですか。", passageId = "p-text", answerIndex = 1),
            question(Q_READ_2, "s-reading", QuestionType.READING_SHORT, 2, "この文章は何についてですか。", passageId = "p-text", answerIndex = 2),
            question(Q_LISTEN, "s-listening", QuestionType.LISTENING_POINT, 1, "男の人はこのあと何をしますか。", passageId = "p-audio", answerIndex = 3),
            question(Q_RESPONSE, "s-listening", QuestionType.LISTENING_RESPONSE, 2, "（音声を聞いて答えてください）", options = listOf("1", "2", "3"), answerIndex = 1, audioPath = "exam/test-exam-1/response-1.mp3"),
        ),
    )
}

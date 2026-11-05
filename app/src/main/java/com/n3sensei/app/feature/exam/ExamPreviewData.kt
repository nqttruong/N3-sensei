package com.n3sensei.app.feature.exam

import com.n3sensei.app.domain.exam.AnswerFormat
import com.n3sensei.app.domain.exam.AttemptStatus
import com.n3sensei.app.domain.exam.ExamAnswer
import com.n3sensei.app.domain.exam.ExamAttempt
import com.n3sensei.app.domain.exam.ExamPassage
import com.n3sensei.app.domain.exam.ExamQuestion
import com.n3sensei.app.domain.exam.ExamSection
import com.n3sensei.app.domain.exam.ExamSectionKind
import com.n3sensei.app.domain.exam.ExamSessionStatus
import com.n3sensei.app.domain.exam.ExamSet
import com.n3sensei.app.domain.exam.ExamType
import com.n3sensei.app.domain.exam.QuestionType

/** PREVIEW ONLY: a made-up set for Android Studio previews. Never shown in the app. */
internal object PreviewExam {
    private fun q(id: String, section: String, order: Int, content: String, passage: String? = null) = ExamQuestion(
        id, section, QuestionType.OTHER, AnswerFormat.CHOICE, content, null, listOf("けいけん", "きょうけん", "けいげん", "きょうげん"), 0,
        emptyList(), null, null, passage, null, order,
    )

    val set = ExamSet(
        id = "preview", title = "Đề xem trước", description = "Chỉ dùng cho bản xem trước giao diện.", jlptLevel = "N3",
        type = ExamType.FULL, durationSeconds = 1_800, source = null, createdAt = 0,
        sections = listOf(ExamSection("s1", ExamSectionKind.VOCABULARY, null, 1), ExamSection("s2", ExamSectionKind.READING, null, 2)),
        passages = listOf(ExamPassage("p1", null, "{来週|らいしゅう}の{月曜日|げつようび}は{図書館|としょかん}が{休|やす}みです。", null, null)),
        questions = (1..12).map { q("q$it", "s1", it, "「{経験|けいけん}」の{読|よ}み{方|かた}") } +
            q("q13", "s2", 1, "{図書館|としょかん}が{休|やす}みなのはいつですか。", passage = "p1"),
    )

    private val attempt = ExamAttempt("a", "preview", set.title, 0, null, 1_800, null, null, set.questions.size, AttemptStatus.IN_PROGRESS)

    val inProgress = ExamSessionUiState(
        status = ExamSessionStatus.IN_PROGRESS, set = set, attempt = attempt, currentIndex = 2,
        answers = listOf("q1", "q2", "q3").associateWith { ExamAnswer("a", it, 1, null, false, 1_000, null, 0, 0) },
        flagged = setOf("q2"), remainingMillis = 1_234_000,
    )
}

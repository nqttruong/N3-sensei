package com.n3sensei.app.feature.exam

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.n3sensei.app.R
import com.n3sensei.app.domain.exam.ExamSectionKind
import com.n3sensei.app.domain.exam.ExamSkill
import com.n3sensei.app.domain.exam.QuestionType

@StringRes
internal fun ExamSkill.labelRes(): Int = when (this) {
    ExamSkill.VOCABULARY -> R.string.exam_skill_vocabulary
    ExamSkill.KANJI -> R.string.exam_skill_kanji
    ExamSkill.GRAMMAR -> R.string.exam_skill_grammar
    ExamSkill.READING -> R.string.exam_skill_reading
    ExamSkill.LISTENING -> R.string.exam_skill_listening
}

@StringRes
internal fun ExamSectionKind.labelRes(): Int = when (this) {
    ExamSectionKind.VOCABULARY -> R.string.exam_section_vocabulary
    ExamSectionKind.GRAMMAR -> R.string.exam_section_grammar
    ExamSectionKind.READING -> R.string.exam_section_reading
    ExamSectionKind.LISTENING -> R.string.exam_section_listening
}

/** "45 phút", or "Không giới hạn giờ" for 0. */
@Composable
internal fun examDuration(seconds: Int): String =
    if (seconds > 0) stringResource(R.string.exam_duration_minutes, (seconds + 59) / 60) else stringResource(R.string.exam_duration_none)

/** Time used: "12 phút 5 giây", or "40 giây" under a minute. */
@Composable
internal fun examTimeSpent(seconds: Long): String =
    if (seconds >= 60) {
        stringResource(R.string.practice_result_duration_min_sec, (seconds / 60).toInt(), (seconds % 60).toInt())
    } else {
        stringResource(R.string.practice_result_duration_sec, seconds.coerceAtLeast(0).toInt())
    }

@StringRes
internal fun QuestionType.labelRes(): Int = when (this) {
    QuestionType.KANJI_READING -> R.string.exam_type_kanji_reading
    QuestionType.ORTHOGRAPHY -> R.string.exam_type_orthography
    QuestionType.CONTEXT -> R.string.exam_type_context
    QuestionType.PARAPHRASE -> R.string.exam_type_paraphrase
    QuestionType.USAGE -> R.string.exam_type_usage
    QuestionType.GRAMMAR_FORM -> R.string.exam_type_grammar_form
    QuestionType.SENTENCE_ORDER -> R.string.exam_type_sentence_order
    QuestionType.TEXT_GRAMMAR -> R.string.exam_type_text_grammar
    QuestionType.READING_SHORT -> R.string.exam_type_reading_short
    QuestionType.READING_MID -> R.string.exam_type_reading_mid
    QuestionType.READING_LONG -> R.string.exam_type_reading_long
    QuestionType.INFORMATION_SEARCH -> R.string.exam_type_information_search
    QuestionType.LISTENING_TASK -> R.string.exam_type_listening_task
    QuestionType.LISTENING_POINT -> R.string.exam_type_listening_point
    QuestionType.LISTENING_SUMMARY -> R.string.exam_type_listening_summary
    QuestionType.LISTENING_EXPRESSION -> R.string.exam_type_listening_expression
    QuestionType.LISTENING_RESPONSE -> R.string.exam_type_listening_response
    QuestionType.OTHER -> R.string.exam_type_other
}

/** Percent, rounded: 0.666 → 67. */
internal fun Double.percent(): Int = Math.round(this * 100).toInt()

/** Countdown text: "mm:ss", or "h:mm:ss" from an hour up. Rounded up, so 0:00 only when the time is up. */
internal fun formatCountdown(millis: Long): String {
    val total = (millis.coerceAtLeast(0) + 999) / 1_000
    val h = total / 3_600
    val m = total % 3_600 / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

/** Under five minutes left: the countdown turns to the warning colour. */
internal const val EXAM_TIME_WARNING_MILLIS = 5 * 60_000L

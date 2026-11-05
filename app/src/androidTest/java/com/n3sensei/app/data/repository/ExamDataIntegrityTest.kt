package com.n3sensei.app.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.data.local.N3Database
import com.n3sensei.app.domain.exam.AnswerFormat
import com.n3sensei.app.domain.exam.AnswerInput
import com.n3sensei.app.domain.exam.AnswerManager
import com.n3sensei.app.domain.exam.ExamGrading
import com.n3sensei.app.domain.exam.ExamSessionManager
import com.n3sensei.app.domain.exam.ExamSubmissionManager
import com.n3sensei.app.domain.mistakes.ErrorReviewSchedule
import com.n3sensei.app.domain.mistakes.ErrorReviewSessionManager
import com.n3sensei.app.testing.ExamTestData
import com.n3sensei.app.testing.MutableTestClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

/**
 * QA: random sequences of what a learner does (exams answered, submitted, timed out or left;
 * reviews right or wrong, early or late; sets edited or deleted; errors deleted), with the data
 * checked after every step: no orphan review item, one notebook entry per question, scores that
 * match the graded questions, earlier results never rewritten, schedules on the ladder.
 * TEST DATA ONLY.
 */
@RunWith(AndroidJUnit4::class)
class ExamDataIntegrityTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "exam-integrity-test.db"
    private val day = 24 * 60 * 60_000L
    private val clock = MutableTestClock(1_800_000_000_000)
    private var ids = 0
    private lateinit var db: N3Database
    private lateinit var exams: OfflineExamRepository
    private lateinit var errors: OfflineErrorRepository

    @Before
    fun setUp() {
        context.deleteDatabase(name)
        db = N3Database.create(context, name)
        exams = OfflineExamRepository(db, clock) { "attempt-${++ids}" }
        errors = OfflineErrorRepository(db, clock)
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(name)
    }

    private fun ints(sql: String): List<Int> = db.openHelper.readableDatabase.query(sql).use { c ->
        generateSequence { if (c.moveToNext()) c.getInt(0) else null }.toList()
    }

    private fun count(sql: String) = ints(sql).single()

    private fun rows(sql: String): List<String> = db.openHelper.readableDatabase.query(sql).use { c ->
        generateSequence { if (c.moveToNext()) (0 until c.columnCount).joinToString("|") { c.getString(it) ?: "∅" } else null }.toList()
    }

    /** Every invariant, after every step. */
    private suspend fun check(step: String, frozen: Map<String, List<String>>) {
        fun ok(what: String, condition: Boolean) = assertTrue("$step: $what", condition)
        db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { ok("foreign keys hold", !it.moveToFirst()) }
        ok("no orphan review item", count("SELECT COUNT(*) FROM review_items r LEFT JOIN error_records e ON e.id = r.error_record_id WHERE e.id IS NULL") == 0)
        ok("no orphan review log", count("SELECT COUNT(*) FROM error_review_log l LEFT JOIN error_records e ON e.id = l.error_record_id WHERE e.id IS NULL") == 0)
        ok("one notebook entry per question", count("SELECT COUNT(*) - COUNT(DISTINCT question_id) FROM error_records") == 0)
        ok("one review item per entry", count("SELECT COUNT(*) FROM error_records e WHERE (SELECT COUNT(*) FROM review_items r WHERE r.error_record_id = e.id) != 1") == 0)
        // Scores match the graded questions; in-progress and abandoned attempts have none.
        ok(
            "score = right graded questions",
            count(
                "SELECT COUNT(*) FROM exam_attempts a WHERE a.status IN ('SUBMITTED','EXPIRED') AND (a.correct_count != " +
                    "(SELECT COUNT(*) FROM exam_attempt_items i WHERE i.attempt_id = a.id AND i.outcome = 'CORRECT') OR a.question_count != " +
                    "(SELECT COUNT(*) FROM exam_attempt_items i WHERE i.attempt_id = a.id) OR a.submitted_at IS NULL)",
            ) == 0,
        )
        ok("an expired attempt lasted its limit", count("SELECT COUNT(*) FROM exam_attempts WHERE status = 'EXPIRED' AND duration_seconds != time_limit_seconds") == 0)
        ok("nothing graded before the submit", count("SELECT COUNT(*) FROM exam_attempt_items i JOIN exam_attempts a ON a.id = i.attempt_id WHERE a.status IN ('IN_PROGRESS','ABANDONED')") == 0)
        ok("an answer per graded answered question", count("SELECT COUNT(*) FROM exam_attempt_items i LEFT JOIN exam_answers s ON s.attempt_id = i.attempt_id AND s.question_id = i.question_id WHERE (i.outcome = 'UNANSWERED') != (s.attempt_id IS NULL)") == 0)
        // Each notebook entry counts the attempts in which its question was missed since it was
        // made (an entry the learner deleted starts over when the question is missed again).
        ok(
            "error count = attempts missed",
            count(
                "SELECT COUNT(*) FROM error_records e WHERE e.error_count != (SELECT COUNT(*) FROM exam_attempt_items i JOIN exam_attempts a ON a.id = i.attempt_id " +
                    "WHERE i.question_id = e.question_id AND i.outcome != 'CORRECT' AND a.status IN ('SUBMITTED','EXPIRED') AND a.submitted_at >= e.first_wrong_at)",
            ) == 0,
        )
        // Schedules on the ladder.
        val ladder = (listOf(0L) + ErrorReviewSchedule.LADDER_DAYS.map { it * ErrorReviewSchedule.DAY_MINUTES }).joinToString(",")
        ok("intervals on the ladder", count("SELECT COUNT(*) FROM review_items WHERE interval_minutes NOT IN ($ladder)") == 0)
        ok("steps in range", count("SELECT COUNT(*) FROM review_items WHERE step_index < 0 OR step_index > ${ErrorReviewSchedule.LEVELS}") == 0)
        ok("successes ≤ reviews", count("SELECT COUNT(*) FROM review_items WHERE success_count > review_count") == 0)
        ok("mastery level = step", count("SELECT COUNT(*) FROM error_records e JOIN review_items r ON r.error_record_id = e.id WHERE e.mastery_level != r.step_index") == 0)
        ok("mastered ⇔ mastered review", count("SELECT COUNT(*) FROM error_records e JOIN review_items r ON r.error_record_id = e.id WHERE (e.status = 'MASTERED') != (r.review_status = 'MASTERED')") == 0)
        ok("an open error is due after its last miss", count("SELECT COUNT(*) FROM error_records e JOIN review_items r ON r.error_record_id = e.id WHERE e.status = 'ACTIVE' AND r.next_review_at < e.last_wrong_at") == 0)
        // Earlier results are never rewritten.
        frozen.forEach { (attempt, items) ->
            assertEquals("$step: result of $attempt rewritten", items, rows("SELECT * FROM exam_attempt_items WHERE attempt_id = '$attempt' ORDER BY position"))
        }
        // Answers graded as their stored question says.
        exams.getGradedHistory().filter { it.selectedIndex != null || it.textAnswer != null }.forEach { o ->
            val input = o.selectedIndex?.let { AnswerInput.Choice(it) } ?: AnswerInput.Text(o.textAnswer!!)
            ok("grade of ${o.question.id} in ${o.attemptId}", ExamGrading.isCorrect(o.question, input) == (o.outcome.name == "CORRECT"))
        }
    }

    @Test
    fun randomLearnerJourneys_keepTheDataConsistent() = runBlocking {
        repeat(3) { seed ->
            setUp()
            val random = Random(seed)
            val submission = ExamSubmissionManager(exams, clock)
            val sessions = ExamSessionManager(exams, submission)
            val answers = AnswerManager(exams, clock)
            val reviews = ErrorReviewSessionManager(errors, exams, clock)
            exams.saveExamSet(ExamTestData.set())
            val frozen = mutableMapOf<String, List<String>>()
            repeat(40) { step ->
                when (random.nextInt(10)) {
                    in 0..3 -> { // take the exam
                        val s = sessions.start(ExamTestData.SET_ID) ?: return@repeat
                        s.questions.forEach { q ->
                            when (random.nextInt(4)) {
                                0 -> Unit // blank
                                1 -> answers.answer(s.attempt.id, q, if (q.format == AnswerFormat.TEXT) AnswerInput.Text("ため") else AnswerInput.Choice(q.answerIndex!!), clock.millis())
                                else -> answers.answer(
                                    s.attempt.id, q,
                                    if (q.format == AnswerFormat.TEXT) AnswerInput.Text("のに") else AnswerInput.Choice(random.nextInt(q.options.size)),
                                    clock.millis(),
                                )
                            }
                        }
                        when (random.nextInt(4)) {
                            0 -> clock.advance(31 * 60_000L) // time runs out
                            1 -> exams.abandon(s.attempt.id)
                            else -> clock.advance(random.nextLong(1, 20) * 60_000)
                        }
                        submission.submit(s.attempt.id)
                        submission.submit(s.attempt.id) // a second submit changes nothing
                        rows("SELECT * FROM exam_attempt_items WHERE attempt_id = '${s.attempt.id}' ORDER BY position")
                            .takeIf { it.isNotEmpty() }?.let { frozen[s.attempt.id] = it }
                    }
                    in 4..6 -> { // a review session, answered at random
                        reviews.dueSession().forEach { card ->
                            val q = card.question.question
                            val input = if (q.format == AnswerFormat.TEXT) {
                                AnswerInput.Text(if (random.nextBoolean()) q.acceptedAnswers.first() else "x")
                            } else {
                                AnswerInput.Choice(if (random.nextBoolean()) q.answerIndex!! else random.nextInt(q.options.size))
                            }
                            reviews.answer(card, input)
                        }
                    }
                    7 -> errors.observeErrors().first().randomOrNull(random)?.let { reviews.singleSession(it.id).firstOrNull()?.let { c -> reviews.answer(c, AnswerInput.Choice(0)) } }
                    8 -> exams.saveExamSet(ExamTestData.set(title = "Đề v$step")) // the set saved again
                    else -> errors.observeErrors().first().randomOrNull(random)?.let { exams.deleteError(it.id) }
                }
                clock.advance(random.nextLong(0, 5) * day + random.nextLong(0, day))
                check("seed $seed step $step", frozen)
            }
            assertFalse(frozen.isEmpty())
            tearDown()
        }
    }
}

package com.n3sensei.app.data.repository

import androidx.room.withTransaction
import com.n3sensei.app.data.local.N3Database
import com.n3sensei.app.data.local.dao.ExamSetBundle
import com.n3sensei.app.data.local.dao.WrongAnswer
import com.n3sensei.app.data.local.entity.ErrorRecordEntity
import com.n3sensei.app.data.local.entity.ExamAnswerEntity
import com.n3sensei.app.data.local.entity.ExamAttemptEntity
import com.n3sensei.app.data.local.entity.ExamFlagEntity
import com.n3sensei.app.data.local.entity.ExamPassageEntity
import com.n3sensei.app.data.local.entity.ExamQuestionEntity
import com.n3sensei.app.data.local.entity.ExamQuestionItemEntity
import com.n3sensei.app.data.local.entity.ExamSectionEntity
import com.n3sensei.app.data.local.entity.ExamSetEntity
import com.n3sensei.app.data.local.entity.ReviewItemEntity
import com.n3sensei.app.domain.exam.AnswerFormat
import com.n3sensei.app.domain.exam.AnswerInput
import com.n3sensei.app.domain.exam.AttemptResult
import com.n3sensei.app.domain.exam.AttemptStatus
import com.n3sensei.app.domain.exam.Confidence
import com.n3sensei.app.domain.exam.ErrorRecord
import com.n3sensei.app.domain.exam.ErrorReview
import com.n3sensei.app.domain.exam.ErrorStatus
import com.n3sensei.app.domain.exam.ErrorType
import com.n3sensei.app.domain.exam.ExamAnswer
import com.n3sensei.app.domain.exam.ExamAttempt
import com.n3sensei.app.domain.exam.ExamGrading
import com.n3sensei.app.domain.exam.ExamPassage
import com.n3sensei.app.domain.exam.ExamQuestion
import com.n3sensei.app.domain.exam.ExamSection
import com.n3sensei.app.domain.exam.ExamSectionKind
import com.n3sensei.app.domain.exam.ExamSet
import com.n3sensei.app.domain.exam.ExamSetFilter
import com.n3sensei.app.domain.exam.ExamSetSummary
import com.n3sensei.app.domain.exam.ExamSkill
import com.n3sensei.app.domain.exam.ExamTimer
import com.n3sensei.app.domain.exam.ExamType
import com.n3sensei.app.domain.exam.QuestionType
import com.n3sensei.app.domain.exam.ReviewItem
import com.n3sensei.app.domain.exam.ReviewStatus
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.repository.ExamRepository
import com.n3sensei.app.domain.repository.SaveExamResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Clock
import java.util.UUID

/**
 * Answers and submits read and write in one transaction: a submit and a late answer, or two
 * submits (the timer and the button), cannot interleave.
 */
class OfflineExamRepository(
    private val database: N3Database,
    private val clock: Clock,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : ExamRepository {

    private val dao = database.examDao()

    override fun observeExamSets(filter: ExamSetFilter): Flow<List<ExamSetSummary>> =
        combine(dao.observeSetSummaries(), dao.observeQuestionKinds()) { rows, kinds ->
            val skills = kinds.groupBy({ it.examSetId }) {
                ExamSkill.of(enumOr(it.questionType, QuestionType.OTHER), enumOr(it.section, ExamSectionKind.VOCABULARY))
            }
            rows.map {
                ExamSetSummary(
                    it.set.id, it.set.title, it.set.jlptLevel, enumOr(it.set.examType, ExamType.SECTION),
                    it.set.durationSeconds, it.questionCount, it.submittedAttempts, it.lastCorrect,
                    skills[it.set.id].orEmpty().toSet(),
                )
            }.filter(filter::matches)
        }

    override suspend fun getExamSet(id: String): ExamSet? = dao.getBundle(id)?.toDomain()

    override suspend fun getQuestions(examSetId: String, skill: ExamSkill?): List<ExamQuestion> {
        val set = getExamSet(examSetId) ?: return emptyList()
        return if (skill == null) set.questions else set.questions.filter { set.skillOf(it) == skill }
    }

    override suspend fun saveExamSet(set: ExamSet): SaveExamResult {
        val problems = set.questions.associate { it.id to ExamGrading.problems(it) }.filterValues { it.isNotEmpty() }.toMutableMap()
        val sectionIds = set.sections.map { it.id }.toSet()
        val passageIds = set.passages.map { it.id }.toSet()
        set.questions.forEach { q ->
            val more = buildList {
                if (q.sectionId !in sectionIds) add("sectionId")
                if (q.passageId != null && q.passageId !in passageIds) add("passageId")
            }
            if (more.isNotEmpty()) problems[q.id] = problems[q.id].orEmpty() + more
        }
        if (set.questions.map { it.id }.toSet().size != set.questions.size) problems["*"] = listOf("duplicateQuestionId")
        if (problems.isNotEmpty()) return SaveExamResult.Invalid(problems)
        dao.replaceSet(set.toBundle())
        return SaveExamResult.Saved
    }

    override suspend fun deleteExamSet(id: String): Boolean = dao.deleteSet(id) > 0

    override suspend fun startAttempt(examSetId: String): ExamAttempt? {
        val set = dao.getBundle(examSetId) ?: return null
        val row = ExamAttemptEntity(
            id = newId(), examSetId = set.set.id, examTitle = set.set.title, startedAt = clock.millis(), submittedAt = null,
            timeLimitSeconds = set.set.durationSeconds, durationSeconds = null, correctCount = null,
            questionCount = set.questions.size, status = AttemptStatus.IN_PROGRESS.name,
            currentQuestionId = set.questions.firstOrNull()?.id,
        )
        dao.insertAttempt(row)
        return row.toDomain()
    }

    override suspend fun answer(
        attemptId: String,
        questionId: String,
        input: AnswerInput,
        timeSpentMs: Long,
        confidence: Confidence?,
    ): ExamAnswer? = database.withTransaction {
        val attempt = openAttempt(attemptId) ?: return@withTransaction null
        val entity = dao.getQuestion(questionId)?.takeIf { it.examSetId == attempt.examSetId } ?: return@withTransaction null
        val question = entity.toDomain(dao.getItemsOf(questionId))
        val previous = dao.getAnswer(attemptId, questionId)
        val selected = (input as? AnswerInput.Choice)?.index
        val text = (input as? AnswerInput.Text)?.value
        val changed = previous != null && (previous.selectedIndex != selected || previous.textAnswer != text)
        val row = ExamAnswerEntity(
            attemptId = attemptId, questionId = questionId, selectedIndex = selected, textAnswer = text,
            isCorrect = ExamGrading.isCorrect(question, input),
            // Time adds up when the learner comes back to a question.
            timeSpentMs = (previous?.timeSpentMs ?: 0) + timeSpentMs.coerceAtLeast(0),
            confidence = confidence?.name, changeCount = (previous?.changeCount ?: 0) + if (changed) 1 else 0,
            answeredAt = clock.millis(), questionSnapshot = snapshot(question),
        )
        dao.upsertAnswer(row)
        row.toDomain()
    }

    /** The attempt if it still takes changes: in progress and within its time. */
    private suspend fun openAttempt(attemptId: String): ExamAttemptEntity? =
        dao.getAttempt(attemptId)?.takeIf {
            it.status == AttemptStatus.IN_PROGRESS.name && !ExamTimer.isExpired(it.toDomain(), clock.millis())
        }

    override suspend fun submit(attemptId: String): AttemptResult? = database.withTransaction {
        val attempt = dao.getAttempt(attemptId)?.takeIf { it.status == AttemptStatus.IN_PROGRESS.name } ?: return@withTransaction null
        // Past the deadline (e.g. the app was closed), the attempt ends at its deadline: no answer
        // after it was accepted, and the time used is the limit.
        val deadline = ExamTimer.deadline(attempt.toDomain())
        val expired = deadline != null && clock.millis() >= deadline
        val now = if (expired) deadline!! else clock.millis()
        val answers = dao.getAnswers(attemptId).associateBy { it.questionId }
        // The set as it is now; questions removed since the start are graded from the answers alone.
        val bundle = dao.getBundle(attempt.examSetId)
        val itemsByQuestion = bundle?.items.orEmpty().groupBy { it.questionId }
        val questions = bundle?.questions.orEmpty()
        val wrong = buildList {
            questions.forEach { q ->
                val answer = answers[q.id]
                if (answer == null || !answer.isCorrect) {
                    val type = if (answer == null) ErrorType.UNANSWERED else ErrorType.INCORRECT
                    add(WrongAnswer(q.id, q.examSetId, type.name, answer?.questionSnapshot ?: snapshot(q.toDomain(itemsByQuestion[q.id].orEmpty()))))
                }
            }
            val inSet = questions.map { it.id }.toSet()
            answers.values.filter { it.questionId !in inSet && !it.isCorrect }
                .forEach { add(WrongAnswer(it.questionId, attempt.examSetId, ErrorType.INCORRECT.name, it.questionSnapshot)) }
        }
        val questionCount = if (bundle == null) attempt.questionCount else questions.size
        val updated = attempt.copy(
            submittedAt = now,
            durationSeconds = ((now - attempt.startedAt) / 1000).coerceAtLeast(0),
            correctCount = answers.values.count { it.isCorrect },
            questionCount = questionCount,
            status = if (expired) AttemptStatus.EXPIRED.name else AttemptStatus.SUBMITTED.name,
        )
        dao.submit(updated, wrong, now)
        AttemptResult(updated.toDomain(), dao.getAnswers(attemptId).map { it.toDomain() })
    }

    override suspend fun setFlag(attemptId: String, questionId: String, flagged: Boolean): Boolean = database.withTransaction {
        val attempt = openAttempt(attemptId) ?: return@withTransaction false
        if (dao.getQuestion(questionId)?.examSetId != attempt.examSetId) return@withTransaction false
        if (flagged) dao.upsertFlag(ExamFlagEntity(attemptId, questionId, clock.millis())) else dao.deleteFlag(attemptId, questionId)
        true
    }

    override suspend fun getFlags(attemptId: String): List<String> = dao.getFlags(attemptId)

    override suspend fun setCurrentQuestion(attemptId: String, questionId: String): Boolean =
        dao.setCurrentQuestion(attemptId, questionId) > 0

    override suspend fun getInProgressAttempts(): List<ExamAttempt> = dao.getInProgressAttempts().map { it.toDomain() }

    override suspend fun getResult(attemptId: String): AttemptResult? {
        val attempt = dao.getAttempt(attemptId) ?: return null
        return AttemptResult(attempt.toDomain(), dao.getAnswers(attemptId).map { it.toDomain() })
    }

    override suspend fun abandon(attemptId: String): Boolean {
        val attempt = dao.getAttempt(attemptId)?.takeIf { it.status == AttemptStatus.IN_PROGRESS.name } ?: return false
        dao.updateAttempt(attempt.copy(status = AttemptStatus.ABANDONED.name))
        return true
    }

    override suspend fun getAttempt(id: String): ExamAttempt? = dao.getAttempt(id)?.toDomain()

    override suspend fun getAnswers(attemptId: String): List<ExamAnswer> = dao.getAnswers(attemptId).map { it.toDomain() }

    override fun observeAttempts(examSetId: String?): Flow<List<ExamAttempt>> =
        dao.observeAttempts(examSetId).map { rows -> rows.map { it.toDomain() } }

    override fun observeErrors(status: ErrorStatus?): Flow<List<ErrorRecord>> =
        dao.observeErrors(status?.name).map { rows -> rows.map { it.toDomain() } }

    override suspend fun getErrorForQuestion(questionId: String): ErrorRecord? = dao.getErrorForQuestion(questionId)?.toDomain()

    override suspend fun setErrorNotes(errorId: Long, notes: String?): Boolean {
        val row = dao.getError(errorId) ?: return false
        dao.updateError(row.copy(notes = notes?.trim()?.ifEmpty { null }))
        return true
    }

    override suspend fun setErrorStatus(errorId: Long, status: ErrorStatus): Boolean {
        val row = dao.getError(errorId) ?: return false
        dao.updateError(row.copy(status = status.name))
        return true
    }

    override suspend fun deleteError(errorId: Long): Boolean = dao.deleteError(errorId) > 0

    override fun observeDueReviews(nowMillis: Long): Flow<List<ErrorReview>> =
        dao.observeDueReviews(nowMillis).map { rows -> rows.map { ErrorReview(it.error.toDomain(), it.review.toDomain()) } }

    // --- mapping ------------------------------------------------------------------------------

    private fun ExamSetBundle.toDomain(): ExamSet {
        val itemsByQuestion = items.groupBy { it.questionId }
        return ExamSet(
            id = set.id, title = set.title, description = set.description, jlptLevel = set.jlptLevel,
            type = enumOr(set.examType, ExamType.SECTION), durationSeconds = set.durationSeconds, source = set.source,
            createdAt = set.createdAt,
            sections = sections.map { ExamSection(it.id, enumOr(it.section, ExamSectionKind.VOCABULARY), it.durationSeconds, it.sectionOrder) },
            passages = passages.map { ExamPassage(it.id, it.title, it.text, it.furigana, it.audioPath) },
            questions = questions.map { it.toDomain(itemsByQuestion[it.id].orEmpty()) },
        )
    }

    private fun ExamSet.toBundle() = ExamSetBundle(
        set = ExamSetEntity(id, title, description, jlptLevel, type.name, durationSeconds, source, createdAt),
        sections = sections.map { ExamSectionEntity(it.id, id, it.kind.name, it.durationSeconds, it.order) },
        passages = passages.map { ExamPassageEntity(it.id, id, it.title, it.text, it.furigana, it.audioPath) },
        questions = questions.map { q ->
            ExamQuestionEntity(
                q.id, id, q.sectionId, q.type.name, q.format.name, q.content, q.contentFurigana, q.options, q.answerIndex,
                q.acceptedAnswers, q.explanation, q.difficulty, q.passageId, q.audioPath, q.order,
            )
        },
        items = questions.flatMap { q -> q.relatedItems.distinct().map { ExamQuestionItemEntity(q.id, it.type, it.id) } },
    )

    private fun ExamQuestionEntity.toDomain(items: List<ExamQuestionItemEntity>) = ExamQuestion(
        id = id, sectionId = sectionId, type = enumOr(questionType, QuestionType.OTHER), format = enumOr(answerFormat, AnswerFormat.CHOICE),
        content = content, contentFurigana = contentFurigana, options = options, answerIndex = answerIndex,
        acceptedAnswers = acceptedAnswers, explanation = explanation, difficulty = difficulty, passageId = passageId,
        audioPath = audioPath, order = questionOrder, relatedItems = items.map { ItemRef(it.itemType, it.itemId) },
    )

    private fun ExamAttemptEntity.toDomain() = ExamAttempt(
        id, examSetId, examTitle, startedAt, submittedAt, timeLimitSeconds, durationSeconds, correctCount, questionCount,
        enumOr(status, AttemptStatus.ABANDONED), currentQuestionId,
    )

    private fun ExamAnswerEntity.toDomain() = ExamAnswer(
        attemptId, questionId, selectedIndex, textAnswer, isCorrect, timeSpentMs,
        confidence?.let { c -> Confidence.entries.firstOrNull { it.name == c } }, changeCount, answeredAt,
    )

    private fun ErrorRecordEntity.toDomain() = ErrorRecord(
        id, questionId, examSetId, enumOr(errorType, ErrorType.UNKNOWN), errorCount, firstWrongAt, lastWrongAt, masteryLevel,
        enumOr(status, ErrorStatus.ACTIVE), notes, fromSnapshot(questionSnapshot),
    )

    private fun ReviewItemEntity.toDomain() = ReviewItem(
        id, errorRecordId, nextReviewAt, intervalMinutes, stepIndex, reviewCount, successCount, lastReviewedAt,
        enumOr(reviewStatus, ReviewStatus.NEW),
    )

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        private inline fun <reified E : Enum<E>> enumOr(name: String, fallback: E): E =
            enumValues<E>().firstOrNull { it.name == name } ?: fallback

        /** The question as answered, kept with answers and errors (JSON; stable field names). */
        internal fun snapshot(q: ExamQuestion): String = json.encodeToString(
            QuestionSnapshot.serializer(),
            QuestionSnapshot(
                q.id, q.sectionId, q.type.name, q.format.name, q.content, q.contentFurigana, q.options, q.answerIndex,
                q.acceptedAnswers, q.explanation, q.difficulty, q.passageId, q.audioPath, q.order,
                q.relatedItems.map { "${it.type.name}:${it.id}" },
            ),
        )

        internal fun fromSnapshot(text: String): ExamQuestion? = runCatching {
            val s = json.decodeFromString(QuestionSnapshot.serializer(), text)
            ExamQuestion(
                s.id, s.sectionId, enumOr(s.type, QuestionType.OTHER), enumOr(s.format, AnswerFormat.CHOICE), s.content, s.contentFurigana,
                s.options, s.answerIndex, s.acceptedAnswers, s.explanation, s.difficulty, s.passageId, s.audioPath, s.order,
                s.relatedItems.mapNotNull { ref ->
                    val type = ItemType.entries.firstOrNull { ref.startsWith(it.name + ":") } ?: return@mapNotNull null
                    ItemRef(type, ref.removePrefix(type.name + ":"))
                },
            )
        }.getOrNull()
    }
}

@Serializable
internal data class QuestionSnapshot(
    val id: String,
    val sectionId: String,
    val type: String,
    val format: String,
    val content: String,
    val contentFurigana: String? = null,
    val options: List<String> = emptyList(),
    val answerIndex: Int? = null,
    val acceptedAnswers: List<String> = emptyList(),
    val explanation: String? = null,
    val difficulty: Int? = null,
    val passageId: String? = null,
    val audioPath: String? = null,
    val order: Int = 0,
    val relatedItems: List<String> = emptyList(),
)

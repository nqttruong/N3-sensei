package com.n3sensei.app.domain.mistakes

import com.n3sensei.app.domain.course.ItemLocations
import com.n3sensei.app.domain.exam.ExamAttempt
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.repository.ContentRepository
import com.n3sensei.app.domain.repository.ExamRepository
import com.n3sensei.app.domain.repository.ProgressRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** The missed questions of one attempt, analysed. */
data class AttemptMistakes(val attempt: ExamAttempt, val analyses: List<MistakeAnalysis>)

/** Weak points over all exams, with the study items they name as found in the content now. */
data class WeaknessOverview(
    val weakness: WeaknessReport,
    val knowledge: Map<ItemRef, Knowledge>,
)

/** One question's mistakes: the analysis of its latest miss and every time it was graded. */
data class QuestionMistakes(
    /** Null when the question was never graded per question (missed before schema 11). */
    val analysis: MistakeAnalysis?,
    /** Oldest first. */
    val history: List<GradedOccurrence>,
)

/**
 * Gathers what the analysis needs (graded history from Room, study items from the content) and
 * hands it to the [ErrorAnalyzer]; the rules themselves live there and in [WeaknessAnalyzer].
 */
class MistakeAnalysisManager(
    private val exams: ExamRepository,
    private val content: ContentRepository,
    private val progress: ProgressRepository,
    private val analyzer: ErrorAnalyzer = RuleBasedErrorAnalyzer(),
) {

    suspend fun analyzeAttempt(attemptId: String): AttemptMistakes? = withContext(Dispatchers.Default) { analyze(attemptId) }

    private suspend fun analyze(attemptId: String): AttemptMistakes? {
        val record = exams.getRecord(attemptId) ?: return null
        val history = exams.getGradedHistory()
        val resolve = resolver()
        val analyses = history.filter { it.attemptId == attemptId && it.missed }.map { occurrence ->
            val passageAudio = occurrence.question.passageId?.let { record.passages[it]?.audioPath } != null
            analyzer.analyze(
                MistakeContext(
                    occurrence, history, occurrence.question.relatedItems.map { resolve(it) },
                    hasAudio = occurrence.question.audioPath != null || passageAudio,
                ),
            )
        }
        return AttemptMistakes(record.attempt, analyses)
    }

    suspend fun weakness(): WeaknessOverview = withContext(Dispatchers.Default) { computeWeakness() }

    private suspend fun computeWeakness(): WeaknessOverview {
        val history = exams.getGradedHistory()
        val resolve = resolver()
        val weakness = WeaknessAnalyzer.analyze(history)
        val refs = (weakness.frequentKnowledge + weakness.unresolved + weakness.improved).map { it.ref }.distinct()
        return WeaknessOverview(weakness, refs.associateWith { resolve(it) })
    }

    /** The latest miss of a question, analysed, with all its graded occurrences. */
    suspend fun questionMistakes(questionId: String): QuestionMistakes = withContext(Dispatchers.Default) { computeQuestion(questionId) }

    private suspend fun computeQuestion(questionId: String): QuestionMistakes {
        val history = exams.getGradedHistory()
        val mine = history.filter { it.question.id == questionId }
        val latest = mine.lastOrNull { it.missed } ?: return QuestionMistakes(null, mine)
        val resolve = resolver()
        val passageAudio = latest.question.passageId?.let { exams.getRecord(latest.attemptId)?.passages?.get(it)?.audioPath } != null
        val analysis = analyzer.analyze(
            MistakeContext(latest, history, latest.question.relatedItems.map { resolve(it) }, latest.question.audioPath != null || passageAudio),
        )
        return QuestionMistakes(analysis, mine)
    }

    /** Marks the words, so "Luyện các mục đã đánh dấu" practises them. */
    suspend fun markForPractice(refs: List<ItemRef>) {
        refs.forEach { progress.setMarked(it, true) }
    }

    /** Looks each study item up once, in the content as it is now. */
    private suspend fun resolver(): suspend (ItemRef) -> Knowledge {
        val locations = ItemLocations.observe(content).first()
        val cache = mutableMapOf<ItemRef, Knowledge>()
        return { ref -> cache.getOrPut(ref) { Knowledge(ref, content.getPracticeItem(ref), locations.of(ref)) } }
    }
}

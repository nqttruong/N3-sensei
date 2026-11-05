package com.n3sensei.app.domain.dashboard

import com.n3sensei.app.domain.course.ItemLocations
import com.n3sensei.app.domain.exam.ExamSkill
import com.n3sensei.app.domain.mistakes.ErrorReviewPriority
import com.n3sensei.app.domain.mistakes.Knowledge
import com.n3sensei.app.domain.mistakes.KnowledgeStat
import com.n3sensei.app.domain.mistakes.WeaknessAnalyzer
import com.n3sensei.app.domain.mistakes.WeaknessReport
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.repository.ContentRepository
import com.n3sensei.app.domain.repository.ErrorRepository
import com.n3sensei.app.domain.repository.ExamRepository
import com.n3sensei.app.domain.repository.ProgressRepository
import com.n3sensei.app.domain.repository.UserPreferencesRepository
import com.n3sensei.app.domain.srs.ReviewTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Clock

data class ExamDashboard(
    /** False until an exam was submitted (with per-question grades). */
    val hasHistory: Boolean,
    val overview: ExamOverview,
    val skills: List<SkillStats>,
    val categories: List<CategoryCount>,
    val weakness: WeaknessReport,
    /** Study items named in [weakness], as found in the content now. */
    val knowledge: Map<ItemRef, Knowledge>,
    val week: PeriodComparison,
    val month: PeriodComparison,
    val plan: DailyPlan,
)

data class SkillDetail(
    val skill: ExamSkill,
    /** Null when no question of the skill was graded yet. */
    val stats: SkillStats?,
    val weekly: List<WeekStats>,
    val byType: List<TypeStats>,
    /** Study items of this skill's questions missed at least twice, with their content. */
    val weakItems: List<Pair<KnowledgeStat, Knowledge>>,
    val openErrors: Int,
)

/**
 * Gathers the dashboard's data (graded questions, error notebook, due flashcards, the learner's
 * study time) and computes it with [ExamStatistics], [WeaknessAnalyzer] and [DailyPlanner].
 */
class ExamDashboardManager(
    private val exams: ExamRepository,
    private val errors: ErrorRepository,
    private val progress: ProgressRepository,
    private val content: ContentRepository,
    private val preferences: UserPreferencesRepository,
    private val clock: Clock,
) {

    suspend fun load(): ExamDashboard = withContext(Dispatchers.Default) { compute() }

    private suspend fun compute(): ExamDashboard {
        val now = clock.instant()
        val zone = clock.zone
        val history = exams.getGradedHistory()
        val notebook = errors.observeErrors().first()
        val weakness = WeaknessAnalyzer.analyze(history)
        val resolve = resolver()
        val named = (weakness.frequentKnowledge + weakness.unresolved).map { it.ref }.distinct()
        val knowledge = named.associateWith { resolve(it) }

        val dueErrors = notebook.filter { it.isOpen && it.record.question != null && it.review != null && it.review.nextReviewAt <= now.toEpochMilli() }
        val dueFlashcards = progress.observeReviewOverview(now, ReviewTime.startOfDay(now, zone), ReviewTime.startOfNextDay(now, zone)).first().due
        val weakSkills = weakness.skills.filter { it.reliable && it.missRate >= WEAK_MISS_RATE }.map { it.skill }
        val plan = DailyPlanner.plan(
            PlanInput(
                dueErrors = dueErrors.map { it to ErrorReviewPriority.of(it, now.toEpochMilli()) },
                weakItems = weakness.unresolved.mapNotNull { knowledge[it.ref] },
                weakSkills = weakSkills,
                exams = exams.observeExamSets().first(),
                dueFlashcards = dueFlashcards,
            ),
            preferences.preferences.first().dailyStudyMinutes,
        )
        return ExamDashboard(
            hasHistory = history.isNotEmpty(),
            overview = ExamStatistics.overview(history, notebook, now.toEpochMilli()),
            skills = ExamStatistics.skills(history, now, zone),
            categories = ExamStatistics.categories(history),
            weakness = weakness,
            knowledge = knowledge,
            week = ExamStatistics.compare(history, notebook, ExamStatistics.week(now, zone), ExamStatistics.week(now, zone, 1)),
            month = ExamStatistics.compare(history, notebook, ExamStatistics.month(now, zone), ExamStatistics.month(now, zone, 1)),
            plan = plan,
        )
    }

    suspend fun skillDetail(skill: ExamSkill): SkillDetail = withContext(Dispatchers.Default) { computeSkill(skill) }

    private suspend fun computeSkill(skill: ExamSkill): SkillDetail {
        val now = clock.instant()
        val history = exams.getGradedHistory()
        val mine = history.filter { it.skill == skill }
        val resolve = resolver()
        val weakItems = WeaknessAnalyzer.analyze(mine).frequentKnowledge.map { it to resolve(it.ref) }
        return SkillDetail(
            skill = skill,
            stats = ExamStatistics.skills(history, now, clock.zone).firstOrNull { it.skill == skill },
            weekly = ExamStatistics.weekly(history, skill, now, clock.zone),
            byType = ExamStatistics.byType(history, skill),
            weakItems = weakItems,
            openErrors = errors.observeErrors().first().count { it.isOpen && it.skill == skill },
        )
    }

    suspend fun setBudget(minutes: Int) = preferences.setDailyStudyMinutes(minutes)

    /** Marks the items, so "Luyện các mục đã đánh dấu" practises them. */
    suspend fun markForPractice(refs: List<ItemRef>) = refs.forEach { progress.setMarked(it, true) }

    private suspend fun resolver(): suspend (ItemRef) -> Knowledge {
        val locations = ItemLocations.observe(content).first()
        val cache = mutableMapOf<ItemRef, Knowledge>()
        return { ref -> cache.getOrPut(ref) { Knowledge(ref, content.getPracticeItem(ref), locations.of(ref)) } }
    }

    companion object {
        /** A skill missed this often (with enough questions) gets a section exam in the plan. */
        const val WEAK_MISS_RATE = 0.4
    }
}

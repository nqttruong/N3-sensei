package com.n3sensei.app.domain.contrast

import java.time.Instant

/**
 * Picks questions (rule R9). Pure and deterministic: same inputs, same plan.
 *
 * Within a block: different question types first; questions never answered before, of the
 * difficulty of the last answered question (lowest difficulty for a new group), in pack order;
 * then answered ones, longest ago first. A question is never repeated in one session.
 */
object ContrastSessionPlanner {

    /** Up to [k] questions for one group's block. */
    fun pickBlock(
        questions: List<ContrastQuestion>,
        lastAnsweredAt: Map<String, Instant>,
        k: Int,
        exclude: Set<String> = emptySet(),
    ): List<ContrastQuestion> {
        val candidates = questions.filter { it.id !in exclude }
        if (candidates.isEmpty()) return emptyList()
        val targetDifficulty = questions.filter { it.id in lastAnsweredAt }.maxByOrNull { lastAnsweredAt.getValue(it.id) }?.difficulty
            ?: candidates.minOf { it.difficulty }
        val packOrder = questions.withIndex().associate { it.value.id to it.index }
        val ordered = candidates.sortedWith(
            compareBy<ContrastQuestion> { if (it.id in lastAnsweredAt) 1 else 0 }
                // Difficulty matters among fresh questions; answered ones go longest ago first.
                .thenBy { if (it.id in lastAnsweredAt || it.difficulty == targetDifficulty) 0 else 1 }
                .thenBy { lastAnsweredAt[it.id] ?: Instant.MIN }
                .thenBy { packOrder.getValue(it.id) },
        )
        val picked = mutableListOf<ContrastQuestion>()
        ordered.forEach { q -> if (picked.size < k && picked.none { it.type == q.type }) picked += q }
        ordered.forEach { q -> if (picked.size < k && q !in picked) picked += q }
        return picked
    }

    /**
     * Plans a session over [groupIds] (due first, then new, already in that order), one block per
     * group; groups without questions are left out.
     */
    fun plan(
        groupIds: List<String>,
        questionsOf: (String) -> List<ContrastQuestion>,
        lastAnsweredAt: (String) -> Map<String, Instant>,
        k: Int,
    ): List<SessionItem> {
        val items = mutableListOf<SessionItem>()
        var block = 0
        groupIds.distinct().forEach { groupId ->
            val picked = pickBlock(questionsOf(groupId), lastAnsweredAt(groupId), k)
            if (picked.isEmpty()) return@forEach
            picked.forEach { q -> items += SessionItem(items.size, groupId, q.id, q.revision, block) }
            block++
        }
        return items
    }

    /**
     * A different question of the same group to ask again at the end after [missed] was answered
     * wrong: not yet in the session, same target item first, then pack order. Null when none is left.
     */
    fun retryFor(missed: ContrastQuestion, questions: List<ContrastQuestion>, usedInSession: Set<String>): ContrastQuestion? =
        questions.filter { it.id != missed.id && it.id !in usedInSession }
            .sortedBy { if (it.targetItemId == missed.targetItemId) 0 else 1 }
            .firstOrNull()
}

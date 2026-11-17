package com.n3sensei.app.domain.contrast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant

class ContrastSessionPlannerTest {

    private val grammar = ContrastFixtures.samplePack().groups[0].questions // mc1(1) fb1(1) fb2(1) ed1(2) cs1(2)
    private val t = Instant.parse("2026-11-01T00:00:00Z")

    @Test
    fun newGroup_lowestDifficulty_differentTypes_packOrder() {
        val block = ContrastSessionPlanner.pickBlock(grammar, emptyMap(), 3)
        assertEquals(listOf("cq-g-koto-mc1", "cq-g-koto-fb1", "cq-g-koto-ed1"), block.map { it.id })
    }

    @Test
    fun freshQuestionsOfTheLastDifficultyFirst_thenOldestAnswered() {
        val answered = mapOf("cq-g-koto-mc1" to t, "cq-g-koto-fb1" to t.plusSeconds(10), "cq-g-koto-ed1" to t.plusSeconds(20))
        // Last answered was ed1 (difficulty 2): fresh cs1 (2) first, then fresh fb2 (1), then the oldest answered (mc1).
        assertEquals(listOf("cq-g-koto-cs1", "cq-g-koto-fb2", "cq-g-koto-mc1"), ContrastSessionPlanner.pickBlock(grammar, answered, 3).map { it.id })
    }

    @Test
    fun plan_oneBlockPerGroup_positionsInOrder_noRepeat() {
        val pack = ContrastFixtures.samplePack()
        val byId = pack.groups.associate { it.detail.group.id to it.questions }
        val items = ContrastSessionPlanner.plan(byId.keys.toList() + "missing", { byId[it].orEmpty() }, { emptyMap() }, 3)
        assertEquals(items.indices.toList(), items.map { it.position })
        assertEquals(listOf(0, 0, 0, 1, 1, 2), items.map { it.blockIndex })
        assertEquals(items.size, items.map { it.questionId }.toSet().size)
        assertThrows(IllegalArgumentException::class.java) { ContrastSession("x", PracticeScope.Due, SessionStatus.ACTIVE, t, t, 1, items.drop(1)) }
    }

    @Test
    fun retry_isAnotherQuestionNotYetInTheSession_sameTargetFirst() {
        val fb1 = grammar.single { it.id == "cq-g-koto-fb1" } // target natteiru
        val used = setOf("cq-g-koto-mc1", "cq-g-koto-fb1", "cq-g-koto-ed1")
        assertEquals("cq-g-koto-cs1", ContrastSessionPlanner.retryFor(fb1, grammar, used)?.id) // natteiru too
        assertNull(ContrastSessionPlanner.retryFor(fb1, grammar, grammar.map { it.id }.toSet()))
    }
}

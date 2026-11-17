package com.n3sensei.app.data.repository

import com.n3sensei.app.data.local.dao.ContrastGroupWithItems
import com.n3sensei.app.domain.contrast.AnswerOutcome
import com.n3sensei.app.domain.contrast.ContrastAttempt
import com.n3sensei.app.domain.contrast.ContrastContentType
import com.n3sensei.app.domain.contrast.ContrastFixtures
import com.n3sensei.app.domain.contrast.ContrastProgress
import com.n3sensei.app.domain.contrast.ContrastQuestionType
import com.n3sensei.app.domain.contrast.ContrastReview
import com.n3sensei.app.domain.contrast.ContrastSession
import com.n3sensei.app.domain.contrast.PracticeScope
import com.n3sensei.app.domain.contrast.SessionItem
import com.n3sensei.app.domain.contrast.SessionStatus
import com.n3sensei.app.domain.model.ReviewRating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Entity ↔ domain round trips (JVM): what is stored is read back unchanged. */
class ContrastMappersTest {

    private val pack = ContrastFixtures.samplePack()
    private val t = Instant.parse("2026-11-17T02:00:00.123Z")

    @Test
    fun aValidatedPack_roundTripsThroughItsRows() {
        val rows = pack.toRows(t, schemaVersion = 1)
        assertEquals(1, rows.pack.contentVersion)
        assertEquals(3, rows.groups.size)
        assertEquals(listOf(0, 1, 2), rows.groups.map { it.sortOrder })
        pack.groups.forEach { validated ->
            val id = validated.detail.group.id
            val row = ContrastGroupWithItems(
                rows.groups.single { it.id == id },
                rows.items.filter { it.groupId == id }.reversed(), // order comes from sort_order, not insertion
                rows.questions.filter { it.groupId == id }.map { it.id },
            )
            val detail = detailOf(row, rows.notes.filter { it.groupId == id }.shuffled(), rows.examples.filter { it.groupId == id }.reversed(), rows.pack)
            assertEquals(validated.detail, detail)
            val questions = rows.questions.filter { it.groupId == id }.map { q -> q.toDomain(rows.options.filter { it.questionId == q.id }.reversed()) }
            assertEquals(validated.questions, questions)
        }
    }

    @Test
    fun searchText_isNormalizedForAccentInsensitiveSearch() {
        val rows = pack.toRows(t, 1)
        val grammar = rows.groups.single { it.contentType == ContrastContentType.GRAMMAR }
        assertTrue(grammar.searchText, grammar.searchText.contains("ことにしている"))
        assertTrue(grammar.searchText, grammar.searchText.contains("quy dinh")) // "quy định" without accents
    }

    @Test
    fun learnerData_roundTrips() {
        val attempt = ContrastAttempt("r1", "s1", 2, "g", 3, "q", 4, ContrastQuestionType.ERROR_DETECTION, "contrast-n3", 5,
            AnswerOutcome.INCORRECT, "b", "d", "A", "B", "健康のために、", "ことになっている。 → ことにしている。", t)
        assertEquals(attempt, attempt.toEntity().toDomain())
        val skipped = attempt.copy(requestId = "r2", outcome = AnswerOutcome.SKIPPED, chosenOptionId = null, chosenItemId = null, chosenText = null)
        assertEquals(skipped, skipped.toEntity().toDomain())
        val progress = ContrastProgress("g", 2, t.plusSeconds(86_400), t, 3, 1, t.minusSeconds(10), t)
        assertEquals(progress, progress.toEntity().toDomain())
        val review = ContrastReview(0, "g", "s1", t, 3, 2, 1, 0, ReviewRating.HARD, null, 0, t.plusSeconds(86_400), 5)
        assertEquals(review, review.toEntity().toDomain())
        listOf(PracticeScope.Due, PracticeScope.Group("g"), PracticeScope.Type(ContrastContentType.KANJI)).forEach { scope ->
            val session = ContrastSession("s", scope, SessionStatus.ACTIVE, t, t, 5,
                listOf(SessionItem(0, "g", "q1", 1, 0), SessionItem(1, "g", "q2", 2, null)))
            val (entity, items) = session.toEntities()
            assertEquals(session, entity.toDomain(items.reversed()))
        }
    }
}

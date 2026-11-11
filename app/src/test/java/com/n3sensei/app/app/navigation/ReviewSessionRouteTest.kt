package com.n3sensei.app.app.navigation

import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.srs.ReviewMode
import com.n3sensei.app.domain.srs.ReviewScope
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewSessionRouteTest {

    @Test
    fun theScope_goesThroughTheRoute_unknownNamesFallBackToTheUsualSession() {
        listOf(
            ReviewScope.ALL,
            ReviewScope(type = ItemType.KANJI),
            ReviewScope(type = ItemType.GRAMMAR, mode = ReviewMode.DUE_ONLY),
            ReviewScope(mode = ReviewMode.FORGOTTEN),
        ).forEach { assertEquals(it, ReviewSessionRoute(it).scope) }
        assertEquals(ReviewScope.ALL, ReviewSessionRoute().scope)
        assertEquals(ReviewScope.ALL, ReviewSessionRoute(type = "SENTENCE", mode = "LATER").scope)
    }
}

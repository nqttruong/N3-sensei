package com.n3sensei.app.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TopLevelDestinationTest {

    @Test
    fun routesAreUniqueAndNonBlank() {
        val routes = TopLevelDestination.entries.map { it.route }
        assertTrue(routes.all { it.isNotBlank() })
        assertEquals(routes.size, routes.toSet().size)
    }

    @Test
    fun startDestinationIsLearn() {
        assertEquals(TopLevelDestination.LEARN, TopLevelDestination.START)
    }

    @Test
    fun bottomBarHasFourTabsInOrder() {
        assertEquals(
            listOf("learn", "review", "stats", "settings"),
            TopLevelDestination.entries.map { it.route },
        )
    }
}

package com.n3sensei.app.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

/** Contrast Lab lives inside the Learn tab: no sixth tab, every Contrast screen belongs to LEARN. */
class ContrastNavigationTest {

    @Test
    fun stillFiveTabs() {
        assertEquals(listOf("HOME", "LEARN", "REVIEW", "STATS", "SETTINGS"), TopLevelDestination.entries.map { it.name })
    }

    @Test
    fun everyContrastScreenIsASubScreenOfLearn() {
        val contrast = AppScreen.entries.filter { it.name.startsWith("CONTRAST_") }
        assertEquals(listOf(ContrastHomeRoute::class, ContrastLibraryRoute::class, ContrastGroupRoute::class), contrast.map { it.route })
        contrast.forEach {
            assertEquals(it.name, TopLevelDestination.LEARN, it.tab)
            assertEquals(it.name, false, it.isTabRoot)
            assertEquals(it.name, false, it.isFocusSession)
        }
    }
}

package com.n3sensei.app.app.navigation

import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination

/**
 * Switches tab the standard way: a single copy of each tab on the back stack, each tab keeps
 * its own sub-stack, and Back from any tab root returns to the start tab.
 */
fun NavController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

fun NavDestination?.isInTab(destination: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(destination.route::class) } == true

/** Opens the lesson list inside the Learn tab, from any tab. */
fun NavController.navigateToLessons() {
    navigateToTopLevel(TopLevelDestination.LEARN)
    navigate(LessonListRoute) { launchSingleTop = true }
}

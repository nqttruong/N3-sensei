package com.n3sensei.app.app.navigation

import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph
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

/**
 * A tap on the bottom bar. Another tab: [navigateToTopLevel] (each tab keeps its own screen).
 * The tab already shown: back to that tab's first screen. Re-navigating to it would only save
 * and restore the same stack, so the tap did nothing on a sub-screen.
 */
fun NavController.selectTab(destination: TopLevelDestination) {
    if (currentDestination.isInTab(destination)) {
        val root = AppScreen.entries.first { it.tab == destination && it.isTabRoot }
        val rootId = graph.findDestination(root)?.id
        if (rootId != null && popBackStack(rootId, inclusive = false)) return
    }
    navigateToTopLevel(destination)
}

private fun NavGraph.findDestination(screen: AppScreen): NavDestination? {
    forEach { node ->
        if (screen.matches(node)) return node
        if (node is NavGraph) node.findDestination(screen)?.let { return it }
    }
    return null
}

fun NavDestination?.isInTab(destination: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(destination.route::class) } == true

/** Opens the lesson list inside the Learn tab, from any tab. */
fun NavController.navigateToLessons() {
    navigateToTopLevel(TopLevelDestination.LEARN)
    navigate(LessonListRoute) { launchSingleTop = true }
}

package com.n3sensei.app.app.navigation

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.icon.N3Icons

/** Bottom navigation tabs. Phones get at most five, per Material guidance. */
enum class TopLevelDestination(
    val route: Any,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    HOME(route = HomeRoute, labelRes = R.string.nav_home, icon = N3Icons.Home),
    LEARN(route = LearnGraphRoute, labelRes = R.string.nav_learn, icon = N3Icons.Learn),
    REVIEW(route = ReviewRoute, labelRes = R.string.nav_review, icon = N3Icons.Review),
    STATS(route = StatsRoute, labelRes = R.string.nav_stats, icon = N3Icons.Stats),
    SETTINGS(route = SettingsRoute, labelRes = R.string.nav_settings, icon = N3Icons.Settings);

    val testTag: String get() = "nav_tab_${name.lowercase()}"

    companion object {
        val START = HOME
    }
}

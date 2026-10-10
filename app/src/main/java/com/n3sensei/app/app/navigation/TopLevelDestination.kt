package com.n3sensei.app.app.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.n3sensei.app.R

enum class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    LEARN(route = "learn", labelRes = R.string.nav_learn, icon = Icons.Filled.Home),
    REVIEW(route = "review", labelRes = R.string.nav_review, icon = Icons.Filled.Refresh),
    STATS(route = "stats", labelRes = R.string.nav_stats, icon = Icons.Filled.DateRange),
    SETTINGS(route = "settings", labelRes = R.string.nav_settings, icon = Icons.Filled.Settings);

    companion object {
        val START = LEARN
    }
}

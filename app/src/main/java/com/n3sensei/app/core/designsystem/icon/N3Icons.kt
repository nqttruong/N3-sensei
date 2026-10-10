package com.n3sensei.app.core.designsystem.icon

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Single source for app icons. Uses material-icons-core where available and defines the few
 * missing Material icons locally instead of pulling in the large material-icons-extended.
 */
object N3Icons {
    val Home: ImageVector = Icons.Filled.Home
    val Review: ImageVector = Icons.Filled.Refresh
    val Settings: ImageVector = Icons.Filled.Settings
    val NavigateUp: ImageVector = Icons.AutoMirrored.Filled.ArrowBack

    /** Material "school". */
    val Learn: ImageVector by lazy {
        materialIcon("School", "M5,13.18v4L12,21l7,-3.82v-4L12,17l-7,-3.82zM12,3L1,9l11,6 9,-4.91V17h2V9L12,3z")
    }

    /** Material "bar_chart". */
    val Stats: ImageVector by lazy {
        materialIcon("BarChart", "M5,9.2h3V19H5V9.2zM10.6,5h2.8v14h-2.8V5zM16.2,13H19v6h-2.8V13z")
    }

    /** Material "bookmark" (filled): item marked as needing attention. */
    val Marked: ImageVector by lazy {
        materialIcon("Bookmark", "M17,3H7c-1.1,0 -1.99,0.9 -1.99,2L5,21l7,-3 7,3V5c0,-1.1 -0.9,-2 -2,-2z")
    }

    /** Material "bookmark_border". */
    val Unmarked: ImageVector by lazy {
        materialIcon("BookmarkBorder", "M17,3H7c-1.1,0 -1.99,0.9 -1.99,2L5,21l7,-3 7,3V5c0,-1.1 -0.9,-2 -2,-2zM17,18l-5,-2.18L7,18V5h10v13z")
    }

    private fun materialIcon(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(pathData = addPathNodes(pathData), fill = SolidColor(Color.Black)).build()
}

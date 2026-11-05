package com.n3sensei.app.core.designsystem.theme

import androidx.compose.ui.unit.dp

/** Spacing scale on a 4 dp grid. Screens use [screen] as their side margin. */
object N3Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    val screen = l

    /** Content wider than this (tablets, landscape) is centred instead of stretched. */
    val maxContentWidth = 720.dp
}

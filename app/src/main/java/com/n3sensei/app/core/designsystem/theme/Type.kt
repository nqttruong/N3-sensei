package com.n3sensei.app.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.intl.LocaleList

// Material 3 defaults; the system font covers Japanese glyphs (Noto Sans CJK).
val Typography = Typography()

private val JapaneseLocale = LocaleList("ja-JP")

/**
 * Marks text as Japanese so the renderer picks Japanese glyph forms for Han characters
 * (e.g. 直, 骨) instead of Chinese ones. Apply to every style used for Japanese text.
 */
fun TextStyle.japanese(): TextStyle = copy(localeList = JapaneseLocale)

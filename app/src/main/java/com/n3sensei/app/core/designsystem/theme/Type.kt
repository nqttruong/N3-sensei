package com.n3sensei.app.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
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

/** True for styles made with [japanese]. */
val TextStyle.isJapanese: Boolean get() = localeList == JapaneseLocale

/**
 * Japanese text whose language is part of the text itself, not only of the style: TalkBack
 * then reads it with a Japanese voice (a style-level locale does not reach accessibility, so
 * 経験 would be read by the Vietnamese voice). Use together with [japanese].
 */
fun japaneseText(text: String): AnnotatedString =
    AnnotatedString(text, spanStyles = listOf(AnnotatedString.Range(SpanStyle(localeList = JapaneseLocale), 0, text.length)))

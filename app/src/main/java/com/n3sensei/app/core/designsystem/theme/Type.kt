package com.n3sensei.app.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Default = Typography()

/**
 * Editorial pairing with system fonts only (nothing bundled, works offline): serif for display
 * and headline styles (Noto Serif; Japanese falls back to Noto Serif CJK, a Mincho face, where
 * the device has it, otherwise to the sans face), sans for titles, body and labels. Headings are
 * slightly tighter, body keeps Material line heights, which leave room for Vietnamese stacked
 * diacritics.
 */
val Typography = Typography(
    displayLarge = Default.displayLarge.copy(fontFamily = FontFamily.Serif),
    displayMedium = Default.displayMedium.copy(fontFamily = FontFamily.Serif),
    displaySmall = Default.displaySmall.copy(fontFamily = FontFamily.Serif),
    headlineLarge = Default.headlineLarge.copy(fontFamily = FontFamily.Serif),
    headlineMedium = Default.headlineMedium.copy(fontFamily = FontFamily.Serif),
    headlineSmall = Default.headlineSmall.copy(fontFamily = FontFamily.Serif),
    titleLarge = Default.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Default.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = Default.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = Default.bodyLarge,
    bodyMedium = Default.bodyMedium,
    bodySmall = Default.bodySmall,
    labelLarge = Default.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = Default.labelMedium.copy(fontWeight = FontWeight.SemiBold),
    // Overlines ("THỰC TẾ", section labels): a little tracking reads as editorial, not shouty.
    labelSmall = Default.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
)

val Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

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

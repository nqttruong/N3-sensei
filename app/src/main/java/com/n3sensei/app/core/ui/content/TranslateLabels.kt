package com.n3sensei.app.core.ui.content

import androidx.annotation.StringRes
import com.n3sensei.app.R
import com.n3sensei.app.domain.translate.Language
import com.n3sensei.app.domain.translate.TranslationMode

/** Labels shared by Settings → Dịch nhanh and the "Dịch nhanh" dialog. */
@StringRes
fun Language.labelRes(): Int = when (this) {
    Language.VIETNAMESE -> R.string.quick_translate_language_vi
    Language.ENGLISH -> R.string.quick_translate_language_en
    Language.JAPANESE -> R.string.quick_translate_language_ja
}

/** Short form for the dialog's compact choices ("Việt", "Anh", "Nhật"). */
@StringRes
fun Language.shortLabelRes(): Int = when (this) {
    Language.VIETNAMESE -> R.string.quick_translate_language_short_vi
    Language.ENGLISH -> R.string.quick_translate_language_short_en
    Language.JAPANESE -> R.string.quick_translate_language_short_ja
}

/** Short form for the dialog's compact choices ("Anh", "Nhật", "Cả hai"). */
@StringRes
fun TranslationMode.shortLabelRes(): Int = when (this) {
    TranslationMode.ENGLISH -> R.string.quick_translate_language_short_en
    TranslationMode.JAPANESE -> R.string.quick_translate_language_short_ja
    TranslationMode.BOTH -> R.string.quick_translate_mode_both
}

@StringRes
fun TranslationMode.labelRes(): Int = when (this) {
    TranslationMode.ENGLISH -> R.string.quick_translate_mode_en
    TranslationMode.JAPANESE -> R.string.quick_translate_mode_ja
    TranslationMode.BOTH -> R.string.quick_translate_mode_both
}

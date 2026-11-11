package com.n3sensei.app.core.ui.content

import androidx.annotation.StringRes
import com.n3sensei.app.R
import com.n3sensei.app.domain.translate.Language
import com.n3sensei.app.domain.translate.TranslationMode

/** Labels shared by Settings → Dịch nhanh and the "Dịch nhanh" popup. */
@StringRes
fun Language.labelRes(): Int = when (this) {
    Language.VIETNAMESE -> R.string.quick_translate_language_vi
    Language.ENGLISH -> R.string.quick_translate_language_en
    Language.JAPANESE -> R.string.quick_translate_language_ja
}

@StringRes
fun TranslationMode.labelRes(): Int = when (this) {
    TranslationMode.ENGLISH -> R.string.quick_translate_mode_en
    TranslationMode.JAPANESE -> R.string.quick_translate_mode_ja
    TranslationMode.BOTH -> R.string.quick_translate_mode_both
}

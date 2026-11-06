package com.n3sensei.app.domain.translate

/** A language "Dịch nhanh" works with. [code]: ISO 639-1. */
enum class Language(val code: String) {
    VIETNAMESE("vi"),
    ENGLISH("en"),
    JAPANESE("ja"),
}

/** The foreign language(s) a selection is translated to or from, chosen in Settings → Dịch nhanh. */
enum class TranslationMode(val languages: List<Language>) {
    ENGLISH(listOf(Language.ENGLISH)),
    JAPANESE(listOf(Language.JAPANESE)),
    BOTH(listOf(Language.ENGLISH, Language.JAPANESE)),
    ;

    /**
     * What a text in [source] would be translated into: Vietnamese text into the chosen foreign
     * language(s); English or Japanese text into Vietnamese, plus the other chosen language.
     * The source language itself is never a target.
     */
    fun targetsFor(source: Language): List<Language> =
        (if (source == Language.VIETNAMESE) languages else listOf(Language.VIETNAMESE) + languages).filter { it != source }
}

/** Settings → Dịch nhanh. */
data class QuickTranslateSettings(
    /** Whether "Dịch nhanh" is offered in other apps' text selection menu. */
    val enabled: Boolean = true,
    val mode: TranslationMode = TranslationMode.BOTH,
)

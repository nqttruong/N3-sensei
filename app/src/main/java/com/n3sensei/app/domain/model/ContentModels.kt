package com.n3sensei.app.domain.model

/** Kind of learnable item. Names are persisted; never rename an entry. */
enum class ItemType { VOCABULARY, KANJI, GRAMMAR }

/** Stable reference to a content item, used by progress data that outlives content updates. */
data class ItemRef(val type: ItemType, val id: String)

/**
 * Origin and license of content. [licenseVerified] is true only when the right to
 * redistribute has actually been checked; the app must not claim a license otherwise.
 */
data class ContentSource(
    val id: String,
    val name: String,
    val url: String?,
    val license: String,
    val attribution: String?,
    val licenseVerified: Boolean,
)

data class LessonSummary(
    val id: String,
    val packId: String,
    val number: Int,
    val title: String,
    val jlptLevel: String,
    val description: String?,
    val vocabularyCount: Int,
    val kanjiCount: Int,
    val grammarCount: Int,
)

/**
 * @property expression the word as normally written (may be kana only).
 * @property reading full reading in kana.
 * @property furigana [expression] in [com.n3sensei.app.domain.furigana.Furigana] markup; null when
 *   no ruby is needed (kana-only word) or not provided.
 */
data class Vocabulary(
    val id: String,
    val lessonId: String,
    val expression: String,
    val reading: String,
    val furigana: String?,
    val meaningVi: String,
    val partOfSpeech: String?,
    val jlptLevel: String,
    val note: String?,
)

data class Kanji(
    val id: String,
    val lessonId: String,
    val character: String,
    val onReadings: List<String>,
    val kunReadings: List<String>,
    /** Âm Hán Việt, e.g. "KINH". */
    val hanViet: String?,
    val meaningVi: String,
    val strokeCount: Int?,
    val note: String?,
)

data class GrammarPoint(
    val id: String,
    val lessonId: String,
    val pattern: String,
    /** How the pattern attaches, e.g. "V-た + ばかり". */
    val structure: String,
    val meaningVi: String,
    val usage: String,
    /** Common confusions with similar patterns. */
    val cautionNote: String?,
)

data class ExampleSentence(
    val id: String,
    val japanese: String,
    val furigana: String?,
    val translationVi: String,
)

data class Tag(val id: String, val name: String)

data class VocabularyDetail(
    val vocabulary: Vocabulary,
    val examples: List<ExampleSentence>,
    val tags: List<Tag>,
    val source: ContentSource,
)

data class KanjiDetail(
    val kanji: Kanji,
    val examples: List<ExampleSentence>,
    val source: ContentSource,
)

data class GrammarDetail(
    val grammar: GrammarPoint,
    val examples: List<ExampleSentence>,
    val source: ContentSource,
)

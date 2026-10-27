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
    /** Kanji the learner linked this word to. */
    val linkedKanji: List<Kanji> = emptyList(),
)

data class KanjiDetail(
    val kanji: Kanji,
    /** The kanji's own example words (from its pack). */
    val examples: List<ExampleSentence>,
    val source: ContentSource,
    /** Vocabulary items the learner linked to this kanji. */
    val linkedVocabulary: List<Vocabulary> = emptyList(),
)

/**
 * A word shown with a kanji: one of its example words (from its pack) or a vocabulary item the
 * learner linked to it ([vocabularyId] set). [furigana] is null when there is no ruby markup.
 */
data class KanjiWord(
    val japanese: String,
    val furigana: String?,
    val meaningVi: String,
    val vocabularyId: String? = null,
)

/** Multiple-choice question attached to a grammar point by the content pack. */
data class GrammarExercise(
    val id: String,
    /** Question text; may contain furigana markup and a blank such as "＿＿＿". */
    val prompt: String,
    val options: List<String>,
    val answerIndex: Int,
    val explanationVi: String?,
)

data class GrammarDetail(
    val grammar: GrammarPoint,
    val examples: List<ExampleSentence>,
    val source: ContentSource,
    val exercises: List<GrammarExercise> = emptyList(),
)

data class ContentPackInfo(
    val id: String,
    val title: String,
    val version: Int,
    val isSeed: Boolean,
    val lessonCount: Int,
)

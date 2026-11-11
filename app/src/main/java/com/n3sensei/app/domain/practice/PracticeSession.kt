package com.n3sensei.app.domain.practice

import com.n3sensei.app.domain.model.ExampleSentence
import com.n3sensei.app.domain.model.GrammarPoint
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.Kanji
import com.n3sensei.app.domain.model.KanjiWord
import com.n3sensei.app.domain.model.ReviewRating
import com.n3sensei.app.domain.model.Vocabulary

/** One card in a practice session. */
sealed interface PracticeItem {
    val ref: ItemRef

    /** [examples]: the word's own example sentences (linked by its id), in the content's order. */
    data class VocabularyItem(val vocabulary: Vocabulary, val examples: List<ExampleSentence> = emptyList()) : PracticeItem {
        override val ref = ItemRef(ItemType.VOCABULARY, vocabulary.id)
    }

    /** [words]: every word shown with the kanji (its own example words, then the vocabulary the learner linked). */
    data class KanjiItem(val kanji: Kanji, val words: List<KanjiWord> = emptyList()) : PracticeItem {
        override val ref = ItemRef(ItemType.KANJI, kanji.id)
    }

    /** [examples]: the pattern's example sentences from the content, shown with the answer. */
    data class GrammarItem(val grammar: GrammarPoint, val examples: List<ExampleSentence> = emptyList()) : PracticeItem {
        override val ref = ItemRef(ItemType.GRAMMAR, grammar.id)
    }
}

/**
 * Self-graded flashcard pass over a fixed list of items, each shown once. Ratings are only
 * tallied here; they are not written to the review schedule (that belongs to the scheduler).
 */
data class PracticeSession(
    val items: List<PracticeItem>,
    val currentIndex: Int = 0,
    val isAnswerRevealed: Boolean = false,
    val ratings: Map<ItemRef, ReviewRating> = emptyMap(),
) {
    val total: Int get() = items.size
    val isFinished: Boolean get() = currentIndex >= items.size
    val current: PracticeItem? get() = items.getOrNull(currentIndex)
    val completedCount: Int get() = ratings.size

    fun revealAnswer(): PracticeSession =
        if (isFinished) this else copy(isAnswerRevealed = true)

    /** Rates the current card and moves on. Ignored before the answer is revealed. */
    fun rate(rating: ReviewRating): PracticeSession {
        val item = current ?: return this
        if (!isAnswerRevealed) return this
        return copy(
            currentIndex = currentIndex + 1,
            isAnswerRevealed = false,
            ratings = ratings + (item.ref to rating),
        )
    }

    fun result(durationSeconds: Long): PracticeResult = PracticeResult(
        total = total,
        again = ratings.values.count { it == ReviewRating.AGAIN },
        hard = ratings.values.count { it == ReviewRating.HARD },
        good = ratings.values.count { it == ReviewRating.GOOD },
        easy = ratings.values.count { it == ReviewRating.EASY },
        durationSeconds = durationSeconds.coerceAtLeast(0),
    )
}

data class PracticeResult(
    val total: Int,
    val again: Int,
    val hard: Int,
    val good: Int,
    val easy: Int,
    val durationSeconds: Long,
) {
    val rated: Int get() = again + hard + good + easy
}

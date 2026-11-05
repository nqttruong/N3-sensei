package com.n3sensei.app.domain.widget

import com.n3sensei.app.domain.course.CourseBuilder
import com.n3sensei.app.domain.model.ItemQuery
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.practice.PracticeItem
import com.n3sensei.app.domain.repository.ContentRepository
import com.n3sensei.app.domain.repository.ProgressRepository
import kotlinx.coroutines.flow.first
import java.time.Clock
import kotlin.random.Random

/** What the home-screen widgets show. Names are persisted; never rename an entry. */
enum class WidgetMode { RANDOM, REVIEW, KANJI, VOCABULARY, GRAMMAR, LESSON }

/** What a tap on the widget does (the large widget also has its own "Đổi" button). */
enum class WidgetTap { OPEN, NEXT }

/**
 * Settings shared by every N3 Sensei widget on the home screen.
 *
 * @property lesson lesson of Học → Bài học for [WidgetMode.LESSON]; the widget stays on it.
 */
data class WidgetSettings(
    val mode: WidgetMode = WidgetMode.RANDOM,
    val lesson: Int = 1,
    val showFurigana: Boolean = true,
    val showMeaning: Boolean = false,
    val tap: WidgetTap = WidgetTap.OPEN,
) {
    init {
        require(lesson >= 1) { "lesson must be >= 1" }
    }

    /** Changes when the set of items the widget may show changes; the shown item is then chosen again. */
    fun selectionKey(): String = if (mode == WidgetMode.LESSON) "$mode:$lesson" else mode.name
}

/** One widget's content: an item, or why there is none. */
sealed interface WidgetPick {
    data class Item(val item: PracticeItem, val lesson: Int?) : WidgetPick

    /** Nothing in the chosen mode (e.g. nothing due, a lesson that no longer exists). */
    data object Empty : WidgetPick
}

/** Refreshes the widgets on the home screen after their settings change (Android side). */
fun interface WidgetUpdater {
    fun refresh()
}

/**
 * Chooses widget items from the content in Room; nothing is copied for the widget. Every mode
 * but [WidgetMode.LESSON] picks at random among its items, never the item shown now when
 * another one exists; a lesson is gone through in its own order and starts again at its end.
 */
class WidgetContentPicker(
    private val content: ContentRepository,
    private val progress: ProgressRepository,
    private val clock: Clock,
    private val random: Random = Random.Default,
) {
    /** The item to show for [settings]: [current] while it still fits, otherwise a new one. */
    suspend fun current(settings: WidgetSettings, current: ItemRef?): WidgetPick {
        val candidates = candidates(settings)
        if (current != null && current in candidates) {
            content.getPracticeItem(current)?.let { return WidgetPick.Item(it, lessonOf(settings)) }
        }
        return next(settings, current, candidates)
    }

    /** Another item for [settings] than [current] (the same one only when it is the only item). */
    suspend fun next(settings: WidgetSettings, current: ItemRef?, candidates: List<ItemRef>? = null): WidgetPick {
        val all = candidates ?: candidates(settings)
        val ordered = if (settings.mode == WidgetMode.LESSON) {
            val at = all.indexOf(current)
            all.drop(at + 1) + all.take(at + 1)
        } else {
            all.filter { it != current }.shuffled(random) + all.filter { it == current }
        }
        // An item deleted since the list was read is skipped, never shown as an error.
        for (ref in ordered) {
            val item = content.getPracticeItem(ref) ?: continue
            return WidgetPick.Item(item, lessonOf(settings))
        }
        return WidgetPick.Empty
    }

    private fun lessonOf(settings: WidgetSettings) = settings.lesson.takeIf { settings.mode == WidgetMode.LESSON }

    suspend fun candidates(settings: WidgetSettings): List<ItemRef> = when (settings.mode) {
        WidgetMode.VOCABULARY -> vocabulary()
        WidgetMode.KANJI -> kanji()
        WidgetMode.GRAMMAR -> grammar()
        WidgetMode.RANDOM -> vocabulary() + kanji() + grammar()
        WidgetMode.REVIEW -> (
            progress.getDueItems(clock.instant(), REVIEW_SCAN_LIMIT) +
                content.observeVocabularyItems(ItemQuery(markedOnly = true)).first().map { ItemRef(ItemType.VOCABULARY, it.item.id) } +
                content.observeKanjiItems(ItemQuery(markedOnly = true)).first().map { ItemRef(ItemType.KANJI, it.item.id) } +
                content.observeGrammarItems(ItemQuery(markedOnly = true)).first().map { ItemRef(ItemType.GRAMMAR, it.item.id) }
            ).distinct()
        WidgetMode.LESSON -> CourseBuilder(content).observe().first().getOrNull(settings.lesson - 1)?.let { lesson ->
            lesson.kanji.map { ItemRef(ItemType.KANJI, it.item.id) } +
                lesson.words.map { ItemRef(ItemType.VOCABULARY, it.item.id) } +
                lesson.grammar.map { ItemRef(ItemType.GRAMMAR, it.item.id) }
        }.orEmpty()
    }

    private suspend fun vocabulary() = content.observeVocabularyItems(ItemQuery()).first().map { ItemRef(ItemType.VOCABULARY, it.item.id) }
    private suspend fun kanji() = content.observeKanjiItems(ItemQuery()).first().map { ItemRef(ItemType.KANJI, it.item.id) }
    private suspend fun grammar() = content.observeGrammarItems(ItemQuery()).first().map { ItemRef(ItemType.GRAMMAR, it.item.id) }

    private companion object {
        const val REVIEW_SCAN_LIMIT = 500
    }
}

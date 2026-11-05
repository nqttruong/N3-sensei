package com.n3sensei.app.domain.reminder

import com.n3sensei.app.domain.model.ItemQuery
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.Vocabulary
import com.n3sensei.app.domain.practice.PracticeItem
import com.n3sensei.app.domain.repository.ContentRepository
import com.n3sensei.app.domain.repository.ProgressRepository
import com.n3sensei.app.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** Why this item was chosen. */
enum class ReminderReason { DUE, MARKED, NEW }

/** Everything a notification (or widget) needs; no Android types. */
data class ReminderMessage(
    val item: PracticeItem,
    val reason: ReminderReason,
    /** Due items of all types right now. */
    val dueCount: Int,
    val settings: ReminderSettings,
) {
    /** The word, for a vocabulary reminder. */
    val vocabulary: Vocabulary? get() = (item as? PracticeItem.VocabularyItem)?.vocabulary
}

/** Platform side of reminders: posting notifications. */
interface ReminderNotifier {
    /** False when the permission is denied or notifications/channel are turned off. */
    fun canPostNotifications(): Boolean

    /** Posts or replaces the single reminder notification. */
    fun show(message: ReminderMessage)
}

/**
 * When the last reminder was shown and for which item, and how many were shown on
 * [countDay] (local date), for spacing and the daily limit.
 */
data class ReminderState(
    val lastShownAt: Instant? = null,
    val lastItem: ItemRef? = null,
    val countDay: LocalDate? = null,
    val countToday: Int = 0,
)

interface ReminderStateStore {
    suspend fun read(): ReminderState
    suspend fun write(state: ReminderState)
}

/** When each item was last shown in a reminder (kept apart from learning progress). */
interface ReminderHistory {
    /** Last time each of [refs] was shown; items never shown are absent. */
    suspend fun lastShown(refs: Collection<ItemRef>): Map<ItemRef, Instant>
    suspend fun recordShown(ref: ItemRef, at: Instant)
}

enum class ReminderOutcome { SHOWN, DISABLED, OUTSIDE_WINDOW, TOO_SOON, DAILY_LIMIT, NOTIFICATIONS_BLOCKED, NOTHING_TO_SHOW }

/**
 * Decides and posts study reminders from Room data. Never changes the review schedule.
 *
 * Choice (see [pick]): items due for review first, then items marked as needing attention,
 * then new items in course order (none of these with [ReminderSettings.reviewOnly] for new
 * items), limited to the kinds in [ReminderSettings.content]. Within a group, items not shown
 * in the last [REPEAT_AFTER] come first, never-shown and longest-ago first; an item can come
 * back once that time has passed. When every candidate was shown recently, the one shown
 * longest ago is used, so a reminder still goes out but never the same item twice in a row
 * while another one is available.
 */
class ReminderRunner(
    private val preferences: UserPreferencesRepository,
    private val stateStore: ReminderStateStore,
    private val content: ContentRepository,
    private val progress: ProgressRepository,
    private val notifier: ReminderNotifier,
    private val clock: Clock,
    private val history: ReminderHistory,
) {
    /** A scheduled background run: honours on/off, window, spacing, daily limit and permission. */
    suspend fun runScheduled(): ReminderOutcome {
        val settings = preferences.preferences.first().reminderSettings
        val state = stateStore.read()
        val now = clock.instant()
        val shownToday = ReminderPolicy.shownToday(now, clock.zone, state)
        when (ReminderPolicy.decide(now, clock.zone, settings, state.lastShownAt, shownToday)) {
            ReminderDecision.DISABLED -> return ReminderOutcome.DISABLED
            ReminderDecision.OUTSIDE_WINDOW -> return ReminderOutcome.OUTSIDE_WINDOW
            ReminderDecision.TOO_SOON -> return ReminderOutcome.TOO_SOON
            ReminderDecision.DAILY_LIMIT -> return ReminderOutcome.DAILY_LIMIT
            ReminderDecision.NOTIFY -> Unit
        }
        return show(settings, state)
    }

    /** "Send a test reminder" from Settings: ignores on/off, window, spacing and the daily limit. */
    suspend fun showNow(): ReminderOutcome {
        val settings = preferences.preferences.first().reminderSettings
        return show(settings, stateStore.read())
    }

    private suspend fun show(settings: ReminderSettings, state: ReminderState): ReminderOutcome {
        if (!notifier.canPostNotifications()) return ReminderOutcome.NOTIFICATIONS_BLOCKED
        val message = nextMessage(settings, avoid = state.lastItem) ?: return ReminderOutcome.NOTHING_TO_SHOW
        notifier.show(message)
        val now = clock.instant()
        history.recordShown(message.item.ref, now)
        stateStore.write(
            ReminderState(
                lastShownAt = now,
                lastItem = message.item.ref,
                countDay = now.atZone(clock.zone).toLocalDate(),
                countToday = ReminderPolicy.shownToday(now, clock.zone, state) + 1,
            ),
        )
        return ReminderOutcome.SHOWN
    }

    /**
     * The item a reminder would show now, or null when there is nothing to study. [kinds]
     * overrides the settings' kinds (the widget shows words only).
     */
    suspend fun nextMessage(
        settings: ReminderSettings,
        avoid: ItemRef? = null,
        kinds: Set<ReminderContent> = settings.content,
    ): ReminderMessage? {
        val now = clock.instant()
        val types = kinds.map { it.type }.toSet()
        val due = progress.getDueItems(now, DUE_SCAN_LIMIT)
        val groups = buildList {
            add(ReminderReason.DUE to due.filter { it.type in types })
            add(ReminderReason.MARKED to marked(types))
            if (!settings.reviewOnly) add(ReminderReason.NEW to progress.getNewItems(NEW_SCAN_LIMIT).filter { it.type in types })
        }
        val lastShown = history.lastShown(groups.flatMap { it.second }.toSet())
        for ((reason, ref) in pick(groups, lastShown, now, avoid)) {
            val item = content.getPracticeItem(ref) ?: continue
            return ReminderMessage(item, reason, due.size, settings)
        }
        return null
    }

    private suspend fun marked(types: Set<ItemType>): List<ItemRef> = buildList {
        val query = ItemQuery(markedOnly = true)
        if (ItemType.VOCABULARY in types) content.observeVocabularyItems(query).first().forEach { add(ItemRef(ItemType.VOCABULARY, it.item.id)) }
        if (ItemType.KANJI in types) content.observeKanjiItems(query).first().forEach { add(ItemRef(ItemType.KANJI, it.item.id)) }
        if (ItemType.GRAMMAR in types) content.observeGrammarItems(query).first().forEach { add(ItemRef(ItemType.GRAMMAR, it.item.id)) }
    }

    companion object {
        /** An item shown in a reminder is held back this long when others are available. */
        val REPEAT_AFTER: Duration = Duration.ofHours(24)
        private const val DUE_SCAN_LIMIT = 500
        private const val NEW_SCAN_LIMIT = 200

        /**
         * Candidates in the order to try. Rested items (not shown within [REPEAT_AFTER]) of every
         * group come first, group by group, each group never-shown first then longest ago (ties
         * keep the group's own order: most overdue, course order). Then every recently shown
         * item, longest ago first, as a fallback. [avoid] (the last reminder) goes last.
         */
        fun pick(
            groups: List<Pair<ReminderReason, List<ItemRef>>>,
            lastShown: Map<ItemRef, Instant>,
            now: Instant,
            avoid: ItemRef?,
        ): List<Pair<ReminderReason, ItemRef>> {
            val seen = HashSet<ItemRef>()
            val all = groups.flatMap { (reason, refs) -> refs.filter { seen.add(it) }.map { reason to it } }
            fun rested(ref: ItemRef) = lastShown[ref]?.let { Duration.between(it, now) >= REPEAT_AFTER || it.isAfter(now) } ?: true
            val byAge = compareBy<Pair<ReminderReason, ItemRef>> { lastShown[it.second] ?: Instant.MIN }
            val fresh = all.filter { rested(it.second) && it.second != avoid }
                .sortedWith(compareBy<Pair<ReminderReason, ItemRef>> { it.first.ordinal }.then(byAge))
            val recent = all.filter { !rested(it.second) && it.second != avoid }.sortedWith(byAge)
            return fresh + recent + all.filter { it.second == avoid }
        }
    }
}

val ReminderContent.type: ItemType
    get() = when (this) {
        ReminderContent.VOCABULARY -> ItemType.VOCABULARY
        ReminderContent.KANJI -> ItemType.KANJI
        ReminderContent.GRAMMAR -> ItemType.GRAMMAR
    }

/** Keeps background work in step with the settings (WorkManager on Android). */
interface ReminderScheduler {
    /** Schedules, reschedules or cancels the periodic reminder work. Idempotent. */
    fun apply(settings: ReminderSettings)
}

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
import java.time.Instant

/** Why this word was chosen. */
enum class ReminderReason { DUE, MARKED, NEW }

/** Everything a notification (or widget) needs; no Android types. */
data class ReminderMessage(
    val vocabulary: Vocabulary,
    val reason: ReminderReason,
    /** Due items of all types right now. */
    val dueCount: Int,
    val settings: ReminderSettings,
)

/** Platform side of reminders: posting notifications. */
interface ReminderNotifier {
    /** False when the permission is denied or notifications/channel are turned off. */
    fun canPostNotifications(): Boolean

    /** Posts or replaces the single reminder notification. */
    fun show(message: ReminderMessage)
}

/** When the last reminder was shown and for which word (used for spacing and variety). */
data class ReminderState(val lastShownAt: Instant? = null, val lastVocabularyId: String? = null)

interface ReminderStateStore {
    suspend fun read(): ReminderState
    suspend fun write(state: ReminderState)
}

enum class ReminderOutcome { SHOWN, DISABLED, OUTSIDE_WINDOW, TOO_SOON, NOTIFICATIONS_BLOCKED, NOTHING_TO_SHOW }

/**
 * Decides and posts study reminders from Room data. Never changes the review schedule.
 *
 * Word choice: the vocabulary item that has been due the longest; otherwise one marked as
 * needing attention; otherwise the next new word in course order. Within the chosen group the
 * word shown last time is skipped when another is available.
 */
class ReminderRunner(
    private val preferences: UserPreferencesRepository,
    private val stateStore: ReminderStateStore,
    private val content: ContentRepository,
    private val progress: ProgressRepository,
    private val notifier: ReminderNotifier,
    private val clock: Clock,
) {
    /** A scheduled background run: honours on/off, window, spacing and permission. */
    suspend fun runScheduled(): ReminderOutcome {
        val settings = preferences.preferences.first().reminderSettings
        val state = stateStore.read()
        when (ReminderPolicy.decide(clock.instant(), clock.zone, settings, state.lastShownAt)) {
            ReminderDecision.DISABLED -> return ReminderOutcome.DISABLED
            ReminderDecision.OUTSIDE_WINDOW -> return ReminderOutcome.OUTSIDE_WINDOW
            ReminderDecision.TOO_SOON -> return ReminderOutcome.TOO_SOON
            ReminderDecision.NOTIFY -> Unit
        }
        return show(settings, state)
    }

    /** "Send a test reminder" from Settings: ignores on/off, window and spacing. */
    suspend fun showNow(): ReminderOutcome {
        val settings = preferences.preferences.first().reminderSettings
        return show(settings, stateStore.read())
    }

    private suspend fun show(settings: ReminderSettings, state: ReminderState): ReminderOutcome {
        if (!notifier.canPostNotifications()) return ReminderOutcome.NOTIFICATIONS_BLOCKED
        val message = nextMessage(settings, avoidId = state.lastVocabularyId) ?: return ReminderOutcome.NOTHING_TO_SHOW
        notifier.show(message)
        stateStore.write(ReminderState(clock.instant(), message.vocabulary.id))
        return ReminderOutcome.SHOWN
    }

    /** The word a reminder or widget would show now, or null when there is nothing to study. */
    suspend fun nextMessage(settings: ReminderSettings, avoidId: String? = null): ReminderMessage? {
        val now = clock.instant()
        val due = progress.getDueItems(now, DUE_SCAN_LIMIT)
        val groups = listOf(
            ReminderReason.DUE to due.filter { it.type == ItemType.VOCABULARY },
            ReminderReason.MARKED to content.observeVocabularyItems(ItemQuery(markedOnly = true)).first()
                .map { ItemRef(ItemType.VOCABULARY, it.item.id) },
            ReminderReason.NEW to progress.getNewItems(NEW_SCAN_LIMIT).filter { it.type == ItemType.VOCABULARY },
        )
        for ((reason, refs) in groups) {
            val ordered = refs.filter { it.id != avoidId } + refs.filter { it.id == avoidId }
            for (ref in ordered) {
                val vocabulary = (content.getPracticeItem(ref) as? PracticeItem.VocabularyItem)?.vocabulary ?: continue
                return ReminderMessage(vocabulary, reason, due.size, settings)
            }
        }
        return null
    }

    private companion object {
        const val DUE_SCAN_LIMIT = 500
        const val NEW_SCAN_LIMIT = 100
    }
}

/** Keeps background work in step with the settings (WorkManager on Android). */
interface ReminderScheduler {
    /** Schedules, reschedules or cancels the periodic reminder work. Idempotent. */
    fun apply(settings: ReminderSettings)
}

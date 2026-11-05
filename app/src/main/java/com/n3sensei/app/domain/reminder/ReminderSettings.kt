package com.n3sensei.app.domain.reminder

/**
 * How often a reminder may be shown. Names are persisted; never rename an entry. The settings
 * offer [SELECTABLE]; the longer periods of earlier versions are still read and kept.
 */
enum class ReminderFrequency(val minutes: Int) {
    EVERY_15_MINUTES(15),
    EVERY_30_MINUTES(30),
    EVERY_HOUR(60),
    EVERY_2_HOURS(120),
    EVERY_3_HOURS(180),
    EVERY_4_HOURS(240),
    EVERY_6_HOURS(360),
    ONCE_A_DAY(1440),
    ;

    companion object {
        /** 15 minutes is the shortest period Android's WorkManager runs periodic work at. */
        val SELECTABLE = listOf(EVERY_15_MINUTES, EVERY_30_MINUTES, EVERY_HOUR, EVERY_2_HOURS)
    }
}

/** What reminders may show. Names are persisted. */
enum class ReminderContent { VOCABULARY, KANJI, GRAMMAR }

/**
 * What a reminder may reveal on the lock screen. Names are persisted.
 *
 * Android decides what a locked screen shows; the app can only mark the notification:
 * - [SHOW_ALL]: `VISIBILITY_PUBLIC`, full content.
 * - [HIDE_MEANING]: `VISIBILITY_PRIVATE` with a public version showing only the word; Android
 *   uses the public version when the device has a secure lock and the user's system setting
 *   hides sensitive content. To never show the meaning anywhere, turn off
 *   [ReminderSettings.showMeaning].
 * - [HIDE_ALL]: `VISIBILITY_SECRET`, nothing is shown on a secure lock screen.
 */
enum class LockScreenPrivacy { SHOW_ALL, HIDE_MEANING, HIDE_ALL }

/**
 * Study reminder preferences. The window is in whole local hours: reminders are allowed from
 * [startHour]:00 until [endHour]:00; a window with start after end runs over midnight, and
 * equal hours mean the whole day.
 */
data class ReminderSettings(
    val enabled: Boolean = false,
    val startHour: Int = 8,
    val endHour: Int = 21,
    val frequency: ReminderFrequency = ReminderFrequency.EVERY_HOUR,
    val showReading: Boolean = true,
    /** Off by default: a reminder asks the learner to recall the meaning; the app shows it. */
    val showMeaning: Boolean = false,
    val lockScreen: LockScreenPrivacy = LockScreenPrivacy.HIDE_MEANING,
    /** At most this many reminders per local day; 0 = no limit besides the frequency. */
    val maxPerDay: Int = DEFAULT_MAX_PER_DAY,
    /** Kinds of items a reminder may show (never empty). */
    val content: Set<ReminderContent> = setOf(ReminderContent.VOCABULARY),
    /** Only items that need review (due, or marked as needing attention); no new items. */
    val reviewOnly: Boolean = false,
    /** One notification a day when exam errors are due for review (see ErrorReviewReminder). */
    val errorReviews: Boolean = false,
    /** Lessons each kind of content is taken from; every lesson by default. */
    val lessons: ReminderLessons = ReminderLessons(),
) {
    init {
        require(startHour in 0..23 && endHour in 0..23) { "Hours must be 0..23" }
        require(maxPerDay >= 0) { "maxPerDay must be >= 0" }
        require(content.isNotEmpty()) { "content must not be empty" }
    }

    companion object {
        const val DEFAULT_MAX_PER_DAY = 12
        val MAX_PER_DAY_CHOICES = listOf(4, 8, 12, 20, 30, 0)
    }

    /**
     * The part of the settings that background work depends on. The window and content are
     * read on every run, so changing them needs no rescheduling.
     */
    fun scheduleKey(): Pair<Boolean, ReminderFrequency> = (enabled || errorReviews) to frequency
}

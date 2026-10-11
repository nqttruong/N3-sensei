package com.n3sensei.app.domain.reminder

/** How often a reminder may be shown. Names are persisted; never rename an entry. */
enum class ReminderFrequency(val hours: Int) {
    EVERY_HOUR(1),
    EVERY_2_HOURS(2),
    EVERY_3_HOURS(3),
    EVERY_4_HOURS(4),
    EVERY_6_HOURS(6),
    ONCE_A_DAY(24),
}

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
    val frequency: ReminderFrequency = ReminderFrequency.EVERY_3_HOURS,
    val showReading: Boolean = true,
    val showMeaning: Boolean = true,
    val lockScreen: LockScreenPrivacy = LockScreenPrivacy.HIDE_MEANING,
) {
    init {
        require(startHour in 0..23 && endHour in 0..23) { "Hours must be 0..23" }
    }

    /**
     * The part of the settings that background work depends on. The window and content are
     * read on every run, so changing them needs no rescheduling.
     */
    fun scheduleKey(): Pair<Boolean, ReminderFrequency> = enabled to frequency
}

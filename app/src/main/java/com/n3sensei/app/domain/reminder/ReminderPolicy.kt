package com.n3sensei.app.domain.reminder

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

enum class ReminderDecision { NOTIFY, DISABLED, OUTSIDE_WINDOW, TOO_SOON, DAILY_LIMIT }

/**
 * Whether a scheduled run may post a reminder. Background runs are inexact (Android may delay
 * or batch them), so a reminder is allowed once 3/4 of the chosen period has passed since the
 * previous one; that absorbs scheduling jitter without ever allowing two reminders close
 * together.
 */
object ReminderPolicy {

    fun isInWindow(now: Instant, zone: ZoneId, settings: ReminderSettings): Boolean {
        val hour = now.atZone(zone).hour
        val start = settings.startHour
        val end = settings.endHour
        return when {
            start == end -> true
            start < end -> hour in start until end
            else -> hour >= start || hour < end // over midnight, e.g. 20:00–02:00
        }
    }

    fun minimumGap(settings: ReminderSettings): Duration =
        Duration.ofMinutes(settings.frequency.minutes * 3L / 4)

    /** Reminders already shown on the local day of [now] (a count from another day is 0). */
    fun shownToday(now: Instant, zone: ZoneId, state: ReminderState): Int =
        if (state.countDay == now.atZone(zone).toLocalDate()) state.countToday else 0

    fun decide(
        now: Instant,
        zone: ZoneId,
        settings: ReminderSettings,
        lastShownAt: Instant?,
        shownToday: Int = 0,
    ): ReminderDecision = when {
        !settings.enabled -> ReminderDecision.DISABLED
        !isInWindow(now, zone, settings) -> ReminderDecision.OUTSIDE_WINDOW
        // A last time in the future means the clock was moved back: do not block forever.
        lastShownAt != null && !lastShownAt.isAfter(now) &&
            Duration.between(lastShownAt, now) < minimumGap(settings) -> ReminderDecision.TOO_SOON
        settings.maxPerDay > 0 && shownToday >= settings.maxPerDay -> ReminderDecision.DAILY_LIMIT
        else -> ReminderDecision.NOTIFY
    }
}

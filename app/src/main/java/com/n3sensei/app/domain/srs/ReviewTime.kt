package com.n3sensei.app.domain.srs

import java.time.Instant
import java.time.ZoneId

/** Where a scheduled item stands relative to now. */
enum class DueState {
    /** New item: never answered. */
    NOT_SCHEDULED,
    NOT_DUE,

    /** Due earlier today (local time) or right now. */
    DUE,

    /** Was already due before today started (local midnight). */
    OVERDUE,
}

/**
 * Day boundaries are local midnight in the device's current time zone. Moments are stored as
 * UTC instants, so a time-zone change moves day boundaries but never moves due times.
 */
object ReviewTime {
    fun startOfDay(now: Instant, zone: ZoneId): Instant =
        now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()

    fun startOfNextDay(now: Instant, zone: ZoneId): Instant =
        now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()

    fun dueState(dueAt: Instant?, now: Instant, zone: ZoneId): DueState = when {
        dueAt == null -> DueState.NOT_SCHEDULED
        dueAt.isAfter(now) -> DueState.NOT_DUE
        dueAt.isBefore(startOfDay(now, zone)) -> DueState.OVERDUE
        else -> DueState.DUE
    }
}

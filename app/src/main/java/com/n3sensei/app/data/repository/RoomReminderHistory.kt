package com.n3sensei.app.data.repository

import com.n3sensei.app.data.local.dao.ReminderHistoryDao
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.reminder.ReminderHistory
import java.time.Instant

class RoomReminderHistory(private val dao: ReminderHistoryDao) : ReminderHistory {

    override suspend fun lastShown(refs: Collection<ItemRef>): Map<ItemRef, Instant> =
        refs.groupBy { it.type }.flatMap { (type, group) ->
            // SQLite allows 999 bound variables per statement.
            group.map { it.id }.chunked(CHUNK).flatMap { ids -> dao.get(type, ids) }
        }.associate { ItemRef(it.itemType, it.itemId) to Instant.ofEpochMilli(it.lastShownAt) }

    override suspend fun recordShown(ref: ItemRef, at: Instant) = dao.recordShown(ref.type, ref.id, at.toEpochMilli())

    private companion object {
        const val CHUNK = 500
    }
}

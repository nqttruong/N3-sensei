package com.n3sensei.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.n3sensei.app.data.local.entity.ReminderHistoryEntity
import com.n3sensei.app.domain.model.ItemType

@Dao
abstract class ReminderHistoryDao {
    @Query("SELECT * FROM reminder_history WHERE item_type = :type AND item_id IN (:ids)")
    abstract suspend fun get(type: ItemType, ids: List<String>): List<ReminderHistoryEntity>

    @Query("SELECT * FROM reminder_history WHERE item_type = :type AND item_id = :id")
    abstract suspend fun get(type: ItemType, id: String): ReminderHistoryEntity?

    @Upsert
    abstract suspend fun upsert(row: ReminderHistoryEntity)

    /** Read then write in one transaction: SQL UPSERT needs SQLite 3.24 (Android 11), minSdk is 26. */
    @Transaction
    open suspend fun recordShown(type: ItemType, id: String, at: Long) {
        val count = get(type, id)?.shownCount ?: 0
        upsert(ReminderHistoryEntity(type, id, at, count + 1))
    }
}

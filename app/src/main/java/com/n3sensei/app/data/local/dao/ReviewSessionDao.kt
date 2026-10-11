package com.n3sensei.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.n3sensei.app.data.local.entity.ReviewSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReviewSessionDao {
    /** Ignored when the id exists (a recreated screen must not reset a session). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(session: ReviewSessionEntity)

    /** Never lowers the count: answers are only ever added. */
    @Query("UPDATE review_sessions SET answered = MAX(answered, :answered), last_activity_at = :at WHERE id = :id")
    suspend fun updateAnswered(id: String, answered: Int, at: Long)

    @Query("UPDATE review_sessions SET finished = 1, last_activity_at = :at WHERE id = :id")
    suspend fun finish(id: String, at: Long)

    @Query("SELECT * FROM review_sessions ORDER BY started_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ReviewSessionEntity>>
}

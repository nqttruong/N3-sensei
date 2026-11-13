package com.n3sensei.app.domain.repository

import com.n3sensei.app.domain.model.JlptKnowledge
import com.n3sensei.app.domain.model.JlptQuestion
import com.n3sensei.app.domain.model.JlptReviewItem
import com.n3sensei.app.domain.model.JlptMistakeDetail
import kotlinx.coroutines.flow.Flow

interface SyncRepository {
    fun isPaired(): Boolean
    fun serverUrl(): String
    suspend fun pair(serverUrl: String, pairingKey: String, deviceName: String): String
    suspend fun pushReviews(): String
    suspend fun syncNow(): String
    suspend fun disconnect()
    suspend fun status(): SyncStatus
    fun observeKnowledge(): Flow<List<JlptKnowledge>>
    fun observeMistakes(): Flow<List<JlptQuestion>>
    fun observeReviewItems(): Flow<List<JlptReviewItem>>
    fun observePendingEventCount(): Flow<Int>
    suspend fun mistakeDetail(question: JlptQuestion): JlptMistakeDetail
    suspend fun recordOfflineReview(item: JlptReviewItem, rating: String)
}

data class SyncStatus(
    val lastSuccessAtMillis: Long?,
    val downloadedCount: Long,
    val knowledgeCount: Int,
    val pendingEvents: Int,
    val lastError: String?,
)

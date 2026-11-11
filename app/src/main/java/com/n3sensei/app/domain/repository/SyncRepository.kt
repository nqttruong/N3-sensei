package com.n3sensei.app.domain.repository

interface SyncRepository {
    fun isPaired(): Boolean
    fun serverUrl(): String
    suspend fun pair(serverUrl: String, pairingKey: String, deviceName: String): String
    suspend fun pushReviews(): String
}

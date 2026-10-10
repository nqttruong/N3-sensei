package com.n3sensei.app.app.di

import android.content.Context
import com.n3sensei.app.data.local.N3Database
import com.n3sensei.app.data.repository.OfflineContentRepository
import com.n3sensei.app.data.repository.OfflineProgressRepository
import com.n3sensei.app.domain.repository.ContentRepository
import com.n3sensei.app.domain.repository.ProgressRepository

/**
 * Manual dependency container, created once in [com.n3sensei.app.N3SenseiApplication] and
 * scoped to the application. Every class receives its dependencies through its constructor.
 */
interface AppContainer {
    val contentRepository: ContentRepository
    val progressRepository: ProgressRepository
}

class DefaultAppContainer(context: Context) : AppContainer {

    // Opened lazily: Room only touches the disk on the first query.
    private val database: N3Database by lazy { N3Database.create(context) }

    override val contentRepository: ContentRepository by lazy {
        OfflineContentRepository(database.contentDao())
    }

    override val progressRepository: ProgressRepository by lazy {
        OfflineProgressRepository(database.progressDao())
    }
}

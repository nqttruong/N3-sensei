package com.n3sensei.app.app.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.n3sensei.app.data.importing.AndroidContentImportRepository
import com.n3sensei.app.data.importing.ContentPackImporter
import com.n3sensei.app.data.local.N3Database
import com.n3sensei.app.app.reminder.AndroidReminderNotifier
import com.n3sensei.app.app.reminder.WorkManagerReminderScheduler
import com.n3sensei.app.data.preferences.DataStoreReminderStateStore
import com.n3sensei.app.data.preferences.DataStoreUserPreferencesRepository
import com.n3sensei.app.data.repository.OfflineAnalyticsRepository
import com.n3sensei.app.data.repository.OfflineContentRepository
import com.n3sensei.app.data.repository.OfflineProgressRepository
import com.n3sensei.app.data.repository.OfflineReviewSessionRepository
import com.n3sensei.app.domain.forecast.ReviewSessionRepository
import com.n3sensei.app.domain.reminder.ReminderNotifier
import com.n3sensei.app.domain.reminder.ReminderRunner
import com.n3sensei.app.domain.reminder.ReminderScheduler
import com.n3sensei.app.domain.repository.AnalyticsRepository
import com.n3sensei.app.domain.repository.ContentImportRepository
import com.n3sensei.app.domain.repository.ContentRepository
import com.n3sensei.app.domain.repository.ProgressRepository
import com.n3sensei.app.domain.repository.UserPreferencesRepository
import java.time.Clock

/**
 * Manual dependency container, created once in [com.n3sensei.app.N3SenseiApplication] and
 * scoped to the application. Every class receives its dependencies through its constructor.
 */
interface AppContainer {
    val contentRepository: ContentRepository
    val progressRepository: ProgressRepository
    val analyticsRepository: AnalyticsRepository
    val reviewSessionRepository: ReviewSessionRepository
    val userPreferencesRepository: UserPreferencesRepository
    val contentImportRepository: ContentImportRepository
    val reminderScheduler: ReminderScheduler
    val reminderNotifier: ReminderNotifier
    val reminderRunner: ReminderRunner
    val clock: Clock
}

class DefaultAppContainer(context: Context) : AppContainer {

    override val clock: Clock = DeviceClock

    private val appContext = context.applicationContext

    /** Opened lazily: Room only touches the disk on the first query. Internal for tests. */
    internal val database: N3Database by lazy { N3Database.create(appContext) }

    override val contentRepository: ContentRepository by lazy {
        OfflineContentRepository(database.contentDao(), database.contentPackDao())
    }

    override val analyticsRepository: AnalyticsRepository by lazy { OfflineAnalyticsRepository(database.analyticsDao()) }

    override val reviewSessionRepository: ReviewSessionRepository by lazy { OfflineReviewSessionRepository(database.reviewSessionDao()) }

    override val progressRepository: ProgressRepository by lazy {
        OfflineProgressRepository(database.progressDao(), clock)
    }

    // One DataStore instance per file per process, so it lives here and nowhere else.
    private val preferencesDataStore by lazy {
        PreferenceDataStoreFactory.create { appContext.preferencesDataStoreFile("user_preferences") }
    }

    override val userPreferencesRepository: UserPreferencesRepository by lazy {
        DataStoreUserPreferencesRepository(preferencesDataStore)
    }

    override val reminderScheduler: ReminderScheduler by lazy { WorkManagerReminderScheduler(appContext) }

    override val reminderNotifier: ReminderNotifier by lazy { AndroidReminderNotifier(appContext) }

    internal val reminderStateStore by lazy { DataStoreReminderStateStore(preferencesDataStore) }

    override val reminderRunner: ReminderRunner by lazy {
        ReminderRunner(
            userPreferencesRepository,
            reminderStateStore,
            contentRepository,
            progressRepository,
            reminderNotifier,
            clock,
        )
    }

    /** Concrete type is internal so tests can switch off the bundled pack. */
    internal val androidContentImportRepository: AndroidContentImportRepository by lazy {
        AndroidContentImportRepository(appContext, ContentPackImporter(database.contentPackDao(), clock))
    }

    override val contentImportRepository: ContentImportRepository get() = androidContentImportRepository
}

package com.n3sensei.app.app.reminder

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.n3sensei.app.domain.reminder.ReminderScheduler
import com.n3sensei.app.domain.reminder.ReminderSettings
import java.util.concurrent.TimeUnit

/**
 * One unique periodic job, every [ReminderSettings.frequency] (15 minutes to a day).
 *
 * Android limits background work: WorkManager runs are inexact (the system may delay them,
 * e.g. in Doze or app-standby buckets, and batches them with other apps), the minimum period
 * is 15 minutes, and no exact alarms are used. WorkManager keeps the job across app restarts
 * and device reboots. The job does not run while the battery is low.
 */
class WorkManagerReminderScheduler(private val context: Context) : ReminderScheduler {

    override fun apply(settings: ReminderSettings) {
        val workManager = WorkManager.getInstance(context)
        // One periodic run serves the study reminders and the error review reminder.
        if (!settings.enabled && !settings.errorReviews) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(
            settings.frequency.minutes.toLong(), TimeUnit.MINUTES,
            flexMinutes(settings.frequency.minutes), TimeUnit.MINUTES,
        )
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .addTag(WORK_NAME)
            .build()
        // UPDATE keeps the existing schedule when nothing changed and applies a new period otherwise.
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    companion object {
        const val WORK_NAME = "study_reminder"

        /**
         * The window at the end of each period in which the run may happen: a quarter of the
         * period, between WorkManager's 5-minute minimum and 30 minutes.
         */
        fun flexMinutes(periodMinutes: Int): Long = (periodMinutes / 4).coerceIn(5, 30).toLong()
    }
}

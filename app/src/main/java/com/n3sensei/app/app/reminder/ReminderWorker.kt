package com.n3sensei.app.app.reminder

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.n3sensei.app.N3SenseiApplication
import com.n3sensei.app.app.widget.StudyWordWidget

/**
 * Periodic background run. All decisions (window, spacing, which word) are made by the
 * domain [com.n3sensei.app.domain.reminder.ReminderRunner]; this class only connects it to
 * WorkManager and refreshes the home-screen widget.
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as N3SenseiApplication).container
        val outcome = container.reminderRunner.runScheduled()
        runCatching { StudyWordWidget().updateAll(applicationContext) }
        // A reminder that could not be shown is not a failure: retrying would only cause bursts.
        return Result.success(workDataOf(KEY_OUTCOME to outcome.name))
    }

    companion object {
        const val KEY_OUTCOME = "outcome"
    }
}

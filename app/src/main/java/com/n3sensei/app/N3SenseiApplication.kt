package com.n3sensei.app

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.StrictMode
import com.n3sensei.app.app.di.AppContainer
import com.n3sensei.app.app.reminder.AndroidReminderNotifier
import com.n3sensei.app.app.di.DefaultAppContainer

class N3SenseiApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) enableStrictModeLogging()
        container = DefaultAppContainer(this)
        AndroidReminderNotifier.ensureChannel(this)
    }

    /**
     * Debug builds only: logs (tag StrictMode) disk or network access on the main thread and
     * leaked resources, so such regressions show up during development and QA. Never crashes.
     */
    private fun enableStrictModeLogging() {
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().detectNetwork().penaltyLog().build())
        StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().detectLeakedSqlLiteObjects().penaltyLog().build())
    }
}

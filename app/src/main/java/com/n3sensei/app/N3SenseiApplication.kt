package com.n3sensei.app

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.StrictMode
import com.n3sensei.app.app.di.AppContainer
import com.n3sensei.app.app.reminder.AndroidReminderNotifier
import com.n3sensei.app.app.di.DefaultAppContainer
import com.n3sensei.app.app.translate.QuickTranslateMenu
import com.n3sensei.app.app.translate.QuickTranslateOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class N3SenseiApplication : Application() {

    lateinit var container: AppContainer
        private set

    /** The floating "Dịch nhanh" popup (one per process). */
    val quickTranslateOverlay: QuickTranslateOverlay by lazy { QuickTranslateOverlay(this) { container } }

    override fun onCreate() {
        super.onCreate()
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) enableStrictModeLogging()
        container = DefaultAppContainer(this)
        AndroidReminderNotifier.ensureChannel(this)
        // Settings → Dịch nhanh on/off, applied to the text selection menu entry.
        appScope.launch { QuickTranslateMenu.follow(this@N3SenseiApplication, container.userPreferencesRepository) }
    }

    /** Work that lives as long as the process (off the main thread). */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Debug builds only: logs (tag StrictMode) disk or network access on the main thread and
     * leaked resources, so such regressions show up during development and QA. Never crashes.
     */
    private fun enableStrictModeLogging() {
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().detectNetwork().penaltyLog().build())
        StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().detectLeakedSqlLiteObjects().penaltyLog().build())
    }
}

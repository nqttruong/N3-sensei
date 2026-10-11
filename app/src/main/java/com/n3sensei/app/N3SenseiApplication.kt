package com.n3sensei.app

import android.app.Application
import com.n3sensei.app.app.di.AppContainer
import com.n3sensei.app.app.reminder.AndroidReminderNotifier
import com.n3sensei.app.app.di.DefaultAppContainer

class N3SenseiApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
        AndroidReminderNotifier.ensureChannel(this)
    }
}

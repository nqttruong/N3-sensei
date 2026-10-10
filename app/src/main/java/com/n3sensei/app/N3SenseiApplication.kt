package com.n3sensei.app

import android.app.Application
import com.n3sensei.app.app.AppContainer
import com.n3sensei.app.app.DefaultAppContainer

class N3SenseiApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}

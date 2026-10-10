package com.n3sensei.app.app.di

import android.content.Context

/**
 * Manual dependency container, created once in [com.n3sensei.app.N3SenseiApplication] and
 * scoped to the application. Holds app-wide dependencies (database, repositories, settings
 * store) as they are added; every class receives its dependencies through its constructor.
 */
interface AppContainer

class DefaultAppContainer(
    @Suppress("unused") private val context: Context,
) : AppContainer

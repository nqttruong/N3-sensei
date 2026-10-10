package com.n3sensei.app.app

import android.content.Context

/**
 * Manual dependency container. Holds app-wide singletons (database, repositories,
 * settings store) so ViewModels never construct data sources themselves.
 */
interface AppContainer

class DefaultAppContainer(
    @Suppress("unused") private val context: Context,
) : AppContainer

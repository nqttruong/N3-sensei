package com.n3sensei.app.app.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.n3sensei.app.N3SenseiApplication

/**
 * Obtains a ViewModel scoped to the current NavBackStackEntry, built from [AppContainer]
 * dependencies. Call it from the navigation layer and pass the ViewModel to the feature
 * screen, so feature code never touches the container.
 *
 * ```
 * composable<VocabularyRoute> {
 *     val vm = appViewModel { VocabularyViewModel(vocabularyRepository) }
 *     VocabularyRoute(vm)
 * }
 * ```
 */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    crossinline create: AppContainer.() -> VM,
): VM {
    val container = (LocalContext.current.applicationContext as N3SenseiApplication).container
    return viewModel(factory = viewModelFactory { initializer { container.create() } })
}

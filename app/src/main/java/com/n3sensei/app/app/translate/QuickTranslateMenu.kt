package com.n3sensei.app.app.translate

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.n3sensei.app.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * Whether "Dịch nhanh" is in other apps' text selection menu: the setting (DataStore) is the
 * source of truth, applied to the [QuickTranslateActivity] component. A disabled component is
 * left out of the system's list of PROCESS_TEXT handlers, so the menu no longer offers it; an
 * app that built its menu before the change may still show it once, and the dialog then says
 * the feature is off. DONT_KILL_APP: switching it from Settings never stops the running app.
 */
object QuickTranslateMenu {

    private fun component(context: Context) = ComponentName(context, QuickTranslateActivity::class.java)

    fun isOffered(context: Context): Boolean =
        context.packageManager.getComponentEnabledSetting(component(context)) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    /** Enabled = the manifest's default state, so a later app update keeps its own declaration. */
    fun apply(context: Context, enabled: Boolean) {
        if (isOffered(context) == enabled) return
        context.packageManager.setComponentEnabledSetting(
            component(context),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_DEFAULT else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    /** Keeps the component in step with the setting for as long as the process lives (call from a background scope). */
    suspend fun follow(context: Context, preferences: UserPreferencesRepository) {
        preferences.preferences
            .map { it.quickTranslate.enabled }
            .catch { /* unreadable settings: leave the menu as it is */ }
            .distinctUntilChanged()
            .onEach { apply(context, it) }
            .collect()
    }
}

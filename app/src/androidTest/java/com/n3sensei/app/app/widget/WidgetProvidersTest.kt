package com.n3sensei.app.app.widget

import android.appwidget.AppWidgetManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The two widgets are real App Widget providers that launchers list in their widget picker:
 * a 1×1 and a 2×2, both resizable, without periodic wake-ups.
 */
@RunWith(AndroidJUnit4::class)
class WidgetProvidersTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val providers = AppWidgetManager.getInstance(context)
        .getInstalledProvidersForPackage(context.packageName, null)
        .associateBy { it.provider.className }

    @Test
    fun bothSizes_areRegistered_withTheirLabels() {
        val small = providers.getValue(SmallStudyWordWidgetReceiver::class.java.name)
        val large = providers.getValue(StudyWordWidgetReceiver::class.java.name)
        val pm = context.packageManager
        assertEquals(context.getString(R.string.widget_small_title), small.loadLabel(pm))
        assertEquals(context.getString(R.string.widget_title), large.loadLabel(pm))
        listOf(small, large).forEach {
            assertEquals(0, it.updatePeriodMillis)
            assertTrue(it.resizeMode and android.appwidget.AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0)
            assertTrue(it.resizeMode and android.appwidget.AppWidgetProviderInfo.RESIZE_VERTICAL != 0)
            assertTrue(it.widgetCategory and android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            assertEquals(1 to 1, small.targetCellWidth to small.targetCellHeight)
            assertEquals(2 to 2, large.targetCellWidth to large.targetCellHeight)
        }
        assertTrue(small.minWidth < large.minWidth)
    }
}

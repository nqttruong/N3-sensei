package com.n3sensei.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.n3sensei.app.app.navigation.TopLevelDestination
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launch_showsAppNameOnHomeTab() {
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.app_name)).assertIsDisplayed()
        composeRule.onNodeWithTag(TopLevelDestination.HOME.testTag).assertIsSelected()
    }

    @Test
    fun backOnStartDestination_leavesApp() {
        val activity = composeRule.activity
        Espresso.pressBackUnconditionally()
        composeRule.waitForIdle()
        assertTrue(activity.isFinishing || activity.isDestroyed)
    }
}

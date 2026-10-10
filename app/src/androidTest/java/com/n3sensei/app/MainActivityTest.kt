package com.n3sensei.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun string(id: Int) = composeRule.activity.getString(id)

    @Test
    fun launch_showsAppNameAndLearnTabSelected() {
        composeRule.onNodeWithText(string(R.string.app_name)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.learn_placeholder)).assertIsDisplayed()
        tab(R.string.nav_learn).assertIsSelected()
    }

    // The label also appears as the screen heading; only the bar item is clickable.
    private fun tab(labelRes: Int) =
        composeRule.onNode(hasText(string(labelRes)) and hasClickAction())

    @Test
    fun tappingTab_switchesContent() {
        val tabs = listOf(
            R.string.nav_review to R.string.review_placeholder,
            R.string.nav_stats to R.string.stats_placeholder,
            R.string.nav_settings to R.string.settings_placeholder,
            R.string.nav_learn to R.string.learn_placeholder,
        )
        tabs.forEach { (label, message) ->
            tab(label).performClick()
            tab(label).assertIsSelected()
            composeRule.onNodeWithText(string(message)).assertIsDisplayed()
        }
    }
}

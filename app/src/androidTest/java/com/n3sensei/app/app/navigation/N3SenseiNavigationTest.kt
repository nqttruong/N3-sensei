package com.n3sensei.app.app.navigation

import androidx.annotation.StringRes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.R
import com.n3sensei.app.app.N3SenseiApp
import com.n3sensei.app.core.designsystem.theme.N3SenseiTheme
import com.n3sensei.app.testing.AppTestState
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class N3SenseiNavigationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var navController: TestNavHostController

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun string(@StringRes id: Int) = context.getString(id)

    @Before
    fun setUp() {
        AppTestState.reset(seedContent = false)
        composeRule.setContent {
            navController = TestNavHostController(LocalContext.current).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
            }
            N3SenseiTheme { N3SenseiApp(navController) }
        }
    }

    private fun assertOn(screen: AppScreen) {
        composeRule.runOnIdle {
            assertEquals(screen, AppScreen.from(navController.currentBackStackEntry?.destination))
        }
        val isHeading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
        composeRule.onNode(hasText(string(screen.titleRes)) and isHeading).assertIsDisplayed()
        composeRule.onNodeWithTag(screen.tab.testTag).assertIsSelected()
    }

    private fun clickTab(tab: TopLevelDestination) =
        composeRule.onNodeWithTag(tab.testTag).performClick()

    private fun clickUp() =
        composeRule.onNode(hasContentDescription(string(R.string.common_navigate_up))).performClick()

    private fun pressBack() {
        Espresso.pressBack()
        composeRule.waitForIdle()
    }

    private val learnSubScreens = listOf(
        R.string.lessons_title to AppScreen.LESSONS,
        R.string.vocabulary_title to AppScreen.VOCABULARY,
        R.string.kanji_title to AppScreen.KANJI,
        R.string.grammar_title to AppScreen.GRAMMAR,
    )

    @Test
    fun startsOnHome() {
        assertOn(AppScreen.HOME)
    }

    @Test
    fun everyTabIsReachable() {
        TopLevelDestination.entries.forEach { tab ->
            clickTab(tab)
            assertOn(AppScreen.entries.single { it.tab == tab && it.isTabRoot })
        }
    }

    @Test
    fun learnSubScreens_areReachable_andUpReturnsToHub() {
        clickTab(TopLevelDestination.LEARN)
        learnSubScreens.forEach { (label, screen) ->
            composeRule.onNodeWithText(string(label)).performClick()
            assertOn(screen)
            clickUp()
            assertOn(AppScreen.LEARN_HUB)
        }
    }

    @Test
    fun systemBack_fromLearnSubScreen_returnsToHub_thenHome() {
        clickTab(TopLevelDestination.LEARN)
        learnSubScreens.forEach { (label, screen) ->
            composeRule.onNodeWithText(string(label)).performClick()
            assertOn(screen)
            pressBack()
            assertOn(AppScreen.LEARN_HUB)
        }
        pressBack()
        assertOn(AppScreen.HOME)
    }

    @Test
    fun systemBack_fromAnyTabRoot_returnsToHome() {
        TopLevelDestination.entries.filter { it != TopLevelDestination.HOME }.forEach { tab ->
            clickTab(tab)
            pressBack()
            assertOn(AppScreen.HOME)
        }
    }

    @Test
    fun switchingTabs_keepsOneCopyOfEachTabOnBackStack() {
        clickTab(TopLevelDestination.REVIEW)
        clickTab(TopLevelDestination.STATS)
        clickTab(TopLevelDestination.REVIEW)
        pressBack()
        assertOn(AppScreen.HOME)
    }

    @Test
    fun returningToLearnTab_restoresItsSubScreen() {
        clickTab(TopLevelDestination.LEARN)
        composeRule.onNodeWithText(string(R.string.kanji_title)).performClick()
        clickTab(TopLevelDestination.REVIEW)
        clickTab(TopLevelDestination.LEARN)
        assertOn(AppScreen.KANJI)
    }

    @Test
    fun homeShortcuts_openTheirTabs() {
        composeRule.onNodeWithText(string(R.string.dashboard_start_review)).performScrollTo().performClick()
        assertOn(AppScreen.REVIEW)
        pressBack()
        composeRule.onNodeWithText(string(R.string.dashboard_open_lessons)).performScrollTo().performClick()
        assertOn(AppScreen.LESSONS)
        pressBack()
        assertOn(AppScreen.LEARN_HUB)
        clickTab(TopLevelDestination.HOME)
        composeRule.onNodeWithText(string(R.string.dashboard_open_stats)).performScrollTo().performClick()
        assertOn(AppScreen.STATS)
    }

    @Test
    fun detailRouteWithUnknownId_showsNotFound() {
        composeRule.runOnIdle { navController.navigate(VocabularyDetailRoute("does-not-exist")) }
        assertOn(AppScreen.VOCABULARY_DETAIL)
        composeRule.waitUntil { composeRule.onAllNodesWithText(string(R.string.common_not_found)).fetchSemanticsNodes().isNotEmpty() }
        clickUp()
        assertOn(AppScreen.HOME)
    }

    @Test
    fun graph_containsExactlyTheRegisteredScreens() {
        composeRule.runOnIdle {
            val leaves = navController.graph.leafDestinations()
            val matched = leaves.map { dest ->
                val screens = AppScreen.entries.filter { it.matches(dest) }
                assertEquals("Destination ${dest.route} must match one AppScreen", 1, screens.size)
                screens.single()
            }
            assertEquals(AppScreen.entries.toSet(), matched.toSet())
            assertEquals(AppScreen.entries.size, leaves.size)
        }
    }

    private fun NavGraph.leafDestinations(): List<NavDestination> =
        flatMap { if (it is NavGraph) it.leafDestinations() else listOf(it) }
}

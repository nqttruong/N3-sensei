package com.n3sensei.app.feature.exam

import androidx.annotation.StringRes
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.theme.N3SenseiTheme
import com.n3sensei.app.domain.exam.ExamResultManager
import com.n3sensei.app.domain.repository.ExamRepository
import org.junit.Test
import org.junit.runner.RunWith

/** QA: a database failure under an exam screen shows the error with "Thử lại" instead of crashing. */
@RunWith(AndroidJUnit4::class)
class ExamErrorStateUiTest {

    @get:org.junit.Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun string(@StringRes id: Int) = context.getString(id)

    private fun waitFor(text: String) = composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun aDatabaseFailure_showsTheErrorWithRetry() {
        // A repository whose every call fails, as when the disk cannot be read.
        val failing = java.lang.reflect.Proxy.newProxyInstance(
            ExamRepository::class.java.classLoader, arrayOf(ExamRepository::class.java),
        ) { _, _, _ -> throw java.io.IOException("disk I/O error") } as ExamRepository
        val viewModel = ExamResultViewModel("any", ExamResultManager(failing))
        composeRule.setContent {
            N3SenseiTheme {
                ExamResultScreen(viewModel, onReview = { _, _ -> }, onOpenNotebook = {}, onNewExam = {}, onRetake = {}, onOpenAttempt = {}, onAnalyze = {})
            }
        }
        waitFor(string(R.string.common_error_generic))
        composeRule.onNodeWithText(string(R.string.common_retry)).performClick()
        waitFor(string(R.string.common_error_generic))
    }
}

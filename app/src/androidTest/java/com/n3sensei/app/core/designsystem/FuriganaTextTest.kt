package com.n3sensei.app.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.n3sensei.app.core.designsystem.component.FuriganaText
import com.n3sensei.app.core.designsystem.theme.N3SenseiTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FuriganaTextTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** Heights (dp) of a one-group short word and of [markup], laid out side by side in boxes [widthDp] wide. */
    private fun heights(markup: String, base: String, widthDp: Int): Pair<Float, Float> {
        composeRule.setContent {
            N3SenseiTheme {
                Column {
                    Box(Modifier.width(widthDp.dp)) { FuriganaText("{経|けい}", style = style()) }
                    Box(Modifier.width(widthDp.dp)) { FuriganaText(markup, style = style()) }
                }
            }
        }
        fun h(text: String) = with(composeRule.density) { composeRule.onNodeWithText(text).fetchSemanticsNode().size.height.toDp().value }
        return h("経") to h(base)
    }

    @Composable
    private fun style() = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp, lineHeight = 48.sp)

    /**
     * Regression: one ruby group wider than the screen (a long word given a single reading, or
     * large font scale) was kept on one line and cut off at the edge. It must wrap instead.
     */
    @Test
    fun rubyGroupWiderThanTheScreen_wrapsInsteadOfBeingCut() {
        // 8 kanji at 40sp ≈ 320dp in a 200dp box: needs at least two base lines.
        val (oneLine, long) = heights("{国際化標準化機構|こくさいかひょうじゅんかきこう}", "国際化標準化機構", 200)
        assertTrue("one line $oneLine dp, long word $long dp: the group was not wrapped", long > oneLine * 1.4f)
    }
}

package com.n3sensei.app.feature.review

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.theme.N3SenseiTheme
import com.n3sensei.app.core.ui.PlaceholderContent

@Composable
fun ReviewScreen(modifier: Modifier = Modifier) {
    PlaceholderContent(
        titleRes = R.string.nav_review,
        messageRes = R.string.review_placeholder,
        modifier = modifier,
    )
}

@Preview(showBackground = true)
@Composable
private fun ReviewScreenPreview() {
    N3SenseiTheme { ReviewScreen() }
}

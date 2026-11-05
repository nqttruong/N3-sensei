package com.n3sensei.app.core.ui.content

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.n3sensei.app.domain.model.LearningStatus
import com.n3sensei.app.domain.model.ReviewRating

/**
 * The one mapping from answers and learning statuses to colours, taken from the colour scheme
 * so both themes follow. Buttons use the container pair, charts and legends the accent.
 */
object StudyColors {
    @Composable
    @ReadOnlyComposable
    fun ratingContainer(rating: ReviewRating): Pair<Color, Color> {
        val c = MaterialTheme.colorScheme
        return when (rating) {
            ReviewRating.AGAIN -> c.errorContainer to c.onErrorContainer
            ReviewRating.HARD -> c.secondaryContainer to c.onSecondaryContainer
            ReviewRating.GOOD -> c.primaryContainer to c.onPrimaryContainer
            ReviewRating.EASY -> c.tertiaryContainer to c.onTertiaryContainer
        }
    }

    @Composable
    @ReadOnlyComposable
    fun ratingAccent(rating: ReviewRating): Color {
        val c = MaterialTheme.colorScheme
        return when (rating) {
            ReviewRating.AGAIN -> c.error
            ReviewRating.HARD -> c.outline
            ReviewRating.GOOD -> c.primary
            ReviewRating.EASY -> c.tertiary
        }
    }

    @Composable
    @ReadOnlyComposable
    fun status(status: LearningStatus): Color {
        val c = MaterialTheme.colorScheme
        return when (status) {
            LearningStatus.NEW -> c.outlineVariant
            LearningStatus.LEARNING -> c.inversePrimary
            LearningStatus.REVIEW -> c.primary
            LearningStatus.MASTERED -> c.tertiary
        }
    }
}

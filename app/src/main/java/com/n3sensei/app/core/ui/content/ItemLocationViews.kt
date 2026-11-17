package com.n3sensei.app.core.ui.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.component.Caption
import com.n3sensei.app.core.designsystem.component.SectionHeader
import com.n3sensei.app.core.designsystem.component.Tag
import com.n3sensei.app.core.designsystem.component.TagTone
import com.n3sensei.app.core.ui.state.DetailLocation
import com.n3sensei.app.domain.course.ItemLocation

/** "Bài 1.2 · Từ vựng", "Bài 04 · Kanji", "Từ của tôi", "Chưa thuộc bài". */
@Composable
fun locationLabel(location: ItemLocation): String = when (location.kind) {
    ItemLocation.Kind.VOCABULARY -> location.vocabularyGroup?.let { stringResource(R.string.location_vocabulary, it) }
        ?: stringResource(R.string.location_none)
    ItemLocation.Kind.KANJI -> location.courseLesson?.let {
        stringResource(R.string.location_in_course, stringResource(R.string.course_lesson_title, it), stringResource(R.string.totals_kanji))
    } ?: location.levelLesson?.let { (level, number) -> stringResource(R.string.location_kanji_level, level, number) }
        ?: stringResource(R.string.location_none)
    ItemLocation.Kind.GRAMMAR -> location.courseLesson?.let {
        stringResource(R.string.location_in_course, stringResource(R.string.course_lesson_title, it), stringResource(R.string.totals_grammar))
    } ?: stringResource(R.string.location_none)
    ItemLocation.Kind.MY_WORDS -> stringResource(R.string.learn_my_words)
    ItemLocation.Kind.NONE -> stringResource(R.string.location_none)
}

/** Compact location on a list card: one small tag, quiet unless the item has no lesson. */
@Composable
fun LocationTag(location: ItemLocation, modifier: Modifier = Modifier) {
    Tag(
        locationLabel(location),
        modifier = modifier,
        tone = if (location.kind == ItemLocation.Kind.MY_WORDS) TagTone.Accent else TagTone.Neutral,
    )
}

/**
 * "Vị trí học" on a detail screen: where the item is studied (Học → Từ vựng group, Học → Bài
 * học lesson), its source lesson, and other lessons that have the same word.
 */
@Composable
fun LocationSection(info: DetailLocation, modifier: Modifier = Modifier) {
    val location = info.location
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionHeader(stringResource(R.string.location_section))
        LocationTag(location)
        if (location.kind == ItemLocation.Kind.VOCABULARY) {
            location.courseLesson?.let {
                Text(stringResource(R.string.location_course_line, stringResource(R.string.course_lesson_title, it)), style = MaterialTheme.typography.bodyMedium)
            }
        }
        location.sourceTitle?.let { Text(stringResource(R.string.location_source_line, it), style = MaterialTheme.typography.bodyMedium) }
        when (location.kind) {
            ItemLocation.Kind.MY_WORDS -> Caption(stringResource(R.string.location_my_words_note))
            ItemLocation.Kind.NONE -> Caption(stringResource(R.string.location_none_note))
            else -> Unit
        }
        if (info.sameWord.isNotEmpty()) {
            val others = info.sameWord.map { locationLabel(it.location) }.distinct().joinToString(", ")
            Text(stringResource(R.string.location_same_word, others), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

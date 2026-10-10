package com.n3sensei.app.core.ui.content

import com.n3sensei.app.domain.model.GrammarPoint
import com.n3sensei.app.domain.model.Kanji
import com.n3sensei.app.domain.model.LessonSummary
import com.n3sensei.app.domain.model.Vocabulary

/**
 * Sample values for @Preview functions only. Never use from screens or ViewModels: the app
 * shows database content exclusively.
 */
internal object PreviewContent {
    val vocabulary = Vocabulary("p-v", "p-l", "経験", "けいけん", "{経験|けいけん}", "kinh nghiệm", "danh từ", "N3", null)
    val kanji = Kanji("p-k", "p-l", "経", listOf("ケイ", "キョウ"), listOf("へ.る"), "KINH", "trải qua", 11, null)
    val grammar = GrammarPoint("p-g", "p-l", "〜たばかり", "V-た + ばかり", "vừa mới", "", null)
    val lesson = LessonSummary("p-l", "p", 1, "Bài xem trước", "N3", null, 30, 27, 7)
}

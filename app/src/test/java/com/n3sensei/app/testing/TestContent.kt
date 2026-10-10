package com.n3sensei.app.testing

import com.n3sensei.app.domain.model.ContentSource
import com.n3sensei.app.domain.model.GrammarPoint
import com.n3sensei.app.domain.model.Kanji
import com.n3sensei.app.domain.model.LessonSummary
import com.n3sensei.app.domain.model.Vocabulary

/** TEST DATA ONLY: small fixtures for JVM ViewModel tests. */
object TestContent {
    val source = ContentSource("src", "Nguồn kiểm thử", null, "test", null, licenseVerified = false)

    fun vocabulary(id: String, lessonId: String, expression: String, reading: String = expression, meaning: String = "nghĩa $id") =
        Vocabulary(id, lessonId, expression, reading, null, meaning, null, "N3", null)

    fun kanji(id: String, lessonId: String, character: String) =
        Kanji(id, lessonId, character, listOf("オン"), listOf("くん"), "HV", "nghĩa $id", null, null)

    fun grammar(id: String, lessonId: String, pattern: String) =
        GrammarPoint(id, lessonId, pattern, "V + $pattern", "nghĩa $id", "", null)

    fun lesson(id: String, number: Int, vocabulary: Int = 0, kanji: Int = 0, grammar: Int = 0) =
        LessonSummary(id, "pack", number, "Bài $number", "N3", null, vocabulary, kanji, grammar)
}

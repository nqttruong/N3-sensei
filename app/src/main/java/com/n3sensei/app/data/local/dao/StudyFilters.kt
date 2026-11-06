package com.n3sensei.app.data.local.dao

/**
 * A vocabulary row aliased `v` that is studied: every word except the learner's "Dịch nhanh"
 * bookmarks still `SAVED` (see UserWordEntity). Used by every query that picks or counts words
 * to study (new cards, "new available", statistics, lists feeding practice, reminders and the
 * widget); lists of "Từ của tôi" and detail screens include saved words.
 */
internal const val STUDIED_WORD = "NOT EXISTS (SELECT 1 FROM user_words u WHERE u.vocabulary_id = v.id AND u.study_state = 'SAVED')"

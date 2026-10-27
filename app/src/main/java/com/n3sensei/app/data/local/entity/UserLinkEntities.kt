package com.n3sensei.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A vocabulary item the learner linked to a kanji (many-to-many; learner data). No foreign
 * keys, like progress: installing a pack deletes and re-inserts its rows, which must not take
 * the learner's links with them. A link whose word or kanji left its pack is kept and shows
 * again if the item returns.
 */
@Entity(
    tableName = "vocabulary_kanji_links",
    primaryKeys = ["vocabulary_id", "kanji_id"],
    indices = [Index("kanji_id")],
)
data class VocabularyKanjiLinkEntity(
    @ColumnInfo(name = "vocabulary_id") val vocabularyId: String,
    @ColumnInfo(name = "kanji_id") val kanjiId: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** When one of the learner's own words ("Từ của tôi") was added and last changed. */
@Entity(tableName = "user_words")
data class UserWordEntity(
    @PrimaryKey @ColumnInfo(name = "vocabulary_id") val vocabularyId: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

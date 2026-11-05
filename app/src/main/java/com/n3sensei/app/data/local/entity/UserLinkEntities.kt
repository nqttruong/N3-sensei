package com.n3sensei.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.n3sensei.app.domain.model.ItemType

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

/**
 * An example the learner added to a kanji: the example itself is a row of `example_sentences`
 * in the learner's own pack ("Từ của tôi", never replaced by a content pack). No foreign keys,
 * like the other learner tables: a pack update deletes and re-inserts the kanji, which must not
 * take the learner's examples with it.
 */
@Entity(tableName = "user_kanji_examples", indices = [Index("kanji_id")])
data class UserKanjiExampleEntity(
    @PrimaryKey @ColumnInfo(name = "example_id") val exampleId: String,
    @ColumnInfo(name = "kanji_id") val kanjiId: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/**
 * When an item was last shown in a study reminder, and how often (learner data, no foreign
 * keys). Used only to vary reminders; it is not learning progress and changes no schedule.
 */
@Entity(tableName = "reminder_history", primaryKeys = ["item_type", "item_id"])
data class ReminderHistoryEntity(
    @ColumnInfo(name = "item_type") val itemType: ItemType,
    @ColumnInfo(name = "item_id") val itemId: String,
    @ColumnInfo(name = "last_shown_at") val lastShownAt: Long,
    @ColumnInfo(name = "shown_count") val shownCount: Int,
)

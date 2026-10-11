package com.n3sensei.app.data.backup

import com.n3sensei.app.data.local.entity.ItemMarkEntity
import com.n3sensei.app.data.local.entity.LearningProgressEntity
import com.n3sensei.app.data.local.entity.ReviewRecordEntity
import com.n3sensei.app.data.local.entity.ReviewSessionEntity
import com.n3sensei.app.domain.backup.MarkBackup
import com.n3sensei.app.domain.backup.ProgressBackup
import com.n3sensei.app.domain.backup.ReviewRecordBackup
import com.n3sensei.app.domain.backup.SessionBackup
import com.n3sensei.app.domain.model.ItemRef

internal fun LearningProgressEntity.toBackup() = ProgressBackup(
    ItemRef(itemType, itemId), state, stepIndex, intervalMinutes, dueAt, lastReviewedAt, reviewCount, lapseCount, createdAt, updatedAt,
)

internal fun ProgressBackup.toEntity() = LearningProgressEntity(
    ref.type, ref.id, state, stepIndex, intervalMinutes, dueAt, lastReviewedAt, reviewCount, lapseCount, createdAt, updatedAt,
)

internal fun ReviewRecordEntity.toBackup() = ReviewRecordBackup(
    ItemRef(itemType, itemId), rating, reviewedAt, intervalBeforeMinutes, intervalAfterMinutes, dueAtAfter, requestId, wasNew,
)

internal fun ReviewRecordBackup.toEntity() = ReviewRecordEntity(
    itemType = ref.type, itemId = ref.id, rating = rating, reviewedAt = reviewedAt,
    intervalBeforeMinutes = intervalBeforeMinutes, intervalAfterMinutes = intervalAfterMinutes,
    dueAtAfter = dueAtAfter, requestId = requestId, wasNew = wasNew,
)

internal fun ItemMarkEntity.toBackup() = MarkBackup(ItemRef(itemType, itemId), markedAt)

internal fun MarkBackup.toEntity() = ItemMarkEntity(ref.type, ref.id, markedAt)

internal fun ReviewSessionEntity.toBackup() = SessionBackup(id, startedAt, lastActivityAt, planned, answered, finished)

internal fun SessionBackup.toEntity() = ReviewSessionEntity(id, startedAt, lastActivityAt, planned, answered, finished)

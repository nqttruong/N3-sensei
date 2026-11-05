package com.n3sensei.app.app.reminder

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.n3sensei.app.MainActivity
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType

/**
 * Deep links into the app, matched by the navigation graph of [MainActivity]. There is no
 * intent filter for them, so they never appear as links in other apps or the browser; but
 * MainActivity is exported (it is the launcher), so another app *can* send an explicit intent
 * with such a link. That only opens the read-only word card; unknown or malformed ids show
 * "not found" (checked with adb, see docs/SECURITY.md).
 */
object ReminderDeepLinks {
    const val VOCABULARY_BASE = "n3sensei://vocabulary"
    const val KANJI_BASE = "n3sensei://kanji"
    const val GRAMMAR_BASE = "n3sensei://grammar"

    /** Today's error review session (no ids: it only opens the session of what is due). */
    const val ERROR_REVIEW = "n3sensei://error-review"

    fun openErrorReview(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            ERROR_REVIEW.hashCode(),
            Intent(Intent.ACTION_VIEW, ERROR_REVIEW.toUri(), context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun base(type: ItemType) = when (type) {
        ItemType.VOCABULARY -> VOCABULARY_BASE
        ItemType.KANJI -> KANJI_BASE
        ItemType.GRAMMAR -> GRAMMAR_BASE
    }

    fun itemUri(ref: ItemRef) = "${base(ref.type)}/${android.net.Uri.encode(ref.id)}".toUri()

    /** The item's card (word, kanji or grammar point) in a fresh task, so Back behaves normally. */
    fun itemIntent(context: Context, ref: ItemRef): Intent =
        Intent(Intent.ACTION_VIEW, itemUri(ref), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

    fun openItem(context: Context, ref: ItemRef): PendingIntent =
        PendingIntent.getActivity(
            context,
            (ref.type.name + ref.id).hashCode(),
            itemIntent(context, ref),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun vocabularyUri(id: String) = "$VOCABULARY_BASE/${android.net.Uri.encode(id)}".toUri()

    /** Opens the word card in a fresh task (Home → Learn → card), so Back behaves normally. */
    fun openVocabularyIntent(context: Context, vocabularyId: String): Intent =
        Intent(Intent.ACTION_VIEW, vocabularyUri(vocabularyId), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

    fun openVocabulary(context: Context, vocabularyId: String): PendingIntent =
        PendingIntent.getActivity(
            context,
            vocabularyId.hashCode(),
            openVocabularyIntent(context, vocabularyId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}

package com.n3sensei.app.app.reminder

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.n3sensei.app.MainActivity

/**
 * Deep links into the app. They are only matched by the navigation graph of our own activity
 * (explicit intents); no intent filter is exported, so other apps cannot use them.
 */
object ReminderDeepLinks {
    const val VOCABULARY_BASE = "n3sensei://vocabulary"

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

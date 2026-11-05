package com.n3sensei.app.app.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.style.LocaleSpan
import android.widget.RemoteViews
import android.text.SpannableString
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.n3sensei.app.R
import com.n3sensei.app.domain.practice.PracticeItem
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.reminder.LockScreenPrivacy
import com.n3sensei.app.domain.reminder.ReminderMessage
import com.n3sensei.app.domain.reminder.ErrorReviewNotifier
import com.n3sensei.app.domain.reminder.ReminderNotifier
import com.n3sensei.app.domain.reminder.ReminderReason
import java.util.Locale

/**
 * Posts the single study-reminder notification. A new reminder replaces the previous one
 * (same id), so reminders never pile up.
 */
class AndroidReminderNotifier(private val context: Context) : ReminderNotifier, ErrorReviewNotifier {

    override fun canPostNotifications(): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val channel = manager.getNotificationChannel(CHANNEL_ID)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    override fun show(message: ReminderMessage) {
        ensureChannel(context)
        val settings = message.settings
        val ref = message.item.ref
        // A short question by default: the word, kanji or pattern; the answer is one tap away.
        val (title, meaning) = when (val item = message.item) {
            is PracticeItem.VocabularyItem -> {
                val v = item.vocabulary
                (if (settings.showReading && v.reading != v.expression) "${v.expression} (${v.reading})" else v.expression) to v.meaningVi
            }
            is PracticeItem.KanjiItem -> {
                val k = item.kanji
                val readings = (k.onReadings + k.kunReadings).joinToString("、")
                (if (settings.showReading && readings.isNotEmpty()) "${k.character} ($readings)" else k.character) to
                    listOfNotNull(k.hanViet, k.meaningVi).joinToString(" · ")
            }
            is PracticeItem.GrammarItem -> item.grammar.pattern to item.grammar.meaningVi
        }
        // Self-test by default: the meaning (and the example's) stays hidden until the app is opened.
        val example = message.example?.let { e ->
            val word = if (settings.showReading && e.reading != null && e.reading != e.japanese) "${e.japanese} (${e.reading})" else e.japanese
            val line = if (settings.showMeaning && e.meaning != null) "$word: ${e.meaning}" else word
            context.getString(
                if (message.item is PracticeItem.KanjiItem) R.string.reminder_example_word else R.string.reminder_example_sentence,
                line.short(),
            )
        }
        val first = if (settings.showMeaning) meaning.short() else context.getString(R.string.reminder_tap_for_meaning)
        val body = listOfNotNull(first, example).joinToString("\n")
        val reason = context.getString(
            when (message.reason) {
                ReminderReason.DUE -> R.string.reminder_reason_due
                ReminderReason.MARKED -> R.string.reminder_reason_marked
                ReminderReason.NEW -> R.string.reminder_reason_new
            },
        )
        val summary = if (message.dueCount > 0) context.getString(R.string.reminder_due_count, message.dueCount) else reason
        val open = ReminderDeepLinks.openItem(context, ref)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setSubText(reason)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body).setSummaryText(summary))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.reminder_action_open), open)
            .addAction(0, context.getString(R.string.reminder_action_mark), markIntent(ref))

        // A kanji is shown large in its own layout (styles in a notification's title are dropped
        // by Android); the plain title and text stay for TalkBack and the lock screen.
        (message.item as? PracticeItem.KanjiItem)?.let { item ->
            val k = item.kanji
            val readings = (k.onReadings + k.kunReadings).joinToString("、")
            val views = RemoteViews(context.packageName, R.layout.notification_kanji).apply {
                setTextViewText(R.id.reminder_kanji, japanese(k.character))
                setTextViewText(R.id.reminder_kanji_readings, if (settings.showReading) japanese(readings) else "")
                setTextViewText(R.id.reminder_kanji_body, body)
            }
            builder.setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(views)
                .setCustomBigContentView(views)
        }

        when (settings.lockScreen) {
            LockScreenPrivacy.SHOW_ALL -> builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            LockScreenPrivacy.HIDE_MEANING -> builder
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion(title, context.getString(R.string.reminder_tap_for_meaning)))
            LockScreenPrivacy.HIDE_ALL -> builder.setVisibility(NotificationCompat.VISIBILITY_SECRET)
        }
        notifyIfAllowed(REMINDER_NOTIFICATION_ID, builder)
    }

    /** Replaces the reminder with a short, silent confirmation after "Cần học lại". */
    fun showMarkedConfirmation(expression: String?) {
        ensureChannel(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(context.getString(R.string.reminder_marked_title))
            .setContentText(expression?.let { context.getString(R.string.reminder_marked_text, it) })
            .setSilent(true)
            .setTimeoutAfter(CONFIRMATION_TIMEOUT_MILLIS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion(context.getString(R.string.reminder_marked_title), null))
        notifyIfAllowed(REMINDER_NOTIFICATION_ID, builder)
    }

    /** Always the same notification: shown again it replaces itself, never piles up. */
    override fun showErrorReviews(dueCount: Int) {
        ensureChannel(context)
        val title = context.getString(R.string.error_reminder_title)
        val text = context.getString(R.string.error_reminder_text, dueCount)
        val open = ReminderDeepLinks.openErrorReview(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion(title, null))
        notifyIfAllowed(ERROR_REVIEW_NOTIFICATION_ID, builder)
    }

    override fun cancelErrorReviews() {
        NotificationManagerCompat.from(context).cancel(ERROR_REVIEW_NOTIFICATION_ID)
    }

    private fun publicVersion(title: CharSequence, text: String?) =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .build()

    private fun markIntent(ref: ItemRef): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java)
            .setAction(ReminderActionReceiver.ACTION_MARK)
            .putExtra(ReminderActionReceiver.EXTRA_ITEM_TYPE, ref.type.name)
            .putExtra(ReminderActionReceiver.EXTRA_VOCABULARY_ID, ref.id)
        return PendingIntent.getBroadcast(
            context,
            (ref.type.name + ref.id).hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun notifyIfAllowed(id: Int, builder: NotificationCompat.Builder) {
        // Permission can be revoked between the check and this call; never crash the worker.
        if (!canPostNotifications()) return
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (e: SecurityException) {
            // Permission revoked concurrently; nothing to show.
        }
    }

    companion object {
        const val CHANNEL_ID = "study_reminders"
        const val REMINDER_NOTIFICATION_ID = 1001
        private const val MAX_LINE = 90

        /** Japanese glyphs, not the Chinese forms a Vietnamese or English locale would pick. */
        private fun japanese(text: String): CharSequence =
            SpannableString(text).apply { setSpan(LocaleSpan(Locale.JAPANESE), 0, length, 0) }

        /** One line of a notification at most [MAX_LINE] characters. */
        private fun String.short(): String = if (length <= MAX_LINE) this else take(MAX_LINE - 1).trimEnd() + "…"
        const val ERROR_REVIEW_NOTIFICATION_ID = 1002
        private const val CONFIRMATION_TIMEOUT_MILLIS = 8_000L

        /** Idempotent; called at app start and before posting. */
        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.reminder_channel_description)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            }
            manager.createNotificationChannel(channel)
        }
    }
}

package com.n3sensei.app.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.n3sensei.app.N3SenseiApplication
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * "Cần học lại" on a reminder: marks the word as needing attention. This is an explicit tap by
 * the user and only sets the mark; the review schedule is not changed. Not exported: only our
 * own PendingIntent can reach it.
 */
class ReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_MARK) return
        val id = intent.getStringExtra(EXTRA_VOCABULARY_ID) ?: return
        val container = (context.applicationContext as N3SenseiApplication).container
        val pending = goAsync()
        scope.launch {
            try {
                val ref = ItemRef(ItemType.VOCABULARY, id)
                container.progressRepository.setMarked(ref, true)
                val expression = container.contentRepository.getVocabularyDetail(id)?.vocabulary?.expression
                AndroidReminderNotifier(context).showMarkedConfirmation(expression)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_MARK = "com.n3sensei.app.action.MARK_FOR_REVIEW"
        const val EXTRA_VOCABULARY_ID = "vocabulary_id"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

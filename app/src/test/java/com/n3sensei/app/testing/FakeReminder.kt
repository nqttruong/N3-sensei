package com.n3sensei.app.testing

import com.n3sensei.app.domain.reminder.ReminderMessage
import com.n3sensei.app.domain.reminder.ReminderNotifier
import com.n3sensei.app.domain.reminder.ReminderScheduler
import com.n3sensei.app.domain.reminder.ReminderSettings
import com.n3sensei.app.domain.reminder.ReminderState
import com.n3sensei.app.domain.reminder.ReminderStateStore

class FakeReminderNotifier(var allowed: Boolean = true) : ReminderNotifier {
    val shown = mutableListOf<ReminderMessage>()
    override fun canPostNotifications(): Boolean = allowed
    override fun show(message: ReminderMessage) {
        shown += message
    }
}

class FakeReminderScheduler : ReminderScheduler {
    val applied = mutableListOf<ReminderSettings>()
    override fun apply(settings: ReminderSettings) {
        applied += settings
    }
}

class FakeReminderStateStore(var state: ReminderState = ReminderState()) : ReminderStateStore {
    override suspend fun read(): ReminderState = state
    override suspend fun write(state: ReminderState) {
        this.state = state
    }
}

package com.n3sensei.app.domain.integrity

import kotlinx.coroutines.flow.StateFlow

/**
 * The database file was found corrupt and had to be recreated empty (SQLite cannot open it).
 *
 * @property at when it happened (epoch millis).
 * @property keptCopy file name of the corrupt database kept for recovery, or null when it
 *   could not be copied.
 */
data class DatabaseResetNotice(val at: Long, val keptCopy: String?)

/** Tells the user, once, that learner data was lost to a corrupt database file. */
interface DatabaseRecoveryRepository {
    /** Pending notice; survives an app restart until [dismiss]. */
    val notice: StateFlow<DatabaseResetNotice?>

    fun dismiss()
}

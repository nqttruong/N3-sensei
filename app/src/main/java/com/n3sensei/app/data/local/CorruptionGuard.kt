package com.n3sensei.app.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.n3sensei.app.domain.integrity.DatabaseRecoveryRepository
import com.n3sensei.app.domain.integrity.DatabaseResetNotice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * When SQLite reports a corrupt database, the platform deletes the file and starts empty. This
 * keeps a copy of the corrupt file first (it may still be recoverable with sqlite3 `.recover`)
 * and leaves a notice for the user, instead of losing everything silently.
 *
 * Files live in the no-backup directory; at most [MAX_COPIES] copies are kept.
 */
class DatabaseCorruptionRecorder(
    private val dir: File,
    private val clock: () -> Long = System::currentTimeMillis,
) : DatabaseRecoveryRepository {

    private val marker = File(dir, MARKER)
    private val _notice = MutableStateFlow(readMarker())
    override val notice: StateFlow<DatabaseResetNotice?> = _notice.asStateFlow()

    /** Called with the path of the corrupt database, before the platform deletes it. */
    @Synchronized
    fun record(databasePath: String?) {
        val at = clock()
        val copy = runCatching {
            val source = File(requireNotNull(databasePath))
            dir.mkdirs()
            val target = File(dir, "corrupt-$at.db")
            source.copyTo(target, overwrite = true)
            File("${source.path}-wal").takeIf { it.exists() }?.copyTo(File("${target.path}-wal"), overwrite = true)
            pruneOldCopies()
            target.name
        }.getOrNull()
        runCatching {
            dir.mkdirs()
            marker.writeText("$at\n${copy.orEmpty()}")
        }
        _notice.value = DatabaseResetNotice(at, copy)
    }

    @Synchronized
    override fun dismiss() {
        marker.delete()
        _notice.value = null
    }

    private fun readMarker(): DatabaseResetNotice? = runCatching {
        val lines = marker.takeIf { it.exists() }?.readLines() ?: return null
        DatabaseResetNotice(lines[0].toLong(), lines.getOrNull(1)?.takeIf { it.isNotBlank() })
    }.getOrNull()

    private fun pruneOldCopies() {
        dir.listFiles { f -> f.name.startsWith("corrupt-") && f.name.endsWith(".db") }
            ?.sortedByDescending { it.name }
            ?.drop(MAX_COPIES)
            ?.forEach { old -> old.delete(); File("${old.path}-wal").delete() }
    }

    companion object {
        const val MAX_COPIES = 2
        private const val MARKER = "database-reset.txt"
    }
}

/** Wraps an open-helper factory so [onCorruption] runs before the default handling (delete and recreate). */
class CorruptionAwareOpenHelperFactory(
    private val delegate: SupportSQLiteOpenHelper.Factory,
    private val onCorruption: (path: String?) -> Unit,
) : SupportSQLiteOpenHelper.Factory {

    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
        val callback = configuration.callback
        val guarded = object : SupportSQLiteOpenHelper.Callback(callback.version) {
            override fun onConfigure(db: SupportSQLiteDatabase) = callback.onConfigure(db)
            override fun onCreate(db: SupportSQLiteDatabase) = callback.onCreate(db)
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = callback.onUpgrade(db, oldVersion, newVersion)
            override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = callback.onDowngrade(db, oldVersion, newVersion)
            override fun onOpen(db: SupportSQLiteDatabase) = callback.onOpen(db)
            override fun onCorruption(db: SupportSQLiteDatabase) {
                onCorruption(db.path)
                callback.onCorruption(db)
            }
        }
        return delegate.create(
            SupportSQLiteOpenHelper.Configuration.builder(configuration.context)
                .name(configuration.name)
                .callback(guarded)
                .noBackupDirectory(configuration.useNoBackupDirectory)
                .allowDataLossOnRecovery(configuration.allowDataLossOnRecovery)
                .build(),
        )
    }
}

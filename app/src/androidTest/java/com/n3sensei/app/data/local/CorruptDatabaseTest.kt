package com.n3sensei.app.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.testing.SeedData
import com.n3sensei.app.testing.countRows
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile

/**
 * Regression: a corrupt database file used to be deleted and recreated empty without a word,
 * losing all progress. Now a copy is kept and a notice is left for the user.
 */
@RunWith(AndroidJUnit4::class)
class CorruptDatabaseTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "corrupt-test.db"
    private val dir = File(context.cacheDir, "corrupt-test-recovery")

    @After
    fun tearDown() {
        context.deleteDatabase(name)
        dir.deleteRecursively()
    }

    @Test
    fun corruptFile_isCopiedAndReported_thenTheAppKeepsWorking() = runBlocking {
        context.deleteDatabase(name)
        dir.deleteRecursively()
        val recorder = DatabaseCorruptionRecorder(dir)
        N3Database.create(context, name, recorder::record).apply {
            contentPackDao().replacePack(SeedData.bundle(isSeed = false))
            openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
            close()
        }
        val file = context.getDatabasePath(name)
        val size = file.length()
        // Overwrite pages after the header: SQLite opens the file but finds it malformed.
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(1024)
            raf.write(ByteArray(8192) { 0x5A })
        }

        val reopened = N3Database.create(context, name, recorder::record)
        try {
            // First access triggers the corruption handling; afterwards the database is usable (empty).
            runCatching { reopened.countRows("vocabulary") }
            assertEquals(0, reopened.countRows("vocabulary"))
            val notice = assertNotNull(recorder.notice.value).let { recorder.notice.value!! }
            val copy = File(dir, notice.keptCopy!!)
            assertTrue(copy.exists())
            assertEquals(size, copy.length())
        } finally {
            reopened.close()
        }
    }
}

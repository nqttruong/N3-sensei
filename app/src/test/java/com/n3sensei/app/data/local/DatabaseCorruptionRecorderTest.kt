package com.n3sensei.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DatabaseCorruptionRecorderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private var time = 1_000L

    @Test
    fun keepsACopy_andTheNoticeSurvivesARestart_untilDismissed() {
        val db = tmp.newFile("n3sensei.db").apply { writeText("corrupt bytes") }
        tmp.newFile("n3sensei.db-wal").writeText("wal")
        val dir = tmp.newFolder("recovery")
        DatabaseCorruptionRecorder(dir) { time }.record(db.path)

        val restarted = DatabaseCorruptionRecorder(dir) { time } // as after an app restart
        val notice = restarted.notice.value!!
        assertEquals(1_000L, notice.at)
        assertEquals("corrupt-1000.db", notice.keptCopy)
        assertEquals("corrupt bytes", dir.resolve("corrupt-1000.db").readText())
        assertEquals("wal", dir.resolve("corrupt-1000.db-wal").readText())

        restarted.dismiss()
        assertNull(restarted.notice.value)
        assertNull(DatabaseCorruptionRecorder(dir).notice.value)
        assertTrue(dir.resolve("corrupt-1000.db").exists()) // the copy is not deleted with the notice
    }

    @Test
    fun keepsAtMostTwoCopies() {
        val db = tmp.newFile("n3sensei.db").apply { writeText("x") }
        val dir = tmp.newFolder("recovery")
        val recorder = DatabaseCorruptionRecorder(dir) { time }
        listOf(1_000L, 2_000L, 3_000L).forEach { time = it; recorder.record(db.path) }
        assertEquals(listOf("corrupt-2000.db", "corrupt-3000.db"), dir.list()!!.filter { it.endsWith(".db") }.sorted())
    }

    @Test
    fun missingFile_stillLeavesANotice() {
        val recorder = DatabaseCorruptionRecorder(tmp.newFolder("recovery")) { time }
        recorder.record(null)
        assertEquals(1_000L, recorder.notice.value!!.at)
        assertNull(recorder.notice.value!!.keptCopy)
    }
}

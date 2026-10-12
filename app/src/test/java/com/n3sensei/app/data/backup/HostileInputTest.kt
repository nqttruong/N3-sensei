package com.n3sensei.app.data.backup

import com.n3sensei.app.data.importing.ContentPackJsonParser
import com.n3sensei.app.domain.backup.BackupIssueCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Files from outside the app (backups, content packs) built to break the parser: deep nesting,
 * huge strings, odd characters. They must be rejected or reported, never crash the app
 * (a StackOverflowError is an Error and would not be caught by the import code).
 */
class HostileInputTest {

    private fun nested(depth: Int) = "[".repeat(depth) + "]".repeat(depth)

    private val header = "\"format\":\"n3sensei-backup\",\"formatVersion\":1,\"createdAt\":1,\"marks\":[],\"reviewSessions\":[],\"reviewRecords\":[]"

    @Test
    fun deeplyNestedEntry_inBackup_isReportedNotCrashing() {
        val text = "{$header,\"progress\":[${nested(100_000)}]}"
        val decoded = BackupCodec.decode(text)
        when (decoded) {
            is BackupCodec.Decoded.Ok -> assertEquals(1, decoded.skippedEntries)
            is BackupCodec.Decoded.Rejected -> assertEquals(BackupIssueCode.INVALID_JSON, decoded.issues.single().code)
        }
    }

    @Test
    fun deeplyNestedUnknownField_inBackup_isHandled() {
        val text = "{$header,\"progress\":[],\"extra\":${nested(100_000)}}"
        assertTrue(BackupCodec.decode(text) is BackupCodec.Decoded.Ok || BackupCodec.decode(text) is BackupCodec.Decoded.Rejected)
    }

    @Test
    fun deeplyNestedContentPack_isRejectedNotCrashing() {
        val result = ContentPackJsonParser.parse("{\"format\":\"n3sensei-content-pack\",\"lessons\":${nested(100_000)}}")
        assertTrue(result is ContentPackJsonParser.Result.Invalid)
        val unknown = ContentPackJsonParser.parse("{\"format\":\"n3sensei-content-pack\",\"x\":${nested(100_000)}}")
        assertTrue(unknown is ContentPackJsonParser.Result.Parsed || unknown is ContentPackJsonParser.Result.Invalid)
    }

    @Test
    fun hugeAndOddStrings_areRejectedPerEntry() {
        val longId = "a".repeat(1_000_000)
        val text = "{$header,\"progress\":[" +
            "{\"itemType\":\"VOCABULARY\",\"itemId\":\"$longId\",\"state\":\"NEW\",\"stepIndex\":0,\"intervalMinutes\":0,\"reviewCount\":0,\"lapseCount\":0,\"createdAt\":0,\"updatedAt\":0}," +
            "{\"itemType\":\"VOCABULARY\",\"itemId\":\"   \",\"state\":\"NEW\",\"stepIndex\":0,\"intervalMinutes\":0,\"reviewCount\":0,\"lapseCount\":0,\"createdAt\":0,\"updatedAt\":0}," +
            "{\"itemType\":\"VOCABULARY\",\"itemId\":\"ok\\u0000\\u202E\",\"state\":\"NEW\",\"stepIndex\":9999999999,\"intervalMinutes\":-1,\"reviewCount\":0,\"lapseCount\":0,\"createdAt\":0,\"updatedAt\":0}" +
            "]}"
        val decoded = BackupCodec.decode(text) as BackupCodec.Decoded.Ok
        assertTrue(decoded.data.progress.isEmpty())
        assertEquals(3, decoded.skippedEntries)
    }
}

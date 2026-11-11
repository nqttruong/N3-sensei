package com.n3sensei.app.data.backup

import com.n3sensei.app.domain.backup.BackupData
import com.n3sensei.app.domain.backup.BackupIssueCode
import com.n3sensei.app.domain.backup.MarkBackup
import com.n3sensei.app.domain.backup.ProgressBackup
import com.n3sensei.app.domain.backup.ReminderBackup
import com.n3sensei.app.domain.backup.ReviewRecordBackup
import com.n3sensei.app.domain.backup.SessionBackup
import com.n3sensei.app.domain.backup.SettingsBackup
import com.n3sensei.app.domain.importing.IssueSeverity
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.ProgressState
import com.n3sensei.app.domain.model.ReviewRating
import com.n3sensei.app.domain.model.ThemeMode
import com.n3sensei.app.domain.reminder.LockScreenPrivacy
import com.n3sensei.app.domain.reminder.ReminderContent
import com.n3sensei.app.domain.reminder.ReminderFrequency
import com.n3sensei.app.domain.reminder.ReminderSettings
import com.n3sensei.app.domain.srs.DailyLimits
import com.n3sensei.app.domain.srs.ReviewLadder
import com.n3sensei.app.domain.srs.ReviewSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BackupCodecTest {

    private val v1 = ItemRef(ItemType.VOCABULARY, "n3-l01-v001")
    private val k1 = ItemRef(ItemType.KANJI, "n3-l01-k001")

    private val sample = BackupData(
        progress = listOf(
            ProgressBackup(v1, ProgressState.REVIEW, 3, 2880, 5_000, 4_000, 3, 1, 1_000, 4_000),
            ProgressBackup(k1, ProgressState.LEARNING, 0, 20, null, null, 0, 0, 1_000, 1_000),
        ),
        reviewRecords = listOf(
            ReviewRecordBackup(v1, ReviewRating.GOOD, 1_000, 0, 60, 3_601_000, null, wasNew = true),
            ReviewRecordBackup(v1, ReviewRating.AGAIN, 4_000, 60, 20, 1_204_000, "req-1", wasNew = false),
        ),
        marks = listOf(MarkBackup(k1, 2_000)),
        sessions = listOf(SessionBackup("s1", 1_000, 4_000, 3, 2, false)),
        settings = SettingsBackup(
            ThemeMode.DARK, false,
            ReviewSettings(ReviewLadder.decodeOrNull("10m,1d,3d")!!, DailyLimits(newVocabulary = 5, reviewsPerDay = 50)),
            ReminderBackup.from(ReminderSettings(startHour = 22, endHour = 6, frequency = ReminderFrequency.ONCE_A_DAY, showMeaning = false, lockScreen = LockScreenPrivacy.HIDE_ALL)),
        ),
    )

    private fun ok(text: String) = BackupCodec.decode(text) as BackupCodec.Decoded.Ok
    private fun rejected(text: String) = (BackupCodec.decode(text) as BackupCodec.Decoded.Rejected).issues.single()

    /** Replaces the first occurrence of [from] in an encoded sample. */
    private fun sampleWith(from: String, to: String): String {
        val text = BackupCodec.encode(sample, 9_000, "1.0")
        require(from in text) { "$from not in $text" }
        return text.replaceFirst(from, to)
    }

    @Test
    fun roundTrip_keepsEveryField() {
        val decoded = ok(BackupCodec.encode(sample, 9_000, "1.0"))
        assertEquals(sample, decoded.data)
        assertEquals(9_000L, decoded.createdAt)
        assertEquals(BACKUP_FORMAT_VERSION, decoded.formatVersion)
        assertEquals("1.0", decoded.appVersion)
        assertEquals(0, decoded.skippedEntries)
        assertTrue(decoded.issues.isEmpty())
    }

    @Test
    fun reminderSettingsAddedLater_areOptional_andKeepTheCurrentValuesWhenAbsent() {
        val text = BackupCodec.encode(sample, 9_000, "1.0")
        val older = Regex(""","?(maxPerDay|content|reviewOnly)"\s*:\s*(\[[^\]]*]|[^,}\s]+)""").replace(text, "")
        require(older != text && "maxPerDay" !in older && "reviewOnly" !in older)
        val reminder = ok(older).data.settings!!.reminder
        assertNull(reminder.maxPerDay)
        val current = ReminderSettings(maxPerDay = 4, content = setOf(ReminderContent.KANJI), reviewOnly = true)
        val restored = reminder.applyTo(current)
        assertEquals(4, restored.maxPerDay)
        assertEquals(setOf(ReminderContent.KANJI), restored.content)
        assertTrue(restored.reviewOnly)
        assertEquals(ReminderFrequency.ONCE_A_DAY, restored.frequency)
    }

    @Test
    fun emptyDatabase_roundTrips() {
        val decoded = ok(BackupCodec.encode(BackupData(), 1, null))
        assertEquals(BackupData(), decoded.data)
    }

    @Test
    fun exportedFile_hasVersionAndNoContent() {
        val text = BackupCodec.encode(sample, 9_000, "1.0")
        assertTrue("\"format\":\"n3sensei-backup\"" in text)
        assertTrue("\"formatVersion\":1" in text)
        assertTrue("\"includesContent\":false" in text)
        // Reminder on/off is device-specific and not exported.
        assertFalse("\"enabled\"" in text)
        assertFalse("onboarding" in text)
    }

    @Test
    fun emptyAndBlankFiles_areRejected() {
        assertEquals(BackupIssueCode.EMPTY_FILE, rejected("").code)
        assertEquals(BackupIssueCode.EMPTY_FILE, rejected("  \n").code)
    }

    @Test
    fun corruptFiles_areRejected() {
        val text = BackupCodec.encode(sample, 9_000, "1.0")
        assertEquals(BackupIssueCode.INVALID_JSON, rejected(text.substring(0, text.length / 2)).code) // truncated
        assertEquals(BackupIssueCode.INVALID_JSON, rejected("not json").code)
        assertEquals(BackupIssueCode.INVALID_JSON, rejected("[1,2,3]").code)
        assertEquals(BackupIssueCode.INVALID_JSON, rejected("{\"format\": 12, ").code)
        assertEquals(IssueSeverity.FATAL, rejected("{").severity)
    }

    @Test
    fun otherJson_isNotABackup() {
        assertEquals(BackupIssueCode.NOT_A_BACKUP, rejected("{}").code)
        assertEquals(BackupIssueCode.NOT_A_BACKUP, rejected("{\"format\":\"n3sensei-content-pack\",\"formatVersion\":1}").code)
    }

    @Test
    fun versions_newerAndUnknownAreRejected() {
        assertEquals(BackupIssueCode.NEWER_VERSION, rejected(sampleWith("\"formatVersion\":1", "\"formatVersion\":2")).code)
        assertEquals(BackupIssueCode.UNSUPPORTED_VERSION, rejected(sampleWith("\"formatVersion\":1", "\"formatVersion\":0")).code)
        assertEquals(BackupIssueCode.MISSING_FIELD, rejected(sampleWith("\"formatVersion\":1,", "")).code)
    }

    @Test
    fun requiredTopLevelFields() {
        assertEquals("createdAt", rejected(sampleWith("\"createdAt\":9000,", "")).location)
        assertEquals("reviewRecords", rejected(sampleWith("\"reviewRecords\":", "\"other\":")).location)
    }

    @Test
    fun invalidEntries_areSkippedAndReported_restIsKept() {
        val text = sampleWith("\"state\":\"REVIEW\"", "\"state\":\"FLYING\"")
            .replaceFirst("\"rating\":\"GOOD\"", "\"rating\":5") // wrong JSON type
            .replaceFirst("\"markedAt\":2000", "\"markedAt\":-1")
            .replaceFirst("\"answered\":2", "\"answered\":9") // more than planned
        val decoded = ok(text)
        assertEquals(listOf(k1), decoded.data.progress.map { it.ref })
        assertEquals(listOf("req-1"), decoded.data.reviewRecords.map { it.requestId })
        assertTrue(decoded.data.marks.isEmpty())
        assertTrue(decoded.data.sessions.isEmpty())
        assertEquals(4, decoded.skippedEntries)
        assertEquals(
            listOf("progress[0]", "reviewRecords[0]", "marks[0]", "reviewSessions[0]"),
            decoded.issues.map { it.location },
        )
        assertEquals("state", decoded.issues[0].detail)
        assertTrue(decoded.issues.all { it.severity == IssueSeverity.ERROR })
    }

    @Test
    fun missingRequiredEntryField_isReported() {
        val decoded = ok(sampleWith("\"itemId\":\"n3-l01-v001\",", ""))
        assertEquals(BackupIssueCode.MISSING_FIELD, decoded.issues.first().code)
        assertEquals("itemId", decoded.issues.first().detail)
    }

    @Test
    fun duplicateIds_keepTheFirst() {
        val dupProgress = sample.copy(
            progress = sample.progress + sample.progress[0].copy(stepIndex = 0),
            reviewRecords = sample.reviewRecords + sample.reviewRecords[1].copy(rating = ReviewRating.EASY) + sample.reviewRecords[0],
            sessions = sample.sessions + sample.sessions[0],
        )
        val decoded = ok(BackupCodec.encode(dupProgress, 1, null))
        assertEquals(sample.progress, decoded.data.progress)
        assertEquals(sample.reviewRecords, decoded.data.reviewRecords)
        assertEquals(sample.sessions, decoded.data.sessions)
        assertEquals(4, decoded.skippedEntries)
        assertTrue(decoded.issues.all { it.code == BackupIssueCode.DUPLICATE_ID })
    }

    @Test
    fun invalidSettings_areNotRestored_butDataIs() {
        val decoded = ok(sampleWith("\"intervals\":\"10m,1d,3d\"", "\"intervals\":\"3d,1d\""))
        assertNull(decoded.data.settings)
        assertEquals(2, decoded.data.progress.size)
        assertEquals(BackupIssueCode.INVALID_SETTINGS, decoded.issues.single().code)
        assertEquals(0, decoded.skippedEntries)
    }

    @Test
    fun outOfRangeValues_areRejectedPerEntry() {
        assertEquals("stepIndex", ok(sampleWith("\"stepIndex\":3", "\"stepIndex\":12")).issues.single().detail)
        // Each type's cap on its own: 0..100.
        assertEquals(BackupIssueCode.INVALID_SETTINGS, ok(sampleWith("\"newKanjiPerDay\":10", "\"newKanjiPerDay\":101")).issues.single().code)
        assertEquals(BackupIssueCode.INVALID_SETTINGS, ok(sampleWith("\"newVocabularyPerDay\":5", "\"newVocabularyPerDay\":999")).issues.single().code)
        assertEquals(BackupIssueCode.INVALID_SETTINGS, ok(sampleWith("\"startHour\":22", "\"startHour\":24")).issues.single().code)
    }

    /** The first released format must keep loading in every later version of the app. */
    @Test
    fun frozenV1File_stillDecodes() {
        val decoded = ok(File("src/androidTest/assets/backup/v1-sample.json").readText())
        assertEquals(1, decoded.formatVersion)
        assertEquals(1_788_400_000_000L, decoded.createdAt)
        assertEquals(3, decoded.data.progress.size)
        assertEquals(5, decoded.data.reviewRecords.size)
        assertNull(decoded.data.reviewRecords[0].requestId) // answer from before database v3
        assertEquals(listOf(true, false, false, true, true), decoded.data.reviewRecords.map { it.wasNew })
        assertEquals(1, decoded.data.marks.size)
        assertEquals(SessionBackup("gold-s1", 1_788_228_000_000, 1_788_228_600_000, 3, 2, false), decoded.data.sessions.single())
        val settings = decoded.data.settings!!
        assertEquals(ThemeMode.DARK, settings.themeMode)
        assertEquals("10m,1d,3d", settings.review.ladder.encode())
        // A file from before per-type caps: its shared cap becomes the vocabulary cap; kanji and
        // grammar take their defaults; reviews as they were.
        assertEquals(DailyLimits(newVocabulary = 5, newKanji = 10, newGrammar = 10, reviewsPerDay = 50), settings.review.limits)
        assertEquals(ReminderBackup(7, 22, ReminderFrequency.EVERY_2_HOURS, true, false, LockScreenPrivacy.HIDE_ALL), settings.reminder)
        assertTrue(decoded.issues.isEmpty())
    }
}

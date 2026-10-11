package com.n3sensei.app.data.backup

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.data.local.N3Database
import com.n3sensei.app.data.local.entity.ItemMarkEntity
import com.n3sensei.app.data.local.entity.LearningProgressEntity
import com.n3sensei.app.data.local.entity.ReviewRecordEntity
import com.n3sensei.app.data.local.entity.ReviewSessionEntity
import com.n3sensei.app.domain.backup.BackupIssueCode
import com.n3sensei.app.domain.backup.BackupSection
import com.n3sensei.app.domain.backup.InspectResult
import com.n3sensei.app.domain.backup.MergeCount
import com.n3sensei.app.domain.backup.RestorePreview
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.ProgressState
import com.n3sensei.app.domain.model.ReviewRating
import com.n3sensei.app.domain.model.ThemeMode
import com.n3sensei.app.domain.model.UserPreferences
import com.n3sensei.app.domain.reminder.LockScreenPrivacy
import com.n3sensei.app.domain.reminder.ReminderFrequency
import com.n3sensei.app.domain.reminder.ReminderSettings
import com.n3sensei.app.domain.srs.DailyLimits
import com.n3sensei.app.domain.srs.ReviewLadder
import com.n3sensei.app.domain.srs.ReviewSettings
import com.n3sensei.app.testing.AnalyticsFixture
import com.n3sensei.app.testing.InMemoryPreferencesRepository
import com.n3sensei.app.testing.RecordingReminderScheduler
import com.n3sensei.app.testing.SeedData
import com.n3sensei.app.testing.countRows
import com.n3sensei.app.testing.inMemoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** Export → file → restore against real Room databases and real files (file:// URIs). */
@RunWith(AndroidJUnit4::class)
class BackupRepositoryTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val clock = Clock.fixed(Instant.parse("2026-10-11T05:00:00Z"), ZoneOffset.UTC)
    private val dir = File(context.cacheDir, "backup-test").apply { deleteRecursively(); mkdirs() }
    private val databases = mutableListOf<N3Database>()

    private val customPrefs = UserPreferences(
        onboardingCompleted = true,
        themeMode = ThemeMode.DARK,
        showFurigana = false,
        reviewSettings = ReviewSettings(ReviewLadder.decodeOrNull("10m,1d,3d,1w")!!, DailyLimits(7, 70)),
        reminderSettings = ReminderSettings(true, 6, 23, ReminderFrequency.EVERY_6_HOURS, false, false, LockScreenPrivacy.HIDE_ALL),
    )

    private class Device(val db: N3Database, val prefs: InMemoryPreferencesRepository, val scheduler: RecordingReminderScheduler, val repo: AndroidBackupRepository)

    private fun device(prefs: UserPreferences = UserPreferences(), seed: Boolean = true): Device = runBlocking {
        val db = inMemoryDatabase().also { databases += it }
        if (seed) db.contentPackDao().replacePack(SeedData.bundle(isSeed = false))
        val p = InMemoryPreferencesRepository(prefs)
        val s = RecordingReminderScheduler()
        Device(db, p, s, AndroidBackupRepository(context, db.backupDao(), p, s, clock, "test"))
    }

    private fun file(name: String) = File(dir, name)
    private fun File.uri() = Uri.fromFile(this).toString()

    @After
    fun tearDown() {
        databases.forEach { it.close() }
        dir.deleteRecursively()
    }

    private data class UserTables(
        val progress: List<LearningProgressEntity>,
        val records: List<ReviewRecordEntity>,
        val marks: List<ItemMarkEntity>,
        val sessions: List<ReviewSessionEntity>,
    )

    /** Everything a learner owns, ids of history rows left out (they are local row numbers). */
    private fun N3Database.userTables(): UserTables {
        fun <T> rows(sql: String, map: (android.database.Cursor) -> T): List<T> =
            openHelper.readableDatabase.query(sql).use { c -> generateSequence { if (c.moveToNext()) map(c) else null }.toList() }
        return UserTables(
            rows("SELECT * FROM learning_progress ORDER BY item_type, item_id") { c ->
                LearningProgressEntity(
                    ItemType.valueOf(c.getString(0)), c.getString(1), ProgressState.valueOf(c.getString(2)), c.getInt(3), c.getLong(4),
                    if (c.isNull(5)) null else c.getLong(5), if (c.isNull(6)) null else c.getLong(6), c.getInt(7), c.getInt(8), c.getLong(9), c.getLong(10),
                )
            },
            rows(
                "SELECT item_type, item_id, rating, reviewed_at, interval_before_minutes, interval_after_minutes, due_at_after, request_id, was_new " +
                    "FROM review_records ORDER BY reviewed_at, item_type, item_id",
            ) { c ->
                ReviewRecordEntity(
                    0, ItemType.valueOf(c.getString(0)), c.getString(1), ReviewRating.valueOf(c.getString(2)), c.getLong(3), c.getLong(4), c.getLong(5),
                    c.getLong(6), if (c.isNull(7)) null else c.getString(7), c.getInt(8) == 1,
                )
            },
            rows("SELECT item_type, item_id, marked_at FROM item_marks ORDER BY item_type, item_id") { c ->
                ItemMarkEntity(ItemType.valueOf(c.getString(0)), c.getString(1), c.getLong(2))
            },
            rows("SELECT id, started_at, last_activity_at, planned, answered, finished FROM review_sessions ORDER BY id") { c ->
                ReviewSessionEntity(c.getString(0), c.getLong(1), c.getLong(2), c.getInt(3), c.getInt(4), c.getInt(5) == 1)
            },
        )
    }

    /** A realistic history: the hand-counted analytics fixture plus a legacy answer, marks and sessions. */
    private suspend fun Device.fillWithHistory() {
        AnalyticsFixture(zone, LocalDate.parse("2026-10-11")).insert(db)
        db.progressDao().insertRecord(
            ReviewRecordEntity(
                itemType = ItemType.KANJI, itemId = SeedData.K_ZOU, rating = ReviewRating.GOOD, reviewedAt = 1_000,
                intervalBeforeMinutes = 0, intervalAfterMinutes = 60, dueAtAfter = 3_601_000, requestId = null, wasNew = true,
            ),
        )
        db.progressDao().insertMark(ItemMarkEntity(ItemType.VOCABULARY, SeedData.V_TODOKERU, 5_000))
        db.reviewSessionDao().insert(ReviewSessionEntity("s-1", 10_000, 20_000, 5, 5, true))
        db.reviewSessionDao().insert(ReviewSessionEntity("s-2", 30_000, 31_000, 4, 1, false))
    }

    private suspend fun AndroidBackupRepository.ready(uri: String): RestorePreview = (inspect(uri) as InspectResult.Ready).preview

    @Test
    fun roundTrip_restoresEveryRowExactly_andTheSettings() = runTest {
        val source = device(customPrefs)
        source.fillWithHistory()
        val backup = file("backup.json")
        val exported = source.repo.export(backup.uri())
        assertTrue(exported.success)
        assertEquals(5, exported.counts[BackupSection.PROGRESS]) // 4 fixture items + "gone"
        assertEquals(9, exported.counts[BackupSection.REVIEW_RECORDS])

        val target = device()
        val preview = target.repo.ready(backup.uri())
        assertEquals(MergeCount(added = 5), preview.plan[BackupSection.PROGRESS])
        val report = target.repo.restore(preview, restoreSettings = true)
        assertTrue(report.success)
        assertTrue(report.settingsRestored)

        assertEquals(source.db.userTables(), target.db.userTables())
        val restored = target.prefs.preferences.first()
        assertEquals(customPrefs.themeMode, restored.themeMode)
        assertEquals(customPrefs.showFurigana, restored.showFurigana)
        assertEquals(customPrefs.reviewSettings, restored.reviewSettings)
        // Everything but the on/off switch, which stays as it was on this device.
        assertEquals(customPrefs.reminderSettings.copy(enabled = false), restored.reminderSettings)
        assertFalse(restored.onboardingCompleted)
        assertEquals(listOf(restored.reminderSettings), target.scheduler.applied)
    }

    @Test
    fun withoutSettingsOption_onlyDataIsRestored() = runTest {
        val source = device(customPrefs)
        source.fillWithHistory()
        val backup = file("b.json").also { source.repo.export(it.uri()) }
        val target = device()
        val report = target.repo.restore(target.repo.ready(backup.uri()), restoreSettings = false)
        assertTrue(report.success)
        assertFalse(report.settingsRestored)
        assertEquals(UserPreferences(), target.prefs.preferences.first())
        assertTrue(target.scheduler.applied.isEmpty())
        assertEquals(source.db.userTables(), target.db.userTables())
    }

    @Test
    fun restoringTwice_createsNoDuplicates() = runTest {
        val source = device()
        source.fillWithHistory()
        val backup = file("b.json").also { source.repo.export(it.uri()) }
        val target = device()
        target.repo.restore(target.repo.ready(backup.uri()), false)
        val before = target.db.userTables()

        val second = target.repo.ready(backup.uri())
        assertTrue(second.plan.values.all { it.written == 0 }) // only the settings could still be applied
        val report = target.repo.restore(second, false)
        assertTrue(report.counts.values.all { it.written == 0 })
        assertEquals(before, target.db.userTables())
        // Restoring into the very database it came from changes nothing either.
        val self = source.repo.restore(source.repo.ready(backup.uri()), false)
        assertTrue(self.counts.values.all { it.written == 0 })
    }

    @Test
    fun conflicts_newerProgressWins_historyIsMerged_firstAnswerRecomputed() = runTest {
        val k = SeedData.K_KEI
        fun progress(step: Int, updatedAt: Long) = LearningProgressEntity(ItemType.KANJI, k, ProgressState.REVIEW, step, 1440, updatedAt + 86_400_000, updatedAt, step, 0, 100, updatedAt)
        fun record(at: Long, id: String) = ReviewRecordEntity(
            itemType = ItemType.KANJI, itemId = k, rating = ReviewRating.GOOD, reviewedAt = at, intervalBeforeMinutes = 0,
            intervalAfterMinutes = 1440, dueAtAfter = at + 86_400_000, requestId = id, wasNew = false,
        )
        // Phone A: answered at 100 and 300. Phone B (target): answered at 200 only, so its row is older.
        val a = device()
        a.db.progressDao().upsert(progress(3, 300))
        a.db.progressDao().insertRecord(record(100, "a-100").copy(wasNew = true))
        a.db.progressDao().insertRecord(record(300, "a-300"))
        val backup = file("a.json").also { a.repo.export(it.uri()) }

        val b = device()
        b.db.progressDao().upsert(progress(2, 200))
        b.db.progressDao().insertRecord(record(200, "b-200").copy(wasNew = true))
        val report = b.repo.restore(b.repo.ready(backup.uri()), false)
        assertEquals(MergeCount(updated = 1), report.counts[BackupSection.PROGRESS])
        assertEquals(MergeCount(added = 2), report.counts[BackupSection.REVIEW_RECORDS])
        val tables = b.db.userTables()
        assertEquals(3, tables.progress.single().stepIndex)
        assertEquals(listOf(100L to true, 200L to false, 300L to false), tables.records.map { it.reviewedAt to it.wasNew })

        // The other way round: B's file into A keeps A's newer progress.
        val back = file("b.json").also { b.repo.export(it.uri()) }
        val reverse = a.repo.restore(a.repo.ready(back.uri()), false)
        assertEquals(MergeCount(unchanged = 1), reverse.counts[BackupSection.PROGRESS])
        assertEquals(3, a.db.userTables().progress.single().stepIndex)
        assertEquals(3, a.db.countRows("review_records"))
    }

    @Test
    fun failureDuringImport_rollsEverythingBack_andKeepsSettings() = runTest {
        val source = device(customPrefs)
        source.fillWithHistory()
        val backup = file("b.json").also { source.repo.export(it.uri()) }

        val target = device()
        target.db.progressDao().insertMark(ItemMarkEntity(ItemType.GRAMMAR, SeedData.G_TABAKARI, 1))
        val before = target.db.userTables()
        val preview = target.repo.ready(backup.uri())
        // Marks are written after progress and history: fail there, in the middle of the transaction.
        target.db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_marks BEFORE INSERT ON item_marks BEGIN SELECT RAISE(ABORT, 'disk full (test)'); END",
        )
        val report = target.repo.restore(preview, restoreSettings = true)
        assertFalse(report.success)
        assertEquals(BackupIssueCode.DATABASE_ERROR, report.issues.last().code)
        assertEquals(before, target.db.userTables())
        assertEquals(UserPreferences(), target.prefs.preferences.first())
        assertTrue(target.scheduler.applied.isEmpty())

        // Once the problem is gone the same preview restores fine.
        target.db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_marks")
        assertTrue(target.repo.restore(preview, false).success)
        assertEquals(5, target.db.countRows("learning_progress"))
    }

    @Test
    fun badFiles_areRejected_andChangeNothing() = runTest {
        val target = device()
        target.fillWithHistory()
        val before = target.db.userTables()
        fun code(text: String?, name: String): BackupIssueCode {
            val f = file(name)
            if (text != null) f.writeText(text)
            val result = runBlocking { target.repo.inspect(f.uri()) } as InspectResult.Rejected
            return result.issues.single().code
        }
        assertEquals(BackupIssueCode.EMPTY_FILE, code("", "empty.json"))
        assertEquals(BackupIssueCode.INVALID_JSON, code("{\"format\":\"n3sensei-backup\",\"formatVersion\":1,\"progress\":[", "cut.json"))
        assertEquals(BackupIssueCode.INVALID_JSON, code("\u0000\u0001binary", "bin.json"))
        assertEquals(BackupIssueCode.NOT_A_BACKUP, code("{\"format\":\"n3sensei-content-pack\"}", "pack.json"))
        assertEquals(BackupIssueCode.NEWER_VERSION, code("{\"format\":\"n3sensei-backup\",\"formatVersion\":99}", "new.json"))
        assertEquals(BackupIssueCode.READ_ERROR, code(null, "missing.json"))
        assertEquals(before, target.db.userTables())
    }

    @Test
    fun exportToUnwritableLocation_reportsWriteError() = runTest {
        val result = device().repo.export(Uri.fromFile(File("/proc/n3sensei-not-writable.json")).toString())
        assertFalse(result.success)
        assertEquals(BackupIssueCode.WRITE_ERROR, result.issues.single().code)
    }

    @Test
    fun frozenV1File_restores_withKnownCounts() = runTest {
        val v1 = file("v1.json")
        InstrumentationRegistry.getInstrumentation().context.assets.open("backup/v1-sample.json").use { input ->
            v1.outputStream().use { input.copyTo(it) }
        }
        val target = device()
        val preview = target.repo.ready(v1.uri())
        // removed-g99 is not in the installed content: restored anyway, with a warning.
        assertEquals(BackupIssueCode.UNKNOWN_ITEMS, preview.issues.single().code)
        assertEquals("1", preview.issues.single().detail)
        val report = target.repo.restore(preview, restoreSettings = true)
        assertTrue(report.success)
        assertEquals(MergeCount(added = 3), report.counts[BackupSection.PROGRESS])
        assertEquals(MergeCount(added = 5), report.counts[BackupSection.REVIEW_RECORDS])
        assertEquals(MergeCount(added = 1), report.counts[BackupSection.MARKS])
        assertEquals(MergeCount(added = 1), report.counts[BackupSection.SESSIONS])

        val tables = target.db.userTables()
        // First answer of each item is recomputed: v01 legacy answer, k01, g99.
        assertEquals(3, tables.records.count { it.wasNew })
        assertTrue(tables.records.single { it.requestId == null }.wasNew)
        val v01 = target.db.progressDao().get(ItemType.VOCABULARY, SeedData.V_KEIKEN)!!
        assertEquals(3, v01.stepIndex)
        assertEquals(1_788_483_900_000L, v01.dueAt)
        val prefs = target.prefs.preferences.first()
        assertEquals(ThemeMode.DARK, prefs.themeMode)
        assertEquals("10m,1d,3d", prefs.reviewSettings.ladder.encode())
        assertEquals(ReminderFrequency.EVERY_2_HOURS, prefs.reminderSettings.frequency)
        assertFalse(prefs.reminderSettings.enabled)
    }

    @Test
    fun emptyDatabase_exportsAndRestoresNothing() = runTest {
        val empty = device(seed = false)
        val backup = file("e.json")
        assertTrue(empty.repo.export(backup.uri()).success)
        val preview = device().repo.ready(backup.uri())
        assertTrue(preview.plan.values.all { it.written == 0 })
        assertTrue(preview.data.settings != null) // settings are always exported
    }
}

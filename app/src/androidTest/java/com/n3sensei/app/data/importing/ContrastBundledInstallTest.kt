package com.n3sensei.app.data.importing

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.data.local.N3Database
import com.n3sensei.app.data.repository.OfflineContrastContentRepository
import com.n3sensei.app.data.repository.OfflineContrastPracticeRepository
import com.n3sensei.app.data.repository.OfflineContrastProgressRepository
import com.n3sensei.app.domain.contrast.AnswerInput
import com.n3sensei.app.domain.contrast.PracticeScope
import com.n3sensei.app.domain.contrast.StartOrResumePractice
import com.n3sensei.app.domain.contrast.StartPracticeResult
import com.n3sensei.app.domain.contrast.SubmitContrastAnswer
import com.n3sensei.app.domain.importing.ImportOutcome
import com.n3sensei.app.testing.countRows
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * installBundledContent(): learning packs first, then Contrast packs, only when every source pack
 * installed. The Contrast pack here is a TEST-ONLY copy of the unreviewed sample, given through the
 * test hooks; the app ships no Contrast pack (CONTRAST_BUNDLED_PACK_ASSETS is empty).
 */
@RunWith(AndroidJUnit4::class)
class ContrastBundledInstallTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val name = "contrast-bundled-test.db"
    private val clock = Clock.fixed(Instant.parse("2026-11-17T02:00:00Z"), ZoneOffset.UTC)
    private val testPack = "contrast/test-pack-sample-copy.json"
    private lateinit var db: N3Database

    private fun repository(sources: List<String> = AndroidContentImportRepository.BUNDLED_PACK_ASSETS) = AndroidContentImportRepository(
        context,
        ContentPackImporter(db.contentPackDao(), clock),
        bundledAssetPaths = sources,
        contrastImporter = ContrastContentImporter(db.contrastDao(), clock),
        contrastAssetPaths = listOf(testPack),
        readContrastAsset = { path -> instrumentation.context.assets.open(path).use { it.readBytes().toString(Charsets.UTF_8) } },
    )

    @Before
    fun setUp() {
        context.deleteDatabase(name)
        db = N3Database.create(context, name)
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(name)
    }

    @Test
    fun theAppShipsNoContrastPack_andTheUnreviewedSampleIsNotInItsAssets() {
        assertTrue(AndroidContentImportRepository.CONTRAST_BUNDLED_PACK_ASSETS.isEmpty())
        val shipped = context.assets.list("content").orEmpty().toList()
        assertTrue(shipped.toString(), shipped.none { "contrast" in it })
    }

    @Test
    fun sourcesFirst_thenContrast_whoseSourcesAllMatch() = runTest {
        val repo = repository()
        val reports = repo.installBundledContent()
        assertTrue(reports.all { it.outcome == ImportOutcome.IMPORTED })
        val contrast = repo.lastContrastReports.single()
        assertEquals(ContrastImportReport.Outcome.IMPORTED, contrast.outcome)
        // Installed after the sources: no source reference is missing or changed.
        assertTrue(contrast.issues.none { it.code.name.startsWith("SOURCE_") })
        // The learning content reports are the same list as before Contrast existed.
        assertEquals(AndroidContentImportRepository.BUNDLED_PACK_ASSETS.size, reports.size)
    }

    @Test
    fun aFailingSourcePack_skipsContrast_andLeavesTheOtherPacksInstalled() = runTest {
        val repo = repository(AndroidContentImportRepository.BUNDLED_PACK_ASSETS + "content/missing-pack.json")
        val reports = repo.installBundledContent()
        assertEquals(ImportOutcome.FAILED, reports.last().outcome)
        assertTrue(reports.dropLast(1).all { it.outcome == ImportOutcome.IMPORTED })
        assertTrue(repo.lastContrastReports.isEmpty())
        assertEquals(0, db.countRows("contrast_packs"))
        // Next start with the sources fixed: Contrast installs.
        val fixed = repository()
        fixed.installBundledContent()
        assertEquals(ContrastImportReport.Outcome.IMPORTED, fixed.lastContrastReports.single().outcome)
    }

    @Test
    fun anUnchangedPackIsNotReinstalled_andARestartKeepsProgress() = runTest {
        repository().installBundledContent()
        // Some learning.
        val content = OfflineContrastContentRepository(db.contrastDao())
        val practice = OfflineContrastPracticeRepository(db.contrastDao())
        val progress = OfflineContrastProgressRepository(db.contrastDao())
        val zone = ZoneId.of("Asia/Ho_Chi_Minh")
        val session = (StartOrResumePractice(content, practice, progress, newId = { "s1" })(PracticeScope.Group("cg-k-seki-accumulate-achieve"), clock.instant(), zone) as StartPracticeResult.Started).session
        val q = content.getQuestion(session.items.single().questionId)!!
        SubmitContrastAnswer(content, practice, progress)(session.id, 0, AnswerInput.Chosen(q.correctOptionId), "r1", clock.instant(), zone)
        val saved = progress.progress("cg-k-seki-accumulate-achieve")
        assertTrue(saved != null)

        // "Restart": a new database connection and repository, installation runs again.
        db.close()
        db = N3Database.create(context, name)
        val again = repository()
        val reports = again.installBundledContent()
        assertTrue(reports.all { it.outcome == ImportOutcome.UP_TO_DATE })
        assertEquals(ContrastImportReport.Outcome.UP_TO_DATE, again.lastContrastReports.single().outcome)
        assertEquals(saved, OfflineContrastProgressRepository(db.contrastDao()).progress("cg-k-seki-accumulate-achieve"))
        assertEquals(1, db.countRows("contrast_attempts"))
    }

    @Test
    fun withoutAContrastImporter_nothingChangesForTheLearningContent() = runTest {
        val plain = AndroidContentImportRepository(context, ContentPackImporter(db.contentPackDao(), clock))
        assertTrue(plain.installBundledContent().all { it.outcome == ImportOutcome.IMPORTED })
        assertTrue(plain.lastContrastReports.isEmpty())
        assertNull(OfflineContrastContentRepository(db.contrastDao()).installedPack())
    }
}

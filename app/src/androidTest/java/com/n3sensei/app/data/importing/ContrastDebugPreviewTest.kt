package com.n3sensei.app.data.importing

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.data.local.N3Database
import com.n3sensei.app.data.repository.OfflineContrastContentRepository
import com.n3sensei.app.data.repository.OfflineContrastPracticeRepository
import com.n3sensei.app.data.repository.OfflineContrastProgressRepository
import com.n3sensei.app.domain.contrast.AnswerInput
import com.n3sensei.app.domain.contrast.ContrastPreview
import com.n3sensei.app.domain.contrast.PracticeScope
import com.n3sensei.app.domain.contrast.StartOrResumePractice
import com.n3sensei.app.domain.contrast.StartPracticeResult
import com.n3sensei.app.domain.contrast.SubmitContrastAnswer
import com.n3sensei.app.testing.countRows
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The debug-build Contrast preview (unreviewed drafts) on a real Room database with the real bundled
 * source packs: installed with the bundled content only in a debug build, only while no reviewed pack
 * is shipped, labelled as unreviewed, and re-installing it keeps the learner's history.
 * (The release build has neither the asset nor the path: checked on the release APK, see the report.)
 */
@RunWith(AndroidJUnit4::class)
class ContrastDebugPreviewTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val name = "contrast-debug-preview-test.db"
    private val clock = Clock.fixed(Instant.parse("2026-11-18T02:00:00Z"), ZoneOffset.UTC)
    private lateinit var db: N3Database

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

    private fun repository(reviewed: List<String> = emptyList()) = AndroidContentImportRepository(
        context,
        ContentPackImporter(db.contentPackDao(), clock),
        contrastImporter = ContrastContentImporter(db.contrastDao(), clock),
        contrastAssetPaths = reviewed,
        contrastPreviewPaths = ContrastPreviewAssets.PATHS,
        readContrastAsset = { path ->
            // Reviewed packs of this test come from the test APK; the preview from the app's (debug) assets.
            runCatching { instrumentation.context.assets.open(path) }.getOrElse { context.assets.open(path) }.use { it.readBytes().toString(Charsets.UTF_8) }
        },
    )

    @Test
    fun theDebugBuildHasThePreviewAsset_labelledUnreviewed() {
        assertEquals(listOf("contrast-preview/contrast-n3-preview.json"), ContrastPreviewAssets.PATHS)
        val pack = JSONObject(context.assets.open(ContrastPreviewAssets.PATHS.single()).use { it.readBytes().toString(Charsets.UTF_8) })
        assertEquals(ContrastPreview.PACK_ID, pack.getJSONObject("pack").getString("id"))
        assertEquals("NỘI DUNG THỬ NGHIỆM — CHƯA KIỂM DUYỆT", pack.getJSONObject("pack").getString("title"))
        val groups = pack.getJSONArray("groups")
        assertEquals(69, groups.length())
        (0 until groups.length()).forEach { assertEquals("chua-duyet-debug-preview", groups.getJSONObject(it).getJSONObject("approval").getString("approvedBy")) }
        // The reviewed list of the app stays empty: nothing was approved.
        assertTrue(AndroidContentImportRepository.CONTRAST_BUNDLED_PACK_ASSETS.isEmpty())
    }

    @Test
    fun bundledInstall_addsThePreview_withoutLearnerData_andAReinstallKeepsHistory() = runBlocking {
        val repo = repository()
        repo.installBundledContent()
        assertEquals(ContrastImportReport.Outcome.IMPORTED, repo.lastContrastReports.single().outcome)
        val content = OfflineContrastContentRepository(db.contrastDao())
        val groups = content.observeGroups().first()
        assertEquals(69, groups.size)
        assertTrue(groups.all(ContrastPreview::isPreview))
        assertEquals(0, db.countRows("contrast_attempts") + db.countRows("contrast_progress") + db.countRows("learning_progress"))
        // Some learning, then the next start installs again: up to date, history kept.
        val practice = OfflineContrastPracticeRepository(db.contrastDao())
        val progress = OfflineContrastProgressRepository(db.contrastDao())
        val zone = ZoneId.of("Asia/Ho_Chi_Minh")
        val session = (StartOrResumePractice(content, practice, progress)(PracticeScope.Group("cg-k-tori-shima"), clock.instant(), zone) as StartPracticeResult.Started).session
        val q = content.getQuestion(session.items.first { it.position == 0 }.questionId)!!
        SubmitContrastAnswer(content, practice, progress)(session.id, 0, AnswerInput.Chosen(q.correctOptionId), "r0", clock.instant(), zone)
        repo.installBundledContent()
        assertEquals(ContrastImportReport.Outcome.UP_TO_DATE, repo.lastContrastReports.single().outcome)
        assertEquals(1, db.countRows("contrast_attempts"))
    }

    @Test
    fun aReviewedPack_isInstalledInsteadOfThePreview() = runBlocking {
        val repo = repository(reviewed = listOf("contrast/test-pack-sample-copy.json"))
        repo.installBundledContent()
        val groups = OfflineContrastContentRepository(db.contrastDao()).observeGroups().first()
        assertEquals(3, groups.size)
        assertTrue(groups.none(ContrastPreview::isPreview))
    }

    @Test
    fun switchedOff_thePreviewIsNotInstalled() = runBlocking {
        val repo = repository().apply { contrastPreviewEnabled = false }
        repo.installBundledContent()
        assertEquals(0, db.countRows("contrast_groups"))
    }
}

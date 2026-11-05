package com.n3sensei.app.feature.settings

import com.n3sensei.app.core.ui.state.UiState
import com.n3sensei.app.domain.edit.UserWords
import com.n3sensei.app.domain.importing.ImportOutcome
import com.n3sensei.app.domain.importing.ImportReport
import com.n3sensei.app.domain.model.ContentPackInfo
import com.n3sensei.app.domain.model.ThemeMode
import com.n3sensei.app.domain.model.UserPreferences
import com.n3sensei.app.testing.FakeContentImportRepository
import com.n3sensei.app.testing.FakeContentRepository
import com.n3sensei.app.testing.FakeUserPreferencesRepository
import com.n3sensei.app.testing.MainDispatcherRule
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val preferences = FakeUserPreferencesRepository(UserPreferences(onboardingCompleted = true))
    private val importRepository = FakeContentImportRepository()
    // Lazy: Dispatchers.Main is only replaced once the rule runs, after field initialisation.
    private val viewModel by lazy { SettingsViewModel(preferences, FakeContentRepository(), importRepository) }

    @Test
    fun changesArePersistedThroughRepository() {
        viewModel.setThemeMode(ThemeMode.DARK)
        viewModel.setShowFurigana(false)
        viewModel.replayOnboarding()

        assertEquals(
            UserPreferences(onboardingCompleted = false, themeMode = ThemeMode.DARK, showFurigana = false),
            preferences.preferences.value,
        )
    }

    @Test
    fun newPack_isImportedWithoutConfirmation_reportShownUntilDismissed() {
        viewModel.importDocument("content://pack.json")

        assertEquals(listOf("content://pack.json"), importRepository.checkedUris)
        assertEquals(1, importRepository.imported.size)
        assertEquals(ImportUiState.Finished(importRepository.nextReport), viewModel.importState.value)

        viewModel.dismissImportReport()
        assertEquals(ImportUiState.Idle, viewModel.importState.value)
    }

    @Test
    fun replacingInstalledPack_waitsForConfirmation() {
        importRepository.installedVersion = 1
        viewModel.importDocument("content://pack-v2.json")
        val confirm = viewModel.importState.value as ImportUiState.Confirm
        assertEquals(2, confirm.preview.removedItems)
        assertTrue(importRepository.imported.isEmpty())

        viewModel.confirmImport()
        assertEquals(1, importRepository.imported.size)
        assertEquals(ImportUiState.Finished(importRepository.nextReport), viewModel.importState.value)
    }

    @Test
    fun cancellingReplacement_writesNothing() {
        importRepository.installedVersion = 1
        viewModel.importDocument("content://pack-v2.json")
        viewModel.dismissImportReport()
        assertEquals(ImportUiState.Idle, viewModel.importState.value)
        viewModel.confirmImport()
        assertTrue(importRepository.imported.isEmpty())
    }

    @Test
    fun invalidPack_showsReport_andImportsNothing() {
        val failed = ImportReport(ImportOutcome.FAILED)
        importRepository.failCheck = failed
        viewModel.importDocument("content://broken.json")
        assertEquals(ImportUiState.Finished(failed), viewModel.importState.value)
        assertTrue(importRepository.imported.isEmpty())
    }

    @Test
    fun contentList_showsInstalledPacks_notTheLearnersOwnWords() = runTest {
        val content = FakeContentRepository().apply {
            packs.value = listOf(
                ContentPackInfo("mimikara-n3-vocab", "Mimikara N3", 1, isSeed = true, lessonCount = 12),
                ContentPackInfo(UserWords.PACK_ID, "Từ của tôi", 1, isSeed = false, lessonCount = 1),
            )
        }
        val settings = SettingsViewModel(preferences, content, importRepository)
        settings.state.launchIn(backgroundScope)
        runCurrent()
        assertEquals(listOf("mimikara-n3-vocab"), (settings.state.value as UiState.Success).data.packs.map { it.id })
    }
}


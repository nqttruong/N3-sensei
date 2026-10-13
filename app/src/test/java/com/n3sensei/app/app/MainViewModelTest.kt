package com.n3sensei.app.app

import com.n3sensei.app.domain.importing.ImportOutcome
import com.n3sensei.app.domain.importing.ImportReport
import com.n3sensei.app.domain.integrity.DatabaseRecoveryRepository
import com.n3sensei.app.domain.integrity.DatabaseResetNotice
import com.n3sensei.app.domain.reminder.ReminderScheduler
import com.n3sensei.app.domain.reminder.ReminderSettings
import com.n3sensei.app.testing.FakeContentImportRepository
import com.n3sensei.app.testing.FakeUserPreferencesRepository
import com.n3sensei.app.testing.MainDispatcherRule
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class MainViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val imports = FakeContentImportRepository()
    private val noRecovery = object : DatabaseRecoveryRepository {
        override val notice = MutableStateFlow<DatabaseResetNotice?>(null)
        override fun dismiss() = Unit
    }
    private val scheduler = object : ReminderScheduler {
        override fun apply(settings: ReminderSettings) = Unit
    }

    private fun viewModel() = MainViewModel(FakeUserPreferencesRepository(), imports, scheduler, noRecovery)

    @Test
    fun bundledPacksInstalled_noFailureShown() {
        imports.bundledReports = listOf(ImportReport(ImportOutcome.IMPORTED, packTitle = "A"), ImportReport(ImportOutcome.UP_TO_DATE))
        val vm = viewModel()
        assertEquals(1, imports.bundledInstalls)
        assertTrue(vm.bundledContentFailures.value.isEmpty())
    }

    @Test
    fun failedPack_isNamed_andRetryClearsIt() {
        imports.bundledReports = listOf(
            ImportReport(ImportOutcome.IMPORTED, packTitle = "Gói minh họa"),
            ImportReport(ImportOutcome.FAILED, packTitle = "Từ vựng Mimikara N3 (880 từ)"),
        )
        val vm = viewModel()
        assertEquals(listOf("Từ vựng Mimikara N3 (880 từ)"), vm.bundledContentFailures.value)

        imports.bundledReports = listOf(ImportReport(ImportOutcome.UP_TO_DATE), ImportReport(ImportOutcome.IMPORTED))
        vm.installBundledContent()
        assertEquals(2, imports.bundledInstalls)
        assertTrue(vm.bundledContentFailures.value.isEmpty())
    }

    @Test
    fun unexpectedError_isReportedInsteadOfCrashing() {
        imports.bundledError = IOException("asset missing")
        val vm = viewModel()
        assertEquals(1, vm.bundledContentFailures.value.size)
        vm.dismissBundledContentFailures()
        assertTrue(vm.bundledContentFailures.value.isEmpty())
    }
}

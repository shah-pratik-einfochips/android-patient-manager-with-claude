package com.einfochips.patienthealth

import com.einfochips.patienthealth.data.PatientRepository
import com.einfochips.patienthealth.ui.ExportKind
import com.einfochips.patienthealth.ui.PatientDetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExportAuditTest {
    private val audit = FakeAuditDao()
    private lateinit var vm: PatientDetailViewModel

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        vm = PatientDetailViewModel(PatientRepository(FakePatientDao(), FakeVitalDao(), audit, FakeFhir()))
    }

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun exportsAreAuditedWithThePatientIdOnly() = runTest {
        vm.load("p1")
        vm.logExport(ExportKind.CSV)
        vm.logExport(ExportKind.PDF)
        assertEquals(listOf("export.csv", "export.pdf"), audit.rows.value.map { it.action })
        assertTrue(audit.rows.value.all { it.detail == "p1" })
    }

    @Test fun nothingIsAuditedWithoutAPatient() = runTest {
        vm.logExport(ExportKind.CSV)
        assertTrue(audit.rows.value.isEmpty())
    }
}

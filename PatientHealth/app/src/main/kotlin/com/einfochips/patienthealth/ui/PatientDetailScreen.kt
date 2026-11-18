package com.einfochips.patienthealth.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.einfochips.patienthealth.domain.Patient
import java.time.LocalDate
import java.util.Date
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientDetailScreen(
    patientId: String,
    showBack: Boolean,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onDeleted: () -> Unit,
) {
    val vm: PatientDetailViewModel = hiltViewModel(key = patientId)
    LaunchedEffect(patientId) { vm.load(patientId) }
    val patient by vm.patient.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(sync.message) { sync.message?.let { snackbar.showSnackbar(it); vm.messageShown() } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(patient?.fullName.orEmpty()) },
                navigationIcon = { if (showBack) IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to patient list") } },
                actions = {
                    if (sync.syncing) CircularProgressIndicator(Modifier.padding(12.dp).padding(2.dp), strokeWidth = 2.dp)
                    else IconButton(vm::syncNow) { Icon(Icons.Filled.Refresh, "Sync with FHIR server") }
                    IconButton({ onEdit(patientId) }) { Icon(Icons.Filled.Edit, "Edit patient") }
                    IconButton({ confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete patient") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            val tabs = listOf("Profile", "Enter Vitals", "Dashboard")
            TabRow(tab) { tabs.forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) } }
            val p = patient
            if (p != null) when (tab) {
                0 -> ProfileTab(p)
                1 -> VitalsEntryTab(vm) { tab = 2 }
                else -> DashboardTab(p, vm)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete patient?") },
            text = { Text("This permanently removes the patient and all of their vitals from this device.") },
            confirmButton = { TextButton({ confirmDelete = false; vm.delete(onDeleted) }) { Text("Delete") } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ProfileTab(p: Patient) {
    val df = rememberDateTimeFormat()
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Heading("Demographics")
        LabeledRow("Medical ID", p.medicalId)
        LabeledRow("Date of birth", LocalDate.ofEpochDay(p.dobEpochDay).toString())
        LabeledRow("Gender", p.gender.title)
        LabeledRow("Ethnicity", p.ethnicity)
        LabeledRow("Phone", p.phone)
        LabeledRow("Email", p.email)
        Heading("Medical")
        LabeledRow("Blood type", p.bloodType.label)
        LabeledRow("Allergies", p.allergies)
        LabeledRow("Medications", p.medications)
        LabeledRow("Conditions", p.conditions)
        Heading("Examination")
        LabeledRow("Purpose", p.examPurpose)
        LabeledRow("Type", p.examType.label)
        LabeledRow("Performed by", p.performedBy)
        LabeledRow("Referred by", p.referredBy)
        Heading("Consent")
        LabeledRow("Status", if (p.consentGiven) "Granted" else "Not granted")
        p.consentAt?.let { LabeledRow("Date", df.format(Date(it))) }
        LabeledRow("FHIR ID", p.fhirId ?: "Not uploaded")
    }
}

@Composable
internal fun Heading(text: String) =
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))

internal enum class ExportKind(val label: String, val mimeType: String, val extension: String) {
    CSV("Export CSV", "text/csv", "csv"),
    PDF("Export PDF", "application/pdf", "pdf"),
}

/** One button per [ExportKind]; [build] writes the file and returns its shareable URI. */
@Composable
internal fun ShareButtons(context: android.content.Context, build: suspend (ExportKind) -> android.net.Uri, enabled: Boolean) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ExportKind.entries.forEach { kind ->
            androidx.compose.material3.OutlinedButton(
                onClick = {
                    scope.launch {
                        val uri = build(kind)
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = kind.mimeType
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(send, kind.label))
                    }
                },
                enabled = enabled,
            ) { Text(kind.label) }
        }
    }
}

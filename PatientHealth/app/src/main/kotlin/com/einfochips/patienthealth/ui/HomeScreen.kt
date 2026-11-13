package com.einfochips.patienthealth.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.einfochips.patienthealth.domain.Patient

/** Phones show list then detail; widths >= 840dp (tablets, unfolded, landscape) show both side by side. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onAdd: () -> Unit, onEdit: (String) -> Unit, onSettings: () -> Unit, vm: PatientListViewModel = hiltViewModel()) {
    val patients by vm.patients.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val wide = LocalConfiguration.current.screenWidthDp >= 840

    if (!wide && selectedId != null) {
        BackHandler { selectedId = null }
        PatientDetailScreen(selectedId!!, showBack = true, onBack = { selectedId = null }, onEdit = onEdit, onDeleted = { selectedId = null })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Patients") },
                actions = { IconButton(onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings") } },
            )
        },
        floatingActionButton = { FloatingActionButton(onAdd) { Icon(Icons.Filled.Add, contentDescription = "Add patient") } },
    ) { padding ->
        Row(Modifier.padding(padding).fillMaxSize()) {
            Column(Modifier.weight(if (wide) 0.38f else 1f).fillMaxHeight()) {
                OutlinedTextField(
                    query, vm::setQuery, label = { Text("Search name or medical ID") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp),
                )
                PatientList(patients, selectedId) { selectedId = it }
            }
            if (wide) {
                Box(Modifier.weight(0.62f).fillMaxHeight()) {
                    val id = selectedId
                    if (id == null) Text("Select a patient", Modifier.align(Alignment.Center), style = MaterialTheme.typography.titleMedium)
                    else PatientDetailScreen(id, showBack = false, onBack = {}, onEdit = onEdit, onDeleted = { selectedId = null })
                }
            }
        }
    }
}

@Composable
private fun PatientList(patients: List<Patient>, selectedId: String?, onSelect: (String) -> Unit) {
    if (patients.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("No patients", style = MaterialTheme.typography.titleMedium)
                Text("Tap + to register a patient.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    LazyColumn {
        items(patients, key = { it.id }) { p ->
            ListItem(
                headlineContent = { Text(p.fullName) },
                supportingContent = { Text("ID ${p.medicalId}") },
                modifier = Modifier.clickable(onClickLabel = "Open patient details") { onSelect(p.id) },
                tonalElevation = if (p.id == selectedId) 4.dp else 0.dp,
            )
            HorizontalDivider()
        }
    }
}

package com.einfochips.patienthealth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.toggleable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.einfochips.patienthealth.domain.BloodType
import com.einfochips.patienthealth.domain.ExaminationType
import com.einfochips.patienthealth.domain.Gender

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientFormScreen(vm: PatientFormViewModel, onClose: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val saveError by vm.error.collectAsStateWithLifecycle()
    val show = s.submitted

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (vm.isEditing) "Edit Patient" else "New Patient") },
            navigationIcon = { IconButton(onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel") } },
            actions = { TextButton({ vm.save(onClose) }) { Text("Save") } },
        )
    }) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Section("Identity")
            FormField("Medical ID", s.medicalId, { v -> vm.update { it.copy(medicalId = v) } }, error = s.medicalIdError, showError = show, capitalization = KeyboardCapitalization.Characters)
            FormField("First name", s.firstName, { v -> vm.update { it.copy(firstName = v) } }, error = s.firstNameError, showError = show)
            FormField("Last name", s.lastName, { v -> vm.update { it.copy(lastName = v) } }, error = s.lastNameError, showError = show)
            DateField("Date of birth", s.dob, { d -> vm.update { it.copy(dob = d) } }, s.dobError, show)
            DropdownField("Gender", Gender.entries, s.gender, { it.title }) { g -> vm.update { it.copy(gender = g) } }
            FormField("Ethnicity", s.ethnicity, { v -> vm.update { it.copy(ethnicity = v) } })

            Section("Contact")
            FormField("Phone", s.phone, { v -> vm.update { it.copy(phone = v) } }, error = s.phoneError, showError = show, keyboardType = KeyboardType.Phone)
            FormField("Email", s.email, { v -> vm.update { it.copy(email = v) } }, error = s.emailError, showError = show, keyboardType = KeyboardType.Email, capitalization = KeyboardCapitalization.None)

            Section("Medical")
            DropdownField("Blood type", BloodType.entries, s.bloodType, { it.label }) { b -> vm.update { it.copy(bloodType = b) } }
            FormField("Allergies", s.allergies, { v -> vm.update { it.copy(allergies = v) } }, singleLine = false)
            FormField("Medications", s.medications, { v -> vm.update { it.copy(medications = v) } }, singleLine = false)
            FormField("Health conditions", s.conditions, { v -> vm.update { it.copy(conditions = v) } }, singleLine = false)

            Section("Examination")
            FormField("Purpose of examination", s.examPurpose, { v -> vm.update { it.copy(examPurpose = v) } }, singleLine = false)
            DropdownField("Type of examination", ExaminationType.entries, s.examType, { it.label }) { t -> vm.update { it.copy(examType = t) } }
            FormField("Performed by", s.performedBy, { v -> vm.update { it.copy(performedBy = v) } })
            FormField("Referred by", s.referredBy, { v -> vm.update { it.copy(referredBy = v) } })

            Section("Privacy & Consent")
            Row(
                Modifier.fillMaxWidth().toggleable(s.consentGiven, role = Role.Switch) { c -> vm.update { it.copy(consentGiven = c) } }
                    .semantics { stateDescription = if (s.consentGiven) "Consent granted" else "Consent not granted" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Patient consents to storage and sharing of health data", Modifier.weight(1f))
                Switch(s.consentGiven, null)
            }
            Text(
                "Consent may be withdrawn at any time. Without consent, data stays on this device only (HIPAA / GDPR).",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("", Modifier.padding(24.dp))
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
}

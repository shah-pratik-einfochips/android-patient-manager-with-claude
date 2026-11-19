package com.einfochips.patienthealth.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Row
import java.text.DateFormat
import java.util.Locale
import java.util.TimeZone
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Text field with a visible, screen-reader-friendly error and an optional abnormal-value marker (icon + text, not colour alone). */
@Composable
fun FormField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    error: String? = null,
    abnormal: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Words,
    singleLine: Boolean = true,
    showError: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = modifier,
        singleLine = singleLine,
        isError = showError && error != null,
        supportingText = {
            when {
                showError && error != null -> Text(error)
                abnormal -> Text("Outside normal range")
            }
        },
        trailingIcon = if (abnormal) {
            { Icon(Icons.Filled.Warning, contentDescription = "Abnormal value", tint = MaterialTheme.colorScheme.error) }
        } else null,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, capitalization = capitalization),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DropdownField(label: String, options: List<T>, selected: T, text: (T) -> String, modifier: Modifier = Modifier.fillMaxWidth(), onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, { expanded = it }, modifier) {
        OutlinedTextField(
            value = text(selected), onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded, { expanded = false }) {
            options.forEach { o -> DropdownMenuItem(text = { Text(text(o)) }, onClick = { onSelect(o); expanded = false }) }
        }
    }
}

/**
 * Medium date, short time in the device locale. Reused across recompositions, but rebuilt when the
 * time zone or locale changes, since a formatter captures both when it is created.
 */
@Composable
fun rememberDateTimeFormat(): DateFormat =
    remember(TimeZone.getDefault().id, Locale.getDefault()) { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

/** Read-only field that opens a date picker; future dates can't be selected. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, date: LocalDate?, onPick: (LocalDate) -> Unit, error: String?, showError: Boolean) {
    var open by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = date?.toString().orEmpty(), onValueChange = {}, readOnly = true, label = { Text(label) },
        isError = showError && error != null,
        supportingText = { if (showError && error != null) Text(error) },
        trailingIcon = { TextButton({ open = true }) { Text("Pick") } },
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "$label, ${date ?: "not set"}. Opens date picker" },
    )
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= System.currentTimeMillis()
            },
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton({ open = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}

@Composable
fun LabeledRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.4f))
        Text(value.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(0.6f))
    }
}

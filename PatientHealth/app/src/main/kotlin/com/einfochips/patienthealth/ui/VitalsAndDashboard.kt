package com.einfochips.patienthealth.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.einfochips.patienthealth.data.ExportService
import com.einfochips.patienthealth.domain.DashboardCalculator
import com.einfochips.patienthealth.domain.HeightUnit
import com.einfochips.patienthealth.domain.Patient
import com.einfochips.patienthealth.domain.TemperatureUnit
import com.einfochips.patienthealth.domain.ValidationService
import com.einfochips.patienthealth.domain.VitalRecord
import com.einfochips.patienthealth.domain.VitalType
import com.einfochips.patienthealth.domain.WeightUnit
import com.einfochips.patienthealth.domain.oneDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date
import java.util.Locale

// region Vitals entry

@Composable
fun VitalsEntryTab(vm: PatientDetailViewModel, onSaved: () -> Unit) {
    val s by vm.entry.collectAsStateWithLifecycle()
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(s.recordedAt).atZone(zone).toLocalDate()

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Heading("Measurement")
        DateField("Date (defaults to today)", date, { d ->
            val time = if (d == LocalDate.now()) LocalTime.now() else LocalTime.NOON
            vm.updateEntry { it.copy(recordedAt = d.atTime(time).atZone(zone).toInstant().toEpochMilli()) }
        }, s.dateError, showError = true)

        Heading("Blood Pressure")
        VitalField("Systolic (mmHg)", s.systolic, { v -> vm.updateEntry { it.copy(systolic = v) } }, s.systolicError, s.isAbnormal(VitalType.SYSTOLIC))
        VitalField("Diastolic (mmHg)", s.diastolic, { v -> vm.updateEntry { it.copy(diastolic = v) } }, s.diastolicError, s.isAbnormal(VitalType.DIASTOLIC))
        s.pairError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        val map = s.mapValue
        Row(Modifier.semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("MAP (auto): ${map?.let { "${it.oneDecimal()} mmHg" } ?: "—"}")
            if (s.isAbnormal(VitalType.MAP)) Icon(Icons.Filled.Warning, "Abnormal value", tint = MaterialTheme.colorScheme.error)
        }

        Heading("Vitals")
        VitalField("Heart rate (bpm)", s.heartRate, { v -> vm.updateEntry { it.copy(heartRate = v) } }, s.heartRateError, s.isAbnormal(VitalType.HEART_RATE))
        VitalField("Respiratory rate (breaths/min)", s.respiratoryRate, { v -> vm.updateEntry { it.copy(respiratoryRate = v) } }, s.respiratoryError,
            s.isAbnormal(VitalType.RESPIRATORY_RATE))
        VitalField("SpO₂ (%)", s.spo2, { v -> vm.updateEntry { it.copy(spo2 = v) } }, s.spo2Error, s.isAbnormal(VitalType.SPO2))

        Heading("Body Measurements")
        UnitChoice("Height unit", HeightUnit.entries, s.heightUnit, { it.label }) { u -> vm.updateEntry { it.copy(heightUnit = u) } }
        if (s.heightUnit == HeightUnit.CM) {
            VitalField("Height (cm)", s.heightCm, { v -> vm.updateEntry { it.copy(heightCm = v) } }, s.heightError)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VitalField("Feet", s.heightFeet, { v -> vm.updateEntry { it.copy(heightFeet = v) } }, s.heightError, modifier = Modifier.weight(1f))
                VitalField("Inches", s.heightInches, { v -> vm.updateEntry { it.copy(heightInches = v) } }, null, modifier = Modifier.weight(1f))
            }
        }
        UnitChoice("Weight unit", WeightUnit.entries, s.weightUnit, { it.label }) { u -> vm.updateEntry { it.copy(weightUnit = u) } }
        VitalField("Weight (${s.weightUnit.label})", s.weight, { v -> vm.updateEntry { it.copy(weight = v) } }, s.weightError)
        UnitChoice("Temperature unit", TemperatureUnit.entries, s.temperatureUnit, { it.label }) { u -> vm.updateEntry { it.copy(temperatureUnit = u) } }
        VitalField("Temperature (${s.temperatureUnit.label})", s.temperature, { v -> vm.updateEntry { it.copy(temperature = v) } }, s.temperatureError,
            s.isAbnormal(VitalType.TEMPERATURE))

        Heading("Pain & Symptoms")
        val pain = s.painLevel
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Pain level: ${pain?.let { "$it / 10" } ?: "not set"}")
            if (pain != null) TextButton({ vm.updateEntry { it.copy(painLevel = null) } }) { Text("Clear") }
        }
        Slider(
            value = (pain ?: 0).toFloat(), onValueChange = { v -> vm.updateEntry { it.copy(painLevel = v.toInt()) } },
            valueRange = 0f..10f, steps = 9,
            modifier = Modifier.semantics { contentDescription = "Pain level, ${pain ?: "not set"} out of 10" },
        )
        FormField("Symptoms", s.symptoms, { v -> vm.updateEntry { it.copy(symptoms = v) } }, singleLine = false)

        Button({ vm.saveVitals(onSaved) }, enabled = s.canSave, modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp)) { Text("Save Vitals") }
    }
}

/** Numeric vitals field: decimal keyboard, live error and the abnormal-value marker. */
@Composable
private fun VitalField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    error: String?,
    abnormal: Boolean = false,
    modifier: Modifier = Modifier.fillMaxWidth(),
) = FormField(label, value, onChange, modifier, error = error, abnormal = abnormal, keyboardType = KeyboardType.Decimal)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> UnitChoice(label: String, options: List<T>, selected: T, text: (T) -> String, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().semantics { contentDescription = label }) {
        options.forEachIndexed { i, o ->
            SegmentedButton(selected == o, { onSelect(o) }, SegmentedButtonDefaults.itemShape(i, options.size)) { Text(text(o)) }
        }
    }
}

// endregion

// region Dashboard

@Composable
fun DashboardTab(patient: Patient, vm: PatientDetailViewModel) {
    val all by vm.recentVitals.collectAsStateWithLifecycle()
    val records = remember(all) { DashboardCalculator.recent(all) }
    val newestFirst = remember(records) { records.asReversed() }
    var type by rememberSaveable { mutableStateOf(VitalType.HEART_RATE) }
    var chart by rememberSaveable { mutableStateOf(true) }
    val context = LocalContext.current

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val chipState = androidx.compose.foundation.lazy.rememberLazyListState()
        androidx.compose.runtime.LaunchedEffect(Unit) { chipState.scrollToItem(VitalType.chartable.indexOf(type).coerceAtLeast(0)) }
        LazyRow(state = chipState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(VitalType.chartable) { t -> FilterChip(type == t, { type = t }, label = { Text(t.title) }) }
        }
        ViewToggle(chart) { chart = it }

        if (records.isEmpty()) {
            Text("No vitals recorded in the last ${DashboardCalculator.WINDOW_DAYS} days.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 24.dp))
        } else {
            val points = remember(records, type) { DashboardCalculator.points(records, type) }
            StatsRow(points, type)
            if (chart) VitalChart(points, type) else VitalTable(newestFirst)
        }
        ShareButtons(context, enabled = records.isNotEmpty(), build = { kind ->
            withContext(Dispatchers.Default) {
                val bytes = when (kind) {
                    ExportKind.CSV -> ExportService.csv(records).toByteArray()
                    ExportKind.PDF -> ExportService.pdf(patient, records)
                }
                ExportService.share(context, bytes, kind.extension)
            }
        })
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewToggle(chart: Boolean, onChange: (Boolean) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        SegmentedButton(chart, { onChange(true) }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Chart") }
        SegmentedButton(!chart, { onChange(false) }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Table") }
    }
}

@Composable
private fun StatsRow(points: List<DashboardCalculator.Point>, type: VitalType) {
    val st = remember(points) { DashboardCalculator.statsOf(points) }
    if (st == null) { Text("No ${type.title} readings"); return }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        Stat("Latest", st.latest, type, flag = ValidationService.isAbnormal(st.latest, type))
        Stat("Min", st.min, type); Stat("Max", st.max, type); Stat("Avg", st.average, type)
    }
}

@Composable
private fun Stat(label: String, v: Double, type: VitalType, flag: Boolean = false) {
    val shown = v.oneDecimal()
    Column(
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "$label $shown ${type.unit}${if (flag) ", abnormal" else ""}" },
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(if (flag) "$shown ⚠" else shown, style = MaterialTheme.typography.titleMedium, color = if (flag) MaterialTheme.colorScheme.error else Color.Unspecified)
        Text(type.unit, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun VitalTable(records: List<VitalRecord>) {
    val df = rememberDateTimeFormat()
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(records, key = { it.id }) { r ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(df.format(Date(r.recordedAt)), style = MaterialTheme.typography.titleSmall)
                    VitalType.entries.forEach { t ->
                        r.value(t)?.let { v ->
                            val bad = ValidationService.isAbnormal(v, t)
                            Text(
                                "${t.title}: ${v.oneDecimal()} ${t.unit}${if (bad) "  ⚠ abnormal" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (bad) MaterialTheme.colorScheme.error else Color.Unspecified,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Line chart on a Canvas. Abnormal points are drawn as larger rings (shape + colour) and the normal range is shaded. */
@Composable
private fun VitalChart(points: List<DashboardCalculator.Point>, type: VitalType) {
    if (points.isEmpty()) { Text("No ${type.title} readings"); return }
    val measurer = rememberTextMeasurer()
    val primary = MaterialTheme.colorScheme.primary
    val error = MaterialTheme.colorScheme.error
    val band = Color(0xFF2E7D32).copy(alpha = 0.15f)
    val axis = MaterialTheme.colorScheme.onSurfaceVariant
    val label = TextStyle(fontSize = 10.sp, color = axis)
    // Derived once per data change, not on every draw pass.
    val (minValue, maxValue) = remember(points) { points.minOf { it.value } to points.maxOf { it.value } }
    val (t0, t1) = remember(points) { points.minOf { it.time } to points.maxOf { it.time } }
    val dayFormat = remember { java.text.SimpleDateFormat("d MMM", Locale.getDefault()) }
    val summary = "${type.title} line chart, ${points.size} readings from ${minValue.oneDecimal()} to " +
        "${maxValue.oneDecimal()} ${type.unit}, ${points.count { it.abnormal }} abnormal. Switch to Table for details."

    Canvas(Modifier.fillMaxWidth().height(260.dp).semantics { contentDescription = summary }) {
        val padL = 48.dp.toPx(); val padB = 24.dp.toPx(); val padT = 8.dp.toPx(); val padR = 12.dp.toPx()
        val w = size.width - padL - padR; val h = size.height - padT - padB
        var lo = minOf(minValue, type.normalRange?.start ?: Double.MAX_VALUE)
        var hi = maxOf(maxValue, type.normalRange?.endInclusive ?: Double.MIN_VALUE)
        if (hi - lo < 1e-6) { lo -= 1; hi += 1 }
        val pad = (hi - lo) * 0.1; lo -= pad; hi += pad
        fun x(t: Long) = padL + if (t1 == t0) w / 2 else ((t - t0).toFloat() / (t1 - t0)) * w
        fun y(v: Double) = padT + (1 - ((v - lo) / (hi - lo)).toFloat()) * h

        type.normalRange?.let { r ->
            drawRect(band, Offset(padL, y(r.endInclusive)), Size(w, y(r.start) - y(r.endInclusive)))
        }
        drawLine(axis, Offset(padL, padT), Offset(padL, padT + h)); drawLine(axis, Offset(padL, padT + h), Offset(padL + w, padT + h))
        listOf(hi, (hi + lo) / 2, lo).forEach { v ->
            drawText(measurer, "%.0f".format(Locale.US, v), Offset(2.dp.toPx(), y(v) - 7.dp.toPx()), label)
        }
        drawText(measurer, dayFormat.format(Date(t0)), Offset(padL, padT + h + 4.dp.toPx()), label)
        if (t1 != t0) drawText(measurer, dayFormat.format(Date(t1)), Offset(padL + w - 36.dp.toPx(), padT + h + 4.dp.toPx()), label)

        val path = Path()
        points.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(p.time), y(p.value)) else path.lineTo(x(p.time), y(p.value)) }
        drawPath(path, primary, style = Stroke(2.dp.toPx()))
        points.forEach { p ->
            val c = Offset(x(p.time), y(p.value))
            if (p.abnormal) {
                drawCircle(error, 7.dp.toPx(), c); drawCircle(Color.White, 3.dp.toPx(), c)
            } else drawCircle(primary, 4.dp.toPx(), c)
        }
    }
    Text("Shaded band = normal range. Ringed red points = abnormal.", style = MaterialTheme.typography.labelSmall)
}

// endregion

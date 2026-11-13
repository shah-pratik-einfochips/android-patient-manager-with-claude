package com.einfochips.patienthealth.data

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.einfochips.patienthealth.domain.Patient
import com.einfochips.patienthealth.domain.ValidationService
import com.einfochips.patienthealth.domain.VitalRecord
import com.einfochips.patienthealth.domain.VitalType
import com.einfochips.patienthealth.domain.roundTo
import java.io.File
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.util.Date

/** CSV and PDF export. Files go to the app cache with PHI-free names and are shared through FileProvider. */
object ExportService {

    /** RFC 4180-style CSV: values in canonical units, fields with commas/quotes/newlines escaped. */
    fun csv(records: List<VitalRecord>): String {
        val types = VitalType.entries
        val header = listOf("Date") + types.map { "${it.title} (${it.unit})" } + "Symptoms"
        val rows = records.sortedBy { it.recordedAt }.map { r ->
            listOf(Instant.ofEpochMilli(r.recordedAt).toString()) +
                types.map { t -> r.value(t)?.roundTo(1)?.toString().orEmpty() } + r.symptoms
        }
        return (listOf(header) + rows).joinToString("\n") { row -> row.joinToString(",") { escape(it) } }
    }

    private fun escape(s: String) =
        if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    /** Short column headings so they never overlap at the PDF's small font size. */
    private val pdfHeader = mapOf(
        VitalType.SYSTOLIC to "Sys mmHg", VitalType.DIASTOLIC to "Dia mmHg", VitalType.HEART_RATE to "HR bpm",
        VitalType.TEMPERATURE to "Temp °C", VitalType.RESPIRATORY_RATE to "Resp /min", VitalType.SPO2 to "SpO₂ %",
        VitalType.PAIN_LEVEL to "Pain /10",
    )

    /** Renders a paginated PDF vitals report; abnormal values are red and marked with an asterisk (not colour alone). */
    fun pdf(patient: Patient, records: List<VitalRecord>): ByteArray {
        val columns = listOf(VitalType.SYSTOLIC, VitalType.DIASTOLIC, VitalType.HEART_RATE, VitalType.TEMPERATURE,
            VitalType.RESPIRATORY_RATE, VitalType.SPO2, VitalType.PAIN_LEVEL)
        val pageW = 595; val pageH = 842; val margin = 36f
        val title = Paint().apply { textSize = 18f; isFakeBoldText = true }
        val head = Paint().apply { textSize = 8f; isFakeBoldText = true }
        val body = Paint().apply { textSize = 8f }
        val bad = Paint().apply { textSize = 8f; color = Color.RED; isFakeBoldText = true }
        val df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        val colW = (pageW - 2 * margin) / (columns.size + 1.5f)

        val doc = PdfDocument()
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0f
        fun row(cells: List<Pair<String, Paint>>) {
            val c = page!!.canvas
            cells.forEachIndexed { i, (t, p) -> c.drawText(t, margin + if (i == 0) 0f else colW * (i + 0.5f), y, p) }
            y += 14f
        }
        fun newPage() {
            page?.let { doc.finishPage(it) }
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, ++pageNo).create())
            y = margin + 10
            page!!.canvas.drawText("Vitals Report", margin, y, title); y += 22
            page!!.canvas.drawText("${patient.fullName} · ID ${patient.medicalId} · DOB ${LocalDate.ofEpochDay(patient.dobEpochDay)}", margin, y, body); y += 22
            row(listOf("Date" to head) + columns.map { pdfHeader.getValue(it) to head })
        }
        newPage()
        for (r in records.sortedBy { it.recordedAt }) {
            if (y > pageH - margin) newPage()
            row(listOf(df.format(Date(r.recordedAt)) to body) + columns.map { t ->
                val v = r.value(t)
                when {
                    v == null -> "–" to body
                    ValidationService.isAbnormal(v, t) -> "${v.roundTo(1)}*" to bad
                    else -> v.roundTo(1).toString() to body
                }
            })
        }
        page?.let { doc.finishPage(it) }
        return java.io.ByteArrayOutputStream().also { doc.writeTo(it); doc.close() }.toByteArray()
    }

    /** Writes bytes to a cache file and returns a shareable content:// URI. */
    fun share(context: Context, bytes: ByteArray, extension: String): android.net.Uri {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "vitals-${System.currentTimeMillis()}.$extension").apply { writeBytes(bytes) }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}

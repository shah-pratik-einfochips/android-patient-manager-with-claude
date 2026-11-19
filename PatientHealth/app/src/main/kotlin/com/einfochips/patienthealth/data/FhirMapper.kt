package com.einfochips.patienthealth.data

import com.einfochips.patienthealth.domain.Gender
import com.einfochips.patienthealth.domain.Patient
import com.einfochips.patienthealth.domain.VitalRecord
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.time.Instant
import java.time.LocalDate

/** Converts between app models and FHIR R4 Patient / Observation JSON. */
object FhirMapper {
    const val MEDICAL_ID_SYSTEM = "urn:einfochips:patient-health:medical-id"
    private const val LOINC = "http://loinc.org"
    private const val UCUM = "http://unitsofmeasure.org"

    private val JsonElement?.string: String? get() = (this as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content
    private operator fun JsonObject.invoke(key: String): JsonElement? = this[key]

    // region Patient

    fun patientResource(p: Patient): JsonObject = buildJsonObject {
        put("resourceType", "Patient")
        p.fhirId?.let { put("id", it) }
        putJsonArray("identifier") { add(buildJsonObject { put("system", MEDICAL_ID_SYSTEM); put("value", p.medicalId) }) }
        putJsonArray("name") {
            add(buildJsonObject { put("family", p.lastName); putJsonArray("given") { add(kotlinx.serialization.json.JsonPrimitive(p.firstName)) } })
        }
        put("gender", p.gender.fhir)
        put("birthDate", LocalDate.ofEpochDay(p.dobEpochDay).toString())
        putJsonArray("telecom") {
            if (p.phone.isNotEmpty()) add(buildJsonObject { put("system", "phone"); put("value", p.phone) })
            if (p.email.isNotEmpty()) add(buildJsonObject { put("system", "email"); put("value", p.email) })
        }
        putJsonArray("extension") {
            add(buildJsonObject { put("url", "urn:einfochips:patient-health:blood-type"); put("valueString", p.bloodType.label) })
        }
    }

    /** Applies the demographics present in [json] onto [p]; absent fields are left unchanged. */
    fun applyPatient(json: JsonObject, p: Patient): Patient {
        var out = p.copy(fhirId = json("id").string ?: p.fhirId, updatedAt = System.currentTimeMillis())
        (json("name") as? JsonArray)?.firstOrNull()?.jsonObject?.let { name ->
            name("family").string?.let { out = out.copy(lastName = it) }
            (name("given") as? JsonArray)?.firstOrNull().string?.let { out = out.copy(firstName = it) }
        }
        json("gender").string?.let { out = out.copy(gender = Gender.fromFhir(it)) }
        json("birthDate").string?.let { s -> runCatching { LocalDate.parse(s) }.getOrNull()?.let { out = out.copy(dobEpochDay = it.toEpochDay()) } }
        (json("telecom") as? JsonArray)?.forEach { t ->
            val o = t.jsonObject
            when (o("system").string) {
                "phone" -> o("value").string?.let { out = out.copy(phone = it) }
                "email" -> o("value").string?.let { out = out.copy(email = it) }
            }
        }
        return out
    }

    // endregion

    // region Observation

    private data class Spec(val code: String, val display: String, val unit: String, val read: (VitalRecord) -> Double?, val write: (VitalRecord, Double) -> VitalRecord)

    private val specs = listOf(
        Spec("8867-4", "Heart rate", "/min", { it.heartRate }, { r, v -> r.copy(heartRate = v) }),
        Spec("8302-2", "Body height", "cm", { it.heightCm }, { r, v -> r.copy(heightCm = v) }),
        Spec("29463-7", "Body weight", "kg", { it.weightKg }, { r, v -> r.copy(weightKg = v) }),
        Spec("8310-5", "Body temperature", "Cel", { it.temperatureC }, { r, v -> r.copy(temperatureC = v) }),
        Spec("9279-1", "Respiratory rate", "/min", { it.respiratoryRate }, { r, v -> r.copy(respiratoryRate = v) }),
        Spec("59408-5", "Oxygen saturation by pulse oximetry", "%", { it.spo2 }, { r, v -> r.copy(spo2 = v) }),
        Spec("72514-3", "Pain severity 0-10", "{score}", { it.painLevel }, { r, v -> r.copy(painLevel = v) }),
    )

    // Blood pressure is one panel Observation with two components, plus MAP on its own.
    private const val BP_PANEL = "85354-9"
    private const val MEAN_BP = "8478-0"
    private const val MM_HG = "mm[Hg]"
    private val bpComponents = listOf(
        Spec("8480-6", "Systolic blood pressure", MM_HG, { it.systolic }, { r, v -> r.copy(systolic = v) }),
        Spec("8462-4", "Diastolic blood pressure", MM_HG, { it.diastolic }, { r, v -> r.copy(diastolic = v) }),
    )

    private fun codeable(code: String, display: String) = buildJsonObject {
        putJsonArray("coding") { add(buildJsonObject { put("system", LOINC); put("code", code); put("display", display) }) }
        put("text", display)
    }

    private fun quantity(v: Double, unit: String) = buildJsonObject {
        put("value", v); put("unit", unit); put("system", UCUM); put("code", unit)
    }

    /** Fields every vital-signs Observation shares. */
    private fun JsonObjectBuilder.envelope(code: String, display: String, subject: JsonObject, whenIso: String) {
        put("resourceType", "Observation"); put("status", "final")
        putJsonArray("category") { add(vitalSignsCategory) }
        put("code", codeable(code, display)); put("subject", subject); put("effectiveDateTime", whenIso)
    }

    private fun observation(code: String, display: String, v: Double, unit: String, subject: JsonObject, whenIso: String) = buildJsonObject {
        envelope(code, display, subject, whenIso)
        put("valueQuantity", quantity(v, unit))
    }

    private val vitalSignsCategory = buildJsonObject {
        putJsonArray("coding") {
            add(buildJsonObject { put("system", "http://terminology.hl7.org/CodeSystem/observation-category"); put("code", "vital-signs") })
        }
    }

    /** One BP panel (systolic + diastolic components) plus MAP, then one Observation per other recorded vital. */
    fun observations(r: VitalRecord, patientFhirId: String): List<JsonObject> {
        val subject = buildJsonObject { put("reference", "Patient/$patientFhirId") }
        val whenIso = Instant.ofEpochMilli(r.recordedAt).toString()
        val out = mutableListOf<JsonObject>()
        if (r.systolic != null && r.diastolic != null) {
            out += buildJsonObject {
                envelope(BP_PANEL, "Blood pressure panel", subject, whenIso)
                putJsonArray("component") {
                    for (c in bpComponents) add(buildJsonObject { put("code", codeable(c.code, c.display)); put("valueQuantity", quantity(c.read(r)!!, c.unit)) })
                }
            }
            r.map?.let { out += observation(MEAN_BP, "Mean blood pressure", it, MM_HG, subject, whenIso) }
        }
        for (s in specs) s.read(r)?.let { out += observation(s.code, s.display, it, s.unit, subject, whenIso) }
        return out
    }

    private fun JsonObject.firstCode(): String? =
        ((this("code") as? JsonObject)?.get("coding") as? JsonArray)?.firstOrNull()?.jsonObject?.get("code").string

    private fun JsonObject.quantityValue(): Double? = (this("valueQuantity") as? JsonObject)?.get("value")?.jsonPrimitive?.doubleOrNull

    /** Builds a record from observations sharing one timestamp, or null when none is a known vital. */
    fun vitalRecord(observations: List<JsonObject>, patientId: String, recordedAt: Long): VitalRecord? {
        var r = VitalRecord(patientId = patientId, recordedAt = recordedAt)
        var found = false
        for (o in observations) {
            // A BP panel carries its values in components; every other vital is the Observation itself.
            val (candidates, parts) =
                if (o.firstCode() == BP_PANEL) bpComponents to (o("component") as? JsonArray).orEmpty().map { it.jsonObject }
                else specs to listOf(o)
            for (part in parts) {
                val spec = candidates.firstOrNull { it.code == part.firstCode() } ?: continue
                part.quantityValue()?.let { r = spec.write(r, it); found = true }
            }
        }
        return if (found) r else null
    }

    // endregion
}
